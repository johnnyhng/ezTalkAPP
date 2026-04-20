# Speaker ASR + LLM Semantic Understanding Overview

Last updated: 2026-04-20

## Purpose

This document summarizes how `Speaker mode` currently performs semantic understanding by combining:

- Local ASR
- utterance variant aggregation
- rule-based command resolution
- content retrieval
- LLM fallback

This is a technical overview only. It does not propose code changes.

## High-Level Summary

`Speaker mode` does not use a simple "ASR -> LLM" pipeline.

Its actual behavior is closer to:

1. collect multiple ASR outputs during one countdown window
2. resolve obvious commands using deterministic rules
3. try content matching against the current document
4. use LLM only when earlier stages cannot decide reliably

The most accurate short description is:

`ASR variants aggregation + rule-based command resolution + content retrieval + LLM fallback`

## Why Variant Aggregation Exists

In `Speaker mode`, a single final ASR result is often not reliable enough for Chinese voice control.

Typical issues include:

- homophone substitutions
- Chinese number recognition errors
- dropped command suffixes
- different partial/final outputs for the same utterance

Because of this, the system does not trust only one ASR string.
Instead, it collects all unique ASR variants produced during one countdown window and treats them as one utterance bundle.

This gives the downstream logic:

- better tolerance to local ASR noise
- multiple textual clues for the same spoken intent
- a stronger basis for LLM correction

## Stage 1: Rule-Based Command Resolution

The first decision layer is deterministic command resolution.

Its job is to catch clear playback-control intents as early as possible, such as:

- play
- pause
- stop
- play line N

This layer typically handles:

- command keyword matching
- line-number extraction
- Chinese number parsing
- common ASR error normalization

The design goal is:

- low latency
- low cost
- high precision for explicit commands

If this layer succeeds, the system executes immediately and does not continue into retrieval or LLM reasoning.

This means the primary path in `Speaker mode` is still rule-first, not LLM-first.

## Stage 2: Content Retrieval

If the utterance is not confidently resolved as a direct command, the system tries to understand whether the user is referring to document content.

The document is split into small sliding windows of lines, and the utterance is compared against those windows.

This layer attempts to answer questions like:

- which line or nearby chunk does the user most likely mean
- whether the utterance should map to a specific playback location

Its outputs are usually one of:

- `AutoPlay`
- `Candidate`
- `NoMatch`

`AutoPlay` means confidence is high enough to directly play.
`Candidate` means there is a plausible match, but the system should highlight first instead of immediately playing.
`NoMatch` means retrieval was not strong enough to support an action.

## Important Reality: This Layer Is Currently More Lexical Than Semantic

Although the internal naming refers to semantic search, the current architecture does not yet operate as a strong embedding-based semantic retriever.

The system has an interface for embeddings, but at present there is no active embedding engine injected into the `Speaker` retrieval path.

That means the current retrieval behavior is dominated by lexical similarity rather than true semantic similarity.

Practical consequences:

- it works better when the spoken query is textually close to the target line
- it is weaker for paraphrases and meaning-preserving rewrites
- it is weaker for Chinese oral phrasing that differs significantly from the source line text

So the current retrieval stage should be understood as:

`content matching with lexical similarity`

not as a mature semantic retrieval system.

## Stage 3: LLM Fallback

Only when the earlier stages cannot make a reliable decision does the system invoke the LLM.

The LLM receives structured context, including:

- the ASR variants collected during one countdown window
- the allowed action space
- the available document lines and line indexes

The LLM is not asked to freely answer in natural language.
Instead, it is constrained to return structured JSON with a limited action space such as:

- `play_document`
- `play_line`
- `pause`
- `stop`
- `no_action`

The response also includes:

- `lineIndex`
- `confidence`
- `reason`

This design is important because it:

- narrows the output space
- reduces hallucination risk
- makes downstream execution safer
- allows threshold-based decision control

## The Real Role of the LLM

Within this system, the LLM is not the primary understanding engine.

Its actual role is to provide:

- ASR error correction
- intent repair using multiple transcript variants
- contextual disambiguation against document lines
- command vs no-action classification
- structured final fallback decisions

So the LLM's value is best described as:

`correction and recovery under uncertainty`

rather than complete end-to-end understanding.

## Why This Multi-Stage Design Makes Sense

`Speaker mode` is a real-time control surface.
That means the system needs:

- reasonably low latency
- predictable actions
- high resistance to accidental triggers

A pure LLM-first design would increase:

- latency
- dependency on network or model availability
- cost
- unpredictability in borderline cases

A pure rule-only design would fail too often under noisy Chinese ASR.

A pure lexical-retrieval design would still struggle with paraphrases and ASR corruption.

The current layered design is therefore a practical compromise:

- rules for fast high-precision commands
- retrieval for content localization
- LLM for uncertain cases

## Main Technical Limitations

### 1. Retrieval is not yet truly semantic

The current retrieval stage is still largely lexical.
This limits performance on:

- paraphrased Chinese commands
- oral reformulations
- semantically similar but textually different content requests

### 2. LLM only runs after no-match

Right now, the LLM fallback is primarily used when earlier stages return `NoMatch`.

That leaves a risk case:

- lexical retrieval produces a weak but threshold-passing wrong candidate
- the system may accept that candidate
- the LLM never gets a chance to correct it

So the architecture is currently stronger at recovering from misses than from false positives.

### 3. Local LLM quality is currently weaker for Chinese

This is especially relevant to `Speaker mode`, because this feature depends heavily on correction quality.

If the local model is weaker for Chinese command disambiguation, the fallback layer becomes less reliable.
That is why defaulting to cloud LLM is currently the safer operational choice.

## What This System Is Good At

The current design is well suited for:

- explicit playback control commands
- modestly noisy Chinese ASR
- line selection with recoverable ASR mistakes
- combining multiple ASR hypotheses before final action
- keeping LLM usage bounded and structured

## What This System Is Not

This is not a general Chinese conversational understanding system.

It is a task-specific control pipeline optimized for:

- speaker playback control
- content line targeting
- safe action selection

It should not be described as a fully semantic LLM-driven interaction system.

## Final Architectural Characterization

The current `Speaker mode` semantic-understanding pipeline is best characterized as:

`Local ASR hypothesis collection + deterministic command parsing + lexical content retrieval + cloud-first structured LLM fallback`

That phrasing is more accurate than simply saying:

`Speaker mode uses ASR plus LLM for semantic understanding`

because it makes clear that:

- the LLM is only one layer
- most actions are intended to be resolved before LLM
- retrieval is currently not fully semantic in the embedding sense

## Key Conclusion

The most important technical insight is that `Speaker mode` does not rely on one understanding mechanism.

It relies on a layered decision stack:

- ASR provides noisy text hypotheses
- variant aggregation preserves uncertainty instead of collapsing it too early
- rule-based resolution handles the easiest and safest cases
- retrieval attempts content localization
- LLM repairs ambiguity only when needed

This layered structure is the core reason the feature can remain usable under imperfect local ASR while still keeping playback control behavior bounded and executable.
