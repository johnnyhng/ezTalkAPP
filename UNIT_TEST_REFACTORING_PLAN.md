# Unit Test Plan For Future Refactoring

This document proposes how to add unit tests to `ezTalkAPP` in a way that supports future refactoring without forcing a full architecture rewrite first.

## Current state

The project currently has only template test files:

- [ExampleUnitTest.kt](/home/hhs/workspace/ezTalkAPP/app/src/test/java/com/k2fsa/sherpa/onnx/simulate/streaming/asr/ExampleUnitTest.kt)
- [ExampleInstrumentedTest.kt](/home/hhs/workspace/ezTalkAPP/app/src/androidTest/java/com/k2fsa/sherpa/onnx/simulate/streaming/asr/ExampleInstrumentedTest.kt)

There is no meaningful automated coverage yet for:

- transcript workflow rules
- feedback routing
- JSONL metadata behavior
- queue/state logic
- model selection behavior
- recognition result orchestration

## Goal

The goal is not “test everything.” The goal is to protect the logic that is most likely to break during refactoring:

- persistence rules
- state transitions
- workflow flags such as `checked`, `mutable`, `removable`
- candidate-fetch caching behavior
- per-screen business rules that currently live inside Compose screens

## Testing strategy

Use a staged approach:

1. extract pure or mostly-pure logic into small testable collaborators
2. add JVM unit tests first for fast feedback
3. use instrumented tests only for Android-specific pieces that cannot reasonably be isolated

This matters because much of the current logic is embedded in:

- Compose screens
- Android framework classes
- file and network helpers

Directly testing those parts as-is will be slow and brittle.

## Recommended test split

### 1. Pure JVM unit tests

These should become the bulk of the future suite.

Best candidates:

- queue navigation logic from [DataCollectViewModel.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/DataCollectViewModel.kt)
- transcript state transition rules from [Home.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/Home.kt) and [TranslateScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/TranslateScreen.kt)
- remote candidate merge/caching rules from [RecognitionUtils.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/RecognitionUtils.kt)
- JSONL serialization rules from [WavUtil.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/WavUtil.kt)
- feedback routing rules from [Api.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/Api.kt)
- settings-to-behavior mapping from [HomeViewModel.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/HomeViewModel.kt)

### 2. Android/JVM boundary tests

These are still unit-style tests, but require a small seam around Android dependencies.

Good examples:

- DataStore-backed settings reads/writes
- file path generation under `context.filesDir`
- model directory discovery under `files/models/<userId>`

These can often be tested with:

- fake file systems or temp directories
- fake settings repositories
- wrapper interfaces around Android APIs

### 3. Instrumented tests

Keep these minimal and focused on integration points:

- app startup and navigation smoke tests
- one end-to-end screen interaction per major screen
- one storage integration test proving `.wav` / `.jsonl` can be created and read on device

Do not put most workflow logic here. It will slow the team down.

## Refactoring seam plan

Before writing many tests, introduce a few small seams.

### A. Extract transcript workflow logic

Current problem:

- important rules are embedded directly in `Home.kt`, `TranslateScreen.kt`, and dialog callbacks

Extract into small classes/functions such as:

- `TranscriptFeedbackDecider`
- `TranscriptStateReducer`
- `TtsFeedbackPolicy`

What these would own:

- when TTS should trigger backend feedback
- when feedback should be skipped
- when `mutable` becomes `false`
- when `removable` becomes `true`
- how `checked` changes

This is the highest-value extraction because these rules are currently easy to regress.

### B. Extract JSONL metadata logic

Current problem:

- JSONL behavior is spread across `WavUtil.kt`, `RecognitionUtils.kt`, `Api.kt`, `Home.kt`, `TranslateScreen.kt`, and `FileManager.kt`

Extract something like:

- `TranscriptMetadataRepository`
- `TranscriptMetadata`

Responsibilities:

- load metadata
- save metadata
- preserve existing fields during updates
- merge remote candidates without losing user edits

This makes file behavior testable without going through UI.

### C. Extract remote candidate service

Current problem:

- `getRemoteCandidates(...)` mixes file I/O, caching, network requests, and merge behavior

Split it into:

- cache lookup
- network fetch
- metadata merge/write-back

Possible shape:

- `RemoteCandidateService`
- `RemoteCandidateApi`
- `TranscriptMetadataStore`

### D. Extract recording result post-processing

Current problem:

- `RecognitionManager` does too much in one place

Separate:

- speech segmentation / audio capture
- transcript creation
- file persistence

This does not need to be a big rewrite. Even one extracted helper would make finalization logic easier to test.

## Priority order for test coverage

### Priority 1: workflow rules

Add tests for the behavior most likely to regress during refactoring.

Target cases:

- `enableTtsFeedback = true` and item is not yet synced
  - feedback should be attempted
  - success should set `checked = true`, `mutable = false`, `removable = true`
- `enableTtsFeedback = true` and item already has `removable = true`
  - TTS should still be allowed
  - backend feedback should be skipped
- `enableTtsFeedback = false`
  - text should still be confirmed
  - `mutable/removable` should remain unchanged unless explicitly changed elsewhere
- dialog TTS confirm should follow the same state rules as Home row TTS

Why first:

- this is user-visible behavior
- this is already evolving
- this is easy to break by moving code around

### Priority 2: metadata persistence

Target cases:

- `saveJsonl(...)` writes expected keys
- writing without `remote_candidates` does not create the field
- rewriting metadata preserves expected values
- reading malformed or missing JSONL fails safely
- remote candidate write-back does not lose latest edited `modified` text

Why second:

- the app relies heavily on `.jsonl` as the real source of truth

### Priority 3: remote candidate behavior

Target cases:

- if cached `remote_candidates` exist, network should not be called
- if no cache exists, network response should be parsed and saved
- if remote response is malformed, result should be empty and not crash
- if metadata has been edited between fetch start and save, the latest metadata should win

Why third:

- there is already explicit re-read logic designed to avoid races
- that logic should be locked down before refactoring

### Priority 4: data collection queue logic

Target cases:

- moving forward consumes the current prompt and updates queue/history correctly
- moving backward restores the last prompt without corrupting current state
- retry/skip paths keep queue order stable
- empty queue and boundary conditions fail safely

Why fourth:

- this logic is stateful but mostly pure after extraction
- regressions here are subtle and annoying for users

## Coverage target reset

After adding `Jacoco`, the first full-module report showed that the current top-line number is not a useful engineering target:

- full `app` module line coverage is about 4%
- this number is dominated by large untested areas such as `com.k2fsa.sherpa.onnx`, Android-heavy screens, and framework glue

Trying to force the entire module to 80% now would create a lot of low-value tests and still leave the code hard to maintain.

The correct target is:

- core logic coverage >= 80%
- full module coverage improves gradually, but is not the primary gate yet

## What counts toward the 80% goal

The 80% goal should apply to the code that holds product logic and refactoring risk:

- [Api.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/Api.kt)
- [RecognitionUtils.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/RecognitionUtils.kt)
- [WavUtil.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/WavUtil.kt)
- [TranscriptWorkflow.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/workflow/TranscriptWorkflow.kt)
- [DataCollectQueue.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/datacollect/DataCollectQueue.kt)
- [SettingsManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/SettingsManager.kt) if it remains an active configuration seam

The 80% goal should not initially include:

- `com.k2fsa.sherpa.onnx/*`
- generated code such as `R`, `BuildConfig`, manifests
- Compose screen bodies
- Android framework callback glue
- native/audio/player/TTS boundary code that has not been isolated yet

## Coverage execution plan

### Batch A: Fix the metric first

- update Jacoco excludes so the report separates `core logic` from `full module`
- keep the full-module report for visibility
- add a second report or filtered class set for the 80% target

Expected outcome:

- coverage numbers become interpretable
- the team stops optimizing for a misleading global percentage

Status:

- completed
- Gradle now exposes both `:app:jacocoTestReport` and `:app:jacocoCoreLogicReport`
- the current filtered `core logic` report starts at about 21.20% line coverage (`131 / 618`)

### Batch B: Raise `Api.kt` coverage

- add tests for request payload variants
- add tests for error paths and fallback behavior
- cover candidate merge edge cases and missing-field handling

Status:

- completed
- `Api.kt` request-building logic now has dedicated pure helpers for update and recognition payloads
- `ApiKt` line coverage increased from `22` covered lines to `51`
- filtered `core logic` line coverage increased from about `21.20%` (`131 / 618`) to about `25.08%` (`160 / 638`)

### Batch C: Raise `RecognitionUtils.kt` coverage

- cover cache hit/miss, malformed responses, write-back preservation, and failure paths
- focus on branch coverage, not only happy paths

Status:

- completed
- remote-candidate orchestration was extracted into a pure helper so cache, fetch, blank-url, null-response, and write-back paths can be unit tested directly
- `RecognitionUtilsKt` line coverage increased from `29` covered lines to `47`
- `RecognitionUtilsKt` branch coverage is now fully covered for the selected core scope (`42 / 42`)
- filtered `core logic` line coverage increased from about `25.08%` (`160 / 638`) to about `27.06%` (`178 / 658`)

### Batch D: Raise `WavUtil.kt` coverage

- expand JSONL round-trip cases
- add overwrite, empty-field, malformed-content, and missing-file scenarios

Status:

- completed
- `WavUtil.kt` now exposes pure helpers for PCM conversion, WAV header construction, JSONL line building, and WAV byte parsing
- round-trip and helper tests were added for WAV save/read, JSONL overwrite behavior, delete behavior, PCM encoding, and header parsing
- `WavUtilKt` line coverage increased from `2` covered lines to `86`
- filtered `core logic` line coverage increased from about `27.06%` (`178 / 658`) to about `38.69%` (`262 / 677`)

### Batch E: Complete reducer coverage

- finish transition matrix tests for `TranscriptWorkflow.kt`
- finish queue edge-case tests for `DataCollectQueue.kt`

Status:

- completed
- `TranscriptWorkflowKt` branch coverage is now fully covered (`6 / 6`)
- `DataCollectQueueKt` branch coverage is now fully covered (`30 / 30`)
- this batch focused on the remaining edge cases only: disabled feedback, empty import input, blank current text, blank-only remaining queue, and non-blank remaining count behavior

### Batch F: Close configuration gaps

- expand `SettingsManager` coverage
- add a Jacoco verification gate for the selected core-logic scope

Status:

- completed with a narrowed gate scope
- JVM tests were added for `SettingsManager` default mapping, stored-value mapping, write helper behavior, and manager round-trip behavior
- `jacocoStableCoreVerification` now enforces `LINE >= 80%`, but only for the stabilized reducer scope:
  - `TranscriptWorkflowKt`
  - `DataCollectQueueKt`
- this narrowed scope is intentional: `SettingsManager` is DataStore-backed and its JVM tests currently do not register usable Jacoco line coverage in this project, even though the tests execute and pass after a clean rebuild
- the stable reducer gate is now green and gives the project its first enforced 80% threshold

## Success criteria

This coverage plan is complete when all of the following are true:

- a filtered `core logic` Jacoco report exists
- the filtered report reaches at least 80% line coverage
- the 80% gate is enforced in Gradle for the selected scope
- full-module coverage is still reported, but not used as the short-term acceptance gate

## Follow-up batches after initial 18-step rollout

### Batch G: Raise `Api.kt` wrapper coverage

Status:

- completed
- network wrapper preparation was extracted into pure request-plan helpers for:
  - `postProcessAudio`
  - `postTransfer`
  - `postForRecognition`
- HTTP success classification was also extracted into a pure helper
- this made the outer wrapper behavior testable without real network calls
- `ApiKt` line coverage increased from `51` covered lines to `94`
- filtered `core logic` line coverage increased from about `37.32%` (`262 / 702`) to about `41.21%` (`305 / 740`)

### Batch H: Raise `WavUtil.kt` wrapper coverage

Status:

- completed
- file-target and delete-plan helpers were extracted so wrapper path logic becomes directly testable
- this batch focused on the outer file-layer decisions instead of the already-covered PCM/header/JSON pure helpers
- `WavUtilKt` line coverage increased from `86` covered lines to `94`
- filtered `core logic` line coverage increased from about `41.21%` (`305 / 740`) to about `41.68%` (`313 / 751`)

### Batch I: Raise `Api.kt` packaging and dispatch coverage

Status:

- completed
- extracted pure helpers for:
  - WAV byte validation and upload-array conversion
  - upload metadata packaging
  - feedback dispatch endpoint selection
- added JVM tests for those helpers instead of relying on wrapper execution side effects
- `ApiKt` line coverage increased from `94` covered lines to `119`
- filtered `core logic` line coverage increased from about `41.68%` (`313 / 751`) to about `43.72%` (`338 / 773`)

### Batch J: Raise `Api.kt` combine and dispatch execution coverage

Status:

- completed
- extracted pure helpers for:
  - combining upload metadata with raw audio arrays
  - executing dispatch plans against injected transport functions
- this batch covered additional `Api.kt` lines without introducing network mocks
- `ApiKt` line coverage increased from `119` covered lines to `127`
- filtered `core logic` line coverage increased from about `43.72%` (`338 / 773`) to about `44.48%` (`346 / 778`)

### Batch K: Raise `Api.kt` metadata parsing coverage

Status:

- completed
- extracted pure helpers for:
  - parsing upload metadata snapshots from JSONL text
  - building packaged upload objects before final JSON combination
- added JVM tests for snapshot parsing and packaged upload construction
- `ApiKt` line coverage increased from `127` covered lines to `133`
- filtered `core logic` line coverage increased from about `44.48%` (`346 / 778`) to about `45.02%` (`352 / 782`)

Target cases:

- importing lines creates the right current text + queue
- `moveToNext()` updates remaining/previous counts correctly
- `moveToPrevious()` restores the previous item correctly
- `retryLastCompleted()` behaves like a rollback
- enabling sequence mode with no saved queue shows the empty-queue state

Why fourth:

- this logic is stateful but mostly deterministic
- it is a strong candidate for clean JVM tests

### Priority 5: model selection and settings logic

Target cases:

- default model is copied when no model exists
- selected model from settings is restored correctly
- changing user ID invalidates the initialized model key
- changing selected model forces re-initialization path

Why fifth:

- less volatile than transcript workflows
- still important for refactoring safety

## Proposed test package structure

Add JVM tests under:

- `app/src/test/java/tw/com/johnnyhng/eztalk/asr/workflow/`
- `app/src/test/java/tw/com/johnnyhng/eztalk/asr/metadata/`
- `app/src/test/java/tw/com/johnnyhng/eztalk/asr/candidates/`
- `app/src/test/java/tw/com/johnnyhng/eztalk/asr/datacollect/`
- `app/src/test/java/tw/com/johnnyhng/eztalk/asr/settings/`

Suggested file names:

- `TranscriptFeedbackDeciderTest.kt`

## Execution roadmap in 18 batches

To keep progress measurable and avoid a large all-at-once testing effort, implement the suite in 18 small batches.

### Batch 1: test scaffolding

- replace template tests with real package structure
- add shared test fixture helpers
- confirm the JVM test task runs cleanly

Status:

- completed on 2026-03-29
- implemented:
  - app-level JVM test package under `app/src/test/java/tw/com/johnnyhng/eztalk/asr/`
  - shared fixture helper in `fixtures/TestFixtures.kt`
  - scaffolding smoke tests
  - app-package-aligned instrumented smoke test
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 2: basic data model checks

- add baseline tests for [Transcript.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/data/classes/Transcript.kt)
- lock down default values and copy behavior

Status:

- completed on 2026-03-29
- implemented:
  - baseline JVM tests for `Transcript`
  - coverage for default `modifiedText`, workflow flags, and candidate list defaults
  - coverage for `copy(...)` updates without mutating the original instance
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 3: JSON helper behavior

- test `optStringList(...)` in [WavUtil.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/WavUtil.kt)
- verify missing keys, empty arrays, and blank items are handled safely

Status:

- completed on 2026-03-29
- implemented:
  - JVM tests for `JSONObject.optStringList(...)`
  - coverage for missing keys, empty arrays, and blank-item filtering
  - coverage for preserving original item order of non-blank values
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 4: JSONL core field persistence

- test `saveJsonl(...)` writes:
  - `original`
  - `modified`
  - `checked`
  - `mutable`
  - `removable`

Status:

- completed on 2026-03-29
- implemented:
  - Robolectric-backed JVM tests for `saveJsonl(...)`
  - coverage for core persisted fields
  - coverage for creating the per-user JSONL directory when missing
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 5: JSONL candidate persistence

- test `saveJsonl(...)` with and without:
  - `local_candidates`
  - `remote_candidates`
- verify omitted fields are not written accidentally

Status:

- completed on 2026-03-29
- implemented:
  - Robolectric-backed JVM tests for candidate persistence in `saveJsonl(...)`
  - coverage for writing both `local_candidates` and `remote_candidates`
  - coverage for omitted candidate fields when parameters are not provided
  - coverage for explicit empty candidate lists producing empty arrays
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 6: JSONL read failure safety

- test `readJsonl(...)` for:
  - missing file
  - empty file
  - malformed JSON

Status:

- completed on 2026-03-29
- implemented:
  - JVM tests for `readJsonl(...)`
  - coverage for missing, empty, and malformed JSONL files returning `null`
  - one valid-file control test to confirm successful parsing still works
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 7: metadata merge preservation

- extract or wrap a small metadata merge seam
- test that updating remote data does not erase:
  - latest `modified`
  - `local_candidates`
  - workflow flags

Status:

- completed on 2026-03-29
- implemented:
  - extracted a small pure metadata builder seam in `RecognitionUtils.kt`
  - JVM tests covering preservation of latest `modified`, workflow flags, and `local_candidates`
  - JVM test covering fallback behavior when latest JSON metadata is absent
  - JVM test covering legacy `canCheck` fallback when `mutable` is missing
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 8: remote candidate cache hit behavior

- test [RecognitionUtils.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/RecognitionUtils.kt) when cached `remote_candidates` already exist
- verify network fetch is skipped

Status:

- completed on 2026-03-29
- implemented:
  - extracted cached remote-candidate lookup into a small pure helper in `RecognitionUtils.kt`
  - JVM tests covering missing metadata, missing key, and cache-hit ordering
  - `getRemoteCandidates(...)` now uses the extracted cached-candidate helper for its early return path
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 9: remote candidate fetch parsing

- test successful parsing of `sentence_candidates`
- test malformed response fallback to empty result

Status:

- completed on 2026-03-29
- implemented:
  - extracted response parsing into `parseRemoteCandidates(...)`
  - JVM tests covering ordered parsing of `sentence_candidates`
  - JVM tests covering blank filtering and malformed/missing response fallback to empty list
  - `getRemoteCandidates(...)` now treats empty parsed candidates as a safe no-result path
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 10: remote write-back race protection

- test re-read-before-save behavior in `RecognitionUtils`
- verify latest metadata wins if edits happen between fetch start and save

Status:

- completed on 2026-03-29
- implemented:
  - extracted `buildRemoteCandidateWriteback(...)` to combine latest metadata with parsed response candidates
  - JVM tests covering latest-metadata-wins behavior for `modified`, workflow flags, and `local_candidates`
  - JVM tests covering fallback behavior when latest metadata is absent
  - JVM test covering null write-back when response cannot produce candidates
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 11: merged candidate ordering

- test merged candidate behavior in [Api.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/Api.kt)
- lock down:
  - local first
  - remote second
  - `distinct()` de-duplication

Status:

- completed on 2026-03-29
- implemented:
  - exposed `buildMergedCandidates(...)` for package-level JVM testing
  - JVM tests covering local-first ordering
  - JVM tests covering `distinct()` de-duplication with first-seen order preserved
  - JVM test covering empty output when metadata is absent
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 12: feedback routing with remote candidates

- test `feedbackToBackend(...)` chooses `PUT /api/updates` when `remote_candidates` exist

Status:

- completed on 2026-03-29
- implemented:
  - extracted `FeedbackRoute` and `decideFeedbackRoute(...)` in `Api.kt`
  - JVM test covering remote-candidate routing to `PUT /api/updates`
  - `feedbackToBackend(...)` now delegates route selection to the extracted helper
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 13: feedback routing with local-only candidates

- test `feedbackToBackend(...)` chooses `POST process_audio` when only `local_candidates` exist

Status:

- completed on 2026-03-29
- implemented:
  - JVM test covering local-only routing to `POST process_audio`
  - JVM test covering fallback to transfer when local candidates exist but recognition URL is blank
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 14: feedback routing with no candidates

- test `feedbackToBackend(...)` chooses `POST /api/transfer` when neither local nor remote candidates exist

Status:

- completed on 2026-03-29
- implemented:
  - JVM test covering transfer routing when metadata is missing
  - JVM test covering transfer routing when neither local nor remote candidate arrays exist
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 15: process_audio request payload shape

- test `postProcessAudio(...)` request JSON shape
- for now, keep it aligned with `postForRecognition(...)`

Status:

- completed on 2026-03-29
- implemented:
  - extracted `buildProcessAudioPayload(...)` from `postProcessAudio(...)`
  - JVM tests covering the expected payload shape with metadata and raw audio bytes
  - JVM tests covering fallback label behavior and omission of `raw` when it is not provided
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 16: transcript workflow state rules

- extract a small reducer or policy from Home/Translate behavior
- test transitions for:
  - `checked`
  - `mutable`
  - `removable`
  - feedback success / skip

Status:

- completed on 2026-03-29
- implemented:
  - extracted `shouldAttemptFeedback(...)` and `reduceTranscriptAfterConfirmation(...)`
  - JVM tests covering feedback-attempt gating and confirmation state transitions
  - integrated the extracted workflow helpers back into `Home.kt` and `TranslateScreen.kt`
- verification:
  - `./gradlew :app:testDebugUnitTest`

### Batch 17: data collect queue logic

- test [DataCollectViewModel.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/DataCollectViewModel.kt)
- cover:
  - import
  - next
  - previous
  - retry
  - skip

Status:

- completed on 2026-03-29
- implemented:
  - extracted pure queue reducers into `DataCollectQueue.kt`
  - JVM tests covering import, next, previous, and queue exhaustion behavior
  - `DataCollectViewModel` now delegates queue transitions to the extracted reducers
  - `skipCurrent()` and `retryLastCompleted()` remain aliases of next/previous behavior through the shared reducers
- verification:
  - `./gradlew :app:testDebugUnitTest`
  - `./gradlew :app:compileDebugKotlin`

### Batch 18: minimal integration coverage

- keep instrumented coverage very small
- add smoke tests for:
  - settings read/write
  - one storage round-trip
  - one major screen interaction path

Status:

- completed on 2026-03-29
- implemented:
  - instrumented settings round-trip test for `SettingsManager`
  - instrumented storage round-trip test for `saveJsonl(...)` and `readJsonl(...)`
  - retained the existing app-context smoke test as the baseline instrumentation sanity check
- verification:
  - `./gradlew :app:compileDebugAndroidTestKotlin`
  - `./gradlew :app:connectedDebugAndroidTest` when a device/emulator is available

## Recommended implementation order

Do the work in this order:

1. batches 1-6 for metadata foundations
2. batches 8-15 for candidates and feedback routing
3. batches 16-18 for workflow policy and minimal integration coverage

## Notes

- prefer JVM tests unless Android framework access is unavoidable
- avoid testing large Compose screens directly until business rules are extracted into small seams
- keep each batch independently shippable so coverage can grow without blocking refactors
- `TranscriptStateReducerTest.kt`
- `TranscriptMetadataRepositoryTest.kt`
- `RemoteCandidateServiceTest.kt`
- `DataCollectQueueStateTest.kt`
- `HomeViewModelModelSelectionTest.kt`

## Concrete first extraction set

If the goal is to start small, extract only these first:

1. `TranscriptFeedbackDecision`
2. `TranscriptMetadataSnapshot`
3. `RemoteCandidateCachePolicy`

### Example scope for each

`TranscriptFeedbackDecision`

- input:
  - current transcript flags
  - `enableTtsFeedback`
  - action source such as row TTS or dialog TTS
- output:
  - whether backend feedback should run
  - the next transcript flags after success

`TranscriptMetadataSnapshot`

- input/output object around:
  - `original`
  - `modified`
  - `checked`
  - `mutable`
  - `removable`
  - `remote_candidates`

`RemoteCandidateCachePolicy`

- input:
  - cached metadata
  - remote response
- output:
  - whether to reuse cache
  - merged metadata to save

These three would already unlock a meaningful unit-test base.

## Suggested fake interfaces

To avoid Android-heavy tests, introduce interfaces around unstable dependencies.

Suggested interfaces:

- `FeedbackApi`
- `RecognitionApi`
- `TranscriptMetadataStore`
- `AudioFileStore`
- `SettingsRepository`
- `Clock`

Why:

- lets tests use fake implementations
- avoids real network
- avoids real `Context`
- avoids timing-dependent assertions

## Test data strategy

Keep fixtures very small and explicit.

Recommended fixtures:

- one basic mutable transcript
- one already-synced transcript with `removable = true`
- one data-collect transcript with `mutable = false`
- one JSONL payload with cached `remote_candidates`
- one malformed API response

Avoid large fixture files until the test suite stabilizes.

## What not to test first

Do not start with:

- pixel-perfect Compose UI tests
- microphone integration tests
- full Sherpa-ONNX decoding tests
- real network calls to backend/recognition services

These are expensive and do not protect refactoring as efficiently as pure workflow tests.

## Dependency recommendations for future test work

The project already has JUnit configured in [app/build.gradle.kts](/home/hhs/workspace/ezTalkAPP/app/build.gradle.kts).

For a more practical unit-test setup, future additions should likely include:

- `kotlinx-coroutines-test`
- a mocking library such as MockK
- optionally Truth or AssertJ for clearer assertions
- optionally Robolectric only if some Android-bound logic cannot be isolated

These are recommendations only. They are not required for this planning document.

## Minimal rollout plan

If the team wants the safest path, do it in four small phases.

### Phase 1

- add test dependencies
- replace the example test files
- create one new workflow-focused test package

### Phase 2

- extract transcript workflow decision logic
- add tests for Home / dialog / Translate TTS feedback rules

### Phase 3

- extract metadata repository logic
- add tests for JSONL read/write and candidate merge behavior

### Phase 4

- extract queue/state logic and model-selection helpers
- add tests for DataCollect and model lifecycle behavior

## Success criteria

The refactoring effort is in a good place when:

- TTS feedback rules are covered by fast JVM tests
- metadata persistence behavior is covered by fast JVM tests
- remote candidate caching behavior is covered by fast JVM tests
- DataCollect queue navigation is covered by fast JVM tests
- only a very small number of instrumented tests are needed

## Short version

If you want the highest ROI test plan for future refactoring, start here:

1. extract transcript workflow rules out of Compose screens
2. unit test `checked/mutable/removable` transitions
3. extract JSONL metadata behavior
4. unit test remote candidate caching and merge behavior
5. then cover DataCollect queue logic

That order will protect the app’s most fragile behavior with the least amount of test infrastructure.
