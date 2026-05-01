# Tensor G2 Test Log

## Purpose

Use this file to record repeatable validation results for `Tensor G2` TSE experiments.

This log is intended to turn ad-hoc `logcat` observations into a stable baseline for:

- model changes
- export changes
- quantization changes
- runtime/provider changes
- JNI/native integration changes

## How To Use

Create one entry per meaningful test configuration.

Use a new entry when any of these change:

- model file
- quantization/export settings
- runtime/provider path
- native library build
- device

## Entry Template

### Test ID

- date:
- device:
- Android version:
- app build/commit:
- native lib build note:

### Model

- model name:
- model family:
- quantized:
- ONNX IR version:
- opset:
- input contract:
- output contract:

### Runtime

- requested provider:
- observed provider signals:
- `nnapi-reference` seen:
- session init success:
- repeated session init stable:

### Latency

- hop size:
- avg inference ms:
- typical range ms:
- worst observed ms:
- realtime verdict:

Use:

- `pass` if comfortably below `10 ms`
- `borderline` if around `10-20 ms`
- `fail` if usually above `20 ms`

### Audio Behavior

- mask min range:
- mask avg range:
- mask max range:
- processed vs raw RMS trend:
- over-suppression observed:
- start-of-utterance stability:
- subjective listening notes:

### Downstream Behavior

- `speech start detected` stable:
- final utterance trigger stable:
- raw vs TSE VAD behavior:
- raw vs TSE ASR result:
- expected phrase:
- recognized variants:

### Overall Verdict

- loadability:
- acceleration verdict:
- latency verdict:
- quality verdict:
- live-path verdict:
- recommended next step:

## Baseline Entries

---

### Test ID

- date: `2026-05-01`
- device: `Pixel 7`
- Android version: `Android 16`
- app build/commit: `unknown from log`
- native lib build note: `VoiceFilter-Lite JNI path`

### Model

- model name: `voice_filter_lite_int8.onnx`
- model family: `VoiceFilter-Lite`
- quantized: `yes`
- ONNX IR version: `9-compatible runtime path`
- opset: `not logged here`
- input contract: `x [1,1,T,257], embed [1,192]`
- output contract: `mask [1,257,T]`, use last timestep

### Runtime

- requested provider: `NNAPI`
- observed provider signals:
  - `NNAPI provider attached successfully`
  - `ExecutionPlan ... compilation finished successfully on nnapi-reference`
- `nnapi-reference` seen: `yes`
- session init success: `yes`
- repeated session init stable: `appears yes for this run`

### Latency

- hop size: `160 samples / 10 ms`
- avg inference ms: `about 32 ms`
- typical range ms: `31-36 ms`
- worst observed ms: `35.96 ms`
- realtime verdict: `fail`

### Audio Behavior

- mask min range: `0.154-0.631`
- mask avg range: `0.191-0.870`
- mask max range: `0.936`
- processed vs raw RMS trend: `often lower, weak audio frequently suppressed`
- over-suppression observed: `yes`
- start-of-utterance stability: `speech start still detected`
- subjective listening notes: `needs separate listening confirmation`

### Downstream Behavior

- `speech start detected` stable: `yes`
- final utterance trigger stable: `yes`
- raw vs TSE VAD behavior: `VAD still works`
- raw vs TSE ASR result: `TSE output not yet reliable for simple phrase`
- expected phrase: `我在做測試`
- recognized variants:
  - `我`
  - `我要`
  - `我在`
  - `股在`
  - `歌訓`
  - `根訓`
  - `運`

### Overall Verdict

- loadability: `pass`
- acceleration verdict: `fail, still on nnapi-reference`
- latency verdict: `fail`
- quality verdict: `not yet acceptable`
- live-path verdict: `not ready`
- recommended next step: `treat as Tensor G2 baseline and continue model/runtime tuning`
