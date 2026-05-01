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

## Non-Goals

This roadmap does not assume:

- `NNAPI attached` equals real acceleration
- `QNN` solves Google Tensor acceleration
- a single runtime path will be optimal for every Android SoC family
