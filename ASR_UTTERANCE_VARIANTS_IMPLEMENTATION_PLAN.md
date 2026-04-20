# ASR Utterance Variants Implementation Plan

Last updated: 2026-04-20

## Purpose

This document turns the cross-screen utterance-variants direction into an implementation-oriented plan.

Scope:

- `Speaker`
- `Home`
- `TranslateScreen`

Non-scope:

- concrete code changes in this document
- prompt redesign
- speaker-specific command logic redesign

## Executive Summary

The app already has one working utterance-variants implementation in `Speaker`.

That implementation proves the value of:

- preserving multiple ASR hypotheses
- deduplicating them within one utterance boundary
- delaying interpretation until the utterance is complete

However, `Home` and `TranslateScreen` still consume local ASR differently:

- `Home` currently uses single partial updates plus one final transcript per VAD/countdown segment
- `TranslateScreen` currently uses streaming text updates plus one final transcript at manual-stop/flush

So the correct next step is not to copy `SpeakerAsrController`.

The correct next step is to extract a shared utterance-hypothesis layer and let each screen keep:

- its own utterance boundary policy
- its own downstream consumer

## Current-State Audit

## 1. Speaker

### Boundary policy

`Speaker` uses:

- VAD to detect speech activity
- `partialIntervalMs` for repeated interim local recognition
- `lingerMs` as the countdown window that closes an utterance after silence

This means one utterance ends when:

- silence exceeds `lingerMs`
- recording is stopped
- or the stream is flushed

### Intermediate ASR behavior

During the utterance:

- multiple partial recognitions are emitted
- every partial/final text is pushed into `SpeakerAsrUtteranceBuffer`
- normalized unique variants are preserved

### Final output shape

When the utterance closes, `Speaker` produces a structured utterance bundle:

- `primaryText`
- `variants`
- `finalTextVersion`

### Downstream consumer

`Speaker` uses the final bundle for:

- rule-based command resolution
- line targeting / retrieval
- LLM fallback disambiguation

### Architectural role

`Speaker` is action-oriented.
Its downstream logic is about deciding:

- play
- pause
- stop
- play line N
- no action

So this screen is the strongest proof that utterance variants improve semantic robustness under noisy local ASR.

## 2. Home

### Boundary policy

`Home` currently shares `RecognitionManager` with `DataCollect`.

Its utterance boundary is:

- VAD-based speech detection
- countdown closure based on `lingerMs`
- final flush when silence is long enough or recording stops

So `Home` already has an utterance boundary model similar to `Speaker`.

### Intermediate ASR behavior

`RecognitionManager` emits:

- `onPartialResult(text)`
- `onFinalResult(transcript)`

The partial updates are shown directly in the UI.
The final result becomes a `Transcript`.

### Final output shape

`Home` currently finalizes one transcript per utterance segment:

- recognized text
- saved wav path
- mutable/checked flags
- later local/remote candidates

### Downstream consumer

`Home` uses final transcripts for:

- local transcript display
- JSONL persistence
- backend remote recognition queue
- later user confirmation / feedback

### Architectural role

`Home` is transcript-oriented.
It does not need speaker-command semantics.

What it can benefit from is:

- preserving multiple ASR hypotheses before freezing the local final text
- carrying richer evidence into later reranking or backend handoff

## 3. TranslateScreen

### Boundary policy

`TranslateScreen` is not countdown-finalized in the same way as `Speaker` and `Home`.

It is primarily stop-driven:

- recording continues until user stop
- VAD is still used during capture
- finalization happens on flush / stop rather than silence-only countdown closure

This makes its utterance-boundary policy different.

### Intermediate ASR behavior

During capture:

- repeated real-time recognition runs every ~500 ms
- the UI text field is updated continuously
- VAD state is used to track whether speech has started

### Final output shape

When capture ends:

- one final recognition pass is performed
- one transcript is created
- local and remote candidates may be loaded later

### Downstream consumer

`TranslateScreen` uses final transcripts for:

- editable source text
- TTS / playback feedback
- local rerecognition
- remote candidate loading
- translation-related confirmation workflows

### Architectural role

`TranslateScreen` is source-text-oriented.
It does not need control semantics.

What it can benefit from is:

- preserving multiple ASR hypotheses until stop
- improving final source text robustness
- supporting later reranking, cleanup, or translation preparation

## Shared Pattern Across Screens

Although the three screens behave differently, they all share the same underlying pattern:

1. local ASR produces multiple imperfect hypotheses over time
2. the UI currently tends to collapse those hypotheses too early
3. downstream logic would benefit from a more structured utterance-level bundle

This means there is a real reusable layer available.

That layer is:

`ASR utterance hypothesis aggregation`

## What Should Be Shared

The shared cross-screen layer should own:

- text normalization for dedupe
- stable ordering of unique variants
- latest visible text tracking
- utterance bundle construction
- reset after finalization

The shared output should be something like:

- `primaryText`
- `variants`
- `finalVersion`
- optional boundary metadata

This output must stay screen-agnostic.

## What Must Stay Separate

These must remain outside the shared layer:

- playback command resolution
- speaker line targeting
- backend submission behavior
- translation-specific candidate loading
- UI countdown rendering
- stop-button semantics

The shared layer should not know what the utterance means.
It should only know how to preserve ASR hypotheses until the utterance boundary is reached.

## Required Layer Separation

The implementation should separate three concerns.

### Layer 1: Hypothesis Aggregation

Responsibility:

- accept partial/final ASR text
- normalize and dedupe
- preserve variants
- finalize a reusable utterance bundle

### Layer 2: Boundary Policy

Responsibility:

- define when one utterance is complete

This differs by screen:

- `Speaker`: VAD + silence countdown
- `Home`: VAD + silence countdown
- `TranslateScreen`: manual-stop / flush-driven

### Layer 3: Downstream Interpretation

Responsibility:

- decide what to do with the finalized bundle

This remains screen-specific:

- `Speaker`: semantic control understanding
- `Home`: transcript persistence and backend handoff
- `TranslateScreen`: source-text preparation and candidate workflows

## Recommended Shared API Direction

At a conceptual level, the reusable layer should expose something close to:

- add hypothesis text
- reset utterance
- inspect current variants
- build/finalize utterance bundle

And optionally:

- configurable normalization strategy
- configurable max variant count
- optional boundary/finalization reason

The important part is that none of this API should mention:

- speaker
- line index
- play/pause/stop
- translation
- backend

## Migration Strategy

Implementation should happen in small slices.

### Slice 1: Extract shared model only

Goal:

- create a neutral shared utterance-variant buffer and bundle model
- do not change `Home` or `TranslateScreen` behavior yet

Adoption:

- migrate `Speaker` first onto the shared model

Reason:

- `Speaker` already has the needed behavior
- lowest risk to validate the abstraction

### Slice 2: Integrate into Home

Goal:

- let `Home` preserve utterance variants during one countdown window
- keep the existing transcript persistence and backend flow intact

Likely first use:

- attach variants to final local transcript state
- optionally expose them to future reranking / remote workflows

### Slice 3: Integrate into TranslateScreen

Goal:

- preserve hypotheses during manual-stop capture
- finalize one variant bundle on stop/flush

Likely first use:

- improve final source-text robustness before later candidate loading

## Risks

### 1. Designing around Speaker too heavily

If the shared model inherits command semantics, it will become harder to reuse in transcript-oriented screens.

### 2. Coupling boundary logic to buffer logic

If silence countdown and manual-stop are mixed into the same abstraction, the shared layer will become brittle.

### 3. Assuming one normalization policy fits all screens

Speaker-style dedupe is a strong starting point, but future needs may differ:

- punctuation sensitivity
- entity preservation
- looser or stricter normalization

So normalization should remain configurable later even if version 1 keeps one shared default.

### 4. Expanding UI expectations too early

Variants can first exist as runtime infrastructure without immediately changing every screen's UI.
Trying to expose all variants visually from day one would increase scope unnecessarily.

## Minimal Viable Target

The smallest safe milestone is:

1. extract a reusable utterance-variant aggregation layer
2. migrate `Speaker` to that shared layer
3. preserve existing `Speaker` behavior exactly
4. then adopt the same shared layer in `Home`
5. adopt `TranslateScreen` only after confirming the boundary abstraction is correct

## Recommended Success Criteria

The shared design is successful if:

- `Speaker` keeps its current behavior without semantic regressions
- `Home` can produce a final transcript with preserved utterance variants available to downstream logic
- `TranslateScreen` can preserve multiple local ASR hypotheses until stop
- boundary policy stays separate from variant storage
- no `Speaker`-specific semantics leak into the shared layer

## Final Recommendation

Proceed with the extraction.

But do it as:

- shared `ASR hypothesis aggregation`
- separate per-screen utterance boundary policy
- separate per-screen downstream interpretation

That is the cleanest path to extend utterance variants from `Speaker` into `Home` and `TranslateScreen` without creating mode-specific coupling in the shared runtime.
