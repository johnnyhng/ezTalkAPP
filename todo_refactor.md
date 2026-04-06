# Speaker ASR Refactor TODO

## Goal

Refactor `Speaker` local ASR flow so it no longer depends on the `Home` transcript/file pipeline.

The `Speaker` use case is transient command recognition, not persistent transcript capture.

## Decision

Do this first:

1. Build a `Speaker`-specific local-only ASR flow.

Do later:

1. Full refactor and cleanup after the new flow is working.

## Scope For Step 1

Create a dedicated ASR path for `Speaker` with these constraints:

- local ASR only
- no remote candidate lookup
- no `wav` persistence
- no `jsonl` persistence
- no feedback pipeline
- no transcript list integration

## Why

Current `Speaker` ASR is reusing `HomeViewModel` and `RecognitionManager`, which are optimized for:

- saved transcripts
- `wav`/`jsonl` output
- candidate workflows
- remote feedback / remote recognition extensions

That is the wrong abstraction for `Speaker`.

## Target Architecture

### Keep

- `LocalASRWidget`
- `SpeechFileExplorer`
- `SpeakerContentScreen`
- `SpeakerScreen`

### Add

- `SpeakerAsrController.kt`
  - start/stop recording
  - expose partial text
  - expose final text
  - expose recording state
  - expose countdown progress

- optionally `SpeakerAsrState.kt`
  - pure UI-facing state holder

### Stop Depending On

- `HomeViewModel` ASR state for `Speaker`
- `RecognitionManager` transcript persistence behavior
- remote candidate utilities in `RecognitionUtils.kt`

## Expected Behavior

When a `Speaker` ASR widget records:

- microphone starts local recognition
- partial text stays inside the widget
- final text stays inside the widget
- no transcript file is created
- no remote request is made
- no feedback or candidate writeback happens

## Integration Notes

- only one `Speaker` ASR widget can record at a time
- if the active widget disappears, stop recording
- keep current UI locking rule:
  - one widget recording disables the other widget

## Later Refactor

After step 1 is stable:

1. Remove `Speaker` ASR dependency on `HomeViewModel`
2. Move `Speaker` ASR state out of `SpeakerScreen`
3. Decide whether `RecognitionManager` should be split into:
   - persistent transcript recognition
   - transient command recognition
4. Revisit whether any logic in `RecognitionUtils.kt` is still useful after separation
