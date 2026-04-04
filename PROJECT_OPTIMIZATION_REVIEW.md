# Project Optimization Review

This document summarizes the main optimization opportunities in `ezTalkAPP` based on the current codebase.

The goal is not to redesign everything at once. The useful approach is:

1. fix consistency bugs and obvious technical debt first
2. split oversized files and mixed responsibilities next
3. only then do larger architectural cleanup

## Refactor rules

Every refactor phase should follow these rules before code movement starts.

### 1. Add tests before each phase

Each refactor phase should start with:

- matching unit tests for the behavior that phase is about to change
- a short user end-to-end testing checklist for the visible flow that phase may affect

The point is to lock behavior first, then refactor.

### 2. Prefer tests that do not require core-code changes

When writing unit tests, the default constraint should be:

- do not modify core production code just to make testing easier

Allowed exceptions should be narrow:

- obvious bug fixes discovered during test writing
- extracting a very small pure helper when the current code is otherwise untestable and the extraction reduces complexity instead of adding test-only seams

The project should avoid large test-driven refactors where core code is reshaped mainly for test convenience.

### 3. Keep user E2E checks in every phase

Unit tests are not enough for this app because major risks are cross-layer:

- recording
- VAD segmentation
- local ASR
- persistence
- feedback upload
- model download
- screen interaction

So each phase should end with a concrete manual or device-backed E2E checklist, even if the code change is mostly internal.

### 4. Prefer phase-local test scope

Do not try to add broad new test coverage everywhere before starting a phase.

Instead:

- identify the exact behavior the phase may break
- add only the unit tests needed to lock that behavior
- add the shortest realistic E2E checklist for that flow

This keeps refactor cost bounded and avoids fake progress from low-value tests.

## Overall assessment

The project already has several good foundations:

- core storage and API helper logic now has meaningful unit-test coverage
- remote candidate caching and feedback routing have explicit seams
- model download/list logic is no longer fake
- major screens already have behavior coverage

The main problems are not lack of features. The main problems are:

- too much logic concentrated in a few oversized files
- duplicated runtime flows between screens
- settings / state / persistence still have some inconsistent defaults and leftover compatibility design
- API, UI, storage, and orchestration concerns are still too tightly coupled

## Phase execution pattern

Each optimization phase should follow this sequence:

1. identify the specific behavior at risk
2. add targeted unit tests without changing core code unless there is a strong reason
3. define a user end-to-end checklist for the affected flow
4. execute the refactor
5. rerun unit tests and the phase E2E checklist
6. only then move to the next phase

Recommended E2E checklist categories:

- `Home`: record, candidate edit, TTS, feedback
- `Translate`: record or input, edit, clear, feedback
- `FileManager`: load, select, delete, replay, feedback
- `Settings`: backend URL, model selection, remote model dialog
- `Remote model flow`: list, update icon, download, refresh local models
- `DataCollect`: manual mode, sequence mode, retry/skip/next

## Highest priority

These are the items most worth doing next.

### 1. Unify default settings behavior

Problem:

- [UserSettings.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/data/classes/UserSettings.kt) and [SettingsManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/SettingsManager.kt) still do not use the same defaults for all fields.
- This creates inconsistent behavior between:
  - newly constructed in-memory settings
  - settings restored from DataStore

Examples currently worth checking carefully:

- `lingerMs`
- `inlineEdit`
- `enableTtsFeedback`

Impact:

- hard-to-debug behavior differences after app reinstall, test setup, or DataStore reset
- behavior tests can pass while runtime defaults differ

Recommendation:

- define one canonical default source
- use it from both `UserSettings` and `SettingsManager`
- avoid repeating fallback constants in multiple places

Priority: `P0`

### 2. Split `Api.kt` into smaller clients

Problem:

- [Api.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/Api.kt) is over 1000 lines
- it currently mixes:
  - recognition upload
  - feedback routing
  - transfer/update payload building
  - remote model endpoints
  - multipart encoding
  - redirect handling
  - response parsing

Impact:

- every API change increases regression risk
- unrelated changes collide in one file
- the file is harder to reason about than the actual domain

Recommendation:

- split into focused units such as:
  - `RecognitionApi`
  - `FeedbackApi`
  - `ModelApi`
  - `MultipartEncoder`
  - `ApiParsers`
- keep endpoint construction in [BackendEndpoints.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/BackendEndpoints.kt)

Priority: `P0`

### 3. Merge duplicated recording logic

Problem:

- [RecognitionManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/RecognitionManager.kt) already owns the main audio + VAD + finalization path
- [TranslateScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/TranslateScreen.kt) still contains its own recording / buffering / finalization implementation

Impact:

- bug fixes need to be applied twice
- subtle behavior drift between `Home` and `Translate`
- testability is worse because `TranslateScreen` still owns too much runtime logic

Recommendation:

- move `TranslateScreen` onto the same recording engine abstraction as `Home`
- if exact behavior must differ, expose a mode or strategy rather than a second full implementation

Priority: `P0`

### 4. Reduce `HomeViewModel` responsibility

Problem:

- [HomeViewModel.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/HomeViewModel.kt) currently coordinates:
  - settings
  - model selection
  - recognizer initialization
  - remote model list
  - model download
  - model update checking
  - recording bridge state
  - final transcript emission

Impact:

- too much unrelated state in one view model
- higher risk when changing either ASR flow or model-management flow

Recommendation:

- split model-management into a dedicated view model or manager
- keep `HomeViewModel` focused on recognition/session state

Priority: `P1`

## UI / screen structure

### 5. Break up oversized screens

Current large files:

- [Home.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/Home.kt): about 515 lines
- [TranslateScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/TranslateScreen.kt): about 742 lines
- [SettingsScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SettingsScreen.kt): about 318 lines

Problem:

- composable rendering, state wiring, async side effects, and business rules are mixed together

Impact:

- harder review and slower iteration
- behavior tests need to target large surfaces

Recommendation:

- split by feature area, not by arbitrary helper count
- examples:
  - `HomeTranscriptList`
  - `HomeControls`
  - `HomeTtsFeedbackController`
  - `TranslateRecorderPane`
  - `TranslateCandidatePanel`
  - `SettingsAccountSection`
  - `SettingsModelSection`
  - `SettingsRecognitionSection`

Priority: `P1`

### 6. Clean up `SettingsScreen`

Problem:

- [SettingsScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SettingsScreen.kt) still contains:
  - deprecated `GoogleSignIn`
  - deprecated `GoogleSignInOptions`
  - unused local variables such as `activity`, `coroutineScope`, `languages`

Impact:

- warning noise hides real problems
- future Android SDK upgrades will get harder

Recommendation:

- remove unused locals now
- decide whether Google sign-in remains in scope
- if it remains, migrate away from deprecated APIs instead of carrying warnings indefinitely

Priority: `P0`

### 7. Move file scanning out of `FileManagerScreen`

Problem:

- [FileManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/FileManager.kt) still walks the filesystem and parses JSON directly inside the screen

Impact:

- UI layer owns storage logic
- harder to cache, paginate, or test reload behavior cleanly

Recommendation:

- move file listing and metadata parsing into a repository or dedicated manager
- expose `StateFlow<List<WavFileEntry>>` to the UI

Priority: `P1`

## ASR engine and concurrency

### 8. Reduce reliance on global singleton runtime state

Problem:

- [SimulateStreamingAsr.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/SimulateStreamingAsr.kt) is a global singleton for recognizer and VAD
- several callers rely on implicit global lifetime and manual synchronization

Impact:

- harder to test
- harder to reason about lifecycle
- model switching and concurrent recognition paths are riskier

Recommendation:

- introduce an engine holder / repository abstraction
- scope recognizer lifecycle more explicitly
- keep VAD access synchronized, but move ownership out of app-global object over time

Priority: `P1`

### 9. Normalize coroutine ownership

Problem:

- some long-running work uses custom scopes, some uses `viewModelScope`, some remains inside composables
- there is still a mix of screen-owned and manager-owned async orchestration

Impact:

- cancellation behavior is harder to reason about
- lifecycle bugs are easier to introduce

Recommendation:

- move long-running non-UI work out of composables where possible
- prefer `viewModelScope` or explicit injected scopes for app logic
- make repository/manager functions fully suspendable and lifecycle-neutral

Priority: `P1`

## Data and persistence

### 10. Rename or redesign `.jsonl` metadata storage

Problem:

- the metadata file is called `.jsonl`
- but the app treats it as a single overwritten JSON snapshot, not a JSON-lines append log

Impact:

- the name misleads maintainers
- schema evolution is harder to explain

Recommendation:

- either rename it to `.json`
- or actually adopt append-style JSONL semantics

Priority: `P2`

### 11. Replace ad-hoc JSON access with typed metadata models

Problem:

- JSON metadata is read and written in multiple places with repeated string keys
- examples include candidate fields, workflow fields, and label fields

Impact:

- schema drift risk
- typo risk
- more boilerplate than necessary

Recommendation:

- introduce a typed persisted metadata model
- keep conversion helpers near persistence boundaries
- reserve `JSONObject` handling for pure API boundary code

Priority: `P1`

## Model-management flow

### 12. Separate model discovery, update-check, and download concerns

Problem:

- remote model logic is now real, but still spread across:
  - [HomeViewModel.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/HomeViewModel.kt)
  - [RemoteModelRepository.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/RemoteModelRepository.kt)
  - [Api.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/Api.kt)
  - [RemoteModelsManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/widgets/RemoteModelsManager.kt)

Impact:

- update-check policy is still partly UI-driven
- model metadata shape is still minimal

Recommendation:

- define a dedicated remote-model domain layer:
  - list
  - compare local hash
  - update availability
  - download
- expose a cleaner immutable UI model to the dialog

Priority: `P1`

## Build and tooling

### 13. Modernize Android and Kotlin targets

Problem:

- [app/build.gradle.kts](/home/hhs/workspace/ezTalkAPP/app/build.gradle.kts) still targets Java 8 bytecode
- compile/target SDK and Compose compiler versions will need periodic refresh

Impact:

- future dependency upgrades become harder
- some modern language/runtime improvements are unavailable

Recommendation:

- plan an upgrade pass for:
  - Java/Kotlin target
  - Compose compiler alignment
  - AndroidX auth/sign-in stack

Priority: `P2`

### 14. Stabilize Android test source set health

Problem:

- JVM tests are much healthier than `androidTest`
- the Android test source set has shown breakage during some refactors

Impact:

- behavior coverage exists, but maintenance cost is higher than it should be

Recommendation:

- keep screen behavior tests, but reduce package/import drift
- consider a smaller shared test fixture layer for settings seeding and screen setup

Priority: `P1`

## Deprecated / warning cleanup backlog

These are smaller but worth clearing because they create review noise:

- deprecated permission callback flow in [MainActivity.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/MainActivity.kt)
- deprecated locale update pattern in [MainActivity.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/MainActivity.kt)
- deprecated Google sign-in APIs in [SettingsScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SettingsScreen.kt)
- delicate coroutine API warnings in recognition flows
- warning-only cleanup such as unused local variables and avoidable casts

Priority: `P2`

## Recommended execution order

### Phase 1

- unify settings defaults
- remove deprecated URL settings completely
- clean `SettingsScreen` warning noise
- keep `backendUrl` as the only persisted URL input

### Phase 2

- split `Api.kt`
- move `FileManager` storage logic out of UI
- split model-management out of `HomeViewModel`

### Phase 3

- unify `TranslateScreen` recording path with `RecognitionManager`
- reduce singleton dependence in `SimulateStreamingAsr`
- introduce typed persisted metadata

### Phase 4

- modernize Android auth / permission flows
- refresh build targets and dependency baseline

## If only three things are done

If scope is limited, the three most valuable improvements are:

1. split [Api.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/Api.kt)
2. remove duplicated recording logic from [TranslateScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/TranslateScreen.kt)
3. break model-management out of [HomeViewModel.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/HomeViewModel.kt)

Those three changes would reduce the largest long-term maintenance cost in the current codebase.
