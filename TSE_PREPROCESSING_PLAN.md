# TSE Preprocessing Plan

## Goal

When the user enables TSE, the audio pipeline should become:

`Mic Audio -> TSE -> VAD -> ASR`

TSE is treated as a preprocessing stage, not as a replacement for VAD.

## Scope

This plan only covers:

- enabling TSE preprocessing through app settings
- applying TSE before VAD and ASR
- saving a TSE-preprocessed wav artifact
- saving the matching raw wav artifact for comparison
- keeping existing backend behavior unchanged for now

This plan does **not** include:

- sending TSE-preprocessed audio to backend
- changing backend APIs
- changing transcript upload behavior
- replacing VAD with TSE-only detection

## Product Clarification

The current setting was added as a VAD/TSE choice, but the intended behavior is:

- default: existing `raw -> VAD -> ASR`
- when enabled: `raw -> TSE -> VAD -> ASR`

So the actual product meaning is:

- `Enable TSE preprocessing`

not:

- `Choose VAD or TSE`

The setting name and UI copy should be aligned to this meaning in a follow-up cleanup if needed.

## Current VAD Integration Points

The current app uses VAD directly in these main flows:

1. `app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/RecognitionManager.kt`
2. `app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerAsrController.kt`
3. `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/TranslateScreen.kt`

Shared VAD state and helper methods live in:

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/SimulateStreamingAsr.kt`

## Proposed Runtime Behavior

### TSE disabled

No behavior change:

- microphone PCM goes directly into VAD
- VAD segments drive ASR
- saved wav behavior remains as-is

### TSE enabled

For each microphone stream:

1. read raw PCM from microphone
2. feed raw PCM into a TSE frame adapter
3. run `NativeTSE.processFrame(...)` on fixed-size frames
4. rebuild a continuous preprocessed audio stream
5. pass the TSE output into the existing VAD logic
6. run ASR on TSE-preprocessed utterance audio

## Important Technical Constraint

`NativeTSE.processFrame(...)` expects `400` samples per frame.

Current VAD processing is chunk/window based and is not naturally aligned to 400-sample frames. So a dedicated buffering adapter is required.

## Proposed Components

### 1. TSE audio preprocessor

Add a dedicated component, for example:

- `app/src/main/java/.../tse/TseAudioPreprocessor.kt`

Responsibilities:

- accept arbitrary incoming PCM chunks
- buffer them into 400-sample frames
- call `NativeTSE.processFrame(frame)`
- collect processed output frames
- expose continuous processed samples back to the caller
- handle partial tail frames between reads

### 2. Runtime gating

Use the existing DataStore-backed setting to gate preprocessing:

- disabled: bypass TSE completely
- enabled: route microphone PCM through TSE first

### 3. Fallback behavior

If any of these happens:

- `NativeTSE` not initialized
- TSE model file missing
- `processFrame(...)` returns `null`
- runtime native error occurs

then the active recording flow should fall back to raw audio for that session rather than failing the whole recognition path.

## Storage Requirement

When TSE is enabled and a wav artifact is saved, both versions should be kept:

- `*.app.wav`
  - TSE-preprocessed audio
- `*.raw.app.wav`
  - original microphone audio without preprocessing

The naming is intentionally:

- `app.wav` = processed / main output
- `raw.app.wav` = unprocessed comparison artifact

Both files should be written only for local storage for now.

## Backend Restriction

For now, **do not send the TSE-preprocessed wav to backend**.

That means:

- local save is allowed for both files
- existing backend upload / feedback / remote candidate logic should continue using the current path and behavior
- no API payload change in this phase

## Recommended Rollout Order

This rollout is planned in **5 phases**.

### Phase 1. Rename mental model

Align internal implementation intent to:

- `Enable TSE preprocessing`

No pipeline change yet.

### Phase 2. Build frame adapter

Implement the `400`-sample TSE buffering component and validate:

- frame splitting
- tail buffering
- continuous output reconstruction
- null/error fallback

### Phase 3. Integrate in `RecognitionManager`

Start with:

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/RecognitionManager.kt`

because it is the main shared microphone recognition path.

Target behavior:

- raw mic -> optional TSE -> VAD -> ASR
- when saving wav locally and TSE is enabled:
  - save TSE-preprocessed audio as `*.app.wav`
  - save original audio as `*.raw.app.wav`
- do not change backend behavior

### Phase 4. Validate saved audio semantics

Confirm which file is used for:

- local playback
- transcript record association
- backend feedback/upload

For this phase, keep backend path unchanged and only add local dual-save behavior.

## Segment Boundary Rule

When TSE is enabled, the app should still use the existing VAD logic to decide utterance boundaries.

That means:

- `VAD` determines speech start and speech end
- `saveVadSegmentsOnly` continues to control whether the saved range is:
  - VAD-only speech segments
  - or the broader buffered range using the current keep/startOffset/endOffset logic

Most importantly:

- the TSE-preprocessed wav and raw wav must share the same segment boundaries
- when TSE is enabled, countdown progress should follow the VAD timeline derived from the TSE-preprocessed audio, so no separate countdown logic is needed as long as processed/raw streams stay sample-aligned

So the saved files should be paired by the same VAD decision:

- `*.app.wav` uses the TSE-preprocessed samples for that exact range
- `*.raw.app.wav` uses the raw microphone samples for that exact same range

### Phase 5. Extend to other VAD flows

After `RecognitionManager` is stable, apply the same pattern to:

1. `SpeakerAsrController.kt`
2. `TranslateScreen.kt`

Do not change all three flows at once before one path is proven stable.

## File-Level Change Plan

### Must change

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/RecognitionManager.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/TranslateScreen.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerAsrController.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/NativeTSE.kt`
- `app/src/main/java/com/example/tse/NativeTSE.kt`

### Likely add

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/TseAudioPreprocessor.kt`

### May change

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/WavUtil.kt`

This depends on whether the extra `*.raw.app.wav` save should reuse existing wav-save utilities or be handled inline.

## Open Design Questions

1. Which wav should remain the primary transcript-linked file?
Current recommendation:
- use TSE-preprocessed audio as the primary app artifact `*.app.wav`
- save raw microphone audio as an additional sibling file `*.raw.app.wav`

2. Should ASR decode the TSE output or only use TSE for VAD?
Current recommendation:
- use TSE output for both VAD and ASR

3. What should happen when TSE fails mid-session?
Current recommendation:
- soft fallback to raw audio for the rest of that recording session

## Next Implementation Step

The safest first implementation is:

1. add `TseAudioPreprocessor`
2. wire it only into `RecognitionManager`
3. save extra local `*.raw.app.wav` when TSE is enabled
4. keep backend behavior unchanged
5. validate on device before touching `SpeakerAsrController` and `TranslateScreen`
