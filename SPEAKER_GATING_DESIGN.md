# Speaker Gating Design

## Goal

Define an alternative path to realtime target-speaker extraction:

`raw -> VAD -> speaker gating -> ASR`

This design is intended as a lower-risk and easier-to-maintain alternative to a native realtime TSE pipeline.

## Why This Path Exists

The current TSE experiments showed:

1. Kotlin + ORT Java is useful for proving functionality, but latency is high.
2. Realtime audio quality is difficult to stabilize.
3. A production-grade TSE implementation likely requires a native/JNI rebuild.

Speaker gating is not the same as target-speaker extraction, but it can still solve an important subset of the product problem:

- prevent non-target speakers from being transcribed
- reduce accidental activation from nearby voices
- preserve a relatively simple and robust architecture

## What Speaker Gating Is

Speaker gating means:

1. Use VAD to find speech segments.
2. For each segment, extract a speaker embedding or speaker label.
3. Compare it against the enrolled user profile.
4. Only pass segments that match the target speaker to ASR.

This is fundamentally different from TSE:

- TSE tries to modify waveform audio to isolate the target speaker.
- Speaker gating keeps or drops segments based on speaker identity.

## What It Can Solve

Speaker gating is good for:

1. Single active speaker scenarios
2. Nearby interference where speakers do not heavily overlap
3. Preventing transcription of the wrong person
4. On-device user-specific filtering

## What It Cannot Solve

Speaker gating does not solve:

1. Strong overlapped speech separation
2. Recovering a target speaker waveform from a mixture
3. Cases where two speakers talk at exactly the same time and ASR needs only one voice

In those cases, only a real TSE/separation pipeline can fully address the problem.

## Suggested sherpa-onnx Building Blocks

Possible use of sherpa-onnx:

1. `SpeakerIdentification`
   - for enrollment and speaker embedding matching
2. `SpeakerDiarization`
   - for multi-speaker segmentation and post-hoc speaker attribution

Recommended first step:

- start with `SpeakerIdentification`-style speaker verification/matching for each VAD segment

Use diarization only if segment-level identification is not enough.

## Proposed App Pipeline

### Baseline runtime

`raw microphone -> VAD -> speech segment`

For every completed VAD segment:

1. Save the raw segment in memory.
2. Extract speaker embedding from that segment.
3. Compare the segment embedding with the enrolled user embedding/profile.
4. If similarity passes threshold:
   - send segment to ASR
   - save normal wav/jsonl
5. If similarity fails:
   - discard segment
   - or save it only for debugging if needed

## Enrollment Requirement

This path requires a reliable target speaker profile.

Possible sources:

1. Reuse current `dvector.bin` if compatible with the chosen speaker model
2. Generate a new speaker embedding using sherpa-onnx enrollment flow
3. Store one or more embeddings per user

Recommendation:

- use sherpa’s own speaker embedding format and enrollment path
- do not try to force compatibility with the current VoiceFilter `dvector.bin` unless verified

## RecognitionManager Integration

### Current structure

The app already has:

1. microphone capture
2. VAD
3. utterance finalization
4. ASR
5. wav/jsonl persistence

### Integration point

Speaker gating should be inserted after VAD has produced a segment, but before final ASR.

Target flow:

1. `VAD` detects speech start/end
2. Build `rawAudioToSave`
3. Run `speakerMatch(rawAudioToSave)`
4. If match:
   - run ASR
   - save transcript
5. Else:
   - skip ASR result
   - optionally log speaker mismatch

This means we do not need to modify the realtime VAD path itself.

## Persistence Behavior

Recommended initial behavior:

### Matched segment

- save `*.app.wav`
- save `*.app.jsonl`
- emit transcript to UI

### Rejected segment

Choose one of these policies:

1. Strict mode
   - do not save
   - do not show transcript
2. Debug mode
   - save `*.rejected.app.wav`
   - do not show transcript
3. Review mode
   - save raw audio with metadata `speakerMatched=false`

Recommended first implementation:

- strict mode for runtime
- optional debug logging only

## Similarity Threshold

Speaker gating quality depends on threshold tuning.

The threshold should be:

1. configurable
2. measured on real recordings
3. different for quiet vs noisy environments if necessary

Initial deployment should expose:

- similarity score
- threshold
- match result

in logs for tuning.

## UX Implications

If gating is enabled:

1. some speech segments may silently disappear
2. users may perceive missed recognition if the threshold is too strict
3. threshold tuning and enrollment quality become important UX factors

For this reason, debug visibility is essential early on.

## Logging Requirements

Every final VAD segment should log:

1. segment duration
2. similarity score
3. threshold
4. match / reject
5. whether ASR was executed

Example:

```text
Speaker gating: durationMs=1840 similarity=0.71 threshold=0.62 matched=true asr=true
```

## Advantages Over Realtime TSE

1. Easier to build and maintain
2. Uses existing VAD/ASR flow with a cleaner insertion point
3. Lower realtime DSP complexity
4. Lower latency risk
5. Better alignment with existing sherpa-onnx speaker APIs

## Disadvantages Compared With TSE

1. Does not separate overlapping voices
2. Rejects or accepts whole segments only
3. Cannot improve waveform quality
4. Cannot rescue a target speaker hidden inside a mixture

## Recommended Rollout

### Phase 1

Implement offline speaker matching for finalized VAD segments.

### Phase 2

Add logging and threshold tuning.

### Phase 3

Add enrollment management UI for user speaker profiles.

### Phase 4

Optionally evaluate diarization for multi-speaker recordings.

### Phase 5

Keep native realtime TSE as an advanced future path only if speaker gating proves insufficient.

## Decision Guidance

Choose speaker gating if:

1. stability matters more than perfect separation
2. overlapping speech is not the dominant case
3. faster implementation is preferred

Choose native realtime TSE if:

1. overlapping speech is a primary use case
2. target-speaker waveform isolation is required
3. the team can afford a native DSP + ORT implementation effort

## Recommended Immediate Next Step

Prototype:

`VAD -> speaker match -> ASR`

inside `RecognitionManager` using finalized segments only.

Do not replace the current realtime path immediately.

Validate first:

1. enrollment quality
2. similarity threshold behavior
3. false reject rate
4. false accept rate
