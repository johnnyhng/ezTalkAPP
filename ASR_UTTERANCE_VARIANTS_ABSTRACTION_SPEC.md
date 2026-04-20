# ASR Utterance Variants Abstraction Spec

Last updated: 2026-04-20

## Purpose

This document defines the shared abstraction for cross-screen ASR utterance variants.

It focuses on:

- shared data model
- shared responsibilities
- boundary-policy separation
- screen integration points

It does not include code changes.

## Design Goal

The goal is to define one reusable runtime concept that can serve:

- `Speaker`
- `Home`
- `TranslateScreen`

without leaking any screen-specific semantics into the shared layer.

The abstraction must be:

- screen-agnostic
- ASR-oriented
- boundary-policy-neutral
- interpretation-neutral

## Core Principle

The shared layer should answer only one question:

`What are the unique ASR hypotheses for this utterance before downstream interpretation begins?`

It should not answer:

- what action to execute
- what text to save
- whether to translate
- whether to upload to backend

Those questions belong to downstream consumers.

## Required Abstraction Layers

The final architecture should separate four conceptual layers.

### Layer 1: Hypothesis Collection

Responsibility:

- receive ASR hypotheses over time
- normalize them for deduplication
- preserve stable insertion order
- track the latest visible hypothesis

### Layer 2: Utterance Finalization

Responsibility:

- package the current hypotheses into one finalized utterance bundle
- increment version or sequence identity
- optionally record why the utterance ended

### Layer 3: Boundary Policy

Responsibility:

- decide when the current utterance should be finalized

This differs by screen and must not be hard-coded into the shared buffer.

### Layer 4: Downstream Interpretation

Responsibility:

- decide what the finalized utterance means in that screen

This is fully outside the shared abstraction.

## Shared Data Model

The shared model should revolve around three core structures.

## 1. Hypothesis

Represents one observed ASR text hypothesis.

Suggested conceptual fields:

- `rawText`
- `normalizedKey`
- `arrivalOrder`
- optional metadata such as source kind

Purpose:

- preserve the original visible text
- dedupe using normalized form
- keep ordering stable

This object is internal to the aggregation layer in most cases.

## 2. Utterance Bundle

Represents one finalized utterance.

Suggested conceptual fields:

- `primaryText`
- `variants`
- `version`
- `boundaryReason`
- optional timing metadata

### Primary text

`primaryText` should represent the best current user-facing text for the utterance.

For version 1, that should remain:

- the latest non-blank ASR text seen before finalization

This preserves current `Speaker` behavior and aligns with how the existing runtime treats the latest text as the most visible hypothesis.

### Variants

`variants` should be:

- ordered
- unique after normalization
- non-blank
- preserved in human-readable form

### Version

`version` exists to:

- make UI processing idempotent
- let screens ignore already-consumed utterances

### Boundary reason

Optional but recommended.

Examples:

- `silence_timeout`
- `manual_stop`
- `flush`
- `stream_closed`

This is useful for:

- analytics
- debugging
- downstream policy decisions

## 3. Aggregator State

Represents the in-progress runtime state before finalization.

Suggested conceptual state:

- latest visible text
- map of normalized key -> original variant
- current version counter
- optional timestamps

This state should remain private to the shared runtime unless screens explicitly need read-only inspection.

## Shared Runtime Behaviors

The shared aggregation layer should support the following behaviors.

## 1. Reset

Reset must:

- clear current hypotheses
- clear latest text
- preserve version counter policy as defined by implementation

Use cases:

- new recording session
- post-finalization cleanup
- explicit screen reset

## 2. Add Hypothesis

The runtime must support adding ASR hypotheses over time.

Input:

- one ASR text string

Behavior:

- trim
- reject blank
- update latest visible text
- normalize for dedupe
- preserve original text for display
- insert only if normalized key has not already been seen

This method should remain independent of whether the source text came from:

- partial ASR
- final ASR
- rerecognition

That distinction may become optional metadata later but should not change core behavior.

## 3. Inspect Current Variants

The runtime should support read-only access to current variants before finalization.

Purpose:

- UI preview
- debugging
- progress-state display

This is especially useful in `Speaker`, which already shows current ASR text and implicitly benefits from seeing active hypothesis accumulation.

## 4. Finalize Current Utterance

The runtime must support producing a finalized utterance bundle.

Behavior:

- build one immutable utterance bundle
- assign a new version
- choose `primaryText`
- preserve ordered variants
- optionally record boundary reason

If there is no usable text, finalization should return no bundle.

## 5. Finalize and Reset

Most screens will want a combined semantic operation:

- finalize current utterance
- clear in-progress state

This is the standard post-utterance flow and should be the common operational pattern.

## Normalization Policy

Normalization must exist because ASR often produces visually different strings that represent the same underlying hypothesis.

Version 1 default normalization should remain conservative:

- trim
- collapse whitespace
- remove punctuation that should not distinguish hypotheses
- lowercase when applicable

This matches the spirit of the current `Speaker` implementation.

## Important Constraint

Normalization should be strong enough for dedupe, but not so aggressive that it destroys useful distinctions too early.

For example:

- punctuation should usually be ignored
- accidental spacing should usually be ignored
- text should remain human-readable in preserved variants

## Future Flexibility

The abstraction should allow configurable normalization later if needed.

Potential future needs:

- punctuation-sensitive transcript modes
- named-entity-preserving modes
- locale-aware normalization

But version 1 can safely start with one shared default policy.

## Boundary Policy Interface

Boundary policy is the most important thing that must remain separate from the shared buffer.

The shared buffer should not decide when an utterance ends.

Instead, the surrounding runtime should decide when to call finalization.

Conceptually, boundary policy must answer:

`Should the current utterance be finalized now?`

## Expected boundary-policy sources

### Speaker

Boundary source:

- VAD + silence countdown

Typical boundary reasons:

- `silence_timeout`
- `manual_stop`

### Home

Boundary source:

- VAD + silence countdown

Typical boundary reasons:

- `silence_timeout`
- `manual_stop`
- `flush`

### TranslateScreen

Boundary source:

- manual stop / flush-driven closure

Typical boundary reasons:

- `manual_stop`
- `flush`
- `stream_closed`

## Why Boundary Policy Must Not Be Shared Blindly

If the shared abstraction bakes in `Speaker` countdown semantics, then:

- `TranslateScreen` becomes awkward
- `Home` gets speaker-specific assumptions
- testability worsens

The correct model is:

- shared hypothesis storage
- external boundary trigger

## Downstream Consumer Contracts

Each screen should consume the finalized utterance bundle differently.

## Speaker Consumer Contract

Input:

- finalized utterance bundle

Expected downstream use:

- command resolution
- content retrieval
- LLM fallback

Important requirement:

- preserve ordered variants exactly enough for semantic disambiguation

## Home Consumer Contract

Input:

- finalized utterance bundle

Expected downstream use:

- final local transcript selection
- future transcript reranking
- future backend handoff context

Important requirement:

- no command semantics required

## TranslateScreen Consumer Contract

Input:

- finalized utterance bundle

Expected downstream use:

- final source-text robustness
- candidate preparation
- future cleanup / translation pre-processing

Important requirement:

- stop-driven capture must still produce one coherent final bundle

## Shared Invariants

The abstraction should preserve the following invariants across all screens.

### Invariant 1

No blank text enters the finalized variants list.

### Invariant 2

Variant order is deterministic and stable.

### Invariant 3

Finalized bundles are immutable.

### Invariant 4

Each finalized bundle has a monotonic version or equivalent identity.

### Invariant 5

The shared runtime never interprets hypotheses semantically.

### Invariant 6

Boundary policy is external to the buffer.

## Version 1 Scope

Version 1 of the abstraction should stay intentionally narrow.

It only needs to support:

- add hypothesis
- inspect variants
- finalize utterance
- reset state
- attach optional boundary reason

It does not need to support yet:

- hypothesis scoring
- confidence tracking
- multiple parallel utterances
- advanced locale-specific policies
- persistence of raw hypothesis history

## Migration Compatibility Goals

The abstraction is good enough for migration if it can preserve:

- current `Speaker` variant behavior
- current `Speaker` latest-text preference
- current `Speaker` version-based idempotent consumption

while being generic enough that:

- `Home` can consume it without importing speaker concepts
- `TranslateScreen` can consume it without adopting countdown assumptions

## Anti-Patterns To Avoid

### 1. Speaker-specific naming

Avoid names like:

- `SpeakerUtteranceBuffer`
- `SpeakerCommandUtterance`

These names will push the abstraction back toward mode-specific logic.

### 2. Embedding screen semantics into the bundle

Avoid fields like:

- `lineIndex`
- `command`
- `playbackIntent`

These belong to downstream interpretation, not to the shared utterance model.

### 3. Merging countdown timers into the shared buffer

The buffer should not own timing state for silence windows.
That belongs to the recorder/controller layer.

### 4. Treating partial/final source type as the primary abstraction

The key abstraction is the hypothesis set, not the recognition event type.
Partial/final origin may be metadata, but should not dominate the design.

## Recommended Naming Direction

The shared abstraction should use neutral names.

Recommended conceptual naming:

- `AsrUtteranceVariantBuffer`
- `AsrUtteranceBundle`
- `AsrUtteranceBoundaryReason`
- `AsrUtteranceFinalizer`

These names express what is actually reusable.

## Final Spec Summary

The shared abstraction should be:

- one screen-agnostic utterance-variant buffer
- one immutable utterance bundle
- one externally controlled boundary-policy contract
- no embedded downstream semantics

That gives the project a stable ASR post-processing foundation that:

- preserves the current `Speaker` strengths
- can be adopted by `Home`
- can be adopted by `TranslateScreen`
- remains neutral about what the utterance means after finalization
