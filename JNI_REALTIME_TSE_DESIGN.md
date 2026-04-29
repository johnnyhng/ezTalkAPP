# JNI Realtime TSE Design

## Goal

Build a low-latency realtime `raw -> TSE -> VAD -> ASR` path using native code for the TSE stage.

The current Kotlin + ORT Java prototype is useful for validation, but it is not the preferred final architecture for production realtime use.

## Why JNI Rebuild Is Needed

### Observed issues in the Kotlin/ORT prototype

1. Latency is too high for a 10 ms hop pipeline.
2. Audio quality is inconsistent and often over-suppressed.
3. Every hop currently pays Java/Kotlin orchestration cost:
   - array slicing/copying
   - tensor creation
   - repeated Java to native bridge overhead
   - FFT/STFT/ISTFT work in managed code
4. The model is sequence-based and requires context, so each hop reruns a full context window instead of true incremental inference.

### Consequence

The Kotlin/ORT path proves that:

- the model runs
- the mask is not all ones
- TSE can alter the signal enough for VAD to still trigger

But it is not the right implementation strategy for a production realtime path.

## Model Constraints

Verified model interface:

- input `x`: `[1, 1, T, 257]`
- input `embed`: `[1, 192]`
- output `mask`: `[1, 257, T]`

Implications:

1. The model does not take waveform directly.
2. The model expects magnitude-spectrum features with temporal context.
3. `dvector.bin` is a `float32` speaker embedding of length `192`.
4. Realtime use requires maintaining a history window and using only the latest output mask.

## Root Cause of Realtime Quality Problems

The VoiceFilter model uses temporal context and behaves like an LSTM-style sequence model.

If we only feed the current frame or rebuild short chunks without proper state handling:

- context is weak or reset
- separation quality collapses
- suppression becomes unstable
- latency increases because more context has to be recomputed repeatedly

The Python reference already identified the required workaround:

- maintain a sliding magnitude history
- run inference on the whole context window
- only use the last time step of the output mask

> [!NOTE]
> **Status: Verified (2026-04-29)**
> A Python-based simulator (`simulate_realtime_tse.py`) has confirmed that a **64-frame context window** (~640ms) effectively restores extraction quality for the INT8 ONNX model. Without this context, the LSTM state reset causes the separation to fail.


## Required Realtime Algorithm

### Fixed parameters

- sample rate: `16000`
- hop length: `160` samples
- window length: `400` samples
- FFT size: `512`
- freq bins: `257`
- context frames: `64`

### Per-hop processing

For every incoming `160` samples:

1. Shift analysis input buffer.
2. Append the new hop.
3. Apply analysis window.
4. Run `RFFT(512)`.
5. Compute:
   - magnitude `[257]`
   - phase `[257]`
6. Shift magnitude history `[64, 257]`.
7. Append the newest magnitude frame.
8. Run ORT inference with:
   - `x = [1, 1, 64, 257]`
   - `embed = [1, 192]`
9. Read `mask[:, :, -1]`, meaning only the last frame from the output sequence.
10. Apply:
    - `estimated_mag = magnitude * latest_mask`
11. Reconstruct complex spectrum with original phase.
12. Run inverse FFT.
13. Apply synthesis window.
14. Perform overlap-add into the output buffer.
15. Emit exactly `160` processed samples to downstream VAD.

## Native State That Must Persist

The JNI/native processor should be stateful and keep all of this alive across calls:

1. ORT session
2. Preloaded dvector embedding
3. Analysis input ring buffer
4. Synthesis/output overlap buffer
5. Window coefficients
6. Magnitude history buffer
7. Reusable FFT work buffers
8. Reusable input/output tensor buffers

Do not rebuild these per hop.

## Native API Recommendation

The JNI surface should stay minimal.

Recommended shape:

```kotlin
class NativeRealtimeTse {
    external fun init(modelPath: String, dvectorPath: String): Boolean
    external fun processHop(audio160: FloatArray): FloatArray?
    external fun reset(): Unit
    external fun release(): Unit
}
```

Constraints:

- `processHop()` should require exactly `160` samples.
- `reset()` should clear:
  - analysis buffer
  - synthesis buffer
  - magnitude history
  - any warm-up state

## Performance Requirements

To be viable in realtime:

1. Average hop processing time must stay well below `10 ms`.
2. Tail latency must not frequently exceed hop duration.
3. Per-hop allocations should be eliminated or near-zero.
4. Session and tensor setup must happen only once in `init()`.

> [!IMPORTANT]
> **Observed Simulation Performance (CPU):**
> - **RTF: 3.59** (on x86_64 simulation)
> - This implies that while the context window solves the quality issue, it increases computation significantly (re-running 64 frames per hop).
> - **Optimization Required:** For mobile NDK, we must use ORT NNAPI/GPU delegates or explore stateful LSTM state passing to avoid the redundant computation of the context window.


## Quality-Sensitive Areas

These are the most likely places to get poor sounding output even if inference is correct:

1. Window choice
2. Overlap-add normalization
3. FFT bin reconstruction symmetry
4. Context warm-up behavior
5. Mask over-suppression
6. Buffer alignment mistakes between analysis and synthesis stages

## Warm-up Strategy

At startup, the context buffer is initially zero-filled.

Possible strategies:

1. Pass through raw audio until context is filled enough.
2. Run TSE immediately but expect reduced quality in the first ~640 ms.
3. Hybrid strategy:
   - start with passthrough
   - fade into processed output after warm-up

For production UX, hybrid or passthrough warm-up is safer.

## Validation Plan

### Phase 1: Offline native verification

Build a native wav-to-wav test harness:

- input wav
- dvector
- output wav

Check:

- intelligibility
- suppression behavior
- waveform continuity
- no obvious boundary clicks

### Phase 2: Realtime simulator

Before integrating with Android mic capture, run a native realtime simulator with:

- fixed hop input
- context history
- overlap-add output

**Status: Completed (Python Reference)**
- Verified that 64-frame context is necessary and sufficient.
- Resulting audio [realtime_sim_output.wav] shows clear extraction and stable suppression.


### Phase 3: App integration

Once native realtime output is stable:

- connect `raw -> native realtime TSE -> VAD -> ASR`
- keep dual save:
  - `*.app.wav` = processed
  - `*.raw.app.wav` = raw

### Phase 4: Tuning

Tune:

- context size
- mask post-processing
- warm-up behavior
- thread count / execution provider

## Non-goals

For the JNI rebuild, do not solve all of these at once:

1. Translate screen integration
2. Speaker mode integration
3. Backend upload changes
4. Full 16 KB alignment cleanup for every unrelated dependency

First make the realtime TSE core correct and measurable.

## Recommended Build Direction

Preferred architecture:

- native DSP + native ORT session for TSE
- Kotlin/Java only orchestrates lifecycle and passes `160`-sample hops
- VAD and ASR continue to consume processed audio on the app side

This minimizes bridge overhead and keeps the most timing-sensitive work in native memory.

## Current Takeaway

The ORT Java prototype was useful because it proved:

- the model can run on-device
- the mask changes the signal
- VAD can still trigger on processed output

But for production realtime use, the critical path should move back to a native implementation with persistent buffers and per-hop incremental processing.
