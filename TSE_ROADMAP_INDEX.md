# TSE Roadmap Index

## Purpose

This index links the current design and validation documents for Android target-speaker extraction work in `ezTalkAPP`.

Use it as the entry point before diving into model, runtime, or accelerator-specific notes.

## Recommended Reading Order

1. [MANAGED_RUNTIME_TSE_PLAN.md](./MANAGED_RUNTIME_TSE_PLAN.md)
2. [ANDROID_TSE_ACCELERATION_STRATEGY.md](./ANDROID_TSE_ACCELERATION_STRATEGY.md)
3. [TENSOR_G2_VALIDATION_CHECKLIST.md](./TENSOR_G2_VALIDATION_CHECKLIST.md)
4. [SNAPDRAGON_QNN_FEASIBILITY_CHECKLIST.md](./SNAPDRAGON_QNN_FEASIBILITY_CHECKLIST.md)
5. [ANDROID_REALTIME_TSE_MODEL_REQUIREMENTS.md](./ANDROID_REALTIME_TSE_MODEL_REQUIREMENTS.md)
6. [JNI_REALTIME_TSE_DESIGN.md](./JNI_REALTIME_TSE_DESIGN.md)

## What Each Document Covers

### Strategy

- [MANAGED_RUNTIME_TSE_PLAN.md](./MANAGED_RUNTIME_TSE_PLAN.md)
  - Declares managed runtime as the primary future direction
  - Downgrades self-compiled JNI inference to paused/fallback status

- [ANDROID_TSE_ACCELERATION_STRATEGY.md](./ANDROID_TSE_ACCELERATION_STRATEGY.md)
  - Defines the top-level acceleration split:
  - `Tensor G2 / Google Tensor` first
  - `Snapdragon / QNN` second

### Validation

- [TENSOR_G2_VALIDATION_CHECKLIST.md](./TENSOR_G2_VALIDATION_CHECKLIST.md)
  - Tensor-first device validation checklist
  - Main gate for deciding whether a model is live-path viable on Pixel/Tensor

- [SNAPDRAGON_QNN_FEASIBILITY_CHECKLIST.md](./SNAPDRAGON_QNN_FEASIBILITY_CHECKLIST.md)
  - Separate checklist for Qualcomm-specific acceleration work
  - Should not be used to drive Tensor-first decisions

### Model Constraints

- [ANDROID_REALTIME_TSE_MODEL_REQUIREMENTS.md](./ANDROID_REALTIME_TSE_MODEL_REQUIREMENTS.md)
  - Model-side deployment requirements
  - Streaming, fixed-shape, latency, and operator guidance

### Native Runtime Design

- [JNI_REALTIME_TSE_DESIGN.md](./JNI_REALTIME_TSE_DESIGN.md)
  - Native DSP/runtime architecture
  - Sliding context STFT pipeline
  - JNI state and performance constraints

## Current Working Direction

At the moment, the intended roadmap is:

1. Use `Tensor G2` as the primary validation baseline.
2. Treat managed `TFLite / LiteRT` style deployment as the primary direction.
3. Continue validating `VoiceFilter-Lite` style models or similarly lightweight streaming architectures.
4. Treat `NNAPI` as an experiment, not the long-term product assumption.
5. Keep the graph simple enough that future Snapdragon/QNN support remains possible.

## Current Progress

### Completed

- `managed runtime` is now the declared primary direction
- `LiteRT in Google Play services` dependency has been added
- `.tflite` asset path has replaced the previous `.onnx` app-side asset assumption
- managed runtime probe can:
  - initialize LiteRT
  - map the model asset
  - create `InterpreterApi`
  - run dummy inference
- managed single-frame API is implemented for:
  - `x [1,32,257,1]`
  - `embed [1,192]`
  - `h1/c1/h2/c2`
  - `mask [257]`
- app-side streaming helpers now exist for:
  - rolling `32 x 257` CNN window
  - LSTM state carry-over
  - `160`-sample hop STFT to `mag[257]`
  - minimal `hop -> STFT -> mask` validation chain
- startup probe logging is documented in:
  - [MANAGED_TSE_PROBE_LOG_GUIDE.md](./MANAGED_TSE_PROBE_LOG_GUIDE.md)

### In Progress

- verifying the managed path on real device logs
- confirming stable `hop -> STFT -> mask` execution on actual app startup

### Not Started Yet

- real microphone-derived frame feeding into the managed mask pipeline
- waveform reconstruction from managed-mask output
- `DataCollectScreen` A/B output generation from managed TSE
- live-path integration into `RecognitionManager`
- Snapdragon/QNN-specific managed runtime optimization

## Immediate Next Step

The next meaningful technical step is:

- validate that the `ManagedTseMaskPipeline` startup probe succeeds on device

The next implementation step after that is:

- feed real mic-derived magnitude frames into the managed runtime path

## Non-Goals

This roadmap does not assume:

- `NNAPI attached` equals real acceleration
- `QNN` solves Google Tensor acceleration
- a single runtime path will be optimal for every Android SoC family
