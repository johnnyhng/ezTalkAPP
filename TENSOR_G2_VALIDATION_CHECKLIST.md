# Tensor G2 Validation Checklist

## Goal

Validate whether a target-speaker extraction model is viable on `Google Tensor G2` as the primary Android deployment baseline.

This checklist is intentionally Tensor-specific and should not be mixed with Snapdragon/QNN validation.

## Success Criteria

A model/runtime combination is considered promising on Tensor G2 only if all of these trend in the right direction:

- model loads reliably on device
- session initialization is stable across repeated runs
- end-to-end latency moves toward realtime viability
- output audio is not severely over-suppressed
- VAD and downstream ASR still behave acceptably

## 1. Model Contract

Confirm all of the following are fixed and documented:

- sample rate: `16 kHz`
- hop size: `160` samples
- analysis window: `400` samples
- input shape is fixed for live path
- speaker embedding shape is fixed
- output contract is fixed

Reject the model from live-path consideration if:

- it depends on dynamic sequence length at runtime
- it requires whole-utterance context
- it relies on bidirectional sequence behavior in the live path

## 2. Export Compatibility

Verify the exported model is deployment-friendly:

- ONNX model loads on desktop CPU
- ONNX model loads on Android native ORT
- IR version is supported by the runtime actually packaged in the app
- opset is known and documented
- quantized model remains loadable after export

Red flags:

- unsupported model IR version
- dynamic control-flow or shape-heavy graph
- export differences between training and deployment models

## 3. Runtime Path Verification

For each test run, capture:

- provider requested
- provider actually used, if observable
- session creation success/failure
- any fallback indicators

Important note:

- `NNAPI attached` is not enough
- absence of obvious failure is not enough
- success means the model is both stable and materially faster

## 4. Latency Measurement

Measure at least:

- session creation time
- average inference time per hop
- P95 or worst-case inference time per hop

Use this interpretation:

- `< 5 ms`: strong
- `5-10 ms`: viable target
- `10-20 ms`: maybe usable with compromises
- `> 20 ms`: not viable for strict realtime `10 ms` hop

Track separately:

- model inference time
- total processing time including DSP/JNI

## 5. Audio Quality Checks

For each recording, save and compare:

- raw output
- TSE output

Review:

- intelligibility
- speech naturalness
- suppression strength
- start-of-utterance behavior
- background noise handling
- whether target speech becomes too thin or quiet

Warning signs:

- processed RMS collapses too often
- output sounds hollow or clipped
- first few hundred milliseconds are unstable

## 6. VAD / ASR Compatibility

Check whether TSE output still works downstream:

- speech start is detected promptly
- final utterance trigger remains stable
- ASR text is not worse than raw in ordinary single-speaker conditions

Compare:

- raw -> VAD -> ASR
- TSE -> VAD -> ASR

If TSE hurts ordinary single-speaker ASR without improving difficult cases, it is not yet production-ready.

## 7. Repeated Session Stability

Run repeated cycles of:

- start
- speak
- stop
- restart

Confirm:

- no stale native state leaks across sessions
- no cached bad model file is reused unexpectedly
- no init failures appear after repeated runs
- no increasing latency across multiple sessions

## 8. Decision Gate

Promote Tensor G2 path only if all are true:

- model loads reliably
- latency is near viable range
- no obvious backend dead-end is observed
- quality is meaningfully useful
- downstream VAD/ASR is not broken

Otherwise:

- keep it as offline or A/B testing only
- continue model simplification or runtime-path changes

## Current Working Interpretation

Based on recent app logs, the current `VoiceFilter-Lite` direction appears:

- more promising than previous VoiceFilter variants
- able to load and run on device
- lower latency than earlier models
- still above ideal realtime latency
- still somewhat over-suppressive

That means:

- continue testing
- do not yet treat it as final live-path ready
