# Coverage Report

This document keeps the current coverage and test-report entry points in one place.

## Current status

Latest verified on:

- `./gradlew :app:jacocoCoreLogicReport`
- `./gradlew :app:connectedDebugAndroidTest`

Core logic Jacoco coverage:

- line: `553 / 876` = `63.13%`
- branch: `219 / 328` = `66.77%`
- instruction: `3581 / 5526` = `64.80%`
- method: `75 / 116` = `64.66%`
- class: `9 / 12` = `75.00%`

Connected Android tests:

- tests: `19`
- failures: `0`

## What this number means

The `jacocoCoreLogicReport` number is the main unit-test coverage signal right now.

It includes the current core logic scope:

- `Api.kt`
- `RecognitionUtils.kt`
- `Utils.kt`
- `WavUtil.kt`
- `UserSettings.kt`
- `Model.kt`
- `TranscriptWorkflow.kt`
- `DataCollectQueue.kt`
- `ModelManager.kt`
- `SettingsManager.kt`

It does not include Compose screen behavior tests, even though those tests now run successfully on device.

That means:

- `connectedDebugAndroidTest` currently verifies UI behavior
- `jacocoCoreLogicReport` currently measures JVM/core-logic line and branch coverage

These are complementary signals, not the same report.

## Report entry points

Core logic Jacoco HTML:

- [index.html](/home/hhs/workspace/ezTalkAPP/app/build/reports/jacoco/jacocoCoreLogicReport/html/index.html)

Connected Android test HTML:

- [index.html](/home/hhs/workspace/ezTalkAPP/app/build/reports/androidTests/connected/debug/index.html)

## Recommended commands

Fast core coverage refresh:

```bash
./gradlew :app:jacocoCoreLogicReport
```

Stable reducer coverage gate:

```bash
./gradlew :app:jacocoStableCoreVerification
```

Device behavior test run:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Combined verification entry:

```bash
./gradlew :app:verificationReport
```

## Interpretation guide

Use `jacocoCoreLogicReport` when the question is:

- how much of the refactoring-risk logic is exercised by JVM tests
- whether line/branch coverage is improving for core helpers and reducers

Use `connectedDebugAndroidTest` when the question is:

- whether current screen behavior tests still pass on a device
- whether Compose interaction wiring still works after UI changes

Do not compare these two outputs as if they were the same metric.

## Next cleanup options

If coverage/report organization should go further, the next practical steps are:

1. add a small Gradle alias task that runs `testDebugUnitTest`, `jacocoCoreLogicReport`, and `connectedDebugAndroidTest`
2. decide whether screen behavior tests should remain pass/fail only, or whether Android coverage collection is worth enabling
3. keep this file updated whenever the core logic include scope changes
