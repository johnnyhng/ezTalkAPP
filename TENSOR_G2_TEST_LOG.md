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

---

### Test ID

- date: `2026-05-06`
- device: `Pixel 7`
- Android version: `not captured`
- app build/commit: `a20887a7 Process DataCollect audio with managed TSE`
- native lib build note: `NativeTSE removed; managed LiteRT path only`

### Model

- model name: `voice_filter_lite.tflite`
- model family: `VoiceFilter-Lite LiteRT export`
- quantized: `no, float32 tensors observed`
- ONNX IR version: `n/a`
- opset: `n/a`
- input contract:
  - `x [1,1,32,257]`
  - `embed [1,192]`
  - `h [2,1,512]`
  - `c [2,1,512]`
- output contract:
  - `mask [1,257,1]`
  - `h_next [2,1,512]`
  - `c_next [2,1,512]`

### Runtime

- requested provider: `Google Play services LiteRT managed runtime with GPU validation first`
- observed provider signals:
  - `gpuAvailable=true`
  - GPU delegate mini-benchmark attempted
  - `ValidatedAccelerationConfig did not pass validation check`
  - `benchmarkErrorCode=1008`
  - `ManagedTseProbe acceleration validation returned no valid config`
  - fallback interpreter created with `TfLiteXNNPackDelegate`
- `nnapi-reference` seen: `no`
- session init success: `yes`
- repeated session init stable: `appears yes`

### Latency

- hop size: `160 samples / 10 ms`
- avg inference ms: `not measured in app log`
- typical range ms: `not measured`
- worst observed ms: `not measured`
- realtime verdict: `unknown`

### Audio Behavior

- mask min range:
  - after Java array tensor binding fix: roughly `0.01-0.89` depending on frame energy
  - before fix: `0.000000` for min/avg/max, caused silent output
- mask avg range:
  - silence/low input: roughly `0.17-0.66`
  - speech/high input: roughly `0.83-0.99`
- mask max range: often near `0.99`
- processed vs raw RMS trend:
  - speech frames produce non-zero waveform output after array binding fix
  - low-energy frames are strongly attenuated
- over-suppression observed: `needs listening confirmation`
- start-of-utterance stability: `speech start detected`
- subjective listening notes: `not captured`

### Downstream Behavior

- `speech start detected` stable: `yes`
- final utterance trigger stable: `yes`
- raw vs TSE VAD behavior: `VAD currently runs on raw passthrough during capture; managed TSE is DataCollect offline post-processing`
- raw vs TSE ASR result: `ASR runs on managed TSE processed output for DataCollect final save path`
- expected phrase: `我在做測試`
- recognized variants:
  - `我在`
  - `我在說`
  - `我在坐車`
  - `我在做測試`

### Issues Recorded

- GPU acceleration is not active on Tensor G2 yet.
  - validation falls back to CPU/XNNPACK
  - observed error: `benchmarkErrorCode=1008` during GPU validation initialization
- `.tflite` and `dvector.bin` assets are ignored by `app/src/main/assets/.gitignore`.
  - source changes reference `voice_filter_lite.tflite`
  - the actual model asset must be provisioned locally or by release packaging
- Initial Android direct `ByteBuffer` tensor binding produced all-zero masks.
  - Python with the same model and d-vector produced non-zero masks
  - Android array-shaped input/output binding fixed mask output
- Managed TSE is still not wired as true live-path processing.
  - capture path still uses `TseAudioPreprocessor` as raw passthrough
  - DataCollect saves raw sibling first, then runs managed TSE offline and saves processed audio back to `*.app.wav`
- Current waveform reconstruction differs from the Python reference in details.
  - Python reference uses `librosa.stft(... center=False)` and `librosa.istft(... center=False)`, then trims `160` samples and peak-normalizes
  - app path uses custom STFT/IFFT/overlap-add and peak-normalizes offline output
  - listening and waveform parity still need validation

### Follow-Up Artifact

- date: `2026-05-07`
- model candidate: `voice_filter_lite_ai_edge.tflite`
- source note: `/media/hhs/FastData/workspace/TSE/release/android/litert_gpu_short_term.md`
- tensor contract: same as `voice_filter_lite.tflite`
  - `x [1,1,32,257]`
  - `embed [1,192]`
  - `h/c [2,1,512]`
  - `mask [1,257,1]`
- desktop verification note from source doc:
  - `test_stream_litert.py --model release/android/voice_filter_lite_ai_edge.tflite`
  - `675` frames
  - average desktop CPU latency `33.90 ms/frame`
  - output RMS matched the existing LiteRT export
- app-side integration note:
  - default managed TSE asset changed to `voice_filter_lite_ai_edge.tflite`
  - actual asset remains ignored by `app/src/main/assets/.gitignore`
- open issue:
  - source doc warns GPU delegate and interpreter invoke should stay on the same thread
  - current app initializes managed TSE from coroutine contexts and can invoke from later coroutine work
  - this must be fixed before interpreting any Pixel/Tensor G2 GPU result as final

### Transformer 64D Artifact

- date: `2026-05-07`
- model candidate: `voice_filter_transformer_64d_fp16.tflite`
- d-vector candidate: `dvector_64d_norm.bin`
- source note: `/media/hhs/FastData/workspace/TSE/docs/TRANSFORMER_DENOISE_ANDROID_DEPLOYMENT.md`
- local asset note:
  - copied to `app/src/main/assets/voice_filter_transformer_64d_fp16.tflite`
  - copied to `app/src/main/assets/voice_filter_transformer_64d_int8.tflite`
  - copied to `app/src/main/assets/dvector_64d.bin`
  - copied to `app/src/main/assets/dvector_64d_norm.bin`
  - model and d-vector assets remain ignored by `app/src/main/assets/.gitignore`
- SHA-256:
  - fp16 model: `294cd091ddf7b8b8711174a634ab8044e7684c45dbfaca4b0df766b2304b4fca`
  - int8 model: `5c32dff01179dd41f27d4ec35dbb0ff7b5aedef0fe38843c895b7c4fe0f709e2`
  - raw 64D d-vector: `65fc7e26f20bb76fd529761bb650ce2a849d4cbb1161531a664042b100435913`
  - normalized 64D d-vector: `d985bff59eb10a64183633c9419153a210e22ecacc2bd450531715035b66218b`
- default fp16 tensor contract:
  - `spec_input float32 [1,80,257,1]`
  - `embed_input float32 [1,64]`
  - `pos_input float32 [1,81]`
  - `mask output float32 [1,80,257,1]`
- int8 artifact tensor contract:
  - same inputs
  - `mask output int8 [1,80,257,1]`
  - output quantization: `scale=0.00390625`, `zeroPoint=-128`
- streaming contract:
  - one hop remains `160 samples / 10 ms`
  - model context is `80` STFT frames, roughly `800 ms` of hop history
  - runner shifts the spectrogram context and uses only output frame `79` as the current mask
- denoise post-processing:
  - `mask_power=1.0`
  - `smoothing=0.7`
  - `soft_gate=0.15`
  - `soft_gate_gain=0.2`
  - `hard_gate=0.08`
  - no fixed target RMS normalization
- app-side integration note:
  - `ManagedTseMaskPipeline` and `ManagedTseWaveformPipeline` previously defaulted to `voice_filter_transformer_64d_fp16.tflite`
  - default 64D embedding changed to `dvector_64d_norm.bin`
  - transformer runner supports both float32 output and int8 output artifacts
  - old LSTM `ManagedTseProbe` remains available as a separate runner path
  - GPU delegate creation is attempted directly; if interpreter creation fails, app falls back to CPU
  - transformer interpreter creation and invocation are pinned to one executor thread to satisfy GPU delegate thread affinity
- open issue:
  - no Pixel 7/Tensor G2 runtime log captured yet for this transformer model
  - need confirm whether direct GPU delegate creation succeeds or falls back to CPU on device
  - need compare saved `*.raw.app.wav` and transformer-processed `*.app.wav` by listening and waveform stats

### Transformer 64D FP16 Pixel 7 Runtime

- date: `2026-05-07 22:22:48`
- device target: `Pixel 7 / Tensor G2`
- model: `voice_filter_transformer_64d_fp16.tflite`
- d-vector: `dvector_64d.bin`
- tensor allocation: `pass`
- observed tensor contract:
  - input 0: `serving_default_spec_input:0 FLOAT32 [1,80,257,1]`
  - input 1: `serving_default_embed_input:0 FLOAT32 [1,64]`
  - input 2: `serving_default_pos_input:0 FLOAT32 [1,81]`
  - output 0: `StatefulPartitionedCall_1:0 FLOAT32 [1,80,257,1]`
  - quantization: `0.0/0`, as expected for float output
- app runner branch: `outputType=FLOAT32`
- GPU availability probe: `true`
- selected accelerator: `CPU fallback`
- immediate conclusion:
  - fp16 model loads and allocates with the expected Android contract
  - direct GPU delegate creation still does not become the active runtime on this device/app path
  - next GPU investigation should focus on the warning/exception emitted before CPU fallback and on direct delegate factory support in Play services LiteRT

### TCN Causal Ratio Tensor G2 Deployment Note

- date: `2026-05-08`
- source note: `/media/hhs/FastData/workspace/TSE/docs/TCN_CAUSAL_RATIO_TENSOR_G2_DEPLOYMENT.md`
- recommended TCN artifacts now available in TSE release output:
  - `voice_filter_tcn_64d_fp16.tflite`
  - `voice_filter_tcn_64d_fp32.tflite`
  - `voice_filter_tcn_64d_int8.tflite`
  - `dvector_64d_norm.bin`
- app default as of 2026-05-09:
  - model: `voice_filter_tcn_64d_fp16.tflite`
  - d-vector: `dvector_64d_norm.bin`
  - local asset copied to `app/src/main/assets/voice_filter_tcn_64d_fp16.tflite`
- SHA-256:
  - TCN fp16 model: `1b5d564bc5497c4e61f98f793f6ad553afe1772365504e9ace4b737ed4695721`
  - normalized 64D d-vector: `d985bff59eb10a64183633c9419153a210e22ecacc2bd450531715035b66218b`
- TCN FP16/FP32 contract:
  - `spec_input float32 [1,80,257,1]`
  - `embed_input float32 [1,64]`
  - `mask output float32 [1,80,257,1]`
- important contract difference from current transformer runner:
  - TCN has `2` inputs, no `pos_input`
  - app runner now detects `2` inputs and invokes TCN with `specBuffer + embed`
  - app runner still supports `3` input Transformer artifacts with `specBuffer + embed + posInput`
- quality note from source doc:
  - TCN FP16/FP32 TFLite is better than mixed audio but still below tuned Transformer/Lite AI Edge quality
  - TCN is expected to be more Tensor G2 GPU-friendly because it is convolution-heavy and avoids attention/recurrent ops
- static-shape export update:
  - source script: `/media/hhs/FastData/workspace/TSE/scripts/export_tflite_tcn_64d_gpu.py`
  - script now exports from a concrete function with fixed input signatures:
    - `spec_input [1,80,257,1]`
    - `embed_input [1,64]`
  - local app asset refreshed from `/media/hhs/FastData/workspace/TSE/release/android/voice_filter_tcn_64d_fp16.tflite`
  - expected Android signature after reinstall: no `-1` in `shapeSignature`
  - 2026-05-09 20:23 artifact also removes GPU-hostile 4D tensors:
    - `bad_4d_count=0`
    - no `[128,1,1,1]` tensors remain

### TCN 64D FP16 Pixel 7 Runtime

- date: `2026-05-09 16:24:14`
- device target: `Pixel 7 / Tensor G2`
- model: `voice_filter_tcn_64d_fp16.tflite`
- d-vector: `dvector_64d_norm.bin`
- tensor allocation: `pass`
- observed tensor contract:
  - input 0: `serving_default_spec_input:0 FLOAT32 [1,80,257,1]`
  - input 1: `serving_default_embed_input:0 FLOAT32 [1,64]`
  - output 0: `StatefulPartitionedCall_1:0 FLOAT32 [1,80,257,1]`
  - quantization: `0.0/0`, as expected for float output
- app runner branch:
  - `contract=TCN_2_INPUT`
  - `outputType=FLOAT32`
- GPU availability probe: `true`
- selected accelerator: `CPU fallback`
- immediate conclusion:
  - TCN model loads with the expected 2-input Android contract
  - switching away from Transformer did not by itself make the Play services GPU delegate active
  - next required log is the GPU delegate creation exception before fallback; app initialized log now includes `gpuFailure=...`

### TCN 64D FP16 Dynamic Signature Failure

- date: `2026-05-09`
- observed failure:
  - GPU delegate factory is now being used correctly
  - GPU delegate creation still failed with `Internal error: Cannot create interpreter`
  - runtime logged: `Attempting to use a delegate that only supports static-sized tensors with a graph that has dynamic-sized tensors`
- root cause:
  - previous TCN fp16 artifact had static allocated shapes but dynamic signatures:
    - `shape=[1,80,257,1] sig=[-1,80,257,1]`
    - `shape=[1,64] sig=[-1,64]`
  - Play services GPU delegate rejects dynamic tensor signatures during interpreter/delegate creation
- fix applied:
  - TSE export script now uses fixed concrete input signatures
  - app asset refreshed to SHA `5fa053a18927e5537f1babc636abed30420612e66355bd3e2cfac673a0c35fa9`
- expected next device log:
  - input signatures should be `[1,80,257,1]` and `[1,64]`, not `[-1,...]`
  - if signatures are static and GPU still fails, `gpuFailure` should identify a real op/delegate limitation

### TCN 64D FP16 OpenCL Kernel Compile Failure

- date: `2026-05-09 19:46`
- static signature result:
  - input 0: `spec_input FLOAT32 [1,80,257,1] sig=[1,80,257,1]`
  - input 1: `embed_input FLOAT32 [1,64] sig=[1,64]`
  - output 0: `Identity FLOAT32 [1,80,257,1] sig=[1,80,257,1]`
- GPU delegate progress:
  - `TfLiteGpuDelegateV2` was selected
  - `Replacing 228 out of 228 node(s) with delegate`, one partition
  - static signature issue is fixed
- observed failure:
  - OpenCL library loaded
  - interpreter creation failed while building GPU program executable
  - generated OpenCL contained invalid source:
    - `read_imageh(... (int2)(((0) * shared_int4_2.y + (())), (0)))`
    - compiler error: `expected expression`
- conclusion:
  - this is no longer an Android app binding issue or dynamic-shape issue
  - this is a GPU delegate OpenCL codegen/compiler failure for the exported TCN graph on this Pixel/GMS runtime
- app-side mitigation:
  - force GPU delegate backend to `OPENGL` instead of default/OpenCL
  - CPU/XNNPACK fallback remains enabled
- expected next device log:
  - success path: `accelerator=GPU delegate (OPENGL) gpuFailure=none`
  - failure path: fallback with an OpenGL-specific `gpuFailure`

### TCN 64D FP16 OpenGL Batch-Dimension Failure

- date: `2026-05-09`
- tested mitigation:
  - app forced `GpuDelegateFactory.Options.GpuBackend.OPENGL`
  - this bypassed the previous OpenCL codegen error path
- observed result:
  - static signatures remained correct:
    - `spec_input [1,80,257,1] sig=[1,80,257,1]`
    - `embed_input [1,64] sig=[1,64]`
    - `Identity [1,80,257,1] sig=[1,80,257,1]`
  - GPU delegate still failed during prepare/init
- failure:
  - `TfLiteGpuDelegate Init: Batch size mismatch, expected 1 but got 11 values with divergent batch sizes`
  - offending tensors are all shaped `[128,1,1,1]`
  - example tensor ids: `4, 6, 11, 16, 21, 26, 31, 36, 41, 46, 51`
- interpretation:
  - TFLite GPU delegate treats 4D tensors as BHWC-style tensors where dim 0 is batch
  - `[128,1,1,1]` is interpreted as batch `128`, conflicting with model batch `1`
  - these tensors likely come from channel-wise constants or FiLM/BatchNorm-style broadcast tensors exported in a channel-first-like 4D shape
- conclusion:
  - GPU API binding is fixed
  - static signature is fixed
  - remaining blocker is TCN export graph layout/broadcast compatibility with TFLite GPU delegate
- export-side fix direction:
  - avoid graph tensors shaped `[128,1,1,1]`
  - channel-wise broadcast constants should be represented as `[1,1,1,128]` for NHWC/BHWC GPU delegate compatibility
  - inspect TFLite tensor ids around `4,6,11,16,...` to identify whether they originate from BatchNorm folding, FiLM gamma/beta, or Conv/SeparableConv reshapes
- fix status:
  - 2026-05-09 20:23 artifact changed TCN export to GPU-static variant
  - TFLite tensor inspection reports `bad_4d_count=0`
  - input/output signatures remain static
- app-side status:
  - CPU/XNNPACK fallback remains valid
  - app asset refreshed to SHA `5fa053a18927e5537f1babc636abed30420612e66355bd3e2cfac673a0c35fa9`
  - next device run should test whether GPU delegate now prepares successfully

### TCN 64D FP16 FullyConnected GPU Failure

- date: `2026-05-09`
- artifact: `voice_filter_tcn_64d_fp16.tflite`
- artifact SHA: `5fa053a18927e5537f1babc636abed30420612e66355bd3e2cfac673a0c35fa9`
- status before this failure:
  - static input/output signatures are fixed
  - `[128,1,1,1]` bad 4D tensors are removed
- observed OpenGL failure:
  - `TfLiteGpuDelegate Init: FULLY_CONNECTED: Amount of input channels should match weights width`
  - `Node number 228 (TfLiteGpuDelegateV2) failed to prepare`
- local graph inspection:
  - the GPU-static artifact uses many `FULLY_CONNECTED` ops
  - embedding FiLM branches use valid 2D inputs such as `[1,64] x [128,64]`
  - projection branches also use `FULLY_CONNECTED` over feature maps such as:
    - input `[1,80,257,128]`
    - weights `[128,128]`
    - output `[1,80,257,128]`
  - some FC/bias paths create intermediate rank-1 tensors such as `[128]`
- interpretation:
  - replacing 1x1 Conv/SeparableConv pointwise projection with `Dense` removed GPU-hostile 4D filter tensors
  - but TFLite GPU delegate does not accept this rank-4 feature-map FULLY_CONNECTED pattern reliably
- app mitigation:
  - app now tries GPU delegate backends in order:
    - default backend
    - `OPENCL`
    - `OPENGL`
- app policy update:
  - CPU/XNNPACK fallback is no longer allowed for managed TSE inference
  - if all GPU delegate backends fail, managed TSE initialization fails
  - DataCollect preserves the raw sibling WAV and saves `*.app.wav` as raw passthrough with runtime `gpu_required_unavailable_raw_passthrough`
- export-side likely next step:
  - avoid `FULLY_CONNECTED` on `[1,80,257,128]` feature maps
  - either restore GPU-compatible `Conv2D 1x1` without producing `[128,1,1,1]` tensors, or restructure pointwise projection into ops that TFLite GPU accepts
  - keep FiLM embedding Dense branches if they remain 2D `[1,64] -> [1,128]`

### TCN 64D FP16 Conv Projection Artifact

- date: `2026-05-09`
- artifact SHA: `1b5d564bc5497c4e61f98f793f6ad553afe1772365504e9ace4b737ed4695721`
- app asset refreshed from `/media/hhs/FastData/workspace/TSE/release/android/voice_filter_tcn_64d_fp16.tflite`
- positive changes:
  - static input/output signatures remain fixed
  - rank-4 `FULLY_CONNECTED` count is `0`
  - graph is again mostly `CONV_2D` / `DEPTHWISE_CONV_2D`
- local tensor inspection:
  - `bad_4d_count=40`
  - offending constants are shaped `[128,1,1,128]`
  - examples:
    - `arith.constant52 [128,1,1,128]`
    - `tfl.dequantize24 [128,1,1,128]`
- risk:
  - first dimension is still `128`, so TFLite GPU delegate may interpret these tensors as batch `128`
  - this may reintroduce a batch-size mismatch during GPU prepare
- next Android test:
  - if GPU fails with batch mismatch, export still needs to force Conv2D filters into a layout accepted by the GPU delegate
  - if GPU prepares, this artifact is the new best candidate

### TCN 64D FP16 Split Conv Artifact

- date: `2026-05-09`
- artifact: `voice_filter_tcn_64d_fp16_split.tflite`
- SHA-256: `2589ee129812d3918ee2284f0955a3415e810299d229debe9438b5a336efe8a3`
- app default updated:
  - `ManagedTseMaskPipeline`
  - `ManagedTseWaveformPipeline`
- tensor contract:
  - input 0: `spec_input FLOAT32 [1,80,257,1] sig=[1,80,257,1]`
  - input 1: `embed_input FLOAT32 [1,64] sig=[1,64]`
  - output 0: `Identity FLOAT32 [1,80,257,1] sig=[1,80,257,1]`
- graph inspection:
  - `bad_4d_count=0`
  - `rank4_fc_count=0`
  - `CONV_2D=2561`
  - `DEPTHWISE_CONV_2D=11`
  - `FULLY_CONNECTED=21`, expected for 2D embedding/FiLM branches
- assessment:
  - cleanest GPU-delegate candidate so far
  - high `CONV_2D` count means latency must be measured on device even if delegate prepare succeeds
- expected next device log:
  - success: `accelerator=GPU delegate (...)`
  - failure: `gpuFailure` should identify a new, more specific delegate limitation

### TCN 64D FP16 Split Conv Android Result

- date: `2026-05-09 22:48`
- artifact: `voice_filter_tcn_64d_fp16_split.tflite`
- SHA-256: `2589ee129812d3918ee2284f0955a3415e810299d229debe9438b5a336efe8a3`
- Android result: `fail`
- policy: managed TSE is GPU-required, so initialization fails instead of falling back to CPU
- observed delegate behavior:
  - XNNPACK can create a CPU interpreter but is intentionally rejected for this path
  - GPU DEFAULT / OPENCL replace `7908/7908` nodes, then fail during shader build
  - GPU OPENGL replaces `7908/7908` nodes, then fails during prepare with divergent batch sizes
- key errors:
  - `Failed to build program executable`
  - generated OpenCL contains `read_imageh(... + (()))`, which is malformed
  - `Batch size mismatch, expected 1 but got 11 values with divergent batch sizes`
  - internal transformed tensors still include shapes such as `[128,1,1,1]`
- interpretation:
  - local flatbuffer inspection is no longer enough; the raw model has no obvious bad 4D constants or rank-4 FC ops, but the Play services GPU delegate transformation still creates a layout it cannot compile
  - this is a TFLite/GMS GPU delegate graph-lowering limitation for this TCN family on Tensor G2, not an app binding issue
- app cleanup after this result:
  - removed `DataCollectViewModel` startup `ManagedTseProbe` validation for the old LSTM model
  - removed startup synthetic `ManagedTseMaskPipeline` probe
  - removed the unused live `ManagedTseMaskPipeline`
  - only the current live waveform pipeline initializes, avoiding repeated GPU delegate failures and old `voice_filter_lite_ai_edge` acceleration validation noise
- next export-side direction:
  - build a tiny GPU-smoke model from the same TCN block family and find the first block/op count that triggers malformed OpenCL
  - reduce or replace the split-conv implementation that expands the graph to thousands of `CONV_2D` nodes
  - test a real int8 export only if targeting CPU/NNAPI-style acceleration; int8 is unlikely to fix the current GPU delegate FP16/OpenCL codegen failure

### Transformer 64D FP16 Recheck

- date: `2026-05-09`
- artifact: `/media/hhs/FastData/workspace/TSE/release/android/voice_filter_transformer_64d_fp16.tflite`
- SHA-256: `294cd091ddf7b8b8711174a634ab8044e7684c45dbfaca4b0df766b2304b4fca`
- app asset status:
  - same SHA already present at `app/src/main/assets/voice_filter_transformer_64d_fp16.tflite`
  - no asset refresh needed
- tensor contract:
  - input 0: `FLOAT32 [1,80,257,1] sig=[-1,80,257,1]`
  - input 1: `FLOAT32 [1,64] sig=[-1,64]`
  - input 2: `FLOAT32 [1,81] sig=[-1,81]`
  - output 0: `FLOAT32 [1,80,257,1] sig=[-1,80,257,1]`
- graph characteristics:
  - `BATCH_MATMUL=6`
  - `TRANSPOSE=12`
  - `SOFTMAX=3`
  - `FULLY_CONNECTED=21`
  - `rank4_fc_count=0`
  - `bad_4d_count=2`, filter-like tensors `[128,5,5,1]`
- conclusion:
  - this is still a dynamic-signature Transformer artifact
  - it keeps attention-style ops that were the original reason to prefer TCN for Tensor G2 GPU testing
  - do not switch app default back to this artifact unless intentionally running a negative/control test

### Return To Voice Filter Lite

- date: `2026-05-10`
- decision:
  - abandon the TCN Tensor G2 GPU path for now
  - return managed TSE default to the CNN+LSTM `voice_filter_lite.tflite` model
- source assets:
  - `/media/hhs/FastData/workspace/TSE/release/android/voice_filter_lite.tflite`
  - `/media/hhs/FastData/workspace/TSE/release/android/dvector.bin`
- app assets refreshed:
  - `app/src/main/assets/voice_filter_lite.tflite`
  - `app/src/main/assets/dvector.bin`
- SHA-256:
  - `voice_filter_lite.tflite`: `cb440929a5ac330e63ead111562f44d8f6ed24823d67a8b110956d6d457c1b51`
  - `dvector.bin`: `c7e83afbb9676a28bac11856124e8330e54e60a28231474152c76e284df26756`
- app changes:
  - `ManagedTseMaskPipeline` now defaults to `ManagedTseFrameRunner`
  - `ManagedTseWaveformPipeline` now defaults to `ManagedTseFrameRunner`
  - default model/d-vector changed to `voice_filter_lite.tflite` + `dvector.bin`
  - processed WAV runtime label changed from `managed_gpu_offline` to `managed_lite_offline`
- note:
  - this path uses the CNN+LSTM 4-input / 3-output contract through `ManagedTseProbe`
  - acceleration verdict must be read from the next device log; the runtime label is intentionally neutral and no longer claims GPU

### Voice Filter Lite GPU Validation Failure

- date: `2026-05-10 18:22`
- model: `voice_filter_lite.tflite`
- observed result:
  - Play services TFLite GPU module loads
  - mini-benchmark runs against `voice_filter_lite`
  - GPU validation fails with `BenchmarkError{stage=INITIALIZATION, benchmarkErrorCode=1008}`
  - interpreter then initializes with XNNPACK CPU fallback
- startup impact:
  - validation attempt blocks cold startup for roughly 12-15 seconds
  - app logs `Skipped 2546 frames`, consistent with startup work overwhelming UI responsiveness
- app mitigation:
  - `ManagedTseProbe` no longer calls `AccelerationService.selectBestConfig()` during normal initialization
  - interpreter is created directly from Play services TFLite runtime
  - log now reports `accelerator=CPU direct benchmarkPassed=false validationSkipped=true`
- conclusion:
  - this confirms GPU delegate is unavailable for the Lite CNN+LSTM path on the current device/runtime
  - next optimization target is CPU latency and avoiding main-thread startup work, not more GPU validation retries

### Voice Filter Lite Live CPU Latency

- date: `2026-05-10 19:04`
- model: `voice_filter_lite.tflite`
- live log interpretation:
  - hop 1 at `19:04:33.395`
  - hop 700 at `19:05:15.828`
  - 699 processed hops took roughly `42.4 s`
  - each hop represents `10 ms` of audio
- observed throughput:
  - average wall-clock time is roughly `60 ms/hop`
  - live path is about `6x` slower than real-time
- app mitigation:
  - live mic probe now logs per-hop `processMs`, `avgProcessMs`, and `maxProcessMs`
  - live mic probe keeps a bounded pending buffer and drops old hops when it falls behind
  - this prevents unbounded background backlog while preserving diagnostic stats
- conclusion:
  - CNN+LSTM Lite is functionally producing non-zero masks and reconstructed waveform
  - current Android CPU pipeline is not live real-time at 10 ms hop cadence

### Offline LiteRT Fallback Removed

- date: `2026-05-10`
- app change:
  - removed `RecognitionManager.runManagedTseOffline()`
  - removed the offline `ManagedTseWaveformPipeline` fallback after native TSE failure
  - DataCollect final-utterance post-processing now uses native ONNX TSE if available
  - if native ONNX TSE fails, the saved `.app.wav` uses raw passthrough
- runtime labels:
  - native success: `native_onnx_lite_offline`
  - native unavailable: `native_onnx_lite_unavailable_raw_passthrough`
- reason:
  - avoid silently falling back to the slow LiteRT managed path
  - keep native TSE as the only post-processing implementation under test

### TensorFlow Lite Runtime Removed

- date: `2026-05-10`
- app change:
  - removed all managed TSE Kotlin source files that used TensorFlow Lite / LiteRT
  - removed `play-services-tflite-java`
  - removed `play-services-tflite-gpu`
  - removed `play-services-tflite-acceleration-service`
  - removed the DataCollect live managed TSE probe
- remaining TSE path:
  - native ONNX `NativeTSE`
  - `NativeTseWaveformPipeline`
  - `RecognitionManager.runNativeTseOffline()`
- expected device behavior:
  - no `TFLite-in-PlayServices`
  - no `tflite_gpu_dynamite`
  - no `libtensorflowlite_jni_gms_client.so`
  - DataCollect final utterance should log `native_onnx_lite_offline` on native success

### Native ONNX Lite Current Performance

- date: `2026-05-10 22:42`
- runtime:
  - native `NativeTSE`
  - ONNX Runtime CPU only
  - `intraOpThreads=1`
  - `nnapiRequested=false`
  - `nnapiAttached=false`
- model contract:
  - input `x [1,1,32,257]`
  - input `embed [1,192]`
  - input `h_in [2,1,512]`
  - input `c_in [2,1,512]`
  - output `mask [1,257,1]`
  - output `h_out [2,1,512]`
  - output `c_out [2,1,512]`
- observed initialization:
  - ORT session created in roughly `78 ms`
- observed inference samples:
  - `12.15 ms`
  - `13.27 ms`
  - `13.63 ms`
  - `12.53 ms`
  - `13.37 ms`
- offline utterance result:
  - input samples: `76928`
  - output samples: `76928`
  - runtime label: `native_onnx_lite_offline`
  - app WAV saved successfully
  - raw sibling WAV saved successfully
- interpretation:
  - native ONNX path is much faster than managed LiteRT/Kotlin STFT path
  - current per-hop inference is still above the `10 ms` live budget, but close enough to optimize
- next optimization candidates:
  - benchmark `intraOpThreads=2` and `4`
  - keep native session alive for live mode instead of initializing at final utterance time
  - reduce JNI per-hop allocation/copying if moving this path into live processing

### Native ONNX Transformer 64D FP32 (Debug)
- date: `2026-05-12`
- source artifact:
  - `/media/hhs/FastData/workspace/TSE/release/android/transformer_64d_fp32.onnx`
- app asset:
  - `app/src/main/assets/transformer_64d_fp32.onnx`
- dvector:
  - updated `dvector.bin`
- acceleration:
  - default to CPU
- notes:
  - use FP32 version for debugging and SHA256 integrity verification

### Native ONNX Transformer 64D Int8
- date: `2026-05-12`
- source artifact:
  - `/media/hhs/FastData/workspace/TSE/release/android/transformer_64d_int8.onnx`
- app asset:
  - `app/src/main/assets/transformer_64d_int8.onnx`
- native engine:
  - updated `libtse_engine.so` from latest release
- acceleration:
  - default to CPU (as requested for initial validation)
- notes:
  - switch from LSTM to Transformer architecture
  - bottleneck dimension: 64D
  - expected per-frame CPU latency: 3.9ms - 4.4ms (measured on Pixel 7/8)

### Native ONNX Transformer Energy 16D Int8

- date: `2026-05-14`
- source artifacts:
  - `/media/hhs/FastData/workspace/TSE/release/android/libtse_engine.so`
  - `/media/hhs/FastData/workspace/TSE/release/android/transformer_energy_16d_int8.onnx`
  - `/media/hhs/FastData/workspace/TSE/release/android/dvector.bin`
- app artifacts:
  - `app/src/main/jniLibs/arm64-v8a/libtse_engine.so`
  - `app/src/main/assets/transformer_energy_16d_int8.onnx`
  - `app/src/main/assets/dvector.bin`
- SHA-256:
  - `libtse_engine.so`: `ab717097c82cbc320e6ae104fb911be5370e9dc38c11600d7ad045071d8eb4af`
  - `transformer_energy_16d_int8.onnx`: `c034ecc27135f1b4fa9a59fa42a673e2e14fd036a15c2bf332f9682c2b741627`
  - `dvector.bin`: `c7e83afbb9676a28bac11856124e8330e54e60a28231474152c76e284df26756`
- app defaults updated:
  - `NativeTseWaveformPipeline`
  - `TseAudioPreprocessor`
- note:
  - rebuilt native engine contains `input_energy`, confirming this is the transformer energy native path
- expected next device log:
  - native model should initialize with energy-specific input contract
  - compare realtime/offline quality and latency against previous `transformer_64d_int8.onnx` and energy 64D candidate

### Native ONNX Transformer Energy 16D 1L Int8

- date: `2026-05-15`
- source artifact:
  - `/media/hhs/FastData/workspace/TSE/release/android/transformer_energy_16d_1L_int8.onnx`
- app asset:
  - `app/src/main/assets/transformer_energy_16d_1L_int8.onnx`
- SHA-256:
  - `transformer_energy_16d_1L_int8.onnx`: `6506565fad661591bbb923d7a8ddc0cc1bb986c899e026ee50a4274462e88d38`
- app defaults updated:
  - `NativeTseWaveformPipeline`
  - `TseAudioPreprocessor`
- note:
  - switch current native ONNX default from `transformer_energy_16d_int8.onnx` to the 1-layer 16D energy candidate
  - keep CPU as the default execution path for Tensor G2 validation
- expected next device log:
  - native model should initialize through the existing JNI/ORT path
  - compare realtime/offline latency and ASR quality against the previous 16D energy candidate

### Native ONNX 192D Negative Int8 Candidate

- date: `2026-05-12`
- source artifact:
  - `/media/hhs/FastData/workspace/TSE/release/android/voice_filter_lite_192d_neg_int8.onnx`
- app asset:
  - `app/src/main/assets/voice_filter_lite_192d_neg_int8.onnx`
- SHA-256:
  - `781633d21e457d731e2c4e7399a3f6606471dec383dcb8f9ee3d8c09b2dcf105`
- app defaults updated:
  - `NativeTseWaveformPipeline`
  - `TseAudioPreprocessor`
- d-vector:
  - unchanged: `dvector.bin`
- expected next device log:
  - native model input/output contract should remain 4-input / 3-output VoiceFilter Lite
  - runtime label should remain native ONNX
  - compare inference time and ASR/quality against previous `voice_filter_lite_int8.onnx`

### Native IO Name Resolver Update

- date: `2026-05-12`
- observed failure:
  - new `voice_filter_lite_192d_neg_int8.onnx` loads successfully
  - model input names are `spec_input`, `embed_input`, `h_in`, `c_in`
  - model output names are `mask_out`, `h_out`, `c_out`
  - native engine was still hardcoded to old names `x`, `embed`, `mask`
  - inference failed with `Invalid input name: x`
- native fix:
  - `TSEEngine` now resolves input/output names from the ORT session during initialization
  - supported aliases include:
    - input 0: `x`, `spec_input`, `serving_default_args_0`
    - input 1: `embed`, `embed_input`, `serving_default_args_1`
    - output 0: `mask`, `mask_out`, `serving_default_output_0_output`
  - rebuilt `libtse_engine.so`
  - copied rebuilt library into `app/src/main/jniLibs/arm64-v8a/libtse_engine.so`
- native library SHA-256:
  - `2e0f69286b6b0e007ca91ab368b495c798b2b41d572e9206eb09a60320514567`
- expected next device log:
  - `[Model] resolved inputs=[spec_input,embed_input,h_in,c_in] outputs=[mask_out,h_out,c_out]`
  - no `Invalid input name: x`

### GPU Delegate Binding Fix

- date: `2026-05-09 16:30:10`
- observed failure:
  - `IllegalArgumentException`
  - `Instantiated delegates (other than NnApiDelegate) are not allowed when using TF Lite from Google Play Services`
  - Play services requires `InterpreterApi.Options.addDelegateFactory()` instead of `addDelegate()`
- root cause:
  - app was creating `com.google.android.gms.tflite.gpu.GpuDelegate()` directly
  - that delegate instance was passed with `InterpreterApi.Options.addDelegate(delegate)`
  - Google Play services LiteRT rejects manually instantiated delegates for GPU
- fix:
  - use `org.tensorflow.lite.gpu.GpuDelegateFactory`
  - attach it through `InterpreterApi.Options.addDelegateFactory(GpuDelegateFactory(options))`
  - keep CPU fallback if factory-based GPU interpreter creation still fails
- expected next log:
  - success path: `accelerator=GPU delegate gpuFailure=none`
  - if graph/runtime still fails: fallback remains, but `gpuFailure` should now report the real delegate prepare/runtime error rather than the binding API error

### Overall Verdict

- loadability: `pass`
- acceleration verdict: `LSTM/Transformer paths fell back to CPU; TCN 64D fp16 path pending device test`
- latency verdict: `unknown`
- quality verdict: `LSTM path partially validated; transformer 64D app output pending listening test`
- live-path verdict: `not ready`
- recommended next step:
  - capture app-side per-hop inference latency
  - compare saved `*.raw.app.wav` and `*.app.wav` by listening and waveform stats
  - confirm TCN 64D fp16 GPU delegate creation on Tensor G2
  - decide whether to align Android STFT/ISTFT more closely to the Python `librosa` reference
