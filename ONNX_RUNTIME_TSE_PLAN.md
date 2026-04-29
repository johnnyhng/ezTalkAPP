# ONNX Runtime TSE Plan

## Goal

Replace the current custom JNI-based TSE inference path with an Android-friendly ONNX Runtime path.

The target product behavior remains:

- recording time: `raw -> VAD`
- after utterance end: `raw segment -> TSE -> ASR`

This plan intentionally avoids realtime streaming TSE in the first phase.

## Why This Plan Exists

The current native `VoiceFilter` path has structural problems:

- TSE mask application in native code is currently unreliable
- the native pipeline is not a correct streaming enhancement pipeline
- JNI maintenance cost is high
- debugging C++ DSP and JNI together is slower than debugging Kotlin + official runtime

Using `onnxruntime-android` removes the app-owned JNI layer while preserving the existing `.onnx` model format.

## Scope

This plan covers:

- replacing model inference runtime with official ONNX Runtime Android
- building an offline utterance-level TSE pipeline first
- using the processed utterance for local playback and final ASR
- preserving raw sibling wav saving for comparison

This plan does not cover:

- realtime TSE before VAD
- backend upload changes
- replacing the existing VAD implementation
- native DSP optimization

## Runtime Direction

### Phase 1 target behavior

Use the existing stable segmentation path:

- microphone PCM -> VAD
- VAD decides utterance boundaries
- app extracts one raw utterance segment
- app runs TSE on that utterance segment
- app saves:
  - `*.app.wav` = TSE processed
  - `*.raw.app.wav` = raw
- final ASR uses the processed utterance audio

### Why utterance-level first

This is the lowest-risk path because:

- VAD stays unchanged
- countdown behavior stays unchanged
- segment boundaries stay unchanged
- TSE quality can be evaluated offline on a fixed utterance
- playback quality can be compared directly between raw and processed files

## Recommended Official Package

Primary choice:

- `com.microsoft.onnxruntime:onnxruntime-android`

Reason:

- the current model is already `voice_filter_int8.onnx`
- the runtime is official and open source
- Android integration is available through Java/Kotlin APIs
- we can remove app-owned JNI inference glue

Official references:

- https://onnxruntime.ai/docs/install
- https://onnxruntime.ai/docs/build/android.html
- https://central.sonatype.com/artifact/com.microsoft.onnxruntime/onnxruntime-android

## Major Technical Constraint

Removing JNI does not remove the DSP work.

The app still needs a correct utterance-level audio pipeline:

1. STFT
2. model input tensor preparation
3. ONNX inference
4. mask application to magnitude features
5. ISTFT
6. overlap-add or equivalent reconstruction logic

So ONNX Runtime replaces native model inference, not the signal-processing logic.

## Proposed Components

### 1. `OrtTseEngine`

Suggested file:

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/OrtTseEngine.kt`

Responsibilities:

- load `voice_filter_int8.onnx`
- create and hold `OrtEnvironment` and `OrtSession`
- expose model metadata:
  - input names
  - output names
  - shapes
  - element types
- run inference on prepared tensors

### 2. `TseSignalProcessor`

Suggested file:

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/TseSignalProcessor.kt`

Responsibilities:

- convert raw PCM to the spectral representation expected by the model
- apply the predicted mask to the correct spectral input
- reconstruct waveform output
- operate on a full utterance, not live stream chunks

### 3. `UtteranceTseProcessor`

Suggested file:

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/UtteranceTseProcessor.kt`

Responsibilities:

- take a raw utterance `FloatArray`
- run the full pipeline:
  - preprocess
  - ONNX inference
  - reconstruct processed waveform
- return either:
  - processed utterance audio
  - or raw audio fallback if inference fails

### 4. `OrtTseDebugRunner`

Suggested file:

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/OrtTseDebugRunner.kt`

Responsibilities:

- support offline wav-to-wav verification
- take one saved raw wav
- produce one processed wav
- emit diagnostic logs:
  - input sample count
  - tensor shapes
  - output sample count
  - fallback reason if any

This should exist before reconnecting TSE into the recognition flow.

## First Implementation Step

Before any app flow change, inspect the ONNX model directly.

### Deliverable

Build a small metadata inspection path that logs:

- model input names
- model output names
- input shapes
- output shapes
- numeric tensor types

### Why

The current native path assumes a certain layout, but the app should not carry that assumption forward blindly.

This inspection step should answer:

- does the model expect single-frame or multi-frame context
- where does `dvector.bin` fit in the input graph
- what exact tensor shapes are required

## Offline Validation Phase

Before reconnecting TSE to `RecognitionManager`, validate on saved wav files only.

### Input

- one raw utterance wav from Home

### Output

- one processed wav written locally

### Validation criteria

1. output wav length is reasonable and non-empty
2. output audio is audibly different from raw when TSE is active
3. output audio is not obviously corrupted by reconstruction errors
4. ASR on processed output is not worse than raw on every sample

If these fail, do not reconnect TSE to the main recognition path yet.

## RecognitionManager Integration Plan

After offline verification is credible:

1. keep current recording-time path:
   - `raw -> VAD`
2. keep current segment boundary behavior:
   - `saveVadSegmentsOnly`
   - `keep`
   - `startOffset`
   - `lastSpeechDetectedOffset`
3. after one utterance is finalized:
   - build `rawAudioToSave`
   - if TSE enabled:
     - run utterance-level ORT TSE
     - save processed to `*.app.wav`
     - save raw to `*.raw.app.wav`
     - run final ASR on processed audio
   - else:
     - preserve current non-TSE behavior

This means the current shape of `RecognitionManager` is already close to the right integration point.

## Storage and Playback Rules

When TSE is enabled:

- `*.app.wav`
  - processed audio
- `*.raw.app.wav`
  - raw audio

Home playback should keep both explicit buttons:

- `TSE`
- `Raw`

Delete behavior should continue deleting both files together.

## Fallback Rules

If any TSE step fails:

- missing model asset
- ORT session init failure
- tensor shape mismatch
- runtime inference error
- invalid reconstruction result

then:

- save raw as the main `*.app.wav`
- skip `*.raw.app.wav` because there is no distinct processed result
- run final ASR on raw audio
- log the fallback reason clearly

This keeps the user flow working even when TSE is temporarily broken.

## Rollout Phases

### Phase 1. Model inspection

- add ORT dependency
- load model
- print model I/O metadata

### Phase 2. Offline utterance processor

- implement utterance-level DSP + inference path
- produce wav-to-wav output locally

### Phase 3. Offline quality check

- compare raw and processed playback
- compare ASR results manually

### Phase 4. Home final-utterance integration

- replace current `NativeTSE` final-utterance path with ORT TSE
- keep VAD unchanged

### Phase 5. Cleanup

- stop using custom JNI TSE path in app logic
- keep native code only if still needed for reference during transition

## File-Level Change Forecast

### Must change

- `app/build.gradle.kts`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/RecognitionManager.kt`

### Likely add

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/OrtTseEngine.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/TseSignalProcessor.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/UtteranceTseProcessor.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/OrtTseDebugRunner.kt`

### Likely keep for now

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/NativeTSE.kt`
- `app/src/main/java/com/example/tse/NativeTSE.kt`

These can remain during migration until ORT output is validated.
