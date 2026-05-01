# TFLite VoiceFilter-Lite Migration Plan

## Goal

Migrate the current `NativeTSE(ONNX)` integration to the stateful `VoiceFilter-Lite` TFLite model described in:

- `/media/hhs/FastData/workspace/TSE/release/android/MODEL_SPEC.md`

The target is not a cosmetic format swap. The goal is to align the app with the model's actual streaming contract:

- fixed `32`-frame CNN window
- explicit `LSTM` state input/output
- current-frame mask output

## Why Migration Is Needed

The current ONNX path was built around this contract:

- `x [1,1,T,257]`
- `embed [1,192]`
- `mask [1,257,T]`
- take the last time step

The new TFLite `VoiceFilter-Lite` contract is different:

- `x [1,32,257,1]`
- `embed [1,192]`
- `h1/c1/h2/c2` state inputs
- `mask [1,257,1]`
- `h1/c1/h2/c2` state outputs

That means the existing DSP pipeline is still useful, but the model adapter layer must change.

## What Can Stay

These pieces can mostly stay:

- `RecognitionManager` high-level TSE flow
- `TseAudioPreprocessor` chunking around `160`-sample hops
- native STFT / ISTFT / overlap-add structure
- `dvector.bin` asset path concept
- `raw -> TSE -> VAD -> ASR` wiring

## What Must Change

## 1. Asset Layer

### Current

- `TseSupport.kt` copies `voice_filter_lite_int8.onnx` / `dvector.bin`

### Target

- copy `voice_filter_lite_int8.tflite`
- keep `dvector.bin`

### Required change

- update asset filename in `TseSupport.kt`
- update init log to reflect `.tflite`

## 2. Kotlin Native Wrapper

### Current

File:

- [app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/NativeTSE.kt](./app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/NativeTSE.kt)

It assumes:

- `System.loadLibrary("onnxruntime")`
- `init(modelPath, dvectorPath)`
- `processFrame(audioFrame: FloatArray): FloatArray?`

### Target

The public Kotlin API can remain minimal:

```kotlin
class NativeTSE {
    external fun init(modelPath: String, dvectorPath: String): Boolean
    external fun processFrame(audioFrame: FloatArray): FloatArray?
    external fun reset()
    external fun release()
}
```

This is good and should be preserved if possible.

### Required change

Internal native implementation changes, but Kotlin call shape can stay the same.

## 3. Native Runtime Backend

### Current

The native backend is ONNX Runtime based.

### Target

Replace the model execution layer with:

- TFLite / LiteRT interpreter
- stateful tensor management

### Required change

The model adapter part of native code must be rewritten to:

- load `.tflite`
- allocate interpreter
- bind fixed input/output tensors
- maintain four recurrent state buffers:
  - `h1`
  - `c1`
  - `h2`
  - `c2`

## 4. Streaming Feature Buffer

### Current

The old model path keeps longer temporal history and feeds `[1,1,T,257]`.

### Target

Maintain a rolling CNN window:

- `32 x 257`
- layout must match `NHWC`
- input tensor shape:
  - `[1,32,257,1]`

### Required change

Native feature preparation must:

1. compute current magnitude frame `[257]`
2. shift the `32`-frame feature history
3. append newest frame
4. pack into TFLite tensor layout `NHWC`

## 5. LSTM State Management

### Current

No explicit recurrent state tensor lifecycle exists in the ONNX integration layer.

### Target

Persist across calls:

- `h1 [1,512]`
- `c1 [1,512]`
- `h2 [1,512]`
- `c2 [1,512]`

### Required behavior

For each frame:

1. feed current state tensors as inputs
2. invoke interpreter
3. copy output states back into persistent buffers
4. use returned mask for the current frame

### Reset behavior

`reset()` must zero:

- CNN window history
- `h1/c1/h2/c2`
- overlap-add state
- pending input buffers

## 6. Mask Application Path

### Current

Mask is taken from a time sequence output and the last frame is selected.

### Target

Use `Identity [1,257,1]` directly as the current-frame mask.

### Required change

Native code no longer needs to index the last timestep from a temporal output tensor.

It should:

- read the current mask frame
- multiply current magnitude by mask
- reconstruct waveform with existing phase / ISTFT path

## 7. Native Packaging

### Current

The ONNX stack required:

- `libonnxruntime.so`
- `libtse_engine.so`
- `liboboe.so`
- `libc++_shared.so`

### Target

If fully moving away from ONNX Runtime for TSE:

- `libonnxruntime.so` may no longer be required for the TSE path

But this depends on whether any other app feature still needs it.

### Required audit

Before removing anything, verify whether:

- ORT is used anywhere else in app/native
- TFLite runtime is bundled statically or via other native libs

## 8. Build and Loading Changes

### Required native changes

- replace ONNX-specific initialization code
- replace ONNX-specific session/provider logging
- add TFLite interpreter init logging
- log tensor allocation success/failure

### Suggested init log

At minimum:

- model filename
- interpreter created successfully
- input tensor shapes verified
- output tensor shapes verified
- state buffers initialized

## 9. Performance Expectations

The main reason to do this migration is that the TFLite model contract is more genuinely streaming:

- `32`-frame CNN buffer
- explicit state passing
- one current-frame mask output

This should remove a large part of the repeated full-window replay cost from the old path.

But success still depends on:

- actual backend used
- tensor packing overhead
- TFLite/LiteRT delegate behavior on device

Do not assume format conversion alone guarantees realtime.

## 10. Suggested Rollout Order

### Phase 1: Native backend swap only

Keep Kotlin/API surface unchanged and replace only native model runtime:

- `.tflite` load
- stateful inference
- current-frame mask output

Goal:

- preserve app integration stability

### Phase 2: Offline or DataCollect validation

First validate in:

- `DataCollectScreen`
- raw/TSE A/B playback

Goal:

- confirm loadability
- confirm audio is not broken
- confirm state logic works

### Phase 3: Tensor device latency check

Run the Tensor G2 checklist:

- latency
- backend signals
- suppression
- ASR behavior

### Phase 4: Decide live-path readiness

Only promote to primary live TSE path if:

- latency is near viable
- quality is acceptable
- VAD/ASR behavior is stable

## 11. Minimum File Touch List

Expected app-side files:

- [app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/TseSupport.kt](./app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/TseSupport.kt)
- [app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/NativeTSE.kt](./app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/NativeTSE.kt)
- [app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/TseAudioPreprocessor.kt](./app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/TseAudioPreprocessor.kt)

Expected native-side files:

- native engine implementation that currently wraps ONNX
- native DSP/model adapter layer

## 12. One-Sentence Summary

The correct migration strategy is to keep the current app-level TSE pipeline shape, but replace the ONNX model adapter with a stateful TFLite/LiteRT adapter that manages a `32`-frame CNN window plus explicit `LSTM` state tensors.
