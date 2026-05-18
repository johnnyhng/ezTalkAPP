# TSE VAD ASR Continuity Plan

## Problem Statement

In `DataCollectScreen` and `Home`, manual recording start currently gives the desired first-utterance path:

```text
raw mic audio -> realtime TSE -> VAD -> ASR
```

After the first utterance finishes and the countdown window advances to the next utterance, TSE still appears to run in the background, but VAD/ASR behavior looks like it has regressed to:

```text
raw mic audio -> VAD -> ASR
```

That means the second and later utterances may no longer be gated by target-speaker extraction. VAD can be triggered by non-target speech or background speech, defeating the point of using TSE as the speaker-focused front end.

## Expected Behavior

For every utterance in the same recording session, including utterances entered automatically after countdown:

```text
raw mic audio -> realtime TSE -> VAD -> ASR
```

The raw stream should still be preserved for sibling raw WAV output, but it should not be the VAD input when `useTseDetection=true` and native TSE initialized successfully.

## Current Relevant Flow

Main code path:

- `HomeViewModel.startTranslateRecording()`
- `HomeViewModel.startDataCollectRecording(dataCollectText)`
- `RecognitionManager.start(...)`
- `RecognitionManager.processAudio(...)`
- `TseAudioPreprocessor.processChunk(...)`
- `SimulateStreamingAsr.acceptVadWaveformSafely(...)`

Important current buffers in `RecognitionManager.processAudio()`:

- `buffer`: used as VAD input and partial/final ASR working buffer.
- `rawAlignedBuffer`: used for raw sibling WAV alignment.
- `utteranceSegments`: VAD segments popped from Sherpa VAD.

The current code already attempts to put `chunkOutput.processed` into `buffer` and `chunkOutput.rawAligned` into `rawAlignedBuffer`. The bug is likely around utterance reset, countdown transition, buffer naming/ownership ambiguity, or a fallback path silently feeding raw audio after the first finalization.

## Design Goals

1. VAD input must be explicit and auditable for every utterance.
2. Realtime TSE should be initialized once per recording session, not per utterance.
3. Countdown/finalization should reset only utterance/VAD/ASR state, not silently bypass TSE.
4. Raw audio should remain available for raw sibling WAVs.
5. Logs must make it obvious whether VAD is using processed TSE audio or raw fallback.

## Proposed Refactor

### 1. Split Buffers By Responsibility

Replace ambiguous buffer ownership with explicit names:

- `rawSessionBuffer`
  - Raw mic audio aligned to processed stream.
  - Used only for raw sibling WAV and diagnostics.

- `tseVadBuffer`
  - Realtime TSE output.
  - Sole input to VAD and partial ASR when TSE is active.

- `utteranceTseBuffer`
  - Current utterance processed audio window.
  - Used for final ASR and processed WAV if we decide to trust realtime TSE output.

- `utteranceRawBuffer`
  - Current utterance raw aligned audio.
  - Used for raw sibling WAV and optional offline TSE reprocessing.

This reduces the chance that a countdown reset clears or repopulates the wrong buffer.

### 2. Make VAD Input Source Explicit

Introduce a small local enum or sealed value inside `RecognitionManager`:

```kotlin
private enum class VadInputSource {
    TSE_PROCESSED,
    RAW_FALLBACK_TSE_DISABLED,
    RAW_FALLBACK_TSE_INIT_FAILED
}
```

At session startup:

- If `useTseDetection=false`: `RAW_FALLBACK_TSE_DISABLED`
- If `useTseDetection=true` and TSE init succeeds: `TSE_PROCESSED`
- If `useTseDetection=true` and TSE init fails: `RAW_FALLBACK_TSE_INIT_FAILED`

Then every VAD push should be traceable:

```text
RecognitionManager VAD input: utterance=2 source=TSE_PROCESSED rawRms=... processedRms=...
```

### 3. Keep TSE Alive Across Utterance Reset

After final utterance processing, reset only:

- VAD state
- utterance buffers
- offsets
- `utteranceSegments`
- `utteranceVariantBuffer`
- countdown progress
- `_isRecognizingSpeech`

Do not release, recreate, or bypass `TseAudioPreprocessor` between utterances in the same recording session.

`TseAudioPreprocessor.release()` should happen only when the full recording session ends.

### 4. Avoid Raw Fallback When TSE Is Healthy

When `VadInputSource.TSE_PROCESSED` is active:

- VAD uses only TSE processed frames.
- Partial ASR uses only TSE processed buffer.
- Final ASR uses either:
  - realtime TSE utterance buffer, or
  - offline TSE output generated from `utteranceRawBuffer`.

It should never use raw mic audio for VAD in this mode.

If TSE fails during `processFrame`, fail loudly:

- log the frame/process failure
- change `VadInputSource` to a raw fallback state only if that is an intentional policy
- include the fallback state in every final transcript log

### 5. Separate Live VAD Runtime From Saved WAV Runtime

Use separate runtime labels:

- `liveVadRuntime`
  - Example: `native_onnx_lite_cpu_realtime`
  - Describes what VAD and partial ASR used.

- `savedWavRuntime`
  - Example: `native_onnx_lite_cpu_offline`
  - Describes what final saved WAV used.

This avoids confusion where final offline TSE succeeds while live VAD may have used raw fallback.

## Implementation Steps

1. Add `VadInputSource` and `liveVadRuntime` in `RecognitionManager.processAudio()`.
2. Rename local buffers or introduce new lists with explicit ownership.
3. Route every `samplesChannel` chunk through one function:

```text
raw chunk -> TSE preprocessor if active -> VadFrameBundle(rawAligned, vadInput)
```

4. Feed only `vadInput` into:

```kotlin
SimulateStreamingAsr.acceptVadWaveformSafely(...)
```

5. On utterance finalization, slice both raw and processed utterance buffers using the same offsets.
6. On utterance reset, clear utterance state only; keep `TseAudioPreprocessor` alive.
7. Add logs for utterance index, VAD source, raw RMS, processed RMS, and final runtime split.
8. Run `./gradlew compileDebugKotlin`.
9. Validate on device with two or more utterances in one recording session.

## Verification Checklist

Manual test in `DataCollectScreen`:

- Start recording manually.
- Speak first phrase.
- Wait for countdown to finish.
- Speak second phrase without pressing start again.
- Confirm logs show both utterances using:

```text
vadInput=TSE_PROCESSED
liveVadRuntime=native_onnx_lite_cpu_realtime
```

Manual test in `Home`:

- Start normal recording.
- Speak multiple phrases separated by countdown pauses.
- Confirm no second-utterance fallback to raw.

Expected logs:

```text
RecognitionManager utterance start: index=1 vadInput=TSE_PROCESSED ...
RecognitionManager utterance reset complete: nextUtterance=2 ...
RecognitionManager utterance start: index=2 vadInput=TSE_PROCESSED ...
RecognitionManager TSE decision: liveVadRuntime=native_onnx_lite_cpu_realtime savedWavRuntime=...
```

Negative test:

- Disable TSE in settings.
- Confirm logs explicitly show:

```text
vadInput=RAW_FALLBACK_TSE_DISABLED
```

Failure test:

- Temporarily break TSE asset name.
- Confirm logs explicitly show:

```text
vadInput=RAW_FALLBACK_TSE_INIT_FAILED
```

## Success Criteria

The fix is complete when:

- First utterance and all countdown-following utterances use the same VAD input source.
- VAD input source is visible in logs.
- Raw fallback never happens silently.
- Raw sibling WAV output remains available.
- Final saved processed WAV behavior remains unchanged or is intentionally documented.

## 2026-05-18 Partial ASR Prefix Issue

After VAD was moved to `TSE_PROCESSED`, final saved WAVs and final ASR were correct, but `DataCollect` still received polluted `utteranceVariants` such as long background phrases before the expected target phrase.

Debug evidence:

- `timeline_probe` showed `rawAlignedSize == vadInputSize`, so raw and realtime TSE buffers were not drifting.
- `speech_detect_probe` showed a normal preroll window around `saveStart`.
- `final_range_probe` and `asr_compare` showed the final saved range recognized correctly from both raw and processed WAVs.
- `partial_asr_probe` showed `inputRange=[0, offset)`, meaning partial ASR was fed from the beginning of the recording session instead of the current utterance start.

Root cause:

Partial ASR used the full session prefix:

```text
vadInputBuffer[0, offset)
```

That included silence, background speech, and non-target audio before `speech_detected`. Those partial results were appended into `utteranceVariantBuffer`, so `DataCollect` JSONL variants contained bad candidates even though the final saved WAV was correct.

Fix:

Partial ASR now uses the same utterance start basis as final saving:

```text
vadInputBuffer[startOffset, offset)
```

The debug log now records `partial_asr_probe inputRange=[startOffset, offset)`, plus `startMinusDetect` and `samplesSinceDetect`, so future regressions can be identified from logcat without inspecting saved audio.

Related tuning:

- VAD threshold was raised from `0.5` to `0.65` for both Silero VAD and Ten-VAD.
- VAD config and range probes use `TAG=ezTalk-VAD`.
