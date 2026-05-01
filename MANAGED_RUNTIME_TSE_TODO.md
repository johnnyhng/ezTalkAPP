# Managed Runtime TSE TODO

## Goal

Convert the managed-runtime TSE direction into concrete implementation tasks that can be executed inside the app repo without returning to a JNI-first workflow.

This file is intentionally execution-oriented.

## Scope

Current scope:

- `voice_filter_lite_int8.tflite`
- `Tensor G2` baseline
- `DataCollectScreen` validation first

Out of scope for now:

- full live-path production rollout
- custom native inference rebuilds
- Snapdragon/QNN optimization

## Workstream 1: Model Contract Validation

### Task 1.1

Document the app-side assumptions for the TFLite model:

- input tensor names
- output tensor names
- tensor shapes
- tensor data types
- state tensor lifecycle

Deliverable:

- one checked-in note or code comment block that reflects the actual `.tflite` contract used by the app

### Task 1.2

Confirm whether the current `.tflite` model is usable from managed runtime without any native inference bridge.

Questions to answer:

- can it be loaded directly from app assets
- can inputs be fed from app-managed buffers
- can outputs be read back in stable form

Deliverable:

- explicit pass/fail note in test log or implementation note

## Workstream 2: Runtime Feasibility Spike

### Task 2.1

Create the smallest possible managed-runtime spike for:

- load model
- allocate state buffers
- run one inference

This should not be wired into the full recording path immediately.

Suggested target:

- isolated app-side probe or test-only integration path

### Task 2.2

Capture the minimum runtime facts:

- load success
- invocation success
- tensor shape correctness
- repeated invoke stability

Deliverable:

- first managed-runtime feasibility result

## Workstream 3: DataCollect Validation Path

### Task 3.1

Keep `DataCollectScreen` as the first user-visible validation surface.

Do not push the new runtime into `Home` live path first.

### Task 3.2

Preserve raw/TSE comparison workflow:

- save processed output
- save raw sibling output
- playback both

Deliverable:

- stable A/B evaluation path for the managed runtime implementation

## Workstream 4: Tensor G2 Baseline Logging

### Task 4.1

Continue using:

- [TENSOR_G2_TEST_LOG.md](./TENSOR_G2_TEST_LOG.md)

Every meaningful managed-runtime test should append:

- device
- model
- runtime
- latency
- quality notes
- ASR notes

### Task 4.2

Stop relying on free-form log review as the only record.

Deliverable:

- one baseline entry per major runtime/model change

## Workstream 5: Existing Code Cleanup

### Task 5.1

Keep existing JNI path from expanding further unless it is strictly required as a temporary bridge.

### Task 5.2

Avoid new design work that assumes:

- ONNX native path is still the main future direction
- provider-specific native debugging is the main optimization lever

## Workstream 6: Decision Gates

### Gate A: Managed runtime loadability

Proceed only if:

- model can load
- one inference can run
- state tensors can be managed cleanly

### Gate B: DataCollect usability

Proceed only if:

- processed audio is actually generated
- A/B playback remains usable
- no obvious output corruption appears

### Gate C: Tensor G2 quality/latency

Proceed only if:

- latency is at least directionally acceptable
- quality is not obviously worse than current baseline

If these gates fail:

- do not re-expand the JNI-first scope automatically
- reassess model/runtime assumptions first

## Immediate Next Task Recommendation

The best next concrete task is:

- build the smallest managed-runtime feasibility spike for `voice_filter_lite_int8.tflite`

That should answer:

- can the app load and invoke this model without a custom native inference engine

## One-Sentence Summary

The next implementation step is not “optimize JNI again”; it is “prove the managed-runtime TFLite path can load, invoke, and produce usable output inside the app with minimal integration cost.”
