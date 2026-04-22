# Project VOICE Experiment Tab Integration Plan

## Goal

Integrate the Traditional Chinese Zhuyin input workflow from
`google/project-voice` branch `zh-tw-impl` into ezTalk's native Android
`Experiment` tab.

The integration should be native Jetpack Compose, not a WebView port. Reuse the
existing ezTalk Gemini OAuth/provider stack and keep the feature isolated from
Home, Translate, Speaker, and Data Collect until the interaction model is
validated.

## Source Scope

Reviewed upstream branch:

- Repository: `https://github.com/google/project-voice`
- Branch: `zh-tw-impl`
- Head: `a61684cec372d4cc3d4f7e246fab59ee788e4598`
- Relevant commits:
  - `fcec825` implements Traditional Chinese language behavior.
  - `06920a0` adds Zhuyin single-row keyboard layout.
  - `8d23fc4` adds Traditional Chinese Zhuyin word prompt.
  - `8045930` adds Traditional Chinese Zhuyin sentence prompt.
  - `95e7e82` adds `zh-TW` localization metadata for the web app.

The initial baseline commit and implementation-log commits are useful context
but do not need direct Android code migration.

## Core Concepts To Port

### Zhuyin Keyboard

Port the single-row grouped Zhuyin keyboard:

- `ㄅ`: `ㄅㄆㄇㄈ`
- `ㄉ`: `ㄉㄊㄋㄌ`
- `ㄍ`: `ㄍㄎㄏ`
- `ㄐ`: `ㄐㄑㄒ`
- `ㄓ`: `ㄓㄔㄕㄖ`
- `ㄗ`: `ㄗㄘㄙ`
- `ㄧ`: `ㄧㄨㄩ`
- `ㄚ`: `ㄚㄛㄜㄝ`
- `ㄞ`: `ㄞㄟㄠㄡ`
- `ㄢ`: `ㄢㄣㄤㄥㄦ`
- `聲詞`: space and tone marks `˙ˊˇˋ`

Android implementation should use stable-size Compose controls suitable for
landscape use. Each grouped key should expand into selectable characters.

### Traditional Chinese Behavior

Port the language behavior:

- Locale: `zh-TW`
- Initial phrases: `你`, `我`, `他`, `她`, `好`, `今天`, `昨天`, `明天`, `謝謝`
- Sentence tones/emotions:
  - normal: `陳述` / `普通`
  - question: `疑問` / `提問`
  - request: `請求` / `拜託`
  - negative: `否定` / `否定`
- Segment and join by character.
- `appendWord()` removes trailing Zhuyin and tone marks before appending the
  selected candidate.

Zhuyin stripping regex from upstream:

```kotlin
Regex("[\\u3100-\\u312F\\u02CA\\u02C7\\u02CB\\u02D9]+$")
```

### Gemini Prompts

Port the upstream word and sentence prompt intent, but adapt the output format
to strict JSON for easier Android parsing and tests.

Word suggestion output target:

```json
{
  "candidates": ["你", "您", "呢", "那"]
}
```

Sentence suggestion output target:

```json
{
  "candidates": [
    "我想吃點東西。",
    "我想吃藥。"
  ]
}
```

The prompt should require Traditional Chinese output and allow mixed Zhuyin,
Chinese characters, and partial roman letters as input.

## Proposed Android Architecture

### Reactive Suggestion Flow

To match the "Project VOICE" fluid experience, shift from manual button triggers to a reactive flow:

- `ExperimentViewModel` uses a `MutableStateFlow` for `inputText`.
- A `Debounce` operator (500-800ms) triggers `requestWordSuggestions()` automatically.
- Sentence suggestions remain manual or semi-automatic to avoid excessive API costs.
- Add a `isThinking` state to `ExperimentUiState` to drive visual feedback during LLM calls.

### Three-Column Landscape Layout ("Command Center")

Reorganize `ExperimentScreen.kt` to use a 2:5:3 width distribution:

1. **Left Rail (20%): Context & History**
   - Emotion/Tone selector.
   - Initial phrases.
   - Recent phrase history (tappable to re-insert).
2. **Center Column (50%): Canvas & Sentences**
   - Main input display (expanded).
   - Sentence Suggestion results (priority display).
   - "Thinking" status shimmer.
3. **Right Rail (30%): Word Candidates & Utils**
   - Word/Character completions.
   - Utility actions (Clear, Backspace, Copy).

## Implementation Phases

### Phase 1-5: Baseline Integration (Completed)
*Native landscape UI, manual suggestions, core Zhuyin engine.*

### Phase 6: Reactive Suggestions & Polishing
- Implement debounced auto-suggestions in `ExperimentViewModel`.
- Transition UI to the Three-Column "Command Center" layout.
- Add "Thinking" indicators (e.g., Pulsing icons or Shimmer) to candidate areas.
- Improve candidate button styling to match Project VOICE "pill" aesthetics.

### Phase 7: Exclusionary Prediction & Prompt Refactor
- Update `ZhuyinPromptBuilders.kt` to explicitly forbid repeating the `inputText` prefix in candidates.
- Shift LLM role to "AAC Communication Assistant" for Traditional Chinese users.
- Implement "Append-only" logic in `ExperimentViewModel.applyCandidate()` for a continuous selection flow.

### Phase 8: High-Contrast & Fitts's Law Layout
- Refactor `ExperimentScreen.kt` to use high-contrast styling (Dark backgrounds for candidates, white text).
- Increase touch targets and inter-element spacing (Gap >= 12dp, Height >= 64dp).
- Remove visual redundancy by ensuring candidate UI only shows the predicted completion (no prefix).

### Phase 9: Firestore Context Integration
- Define Firestore collection `experiment_contexts` to store scenario-specific vocabulary and instructions.
- Implement dynamic context loading in `ExperimentViewModel` from Firestore based on selected profile.
- Inject scenario-specific metadata into Gemini prompts to weight suggestions toward specific domains (e.g., medical, professional, or care-taking).

## Implementation Status

- **Baseline:** Completed.
- **Refinement (Project VOICE parity):** In Progress.
  - [x] Hide Title/Tab bars.
  - [x] Debounced auto-suggestions.
  - [x] Full-width vertical layout with scrolling.
  - [x] Separate Word/Sentence candidate pools.
  - [x] TTS Integration & Global Loading Indicator.
  - [ ] **Phase 7: Exclusionary Prediction Prompt.** (Next)
  - [ ] **Phase 8: High-contrast large-target layout.**
  - [ ] **Phase 9: Firestore Context Integration.**
