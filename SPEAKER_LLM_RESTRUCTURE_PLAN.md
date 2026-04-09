# Speaker LLM Restructure Plan

## Current Status

Last updated: 2026-04-09

Overall status:

- Phase 1: Completed
- Phase 2: Completed
- Phase 3: Completed
- Phase 4: Completed
- Phase 5: Partially Completed

Repo state snapshot:

- `speaker/` runtime/domain package exists and is in active use.
- `ui/speaker/` UI package exists and is in active use.
- `llm/` shared abstraction package exists with base request/response types, OAuth token plumbing, and a real Gemini HTTP provider.
- `prompt/` package exists and is already used by `SpeakerSemanticModule.buildLlmRequest(...)`.
- `SpeakerSemanticModule` exists and acts as the semantic entry point.
- `Speaker` semantic behavior is still lexical-first, but the LLM fallback path now executes through the provider hook.
- `GeminiLlmProvider` now performs real OAuth-backed Gemini HTTP calls with provider-side `401` invalidate/retry.
- `SettingsScreen` now surfaces Google account status, Gemini OAuth readiness, consent recovery, and scope diagnostics.
- Gemini model selection is now settings-backed instead of hard-coded in `SpeakerScreen`.
- LLM fallback toggle/state has started moving out of `SpeakerScreen` and into `SpeakerViewModel`.
- Content semantic candidate/highlight state has started moving into `SpeakerViewModel`.
- Successful fallback decisions are now wired back into the same candidate/autoplay flow used by lexical decisions.
- `app/google-services.json` is present and matches the Android package name.
- Android OAuth token fetch is wired and device-validated far enough to reach Gemini HTTP 200 responses.
- `MediaPipe` runtime has already been removed for 16 KB page-size safety.

Files already present in repo:

- `speaker/`
  - `SpeakerViewModel.kt`
  - `SpeakerRepository.kt`
  - `SpeakerPlaybackController.kt`
  - `SpeakerAsrController.kt`
  - `SpeakerCommandResolver.kt`
  - `SpeakerUiModels.kt`
  - `SpeakerSemanticIndexer.kt`
  - `SpeakerSemanticSearch.kt`
  - `SpeakerSearchModels.kt`
  - `SpeakerSemanticLogging.kt`
  - `SpeakerContentAsrActions.kt`
  - `SpeakerSemanticUiActions.kt`
  - `SpeakerImportManager.kt`
- `ui/speaker/`
  - `SpeakerScreen.kt`
  - `SpeechFileExplorer.kt`
  - `SpeakerContentScreen.kt`
  - `LocalASRWidget.kt`
- `screens/`
  - `SettingsScreen.kt`
- `llm/`
  - `GeminiLlmProvider.kt`
  - `GeminiApiClient.kt`
  - `GeminiAccessTokenProvider.kt`
  - `LlmOutputFormat.kt`
  - `LlmProvider.kt`
  - `LlmRequest.kt`
  - `LlmResponse.kt`
  - `LlmUsageMetadata.kt`
  - `LlmError.kt`
- `auth/`
  - `GoogleAccountSession.kt`
  - `GoogleSignInManager.kt`
- `prompt/`
  - `PromptTemplate.kt`
  - `SpeakerSemanticPromptBuilder.kt`

Files not yet present:

- runtime/viewmodel ownership of the remaining fallback orchestration that still lives in `SpeakerScreen`
- broader provider integration tests beyond the current success / `401` / malformed-payload coverage
- end-to-end automated coverage for the real Gemini HTTP fallback path

## Goal

Restructure `Speaker` semantic search and future Gemini integration so that:

- UI code does not own semantic or LLM runtime logic.
- `Speaker` domain logic is separated from generic LLM provider logic.
- Prompt definitions are isolated from both UI and network/provider code.
- Future Gemini-based semantic search can be reused outside `Speaker`.
- Gemini usage should consume the signed-in user's quota via Android OAuth, not an app-owned API key.

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
- `SettingsScreen.kt` remains the current OAuth/account control surface until that flow is moved behind a shared session/runtime layer

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
- `SpeakerUiModels.kt`
- `SpeakerSemanticModule.kt`
- `SpeakerSemanticIndexer.kt`
- `SpeakerSemanticSearch.kt`
- `SpeakerSearchModels.kt`
- `SpeakerSemanticLogging.kt`
- `SpeakerContentAsrActions.kt`
- `SpeakerSemanticUiActions.kt`
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
- `GeminiAccessTokenProvider.kt`
- `GeminiApiClient.kt`

Responsibilities:
- Execute prompt requests
- Select provider/model
- Normalize errors and metadata
- Handle quota, retry, and fallback policy at provider boundary
- Acquire a scoped Gemini access token from Android sign-in state
- Invalidate expired tokens and retry once on `401 Unauthorized`

Should not contain:
- `Speaker` UI assumptions
- `Speaker` line-index mapping logic
- Prompt text specific to one feature

### `auth/` or `session/`

Purpose:
- Android sign-in/session ownership
- Google account selection state
- Credential Manager / Google Sign-In integration

Suggested files:
- `GoogleSignInManager.kt`
- `GeminiOAuthSession.kt`

Responsibilities:
- Start and restore sign-in state
- Expose current signed-in account
- Keep OAuth flow out of `SpeakerScreen`
- Provide account/session context to `GeminiAccessTokenProvider`

Should not contain:
- `Speaker` semantic mapping
- Prompt construction
- Gemini response parsing

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
- `ui/speaker` -> token fetch / token refresh / OAuth recovery logic

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

Status: Completed

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

Status: Completed

Move `Speaker` UI files into `ui/speaker/`:

- move `SpeakerScreen.kt`
- move `SpeechFileExplorer.kt`
- move `SpeakerContentScreen.kt`
- move `LocalASRWidget.kt`
- move `SpeakerUiModels.kt`

No behavior change.

### Phase 3

Status: Completed

Introduce shared LLM module:

- add `llm/` package
- define `LlmProvider`
- add `GeminiLlmProvider`

Current repo state:

- `llm/` package exists
- `LlmProvider` exists
- `LlmRequest.kt` / `LlmResponse.kt` / `LlmUsageMetadata.kt` / `LlmOutputFormat.kt` exist
- `GeminiLlmProvider.kt` now performs OAuth-backed Gemini HTTP execution
- `GeminiApiClient.kt` exists for provider-side HTTP transport
- `GeminiAccessTokenProvider.kt` exists for scoped token fetch / invalidation
- provider behavior is covered by unit tests for success, `401` retry, and malformed payload handling

No `Speaker` integration yet.

### Phase 4

Status: Completed

Introduce prompt package:

- add `prompt/`
- add `SpeakerSemanticPromptBuilder`

Current repo state:

- `prompt/` package exists
- `PromptTemplate.kt` exists
- `SpeakerSemanticPromptBuilder.kt` exists
- prompt output is already wired into `SpeakerSemanticModule.buildLlmRequest(...)`

Still keep lexical-only runtime behavior.

### Phase 5

Status: Partially Completed

Add Gemini-backed semantic resolution to `SpeakerSemanticModule`:

- call shared `LlmProvider`
- use `SpeakerSemanticPromptBuilder`
- return structured `SpeakerSemanticDecision`
- route real Gemini execution through Android OAuth instead of API-key auth

Current repo state:

- `SpeakerSemanticModule` exists as the semantic entry point
- `SpeakerSemanticDecision` and `SpeakerSemanticResolution` exist
- lexical semantic search remains the active implementation
- `SpeakerSemanticModule` can already build an LLM fallback request from ranked lexical candidates
- `SpeakerSemanticModule` can parse an `LlmResponse` back into `SpeakerSemanticDecision`
- LLM response parsing is now stricter and requires structured JSON for actionable candidate/autoplay results
- fenced JSON parsing was hardened to avoid Android regex-engine crashes during real Gemini responses
- `SpeakerSemanticModule` can call `LlmProvider.generate(...)` through `tryLlmFallback(...)`
- `SpeakerScreen` has an LLM fallback preview toggle and preview status
- fallback state has started moving into `SpeakerViewModel`
- `Speaker` now exercises the provider hook on lexical no-match
- successful fallback decisions now re-enter the same candidate/autoplay path used by lexical decisions
- real Gemini network execution is wired through `GeminiLlmProvider`
- Android OAuth token fetch and provider-side `401` invalidation/retry are implemented
- device testing has already reached: ASR no-match -> OAuth token success -> Gemini HTTP 200 -> response parse success
- parser behavior and provider retry behavior are both covered by unit tests
- remaining gaps are moving more fallback orchestration out of `SpeakerScreen`, adding model config, and expanding automated coverage around the real fallback path

## Gemini-Specific Rules

Do not hardcode a preview model inside UI or domain code.

Use provider config with default stable model:

- default candidate: `gemini-2.5-flash`

Allow model override in settings or config layer.

Preview models can be tested later, but the architecture must treat model name as configuration, not a constant embedded in `SpeakerScreen`.

Do not use an app-owned Gemini API key for this flow.

Follow the Android OAuth approach in `ezTalk Android OAuth 2.0 實作指南.md`:

- use the signed-in Google account
- request the `https://www.googleapis.com/auth/generative-language.retriever` scope for app-managed Android token fetch
- fetch an access token before each Gemini call
- if Gemini returns `401`, invalidate the cached token and retry once
- keep OAuth/account/session management outside `SpeakerScreen`

`google-services.json` can support project/app wiring, but it does not replace OAuth sign-in, scope consent, or access-token refresh logic.

Use OAuth tokens in the provider layer with `Authorization: Bearer <token>`.

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

1. Validate the Android OAuth flow on device, especially scope consent and `UserRecoverableAuthException` recovery
2. Continue shrinking screen-local orchestration so fallback behavior lives in runtime/viewmodel instead of `SpeakerScreen`
3. Add config/settings-backed Gemini model selection
4. Expand provider and semantic fallback test coverage beyond the current parser / retry happy-path set
5. Add automated coverage around the real Gemini HTTP fallback path where feasible
6. Keep Gemini auth OAuth-based; do not introduce API-key-based auth for this flow

Only after that should Phase 5 be considered complete.
