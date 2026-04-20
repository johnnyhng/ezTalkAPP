# ASR Utterance Variants Cross-Screen Plan

Last updated: 2026-04-20

## Purpose

This document outlines how the current `Speaker`-mode utterance-variant mechanism can be generalized into a reusable capability for:

- `Speaker`
- `Home`
- `TranslateScreen`

This is a technical planning document only.
It does not include code changes.

## Background

The current `Speaker` flow already uses an utterance-variant aggregation strategy:

- during one countdown window, multiple ASR outputs are collected
- duplicate outputs are normalized and removed
- the result becomes one structured utterance bundle

This design exists because local ASR often produces multiple imperfect versions of the same spoken utterance.
Instead of trusting only the last final result, the system keeps a set of unique variants and lets downstream logic decide using the whole bundle.

In `Speaker`, this is especially useful for:

- command correction
- line-number correction
- LLM fallback disambiguation

The same underlying idea is also useful in `Home` and `TranslateScreen`, but those screens do not need the same downstream semantics as `Speaker`.

## Core Insight

`SpeakerAsrUtteranceBuffer` should not be thought of as a `Speaker`-only concept.

The real reusable concept is:

`ASR utterance hypothesis aggregation`

That concept can be shared across screens, while each screen still keeps its own downstream behavior.

## Why This Should Be Extracted

If the current variant logic remains embedded only inside `Speaker`, several problems remain:

- `Home` and `TranslateScreen` cannot benefit from the same multi-hypothesis robustness
- normalization and deduplication logic may diverge across screens
- future LLM-assisted correction pipelines would need to be rebuilt separately
- countdown-window and stop-window utterance packaging would remain inconsistent

Extracting the mechanism into a shared layer would allow the app to reuse one stable ASR-hypothesis model while preserving screen-specific behavior.

## The Correct Abstraction

The reusable layer should not be "speaker command understanding".

It should be:

- utterance hypothesis collection
- normalization
- deduplication
- utterance finalization

That means the abstraction should remain neutral about:

- playback commands
- line targeting
- translation
- backend recognition

The reusable layer should only produce a clean bundle of ASR variants for a single utterance boundary.

## Proposed Shared Model

The shared layer should revolve around two concepts:

### 1. Variant Buffer

A reusable component that:

- accepts ASR partial and final text updates
- normalizes text for deduplication
- preserves the latest visible text
- stores unique variants in stable order
- produces a final bundle when the utterance ends

### 2. Utterance Bundle

A reusable bundle that represents one finalized utterance, for example:

- primary text
- ordered unique variants
- final version id
- optional metadata such as utterance end reason

This bundle should be screen-agnostic.

## What Must Not Be Shared

The following should remain screen-specific:

- speaker command parsing
- content-line retrieval
- LLM action-space prompt design
- translation logic
- backend recognition upload policy

In other words:

- share the hypothesis collection
- do not share the downstream interpretation

## Screen-Specific Usage

## 1. Speaker

`Speaker` should continue using utterance variants for:

- control-command resolution
- content targeting
- LLM fallback disambiguation

In this screen, variants are part of the semantic-control pipeline.

Important characteristic:

- the downstream step is action-oriented
- the system must decide `play`, `pause`, `stop`, `play_line`, or `no_action`

So `Speaker` needs the richest downstream interpretation, but it does not need a special variant model.

## 2. Home

`Home` is not a control screen.
Its main role is local ASR plus backend remote recognition.

Utterance variants would be useful here for:

- improving robustness before backend submission
- keeping multiple transcript hypotheses until countdown completes
- surfacing richer candidate context to later stages
- reducing dependence on one possibly noisy final local ASR string

Possible uses in `Home`:

- choose the best representative local text before saving or showing final result
- attach variant context to remote-recognition workflows
- use variants for future correction / reranking logic

Important characteristic:

- the downstream step is transcript-oriented, not command-oriented

That means `Home` would consume the same utterance bundle, but interpret it as transcript evidence rather than control intent.

## 3. TranslateScreen

`TranslateScreen` differs from `Speaker` because it is stop-driven rather than countdown-driven.

The local ASR continues until the user explicitly stops recording.

Utterance variants would still be valuable, but the packaging boundary is different.

Possible uses in `TranslateScreen`:

- preserve multiple hypotheses during the segment
- improve final source text before translation
- protect named entities and noisy words from premature collapse
- supply a future correction / LLM cleanup stage with richer input context

Important characteristic:

- the downstream step is translation preparation, not action selection

So `TranslateScreen` needs the same hypothesis aggregation concept, but likely with a different utterance-finalization strategy.

## The Most Important Design Requirement

The extraction must not copy `Speaker` assumptions into shared code.

That means the shared layer should not know anything about:

- `play_document`
- `play_line`
- line indexes
- playback state
- document content

If the abstraction is done correctly, then:

- `Speaker` consumes utterance bundles and applies control semantics
- `Home` consumes utterance bundles and applies transcript semantics
- `TranslateScreen` consumes utterance bundles and applies translation semantics

## Recommended Shared Responsibilities

The shared ASR utterance-variants layer should own:

- text normalization for dedupe
- stable insertion ordering of unique variants
- latest-text tracking
- utterance bundle creation
- reset behavior after flush

It may also optionally own:

- version numbering
- flush-reason tagging
- maximum variant count

## Recommended Non-Shared Responsibilities

The shared layer should not own:

- countdown timers
- stop-button policy
- playback control logic
- remote-recognition logic
- translation prompting
- LLM prompt design

Those belong to each screen or each mode-specific controller.

## Boundary Strategy

To make the design reusable, the system should separate:

### Layer A: Hypothesis Collection

Responsibility:

- collect ASR outputs
- normalize
- dedupe
- finalize

### Layer B: Utterance Boundary Policy

Responsibility:

- decide when one utterance is complete

This varies by screen:

- `Speaker`: countdown/VAD-based boundary
- `Home`: countdown/VAD-based boundary
- `TranslateScreen`: manual-stop or segment-final boundary

### Layer C: Downstream Interpretation

Responsibility:

- decide what the utterance means in that screen

This is fully screen-specific:

- `Speaker`: command / content control
- `Home`: transcript handling / backend handoff
- `TranslateScreen`: translation-source preparation

This three-layer separation is the cleanest way to avoid coupling.

## Why Boundary Policy Matters

Even if all three screens use utterance variants, they do not share the same definition of "one utterance is complete".

Examples:

- `Speaker` uses a short countdown window because low-latency control matters
- `Home` uses the same kind of countdown, but the result is a transcript rather than a command
- `TranslateScreen` may want to preserve hypotheses until the user explicitly stops or a longer segment completes

So the correct reuse point is not "copy `SpeakerAsrController` everywhere".

The correct reuse point is:

- common hypothesis buffer
- per-screen utterance boundary policy

## Expected Benefits

If done correctly, a shared utterance-variants layer would provide:

- more robust local ASR handling across screens
- consistent normalization behavior
- cleaner future integration of LLM-assisted correction
- reduced duplicated ASR post-processing logic
- easier experimentation with reranking and transcript repair

## Main Risks

### 1. Overfitting the abstraction to `Speaker`

If the shared model includes command semantics, it will become harder to reuse cleanly in `Home` and `TranslateScreen`.

### 2. Mixing utterance buffering with screen lifecycle logic

If countdown behavior, stop behavior, and variant storage are bundled together, the abstraction will be difficult to reuse.

### 3. Using one normalization policy for all future needs without review

The current dedupe normalization is reasonable for `Speaker`, but other screens may eventually need:

- looser normalization
- stricter punctuation retention
- entity-preserving behavior

So the shared layer may need configurable normalization policy later.

## Suggested Design Direction

The safest direction is:

1. extract a screen-agnostic utterance-variant buffer
2. keep utterance-boundary logic separate
3. let each screen decide how to consume the final utterance bundle

This avoids both duplication and over-coupling.

## Recommended Future Naming Direction

Names should avoid `Speaker` specificity.

Examples of better conceptual naming:

- `AsrUtteranceVariantBuffer`
- `AsrUtteranceBundle`
- `AsrUtteranceBoundaryPolicy`
- `AsrUtteranceFinalizer`

These names better reflect what is actually reusable.

## Final Recommendation

Yes, utterance variants should become a shared capability for `Speaker`, `Home`, and `TranslateScreen`.

But the reusable unit should be:

`ASR hypothesis aggregation`

not:

`Speaker command understanding`

The correct architecture is:

- shared utterance-variant collection
- separate per-screen utterance boundary policy
- separate per-screen downstream interpretation

That structure gives the app a reusable ASR post-processing foundation without leaking `Speaker`-specific semantics into the rest of the product.
