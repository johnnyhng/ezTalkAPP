# Managed Runtime TSE Plan

## Goal

Define the next-phase TSE plan for `ezTalk` under the constraint:

- do not continue investing in self-compiled JNI/native inference as the primary path

This document changes the working assumption from:

- custom native runtime first

to:

- managed runtime first

## Why The Direction Changes

The JNI/native path has already proven useful for learning, but its maintenance cost is too high relative to its current payoff.

Current costs include:

- native runtime integration work
- ABI and packaging maintenance
- page-size alignment issues
- execution-provider compatibility debugging
- device-specific acceleration debugging
- model/runtime mismatch handling
- repeated rebuild/test cycles outside the app workflow

These costs are currently too large for the value gained.

## Decision

### Primary direction

Use a managed model runtime as the primary future direction for TSE.

Practical meaning:

- prefer `TFLite / LiteRT` style deployment
- avoid building and maintaining custom JNI inference code as the main strategy
- keep app-side orchestration and evaluation simple

### Paused direction

Pause custom JNI/native inference development as the main line of work.

It can remain:

- a reference path
- a fallback experiment
- a source of DSP learnings

But it should no longer be treated as the main delivery route.

## What This Does Not Mean

This does not mean:

- all native code must disappear immediately
- all DSP must move to Java/Kotlin immediately
- previous JNI work was wasted

It means:

- do not keep paying high integration cost for a custom inference stack unless it produces a clear win

## New Primary Roadmap

## 1. Model Runtime

Primary target:

- `voice_filter_lite_int8.tflite`

Primary deployment direction:

- managed TFLite/LiteRT path

The main question becomes:

- can this model be executed in a stable, lower-maintenance way on Android without a custom inference `.so`

## 2. Device Priority

Priority remains:

1. `Google Tensor`
2. baseline device: `Tensor G2`
3. secondary device family: `Snapdragon`

But the runtime strategy changes:

- test Tensor-first with managed runtime assumptions
- only evaluate Qualcomm-specific acceleration later if needed

## 3. Product Scope

Short-term objective:

- stable validation path in `DataCollectScreen`

Not short-term objective:

- production-ready fully live low-latency TSE

This is important because it keeps scope aligned with current engineering capacity.

## What To Keep From The Old Work

These remain useful:

- `Tensor G2` baseline logs
- TSE roadmap documents
- model requirements documents
- raw/TSE A/B playback in `DataCollectScreen`
- lessons learned about over-suppression, latency, and provider fallback

These should not be discarded.

## What To Stop Doing For Now

Stop prioritizing:

- frequent custom `.so` rebuild loops
- ORT provider debugging as the main development activity
- bespoke acceleration experiments that depend on fragile native packaging
- custom native inference migration as the default response to each new model

## Recommended Execution Plan

## Phase 1: Stabilize evaluation workflow

Keep the evaluation flow centered on:

- `DataCollectScreen`
- saved raw/TSE pairs
- repeatable test logging

Use this to decide whether the model is worth further investment.

## Phase 2: Validate managed runtime feasibility

For the current TFLite model, verify:

- loadability
- input/output contract
- state handling expectations
- achievable latency
- output quality

This should happen before any new deep integration work.

## Phase 3: Reassess live-path ambition

Only after managed runtime feasibility is clear should we decide whether:

- live TSE is still worth chasing
- TSE should remain offline / near-offline
- a hybrid design makes more sense

## Role Of JNI Going Forward

JNI is now secondary.

Allowed roles:

- temporary bridge
- legacy path
- narrow utility layer if a managed path still needs a small native shim

Disallowed role:

- the default answer to TSE deployment

## Success Criteria

This new direction is successful if it reduces:

- integration churn
- packaging complexity
- accelerator-specific debugging time
- model deployment friction

while still allowing:

- meaningful TSE quality evaluation
- Tensor-first device validation
- eventual product decision-making

## Current Recommended Next Step

The immediate next step is:

- treat `TFLite/LiteRT` as the working model path
- keep `Tensor G2` as baseline
- continue testing with structured logs
- avoid new custom JNI inference expansion

## One-Sentence Summary

`ezTalk` should now treat managed TSE runtime integration as the main path and downgrade self-compiled JNI inference from primary architecture to paused/fallback status.
