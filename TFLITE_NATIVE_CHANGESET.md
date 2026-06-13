# TFLite Native Changeset

## Goal

Define the minimum native-side changes needed to migrate the current C++ TSE engine from:

- `ONNX Runtime` backend

to:

- `TFLite / LiteRT` backend

while preserving as much of the current realtime DSP pipeline as possible.

This document is based on the current native structure in:

- `/media/hhs/FastData/workspace/TSE/cpp/TSEEngine.h/.cpp`
- `/media/hhs/FastData/workspace/TSE/cpp/AudioEngine.h`
- `/media/hhs/FastData/workspace/TSE/cpp/SignalProcessor.h`

## Current Native Architecture

The current split is already good:

### `SignalProcessor`

Responsible for:

- `160`-sample hop input
- STFT
- current-frame magnitude/phase
- ISTFT
- overlap-add

This layer is still valid for TFLite migration.

### `AudioEngine`

Responsible for:

- calling `analyzeStreaming()`
- invoking `engine.processContext(...)`
- calling `synthesizeStreaming(...)`

This layer is also still valid with only small adjustments.

### `TSEEngine`

Currently responsible for:

- loading ONNX model
- loading `dvector.bin`
- managing feature history
- managing recurrent state
- running ORT inference
- returning current-frame mask

This is the layer that must change the most.

## Good News

The current `TSEEngine` structure already matches most of the TFLite `VoiceFilter-Lite` spec better than the older ONNX model:

- `kContextFrames = 32`
- explicit `h_state`
- explicit `c_state`
- current-frame mask output

That means the model contract and current C++ state layout are already conceptually aligned.

So this migration is mostly:

- backend swap
- tensor layout swap
- state tensor split/reshape cleanup

not a full redesign.

## File-by-File Changes

## 1. `TSEEngine.h`

### Keep

- `init(modelPath, dvectorPath)`
- `processContext(const std::vector<float>& currentMag, std::vector<float>& outputMask)`
- `resetHistory()`
- `kContextFrames = 32`
- `kFreqBins = 257`
- `kLstmDim = 512`

### Change

Remove ONNX-specific members:

- `Ort::Env env`
- `Ort::Session* session`
- `Ort::MemoryInfo`
- `std::vector<Ort::Value> inputTensors`
- ONNX input/output name arrays

Replace with TFLite/LiteRT-specific interpreter/session members.

### State shape change

Current code keeps:

- `h_state [2 * 1 * 512]`
- `c_state [2 * 1 * 512]`

The TFLite spec exposes:

- `h1 [1,512]`
- `c1 [1,512]`
- `h2 [1,512]`
- `c2 [1,512]`

Recommended native representation:

- either keep one flat `h_state/c_state` and split per layer when binding tensors
- or switch to explicit per-layer buffers:
  - `h1_state`
  - `c1_state`
  - `h2_state`
  - `c2_state`

I recommend explicit per-layer buffers for clarity.

### Feature buffer layout

Current comment:

- `magHistory [kContextFrames * kFreqBins]`

This can stay, but TFLite input packing must output:

- `[1,32,257,1]` in `NHWC`

## 2. `TSEEngine.cpp`

### Keep

- dvector validation and loading
- `resetHistory()`
- `framesProcessed`
- current-frame processing contract

### Remove

- all ONNX Runtime session code
- NNAPI provider attach code
- ORT tensor creation
- ORT `Run(...)`
- ORT-specific logs

### Replace with

- TFLite/LiteRT model load
- interpreter creation
- tensor index lookup
- input tensor writing
- `Invoke()`
- output tensor reading

### Required input bindings

Bind these inputs each frame:

- `x [1,32,257,1]`
- `embed [1,192]`
- `h1_in [1,512]`
- `c1_in [1,512]`
- `h2_in [1,512]`
- `c2_in [1,512]`

### Required outputs

Read these outputs each frame:

- `mask [1,257,1]`
- `h1_out [1,512]`
- `c1_out [1,512]`
- `h2_out [1,512]`
- `c2_out [1,512]`

### Key tensor-packing change

The current feature history is easy to maintain, but the old ONNX layout was not `NHWC`.

Now each inference must pack `magHistory` into:

- batch `1`
- time `32`
- freq `257`
- channel `1`

That packing should be done into a reusable flat input buffer.

### Logging change

Replace current ORT logs with:

- model loaded successfully
- interpreter created successfully
- tensor indices found
- input/output shapes verified
- backend/delegate configuration if available

Keep perf logging:

- inference time
- mask min/avg/max

## 3. `AudioEngine.h`

### Keep

- `processFrame(float* input160, float* output160)`
- `TSEEngine engine`
- `SignalProcessor processor`
- reusable:
  - `currentMag`
  - `currentPhase`
  - `currentMask`

### No major API change needed

This file is already in a good shape for the new TFLite model contract.

Possible small update:

- rename `processContext()` conceptually in comments from "context replay" to "stateful frame inference"

But this is optional.

## 4. `SignalProcessor.h`

### Keep as-is

This layer should not need structural change for TFLite migration.

It already does exactly what the Lite model still needs:

- current frame magnitude
- current frame phase
- apply current-frame mask
- reconstruct waveform

## 5. JNI Surface

### Current surface

The current Kotlin/native API:

- `init(modelPath, dvectorPath)`
- `processFrame(audioFrame)`
- `reset()`
- `release()`

### Recommendation

Keep it unchanged.

This is a strong constraint because it avoids app-level churn while swapping the backend.

## 6. Packaging Changes

## Current

The app currently loads:

- `c++_shared`
- `onnxruntime`
- `oboe`
- `tse_engine`

## Target

If the TSE path fully leaves ONNX:

- `onnxruntime` should eventually disappear from the TSE dependency chain

But do not remove it until you verify:

- no other runtime path still needs it
- TFLite/LiteRT native libraries are correctly packaged

## 7. Minimum Implementation Order

### Step 1

Refactor `TSEEngine` internals only:

- keep public API
- replace ORT backend with TFLite backend

### Step 2

Keep `AudioEngine` and `SignalProcessor` unchanged except for compile fixes.

### Step 3

Verify one-frame inference correctness:

- mask shape
- state update correctness
- no crashes on repeated invoke/reset

### Step 4

Validate in `DataCollectScreen` only before using live path.

## 8. Risk Areas

The highest-risk points are:

1. TFLite tensor ordering (`NHWC`)
2. mapping the four LSTM state tensors correctly
3. ensuring `reset()` zeros all recurrent state
4. packaging the right runtime/delegate libs
5. not accidentally preserving old ONNX assumptions in logs/comments/init code

## 9. One-Sentence Summary

The current native structure is already close to the TFLite `VoiceFilter-Lite` contract, so the migration should focus on swapping `TSEEngine` from ORT to a TFLite/LiteRT interpreter while keeping `AudioEngine`, `SignalProcessor`, and the JNI surface largely intact.
