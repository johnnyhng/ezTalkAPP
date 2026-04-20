# Transcript Candidate Schema Notes

Last updated: 2026-04-20

## Purpose

This note clarifies the intended semantics of transcript-related candidate fields after the introduction of `utteranceVariants`.

The goal is to avoid mixing different concepts into the same field.

## Current Transcript Fields

`Transcript` now contains three related but distinct collections:

- `utteranceVariants`
- `localCandidates`
- `remoteCandidates`

These fields must not be treated as interchangeable.

## 1. utteranceVariants

Meaning:

- the set of unique ASR hypotheses collected during one utterance boundary

Typical source:

- partial ASR updates
- final ASR update
- same utterance window before finalization

Typical use:

- preserving ASR uncertainty
- future reranking
- future LLM cleanup
- semantic-control disambiguation

Important property:

- this is not a recognition-candidate ranking output
- this is an utterance-level hypothesis set

## 2. localCandidates

Meaning:

- local recognition candidates produced after a finalized transcript exists

Typical source:

- the local final ASR result for the utterance
- later local rerecognition passes on saved audio

Typical use:

- candidate display
- comparison against remote candidates
- transcript refinement workflows

Important property:

- this is candidate-oriented
- it should represent local recognition outputs, not raw utterance aggregation

## 3. remoteCandidates

Meaning:

- candidates returned by backend remote recognition

Typical source:

- backend processing of the saved audio

Typical use:

- candidate comparison
- transcript refinement
- feedback-assisted correction

Important property:

- this is downstream of saved audio and remote processing

## Intended Separation

The three collections correspond to three different processing stages:

### Utterance stage

- `utteranceVariants`

### Local candidate stage

- `localCandidates`

### Remote candidate stage

- `remoteCandidates`

This stage separation should remain stable going forward.

## Why This Separation Matters

If `utteranceVariants` and `localCandidates` are merged conceptually, then:

- local rerecognition logic becomes ambiguous
- candidate UI becomes harder to reason about
- future ranking logic loses provenance
- JSONL persistence becomes semantically unclear

Keeping them separate preserves:

- where the text came from
- when it was produced
- what downstream logic should consume it

## JSONL Schema Mapping

The JSONL representation should now be interpreted as:

- `utterance_variants`: utterance-level hypothesis set
- `local_candidates`: local candidate set
- `remote_candidates`: remote candidate set

These keys should be preserved independently during writeback and sync.

## Minimal Operational Rule

When a new transcript is first created:

- `utteranceVariants` should contain the utterance hypothesis set if available
- `localCandidates` should contain at least the local final recognition text
- `remoteCandidates` should remain empty until backend results exist

This is the current target semantics across `Home` and `Translate`.

## Final Note

`utteranceVariants` is infrastructure.
`localCandidates` and `remoteCandidates` are candidate products.

That distinction is the key rule that should guide future changes.
