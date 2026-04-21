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

### Package Layout

Add a new package:

```text
app/src/main/java/tw/com/johnnyhng/eztalk/asr/experiment/
```

Suggested files:

- `ZhuyinModels.kt`
  - key group models
  - candidate models
  - emotion/tone models
- `ZhuyinTextEngine.kt`
  - append behavior
  - trailing Zhuyin stripping
  - small pure functions
- `ZhuyinPromptBuilders.kt`
  - word prompt builder
  - sentence prompt builder
- `ZhuyinSuggestionModule.kt`
  - builds `LlmRequest`
  - calls `LlmProvider`
  - parses JSON candidates
- `ExperimentViewModel.kt`
  - screen state
  - key input
  - candidate loading
  - candidate selection
  - clear/backspace/history

Keep UI composables under:

```text
app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/ExperimentScreen.kt
```

or split UI-only composables into:

```text
app/src/main/java/tw/com/johnnyhng/eztalk/asr/ui/experiment/
```

if the screen grows.

### Reuse Existing LLM Stack

Use existing classes:

- `TranscriptCorrectionProviderFactory`
- `GeminiLlmProvider`
- `GoogleAuthGeminiAccessTokenProvider`
- `LlmRequest`
- `LlmOutputFormat.JSON`

Use the selected Gemini model from `UserSettings.geminiModel`. If it is `none`
or blank, show a disabled/error state in Experiment instead of making a request.

## UI Plan

The `Experiment` tab is already route-scoped to landscape. Use the extra width
for an input-assist layout:

- Top region: current composed text and selected mode.
- Left rail: initial phrases and emotion selector.
- Center region: candidate words/sentences.
- Bottom region: Zhuyin grouped keyboard.
- Utility buttons: backspace, clear, word suggestions, sentence suggestions.

Interaction flow:

1. User taps initial phrase or Zhuyin key.
2. Text buffer updates immediately.
3. User taps word suggestion or sentence suggestion.
4. App calls Gemini through existing provider.
5. Candidate list renders.
6. User taps candidate.
7. Word candidate applies with `appendWord()`. Sentence candidate replaces or
   commits the current sentence depending on selected mode.

Initial implementation can omit speech output and persistence. Add those only
after the core input loop is usable.

## Implementation Phases

### Phase 1: Pure Domain And Tests

- Add Zhuyin key grid models.
- Add `stripTrailingZhuyin()` and `appendZhuyinCandidate()`.
- Add unit tests for:
  - pure Zhuyin input replaced by candidate
  - mixed Chinese plus trailing Zhuyin
  - tone marks
  - candidate starting with `-`
  - no trailing Zhuyin

Verification:

```bash
./gradlew :app:testDebugUnitTest
```

### Phase 2: Prompt Builders And Parser

- Add word and sentence prompt builders.
- Add JSON response parser for candidate arrays.
- Add tests for prompt contents and parser behavior.

Verification:

```bash
./gradlew :app:testDebugUnitTest
```

### Phase 3: Suggestion Module

- Add `ZhuyinSuggestionModule`.
- Reuse `LlmProvider`.
- Return typed success/error states.
- Add fake provider tests.

Verification:

```bash
./gradlew :app:testDebugUnitTest
```

### Phase 4: Experiment ViewModel

- Add state reducer for:
  - key input
  - initial phrase input
  - emotion selection
  - loading/error/candidate state
  - applying candidates
  - backspace/clear
- Keep dependencies injectable for tests.

Verification:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugKotlin
```

### Phase 5: Compose UI

- Replace empty `ExperimentScreen` with the native landscape UI.
- Keep controls stable in size to avoid layout shifts.
- Use Material icons for utility actions where available.
- Add strings for labels and error states in English and zh-TW resources.

Verification:

```bash
./gradlew :app:compileDebugKotlin
```

Manual checks:

- Switching into Experiment forces landscape.
- Switching out returns portrait.
- Keyboard keys do not overlap in landscape.
- Empty/no-model/error states are readable.
- Candidate selection updates text as expected.

## Risks And Decisions

- Do not import Lit/Web components; they do not fit this Android app.
- Do not add a WebView unless native implementation proves too expensive.
- Keep Project VOICE behavior isolated in Experiment until validated.
- Prefer JSON prompts over numbered-list parsing for reliability.
- Keep the first version local to Gemini cloud provider. Local Gemini Nano can
  be evaluated later using the existing local-LLM code paths.
- Preserve Apache-2.0 attribution if copying substantial upstream prompt or
  keyboard content verbatim.

## Done Criteria

- `Experiment` tab provides a usable Zhuyin input loop.
- Word and sentence candidates come from Gemini and are selectable.
- Core text behavior is covered by unit tests.
- Existing tabs continue to compile and navigate normally.
- `./gradlew :app:compileDebugKotlin` passes.
