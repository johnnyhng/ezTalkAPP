# Managed TSE Probe Log Guide

## Purpose

This note explains how to read the current managed-runtime probe logs in `DataCollectViewModel`.

The managed TSE validation currently has three layers:

1. runtime initialization
2. dummy inference
3. real-shape `hop -> STFT -> mask` validation

## Log 1: Interpreter creation

Expected logs:

- `ManagedTseProbe initialized: model=voice_filter_lite_int8.tflite`
- `ManagedTseProbe startup result: initialized=true ...`

Meaning:

- LiteRT in Google Play services initialized
- model asset was mapped successfully
- `InterpreterApi.create(...)` succeeded

If this fails:

- the managed runtime path is not usable yet

## Log 2: Dummy inference

Expected log:

- `ManagedTseProbe startup result: initialized=true dummyInferenceOk=true`

Meaning:

- `runForMultipleInputsOutputs(...)` succeeded
- the model accepted zeroed-but-correctly-shaped tensors
- tensor ordering and output binding are at least minimally correct

If `initialized=true` but `dummyInferenceOk=false`:

- interpreter exists, but model invocation or tensor binding is wrong

## Log 3: Real-shape STFT-to-mask chain

Expected log:

- `ManagedTseMaskPipeline startup result: initialized=true processedHops=4 lastMaskStats=min=... avg=... max=...`

Meaning:

- app-side STFT helper produced real `mag[257]` frames
- those frames were fed through the managed TSE path
- mask inference succeeded repeatedly across multiple hops

If `initialized=true` but `processedHops=0`:

- initialization worked
- real-shape frame inference failed

If `processedHops > 0` but mask stats look invalid:

Examples:

- `min/max/avg` all zero
- `NaN`
- extreme nonsense values

Then:

- model invocation is technically happening
- but feature feeding or output interpretation is likely wrong

## Current Validation Ladder

The current managed-runtime ladder is:

1. `.tflite` loads
2. interpreter is created
3. dummy invoke works
4. real-shape STFT-to-mask path works

Only after step 4 is stable should the next phase start:

- feeding real microphone-derived frames more intentionally
- comparing mask behavior across sessions
- eventually deciding whether waveform reconstruction is worth wiring

## One-Sentence Summary

The key milestone log is `ManagedTseMaskPipeline startup result: initialized=true processedHops=4 ...`; that is the first proof that the managed path is consuming real `STFT` frames rather than only dummy tensors.
