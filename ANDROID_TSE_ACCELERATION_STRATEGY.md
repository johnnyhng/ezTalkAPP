# Android TSE Acceleration Strategy

## Goal

Define the accelerator strategy for realtime target-speaker extraction on Android, with:

- first priority: `Google Tensor` devices
- baseline device: `Tensor G2`
- secondary path: `Qualcomm Snapdragon`

This document exists because `NNAPI attached` is not a sufficient success criterion, and because `QNN` is not a universal Android acceleration path.

## Current Reality

### Tensor G2 / Google Tensor path

For Google Tensor devices, the current model/runtime work shows:

- `ONNX Runtime + NNAPI` may initialize successfully
- model execution may still fall back to a non-ideal backend
- measured latency is still above the `10 ms` hop target

As of `2026-05-01`, Android officially treats `NNAPI` as deprecated, and recommends migration toward newer Google-managed runtimes such as TensorFlow Lite in Play Services.

Implication:

- `NNAPI` should be treated as a transitional experiment, not the long-term primary strategy for Tensor devices

### Qualcomm / QNN path

`QNN` is a Qualcomm-specific path.

It is relevant for:

- Snapdragon devices
- Qualcomm AI Engine Direct / HTP acceleration

It is not the correct baseline path for:

- `Tensor G2`
- other Google Tensor devices

Implication:

- `QNN` should be planned as a separate secondary deployment lane
- do not couple Tensor-first design decisions to QNN requirements

## Decision

### Primary path: Google Tensor

Use `Tensor G2` as the first deployment baseline.

Success criteria for this path:

- model architecture is compatible with mobile deployment
- model can be exported to a Tensor-friendly format if needed
- runtime path can avoid legacy `NNAPI` dependence where possible
- latency target moves toward realtime viability on Tensor hardware

Preferred strategic direction:

- keep the model architecture streaming-first
- keep fixed shapes
- keep quantization-friendly operator choices
- remain open to a future `LiteRT / TFLite` deployment lane for Tensor devices

### Secondary path: Qualcomm Snapdragon

Treat `QNN` as a separate optimization path for Snapdragon devices.

Use it only when all of these are true:

- the model has fixed shapes
- the model is quantized appropriately
- the operator set is within QNN support
- we have a Snapdragon target worth optimizing for

Do not use QNN as the architecture driver for the Tensor baseline.

## What This Means For Model Design

The next TSE model should satisfy both:

1. Tensor-first requirements
2. QNN-friendly constraints where practical

That means:

- streaming or chunk-based inference
- fixed input shapes
- no dynamic control flow
- no dynamic sequence length in the live path
- quantization-friendly graph
- minimal exotic operators

Recommended practical rule:

- design first for `Tensor G2`
- keep the graph simple enough that a later `QNN` export remains plausible

## Runtime Strategy Split

### Lane A: Tensor-first lane

Target:

- `Tensor G2` baseline
- Google Tensor family first

Near-term runtime:

- continue using current native ONNX path for validation
- continue measuring actual latency and suppression behavior

Long-term runtime direction:

- avoid relying on deprecated NNAPI as the final answer
- be ready to evaluate a Tensor-native deployment lane if ONNX + NNAPI remains unstable or slow

### Lane B: Snapdragon/QNN lane

Target:

- Qualcomm Snapdragon devices

Runtime:

- `ONNX Runtime + QNN EP`

Requirements from official QNN EP docs:

- Qualcomm chipset only
- quantized model for HTP/NPU
- fixed shapes
- supported operator subset

## Priority Order

1. Confirm a good `Tensor G2` model/runtime path.
2. Keep the model graph simple enough for future cross-device acceleration.
3. Add `QNN` as a secondary optimization lane for Snapdragon, not as the primary baseline.

## Immediate Next Steps

1. Keep measuring `VoiceFilter-Lite` style models on the current Tensor device path.
2. Track whether latency improvements come from real backend improvements or only graph simplification.
3. Maintain fixed-shape, quantized, streaming-friendly models.
4. When the model stabilizes, evaluate a separate Snapdragon/QNN feasibility pass.

## Non-Goals

These should not drive the next design iteration:

- chasing `NNAPI attached` as a success metric
- assuming `QNN` can solve Tensor-device acceleration
- optimizing first for Snapdragon while Tensor is still the primary deployment target

## References

- Android NNAPI migration guide:
  - <https://developer.android.com/ndk/guides/neuralnetworks/migration-guide>
- Android NNAPI overview and deprecation warning:
  - <https://developer.android.com/ndk/guides/neuralnetworks/>
- ONNX Runtime QNN Execution Provider:
  - <https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html>
- Google announcement that TensorFlow Lite is now LiteRT:
  - <https://developers.googleblog.com/en/tensorflow-lite-is-now-litert/>
