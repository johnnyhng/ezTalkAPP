# Snapdragon QNN Feasibility Checklist

## Goal

Evaluate whether the same TSE model can be accelerated on Qualcomm Snapdragon devices using `ONNX Runtime + QNN Execution Provider`.

This checklist is secondary to the Tensor G2 baseline and should be run separately.

## Ground Rule

`QNN` is only relevant for Qualcomm devices.

Do not use QNN results to infer anything about:

- Google Tensor
- Pixel-specific TPU behavior
- Android-wide acceleration behavior

## 1. Device Scope

Before any model work, document the target Snapdragon device:

- chipset family
- Android version
- expected QNN backend

Minimum requirement:

- a real Snapdragon target worth optimizing for

## 2. Runtime Availability

Confirm the runtime stack exists:

- ONNX Runtime build includes `QNN EP`
- required Qualcomm runtime libraries are available
- packaging story for Android app is understood

Do not proceed if the runtime story is still hypothetical.

## 3. Model Shape Requirements

QNN feasibility assumes:

- fixed input shapes
- fixed embedding shape
- quantized model
- no dynamic live-path sequence length

Reject QNN evaluation early if the live model still depends on dynamic shapes.

## 4. Operator Compatibility

Review the model operator set against QNN EP support.

Pay special attention to:

- attention-related graph expansion
- dynamic masking logic
- Gather/Slice/Transpose heavy patterns
- unsupported control flow

High-level rule:

- a simpler fixed-shape graph is much more plausible than a highly dynamic exported graph

## 5. Quantization Readiness

QNN HTP path expects quantized models.

Verify:

- quantized model can be generated reproducibly
- calibration data is representative
- quantized model still passes correctness checks
- graph I/O quantization behavior is understood

## 6. Backend Validation

For QNN tests, capture:

- QNN backend requested
- whether CPU fallback is disabled
- whether full graph offload succeeds
- any partial partition/fallback behavior

Important:

- QNN success means actual supported execution on Qualcomm backend
- not just session creation success

## 7. Latency Validation

Measure:

- session creation time
- average per-hop inference time
- worst-case per-hop inference time

Interpretation target:

- `< 10 ms` per hop is the meaningful threshold for this project

If QNN remains well above that threshold, it may still be useful for offline or batched use, but not for strict live path.

## 8. Quality Validation

Repeat the same A/B review used for Tensor:

- raw vs TSE audio
- VAD behavior
- ASR behavior

Do not accept a fast path that materially damages speech quality.

## 9. Integration Cost Check

Estimate real implementation cost:

- separate ORT/QNN build pipeline
- packaging native libraries
- device-specific testing burden
- ongoing maintenance burden

QNN is only worth carrying if the performance win is large enough to justify the extra platform complexity.

## 10. Decision Gate

Proceed with Snapdragon/QNN only if:

- the model graph fits QNN constraints
- the runtime stack is real and reproducible
- latency improves materially
- quality remains acceptable

Otherwise:

- keep Snapdragon as CPU/XNNPACK class fallback
- do not let QNN complexity dominate the core Tensor-first roadmap
