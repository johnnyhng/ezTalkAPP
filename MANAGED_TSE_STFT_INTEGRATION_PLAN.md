# Managed TSE STFT Integration Plan

## Goal

Define the next concrete integration step for the managed TSE path:

- feed real `STFT magnitude` frames into `ManagedTseFrameRunner`

At this stage, the managed path already supports:

- model load
- interpreter creation
- dummy inference
- single-frame inference
- streaming CNN/LSTM state maintenance

The missing piece is the bridge from real audio to real `mag[257]` input.

## Current Managed Pieces

Already implemented:

- [ManagedTseProbe.kt](./app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/ManagedTseProbe.kt)
  - LiteRT initialization
  - model mapping
  - `InterpreterApi` creation
  - single-frame invocation

- [ManagedTseStreamingState.kt](./app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/ManagedTseStreamingState.kt)
  - rolling `32 x 257` CNN window
  - LSTM state lifecycle

- [ManagedTseFrameRunner.kt](./app/src/main/java/tw/com/johnnyhng/eztalk/asr/tse/ManagedTseFrameRunner.kt)
  - accepts one magnitude frame
  - updates window/state
  - returns current-frame mask

## Missing Piece

Still missing:

- app-side or reusable STFT path that converts real waveform into:
  - current magnitude frame `[257]`
  - current phase frame `[257]` if reconstruction is needed later

## Integration Strategy

## Phase 1: Magnitude-only feeder

The immediate next target should be:

- build a minimal reusable component that accepts `160`-sample hops
- maintains a `400`-sample analysis window
- produces current `mag[257]`

This component does not need to reconstruct waveform yet.

Purpose:

- validate real feature flow into the managed TSE model

## Phase 2: Managed mask validation

Once a real magnitude frame is available:

1. append frame to `ManagedTseStreamingState`
2. invoke `ManagedTseFrameRunner.processMagnitudeFrame(...)`
3. inspect returned `mask[257]`

Validation targets:

- no crashes
- no shape mismatch
- reasonable mask statistics
- recurrent state updates across frames

## Phase 3: Optional reconstruction

Only after magnitude-to-mask flow is stable should we consider:

- applying the mask to current magnitude
- reconstructing waveform using current phase
- ISTFT / overlap-add

This is not yet required to validate the managed runtime path itself.

## Recommended Implementation Shape

## Option A: Reuse existing DSP ideas in Kotlin

Create a small managed-side STFT helper that mirrors:

- `hop = 160`
- `window = 400`
- `nfft = 512`
- `freqBins = 257`

Output:

- `FloatArray(257)` magnitude
- optional `FloatArray(257)` phase

This is the fastest path for validation.

## Option B: Reuse existing native DSP only as a utility

This should only be considered if a minimal Kotlin STFT helper becomes too awkward.

Given the current strategy shift, avoid introducing new native inference complexity just to get STFT.

## Recommendation

Choose Option A first.

## Immediate Tasks

1. Add a small app-side STFT helper for `160`-sample hop input.
2. Add a test-only or debug-only path that feeds real magnitude frames into `ManagedTseFrameRunner`.
3. Log:
   - mask min/avg/max
   - number of processed frames
   - whether recurrent state remains stable

## What Not To Do Yet

Do not yet:

- wire this into `Home` live path
- remove the existing JNI TSE path
- attempt full raw->TSE waveform reconstruction in the same step
- optimize for Snapdragon/QNN

## Success Criteria

This phase is successful if:

- real audio can be converted into real `mag[257]` frames
- those frames can be fed into managed TSE repeatedly
- masks are produced stably across time

## One-Sentence Summary

The next practical step is to build a small app-side STFT feeder so the managed TSE path can consume real `mag[257]` frames, rather than only dummy inputs.
