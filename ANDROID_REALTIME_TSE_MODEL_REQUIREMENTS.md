# Android Realtime TSE Model Requirements

## Goal

Define model requirements for a target-speaker extraction model that can realistically run in the `ezTalkAPP` Android realtime path:

`raw mic -> TSE -> VAD -> ASR`

This document exists because the current Google VoiceFilter-style model is not a good fit for low-latency Android deployment.

## Current Constraint Summary

- Input audio: `16 kHz`, mono
- Current realtime hop target: `160 samples` (`10 ms`)
- Existing native DSP path expects:
  - streaming STFT
  - rolling context
  - per-hop output
- App-side requirement:
  - low enough latency for live speech detection
  - stable stop/start behavior
  - predictable memory use
  - compatible with Android native deployment

## What Failed In The Current Model

The current VoiceFilter-like model is a poor deployment fit because:

- It uses `CNN + Bi-LSTM`
- `Bi-LSTM` is not naturally causal
- realtime inference requires future context
- Android acceleration support is poor or inconsistent
- NNAPI attach may succeed while actual execution still falls back to `nnapi-reference`
- measured latency is far above realtime budget

Observed result:

- about `90 ms` inference for each `10 ms` hop
- this is roughly `9x` slower than realtime

## Hard Requirements For The Next Model

### 1. Streaming-first architecture

The model must be designed for streaming inference, not adapted afterward.

Required properties:

- causal or chunk-based
- bounded look-ahead
- no bidirectional recurrent layers in the live path
- deterministic per-hop inference behavior

Preferred:

- causal Conformer
- streaming Conformer
- chunk-based Transformer/Conformer with explicit cache
- uni-directional recurrent components only if strictly necessary

Avoid:

- Bi-LSTM
- full-sequence bidirectional attention
- designs that require whole-utterance context for acceptable quality

### 2. Explicit state/cache interface

The model should expose a deployment-friendly streaming state.

Preferred:

- cached attention state
- cached convolution state
- fixed-size rolling context

Avoid:

- recomputing long history every hop
- hidden internal sequence assumptions that require full-window replay

If the model is exported to ONNX, state tensors should be explicit inputs/outputs whenever possible.

### 3. Tight latency budget

Target budget should be defined before training.

Recommended target on device:

- ideal: `< 5 ms` per `10 ms` hop
- acceptable: `< 10 ms` per `10 ms` hop
- warning zone: `10-20 ms`
- not viable for live path: `> 20 ms`

This budget is for model inference only. Total path also includes:

- framing / STFT
- feature formatting
- JNI/native overhead
- ISTFT / overlap-add
- VAD / ASR handoff

So the model itself should be comfortably below the full `10 ms` hop budget.

### 4. Small and acceleration-friendly operator set

Deployment matters more than paper quality.

Preferred ops:

- Conv1d / Conv2d
- Linear / MatMul
- LayerNorm if runtime support is solid
- simple activations
- limited depthwise conv if target runtime handles it well

Be cautious with:

- dynamic-shape-heavy graphs
- unsupported recurrent ops
- exotic masking logic
- ops that frequently fall back from NNAPI/QNN/XNNPACK

The model should be export-tested early against:

- ONNX Runtime CPU
- ONNX Runtime Android native
- any target Android accelerator path under consideration

### 5. Stable input/output contract

The model I/O must be fixed before app integration.

Decide early:

- input domain:
  - spectrogram magnitude
  - complex STFT features
  - waveform
- output domain:
  - mask
  - enhanced magnitude
  - waveform
- speaker conditioning input:
  - fixed embedding such as `dvector`
  - updated speaker token/state

Preferred for incremental migration:

- keep a mask-based STFT pipeline if you want to reuse existing native DSP structure

Preferred for simpler integration long-term:

- waveform-in / waveform-out, if latency and operator support are proven acceptable

## Recommended Model Shape For This Project

Most practical near-term option:

- causal Conformer-based mask estimator
- speaker-conditioned
- chunk-based streaming
- fixed frame hop
- explicit cache/state

Recommended deployment contract:

- sample rate: `16 kHz`
- hop: `160 samples`
- analysis window: `400 samples`
- streaming chunk:
  - either `1 hop`
  - or a small fixed chunk such as `2-4 hops`
  - **Current Production/Live Implementation (2026-06-13):** Accumulates `320ms` (32 hops / 5120 samples) before running model inference once to optimize CPU/JNI performance.
- output:
  - current-chunk mask only
- state:
  - cached attention/convolution state tensors

This is much better aligned with Android realtime than a bidirectional LSTM model.

## Speaker Conditioning Requirements

If target-speaker conditioning remains part of the design:

- speaker embedding dimension must be fixed
- embedding format must be documented
- embedding extraction pipeline must be stable and reproducible

Recommended:

- fixed `Float32` embedding tensor
- explicit normalization policy
- fixed shape documented in training and runtime code

Avoid:

- ambiguous binary formats
- runtime assumptions not documented in export metadata

## Deployment Requirements

### Native-first path

Assume final realtime deployment will use:

- Android NDK / JNI
- ONNX Runtime C++ or another native runtime
- native DSP

Do not assume Kotlin + Java ORT will be the final live path.

### Android acceleration

Do not assume `NNAPI attached` means actual acceleration.

Each candidate model must be tested for:

- actual backend used
- fallback behavior
- latency on target device

Watch for:

- `nnapi-reference`
- CPU fallback
- unsupported partitions

### Memory behavior

The model should support:

- fixed buffer sizes
- reusable tensors
- no large per-hop allocations

This matters for audio jitter, GC avoidance, and predictable realtime behavior.

## Evaluation Checklist Before Integration

Every new candidate model should pass these stages in order.

### Stage 1. Offline quality

- clean target speaker retention
- noise/interference suppression
- minimal musical noise / artifacts
- acceptable ASR impact

### Stage 2. Desktop streaming simulation

- chunked streaming simulation
- state/cache correctness
- no drift across long streams
- warm-up behavior understood

### Stage 3. Android native benchmark

- JNI/native inference benchmark
- per-hop latency distribution
- average and p95 latency
- memory allocation profile

### Stage 4. Device execution-path validation

- CPU baseline
- accelerator attempt if applicable
- verify actual backend, not just attach success

### Stage 5. App integration

- stop/start stability
- realtime VAD responsiveness
- no queue buildup
- acceptable battery/thermal behavior

## Non-Goals

The next model should not optimize first for:

- maximum offline separation quality at any cost
- large bidirectional context
- research-only architectures without Android export proof

The first priority is deployment viability.

## Practical Recommendation

For this project, train the next model with these assumptions:

- causal streaming model
- deployment-first ONNX export
- explicit state tensors
- fixed chunk/hop behavior
- small enough to benchmark under realtime constraints on Android native

If a Conformer variant is used, prefer:

- causal attention
- chunk-wise inference
- limited look-ahead
- explicit cache reuse

Do not continue with a bidirectional VoiceFilter-style live path.
