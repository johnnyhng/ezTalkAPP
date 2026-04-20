# Home LLM Correction Flow

This document explains the current `Home` screen flow after enabling background LLM correction.

## Scope

This flow only applies to `Home`:

- local microphone capture
- local real-time ASR
- utterance finalization after countdown
- optional Gemini-based background correction
- row-level progress display

It does not describe `Speaker` or `Translate`.

## Goal

When `Home LLM correction` is enabled in Settings, the UX target is:

1. local ASR remains the primary real-time path
2. when one utterance finishes, the final row appears immediately
3. the next utterance can start local partial ASR immediately
4. LLM correction runs in the background
5. only the affected row shows a linear progress bar
6. background correction must not overwrite newer user edits

## Main Components

- `RecognitionManager`
  file: [RecognitionManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/RecognitionManager.kt)
- `HomeScreen`
  file: [Home.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/Home.kt)
- `CandidateList` row UI
  file: [CandidateList.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/widgets/CandidateList.kt)
- shared correction module
  file: [TranscriptCorrectionModule.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/llm/TranscriptCorrectionModule.kt)

## End-to-End Flow

### 1. Recording session stays open

`Home` starts recording through `RecognitionManager.startTranslate(...)`.

`RecognitionManager` keeps one continuous recording session alive until the user presses stop. It does not stop the entire recording session after each utterance.

This is important because it means:

- one utterance can finalize
- state can reset for the next utterance
- microphone capture and local ASR continue without restarting the session

### 2. Partial local ASR updates the active placeholder row

While VAD says speech is active, `RecognitionManager` periodically emits local partial text through `onPartialResult`.

`HomeScreen` receives this in `partialText.collect`.

UI behavior:

- if the last row is already a finalized transcript, `Home` appends a placeholder row with `wavFilePath = ""`
- if the last row is already the active placeholder, `Home` updates that same row

This gives the real-time local ASR row shown during speaking.

### 3. Countdown finalizes one utterance

After VAD stops detecting speech, `RecognitionManager` starts a linger/countdown window.

When the countdown expires:

- the utterance audio is finalized
- a final local ASR result is generated
- utterance variants are built from the collected hypotheses
- audio is saved to wav
- JSONL is written once with the initial local result
- `onFinalResult(Transcript(...))` is emitted

At this point:

- `recognizedText` is the raw local final ASR result
- `modifiedText` initially equals the local final result
- `utteranceVariants` contains the utterance-level hypothesis set
- `localCandidates` keeps local-candidate semantics

### 4. Home inserts the final row immediately

`HomeScreen` receives the final transcript in `finalTranscript.collect`.

The current safe behavior is:

- immediately replace the current placeholder row if the last row is still the active partial row
- otherwise append the final row
- immediately queue remote-candidate fetch if configured
- immediately launch background LLM correction if enabled

The critical point is that `Home` no longer waits for LLM correction before inserting the final row.

This preserves responsiveness and allows the next utterance to begin immediately.

### 5. Next utterance starts local ASR immediately

After finalization, `RecognitionManager` resets utterance-local state:

- utterance segments
- variant buffer
- offsets
- countdown progress
- speech flags

But the overall recording loop is still running.

So the next utterance can start:

- microphone capture continues
- VAD restarts detection
- partial local ASR resumes
- a new placeholder row appears when new partial text arrives

This is the core UX improvement: finalization of utterance N does not block local ASR for utterance N+1.

## Background LLM Correction

### Trigger

Background correction only starts when all of the following are true:

- `enableHomeLlmCorrection == true`
- the finalized transcript has a non-empty `wavFilePath`
- a Gemini provider is available

### Input

The correction request uses:

- `utteranceVariants`
- fallback to `recognizedText` if variants are empty
- up to the previous 5 transcript lines as context, excluding the current row

### Output policy

The correction module only auto-applies when:

- the LLM returns valid JSON
- `corrected_text` is non-empty
- `confidence >= 0.85`

When accepted, only `modifiedText` is replaced.

These fields are preserved:

- `recognizedText`
- `utteranceVariants`
- `localCandidates`
- `remoteCandidates`

## Row-Level Progress UI

`Home` keeps a per-row correction state:

- `correctionJobs: MutableMap<String, Job>`
- `correctionRunning: MutableStateMap<String, Boolean>`

The key is `wavFilePath`.

`CandidateList` receives `isLlmCorrectionRunning(transcript)` and each row decides whether to show:

- no progress UI
- or a `LinearProgressIndicator` under that row

This means multiple finalized rows can independently show background correction state without interfering with the active local ASR row.

## Race Conditions Considered

### 1. Final row vs next partial row

Previous unsafe approach:

- wait for LLM correction inside `finalTranscript.collect`
- meanwhile the next utterance might already create a new partial placeholder row
- when correction returns, the old final row could overwrite the new placeholder row

Current fix:

- insert the final row immediately
- run correction in background after the row already exists

This removes the row-misalignment race.

### 2. LLM result overwriting user edits

Possible risk:

- user edits or confirms a row after final local ASR
- background LLM returns later
- LLM result overwrites the newer manual change

Current fix:

Before applying correction, `Home` checks:

- the row with the same `wavFilePath` still exists
- the row is still `mutable`
- the current `modifiedText` still equals the original text captured when correction started

If any of these checks fail, the correction result is discarded.

### 3. Multiple correction jobs on the same row

Possible risk:

- same row starts multiple background jobs
- later completion order becomes nondeterministic

Current fix:

- `clearCorrectionState(wavPath)` cancels and removes any previous job for that row
- then a new job is registered

Only one active correction job is allowed per row.

### 4. Clear/delete while correction is running

Possible risk:

- user clears the list or deletes one row while background correction is still running

Current fix:

- clear/delete paths call `clearCorrectionState(wavPath)`
- active job is cancelled
- progress state is removed

This prevents stale completion from trying to update a removed row.

## Interaction with Remote Candidate Fetch

Remote candidate fetch is independent from background LLM correction.

Both can happen for the same row:

- remote fetch updates `remoteCandidates`
- LLM correction may update `modifiedText`

They target different fields, so they can coexist as long as updates remain keyed by `wavFilePath`.

## Current Design Summary

The current `Home` pipeline is:

1. continuous local recording session
2. real-time local partial ASR
3. utterance countdown finalization
4. immediate finalized row insertion
5. immediate readiness for next utterance
6. optional background LLM correction on the finalized row
7. per-row linear progress indicator
8. guarded correction apply to avoid overwriting newer state

This design keeps `Home` responsive while still allowing LLM correction to improve the displayed transcript.
