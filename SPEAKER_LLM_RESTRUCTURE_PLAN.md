# Speaker LLM Restructure Plan

## Goal

Restructure `Speaker` semantic search and future Gemini integration so that:

- UI code does not own semantic or LLM runtime logic.
- `Speaker` domain logic is separated from generic LLM provider logic.
- Prompt definitions are isolated from both UI and network/provider code.
- Future Gemini-based semantic search can be reused outside `Speaker`.

This plan assumes:

- `MediaPipe` is removed from runtime for 16 KB page-size safety.
- Current `Speaker` semantic behavior remains lexical-only until a safe Gemini path is added.

## Target Package Layout

### `ui/speaker/`

Purpose:
- Pure UI composition
- User interaction wiring
- No Gemini SDK or prompt logic

Files to place here:
- `SpeakerScreen.kt`
- `SpeechFileExplorer.kt`
- `SpeakerContentScreen.kt`
- `LocalASRWidget.kt`
- `SpeakerUiModels.kt`

Responsibilities:
- Render panes
- Render ASR widget state
- Send user events to ViewModel/controller
- Show toasts/snackbars if kept in UI layer

Should not contain:
- Prompt strings
- Semantic ranking algorithms
- Provider/client logic
- Gemini model names

### `speaker/`

Purpose:
- `Speaker`-specific domain and runtime logic
- File/document orchestration
- Playback logic
- Local ASR routing
- Semantic resolution orchestration

Files to place here:
- `SpeakerViewModel.kt`
- `SpeakerRepository.kt`
- `SpeakerPlaybackController.kt`
- `SpeakerAsrController.kt`
- `SpeakerCommandResolver.kt`
- `SpeakerSemanticModule.kt`
- `SpeakerSemanticIndexer.kt`
- `SpeakerSemanticSearch.kt`
- `SpeakerSearchModels.kt`
- `SpeakerImportManager.kt`

Responsibilities:
- Manage current folder/document state
- Drive TTS playback
- Handle `Speaker` voice commands
- Build semantic candidates from current content
- Call shared LLM layer when needed
- Map semantic result back to `lineIndex`, `candidate`, `no match`

Should not contain:
- Direct Compose UI rendering
- Raw Gemini HTTP/client code
- Hard-coded provider config in screen files

### `llm/`

Purpose:
- Shared LLM abstraction
- Provider-specific implementation
- Reusable beyond `Speaker`

Files to place here:
- `LlmProvider.kt`
- `LlmRequest.kt`
- `LlmResponse.kt`
- `LlmError.kt`
- `GeminiLlmProvider.kt`

Responsibilities:
- Execute prompt requests
- Select provider/model
- Normalize errors and metadata
- Handle quota, retry, and fallback policy at provider boundary

Should not contain:
- `Speaker` UI assumptions
- `Speaker` line-index mapping logic
- Prompt text specific to one feature

### `prompt/`

Purpose:
- Prompt construction and template isolation

Files to place here:
- `PromptTemplate.kt`
- `SpeakerSemanticPromptBuilder.kt`

Responsibilities:
- Build system instruction
- Build user prompt from ASR text + candidate chunks
- Define expected response schema

Should not contain:
- UI rendering
- HTTP/provider execution
- File I/O

## Dependency Direction

Allowed:

- `ui/speaker` -> `speaker`
- `speaker` -> `llm`
- `speaker` -> `prompt`

Avoid:

- `llm` -> `speaker`
- `prompt` -> `ui/speaker`
- `ui/speaker` -> provider-specific Gemini code

## Speaker Semantic Flow After Refactor

Target flow:

1. `SpeakerScreen` receives local ASR final text.
2. `SpeakerViewModel` or `SpeakerSemanticModule` evaluates the text.
3. First pass:
   - `SpeakerCommandResolver`
4. Second pass:
   - lexical semantic search
5. Third pass:
   - Gemini semantic resolution via `LlmProvider`
6. Result returns as one of:
   - `AutoPlay(lineIndex)`
   - `Candidate(lineIndex)`
   - `NoMatch`
   - `Ambiguous(candidates)`
7. UI updates highlight / scroll / playback state.

## Proposed New Types

### In `speaker/`

- `SpeakerSemanticModule`
  - entry point for semantic resolution
  - hides whether resolution is lexical or Gemini-backed

- `SpeakerSemanticDecision`
  - `AutoPlay`
  - `Candidate`
  - `NoMatch`
  - `Ambiguous`

- `SpeakerSemanticCandidate`
  - `documentId`
  - `lineStart`
  - `lineEnd`
  - `matchedText`
  - `score`

### In `llm/`

- `LlmRequest`
  - `model`
  - `systemInstruction`
  - `userPrompt`
  - `expectedFormat`

- `LlmResponse`
  - `rawText`
  - `parsedJson`
  - `finishReason`
  - `usageMetadata`

### In `prompt/`

- `SpeakerSemanticPromptBuilder`
  - builds prompt using:
    - current ASR text
    - current document chunks
    - strict instruction to choose from candidates only

## Migration Plan

### Phase 1

Move existing `Speaker` runtime logic out of `screens/`:

- move `SpeakerViewModel.kt`
- move `SpeakerRepository.kt`
- move `SpeakerPlaybackController.kt`
- move `SpeakerAsrController.kt`
- move `SpeakerCommandResolver.kt`
- move `SpeakerSemanticIndexer.kt`
- move `SpeakerSemanticSearch.kt`
- move `SpeakerSearchModels.kt`
- move `SpeakerImportManager.kt`

No behavior change.

### Phase 2

Move `Speaker` UI files into `ui/speaker/`:

- move `SpeakerScreen.kt`
- move `SpeechFileExplorer.kt`
- move `SpeakerContentScreen.kt`
- move `LocalASRWidget.kt`
- move `SpeakerUiModels.kt`

No behavior change.

### Phase 3

Introduce shared LLM module:

- add `llm/` package
- define `LlmProvider`
- add `GeminiLlmProvider`

No `Speaker` integration yet.

### Phase 4

Introduce prompt package:

- add `prompt/`
- add `SpeakerSemanticPromptBuilder`

Still keep lexical-only runtime behavior.

### Phase 5

Add Gemini-backed semantic resolution to `SpeakerSemanticModule`:

- call shared `LlmProvider`
- use `SpeakerSemanticPromptBuilder`
- return structured `SpeakerSemanticDecision`

## Gemini-Specific Rules

Do not hardcode a preview model inside UI or domain code.

Use provider config with default stable model:

- default candidate: `gemini-2.5-flash`

Allow model override in settings or config layer.

Preview models can be tested later, but the architecture must treat model name as configuration, not a constant embedded in `SpeakerScreen`.

## Why This Layout

This structure keeps:

- `Speaker` UI maintainable
- LLM code reusable
- prompts auditable
- future provider replacement possible

It also prevents `screens/` from becoming the default dumping ground for:

- prompt text
- semantic ranking
- provider calls
- API integration logic

## Immediate Recommendation

Before adding Gemini:

1. Finish moving `Speaker` runtime files out of `screens/`
2. Create `llm/` and `prompt/` packages
3. Keep lexical fallback working during the move

Only after that should Gemini integration begin.
