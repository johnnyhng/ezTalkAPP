# Speaker Local LLM Auto Mode Plan

## Current Status

Last updated: 2026-04-18

Current implementation snapshot:

- `Speaker` semantic parse does not run inside `ui/speaker/SpeakerContentScreen.kt`.
- The actual `SpeakerSemanticModule` is created in `ui/speaker/SpeakerScreen.kt`.
- `SpeakerScreen.kt` currently wires Gemini fallback directly with:
  - `GeminiLlmProvider`
  - `GoogleAuthGeminiAccessTokenProvider`
- The current LLM path is cloud-only and depends on Gemini OAuth.
- `SettingsScreen.kt` currently exposes:
  - Gemini model selection
  - Gemini OAuth readiness check
  - Advanced settings expansion
- `UserSettings.kt` and `SettingsManager.kt` do not currently store any "LLM execution mode" or "local vs cloud" preference.
- The repo currently has no AICore / ML Kit GenAI Prompt API integration yet.

Current limitation:

- On devices that may support Gemini Nano through Android AICore, the app still always uses cloud Gemini OAuth for speaker semantic fallback.

## Goal

Allow users to choose how `Speaker` semantic parse fallback executes:

- `Local (Auto)`:
  - default mode
  - prefer Gemini Nano on-device when available
  - automatically fall back to cloud Gemini OAuth when local inference is unavailable
- `Cloud LLM`:
  - always use the existing Gemini OAuth cloud path

This should be surfaced in `Advanced settings`, while preserving the current cloud Gemini path as a safe fallback.

## Scope

This plan applies to the `Speaker` semantic parse / LLM fallback path only.

It does not change:

- Local ASR
- TTS playback
- Speaker lexical ranking
- General app-wide LLM behavior outside `Speaker`

## Why The Decision Point Is In `SpeakerScreen`

Although the user-facing content pane is rendered by `ui/speaker/SpeakerContentScreen.kt`, the LLM runtime is currently assembled in `ui/speaker/SpeakerScreen.kt`.

That means:

- settings UI belongs in `screens/SettingsScreen.kt`
- runtime provider selection belongs in `ui/speaker/SpeakerScreen.kt` or a factory it calls
- `SpeakerContentScreen.kt` should stay UI-only

This keeps provider logic out of the content composable and aligns with the existing separation already present in the repo.

## Proposed User Experience

### Advanced Settings

Add a new section under `Advanced settings`:

- `LLM execution mode`
  - `Local (Auto)` as default
  - `Cloud LLM`

### Status Display

When `Local (Auto)` is selected, show local runtime status:

- `Available`
- `Downloadable`
- `Downloading`
- `Unavailable`

Also show cloud fallback readiness:

- Gemini OAuth status remains visible

When `Cloud LLM` is selected:

- keep current Gemini OAuth status UI
- local Gemini Nano status can still be shown as informational, but it should not control execution

### Runtime Behavior

`Local (Auto)`:

- if Gemini Nano is `AVAILABLE`, execute local prompt inference
- if Gemini Nano is `DOWNLOADABLE`, `DOWNLOADING`, or `UNAVAILABLE`, fall back to cloud Gemini OAuth

`Cloud LLM`:

- always execute the current Gemini OAuth cloud provider

## Proposed Settings Model

### New Setting

Add a new persisted setting in `UserSettings`:

- `speakerLlmExecutionMode: String`

Suggested values:

- `auto_local`
- `cloud`

### Files Affected

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/data/classes/UserSettings.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/SettingsManager.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/HomeViewModel.kt`

### Requirements

- default to `auto_local`
- fully persisted through DataStore
- exposed through `HomeViewModel` update API

## Proposed Runtime Architecture

### New Abstractions

Keep `SpeakerSemanticModule` depending on `LlmProvider`, not on UI or AICore APIs directly.

Add the following pieces:

- `LocalGeminiNanoLlmProvider`
- `SpeakerLocalLlmAvailabilityChecker`
- `SpeakerLlmProviderFactory` or equivalent `remember...` assembly helper

Optional supporting model:

- `SpeakerLocalLlmStatus`
  - `Available`
  - `Downloadable`
  - `Downloading`
  - `Unavailable`
  - `Error`

### Provider Responsibilities

`LocalGeminiNanoLlmProvider`

- wraps ML Kit GenAI Prompt API
- converts `LlmRequest` into local prompt input
- converts model text output into `LlmResponse`
- normalizes local errors into existing `LlmError` shapes where practical

`GeminiLlmProvider`

- remains the cloud Gemini OAuth provider
- no behavioral regression for current cloud flow

### Factory Responsibilities

The provider factory should decide which provider is passed into `SpeakerSemanticModule` based on:

- `speakerLlmExecutionMode`
- current local availability status
- cloud Gemini model setting

This removes hard-coded provider construction from `SpeakerScreen.kt`.

## Proposed AICore / Gemini Nano Integration

## Official Runtime Constraint

According to Android and ML Kit documentation, Gemini Nano on Android is exposed through AICore and ML Kit GenAI APIs, and availability must be checked at runtime.

Implication:

- device model alone is not a sufficient condition
- even on a high-end phone, local Gemini Nano may still be unavailable, not yet downloaded, or still initializing

## Availability States

Use ML Kit Prompt API status as the source of truth:

- `AVAILABLE`
- `DOWNLOADABLE`
- `DOWNLOADING`
- `UNAVAILABLE`

Recommended mapping:

- `AVAILABLE` -> use local provider
- `DOWNLOADABLE` -> surface download-ready state and fall back to cloud for now
- `DOWNLOADING` -> surface progress or pending state and fall back to cloud
- `UNAVAILABLE` -> skip local and fall back to cloud

## Download Policy

For initial rollout, do not auto-download Gemini Nano silently from `Speaker` runtime.

Recommended first-phase behavior:

- detect capability
- report status in settings
- fall back to cloud when local is not ready

Reason:

- download management adds lifecycle, permissions, progress, and failure-state complexity
- the first product goal is execution-mode switching, not model lifecycle management

## UI Plan For `SettingsScreen`

Add a new advanced-settings block in:

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SettingsScreen.kt`

### New UI Elements

- execution mode dropdown or radio group
- local Gemini Nano availability text
- optional helper text explaining auto fallback behavior

Suggested labels:

- `LLM execution mode`
- `Local (Auto)`
- `Cloud LLM`
- `Local Gemini Nano status`
- `Cloud Gemini OAuth status`

### UX Rules

- `Local (Auto)` must be the default selection
- avoid implying local execution is guaranteed on specific devices
- avoid blocking the user if local is unavailable
- make fallback behavior explicit in the UI text

## Runtime Plan For `SpeakerScreen`

Update:

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/ui/speaker/SpeakerScreen.kt`

Current behavior:

- directly creates `GeminiLlmProvider(accessTokenProvider = GoogleAuthGeminiAccessTokenProvider(appContext))`

Target behavior:

- assemble provider through execution-mode-aware logic
- pass the selected provider into `SpeakerSemanticModule`
- keep `llmModel` selection behavior intact for the cloud path

Recommended runtime policy:

- if `geminiModel == "none"`, disable cloud LLM path
- in `auto_local` mode:
  - prefer local provider when available
  - otherwise use cloud provider if configured
- in `cloud` mode:
  - use cloud provider only

## Logging Plan

Add structured logs that clearly indicate which path was used:

- `source=local`
- `source=cloud`
- `executionMode=auto_local|cloud`
- `localStatus=available|downloadable|downloading|unavailable`

This is required for:

- debugging fallback behavior
- comparing semantic parse quality between local and cloud
- diagnosing device-specific AICore issues

## Compatibility And Risk Notes

## Main Risks

### 1. Local API availability is runtime-dependent

Even if a device family is expected to support Gemini Nano, the app still must check runtime availability.

### 2. Prompt API quality may differ from cloud Gemini

Local Gemini Nano prompt quality may differ by:

- device
- model revision
- language
- LoRA/config shipped through AICore

### 3. Chinese command parsing must be validated

The current `Speaker` use case is command-oriented and likely Chinese-heavy. Local prompt quality must be validated on real devices before assuming parity with cloud Gemini.

### 4. ML Kit Prompt API introduces new dependency and lifecycle complexity

The repo does not currently contain any AICore or ML Kit GenAI integration. This means implementation will require:

- dependency additions
- runtime status checks
- error handling for AICore setup failures

## Rollout Strategy

### Phase 1

- add execution mode setting
- add advanced settings UI
- add local availability checker
- add provider factory
- keep cloud fallback working

Success criteria:

- user can switch between `Local (Auto)` and `Cloud LLM`
- app never regresses current cloud behavior
- local unavailability does not break semantic fallback

### Phase 2

- implement actual `LocalGeminiNanoLlmProvider`
- validate prompt formatting and JSON output stability
- tune thresholds if local output differs materially from cloud output

Success criteria:

- local prompt execution works on supported devices
- semantic decisions remain parseable by `SpeakerSemanticModule`

### Phase 3

- improve local status UX
- optionally support manual download trigger
- add metrics / test coverage / device-specific verification notes

## Suggested File-Level Changes

New files likely needed:

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/llm/LocalGeminiNanoLlmProvider.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/llm/SpeakerLocalLlmAvailabilityChecker.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/llm/SpeakerLlmProviderFactory.kt`

Existing files likely to change:

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/data/classes/UserSettings.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/SettingsManager.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/HomeViewModel.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SettingsScreen.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/ui/speaker/SpeakerScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh-rTW/strings.xml`
- `app/build.gradle.kts`

## Testing Plan

Manual verification should cover:

- device with local Gemini Nano `AVAILABLE`
- device with local Gemini Nano `DOWNLOADABLE`
- device with local Gemini Nano `UNAVAILABLE`
- signed-in cloud path
- cloud path with OAuth unavailable
- `geminiModel = none`

Key checks:

- advanced settings selection persists
- runtime path matches selected mode
- `Local (Auto)` falls back safely
- `Cloud LLM` ignores local availability
- no crash when AICore is missing or still initializing

## Open Questions

- whether the first version should expose manual Gemini Nano download controls
- whether local availability should be checked only in settings, or also refreshed in `SpeakerScreen` before each semantic session
- whether `geminiModel = none` should disable only cloud fallback or all LLM fallback
- whether local execution should later become a shared app-wide LLM option instead of a `Speaker`-specific setting

## Recommended Decision

Proceed with a `Speaker`-scoped implementation first:

- add `Local (Auto)` and `Cloud LLM` in advanced settings
- keep cloud Gemini OAuth as the fallback safety net
- perform runtime AICore availability checks instead of device-name assumptions
- avoid auto-download in the first implementation

This gives the app a safe migration path toward on-device Gemini Nano without destabilizing the current speaker semantic flow.

## References

- Android Developers, Gemini Nano:
  - https://developer.android.com/ai/gemini-nano
- ML Kit GenAI Prompt API get started:
  - https://developers.google.com/ml-kit/genai/prompt/android/get-started

Inference from those sources:

- Gemini Nano access on Android must be determined through runtime feature status, not by phone model alone.
- `Local (Auto)` is therefore the correct product behavior, because it matches the real availability model enforced by AICore / ML Kit.
