# Audio Routing Settings Plan

## Goal

Add an audio routing section to [SettingsScreen](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SettingsScreen.kt) so the app can:

- inspect detected audio input devices
- inspect detected audio output devices
- persist user routing preferences
- later apply those preferences to recording and playback paths

This plan is based on [Android 音訊錄製併發與非對稱路由技術方案.md](/home/hhs/workspace/ezTalkAPP/Android%20%E9%9F%B3%E8%A8%8A%E9%8C%84%E8%A3%BD%E4%BD%B5%E7%99%BC%E8%88%87%E9%9D%9E%E5%B0%8D%E7%A8%B1%E8%B7%AF%E7%94%B1%E6%8A%80%E8%A1%93%E6%96%B9%E6%A1%88.md).

## Current State

- `SettingsScreen` has no audio device inventory or routing controls.
- `UserSettings` has no persisted audio routing fields.
- `SettingsManager` has no keys for input/output routing preferences.
- The app currently has no shared repository or manager for:
  - enumerating `AudioDeviceInfo`
  - showing active route state
  - applying preferred input/output devices

## Product Scope

The settings page should distinguish three things clearly:

1. Detected devices
2. Preferred devices selected by the user
3. Actual active route chosen by Android at runtime

This distinction is required because Android routing APIs are advisory in many cases. A selected device may not become the active route.

## Phase Plan

### Phase 1: Discovery And Persistence

Goal: make the settings page useful without changing live audio behavior yet.

Deliverables:

- list detected audio input devices
- list detected audio output devices
- allow selecting preferred input device
- allow selecting preferred output device
- persist those selections in `UserSettings`
- show capability and limitation text in UI

No recording or playback engine changes in this phase.

### Phase 2: Runtime Route Application

Goal: actually apply user preferences during audio capture and playback.

Deliverables:

- apply preferred input device to recording path
- apply preferred output device to playback path
- on Android 13+, try `setCommunicationDevice(...)` when appropriate
- expose apply result and fallback reason to UI/logs

### Phase 3: Concurrency And Capture Policy

Goal: align settings with the screen-recording / shared-capture strategy in the technical proposal.

Deliverables:

- persist `allowAppAudioCapture`
- wire `setAllowedCapturePolicy(ALLOW_CAPTURE_BY_ALL)` to relevant playback streams
- document that this affects app audio capture policy, not microphone sharing guarantees

## Data Model

### New UI / Domain Models

Add a light-weight UI model instead of exposing `AudioDeviceInfo` directly to Compose:

```kotlin
data class AudioRouteDeviceUi(
    val id: Int,
    val productName: String,
    val type: Int,
    val typeLabel: String,
    val isInput: Boolean,
    val isOutput: Boolean,
    val isConnected: Boolean,
    val isCommunicationDeviceCapable: Boolean
)
```

```kotlin
data class AudioRoutingStatus(
    val inputs: List<AudioRouteDeviceUi> = emptyList(),
    val outputs: List<AudioRouteDeviceUi> = emptyList(),
    val selectedInputDeviceId: Int? = null,
    val selectedOutputDeviceId: Int? = null,
    val activeInputLabel: String? = null,
    val activeOutputLabel: String? = null,
    val lastApplyMessage: String? = null,
    val apiLevelSupportsCommunicationDevice: Boolean = false
)
```

Optional if phase 2 is implemented:

```kotlin
data class AudioRoutingApplyResult(
    val inputApplied: Boolean,
    val outputApplied: Boolean,
    val communicationDeviceApplied: Boolean,
    val message: String
)
```

### UserSettings Extensions

Extend [UserSettings.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/data/classes/UserSettings.kt):

- `preferredAudioInputDeviceId: Int? = null`
- `preferredAudioOutputDeviceId: Int? = null`
- `allowAppAudioCapture: Boolean = false`
- `preferCommunicationDeviceRouting: Boolean = true`

Rationale:

- persist ids, not device names
- names can change
- ids map more directly to Android routing APIs
- null means `System default`

### SettingsManager Extensions

Extend [SettingsManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/SettingsManager.kt) with preference keys:

- `preferred_audio_input_device_id`
- `preferred_audio_output_device_id`
- `allow_app_audio_capture`
- `prefer_communication_device_routing`

And add corresponding read/write mapping in:

- `preferencesToUserSettings(...)`
- `writeUserSettings(...)`

## Architecture

### New Repository / Manager

Create a dedicated component rather than placing `AudioManager` logic inside `SettingsScreen`.

Suggested file:

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/audio/AudioRoutingRepository.kt`

Suggested responsibilities:

- enumerate audio inputs via `AudioManager.getDevices(GET_DEVICES_INPUTS)`
- enumerate audio outputs via `AudioManager.getDevices(GET_DEVICES_OUTPUTS)`
- map `AudioDeviceInfo` to `AudioRouteDeviceUi`
- expose current active route snapshot
- later apply routing preferences

Suggested API:

```kotlin
interface AudioRoutingRepository {
    fun getInputDevices(): List<AudioRouteDeviceUi>
    fun getOutputDevices(): List<AudioRouteDeviceUi>
    fun getStatus(
        selectedInputDeviceId: Int?,
        selectedOutputDeviceId: Int?
    ): AudioRoutingStatus
}
```

For phase 2:

```kotlin
fun applyPreferences(
    record: AudioRecord?,
    track: AudioTrack?,
    selectedInputDeviceId: Int?,
    selectedOutputDeviceId: Int?,
    preferCommunicationDeviceRouting: Boolean
): AudioRoutingApplyResult
```

### ViewModel Integration

Minimal-change path:

- keep persistence updates in `HomeViewModel`
- expose a `StateFlow<AudioRoutingStatus>` from `HomeViewModel`

Cleaner path:

- create a dedicated `AudioRoutingViewModel`
- use it only in `SettingsScreen`

Recommendation:

- Phase 1 can stay in `HomeViewModel`
- Phase 2 should move to a dedicated audio routing manager/repository if runtime logic grows

## SettingsScreen UI Plan

Add a new section below existing model/account settings:

### Section Title

- `Audio Routing`

### Block A: Input Devices

Purpose:

- show detected microphone-capable devices
- let user choose preferred recording device

UI:

- read-only status text: `Detected inputs: N`
- dropdown or radio-list
- first option: `System default`
- each device row shows:
  - product name
  - type label
  - optional tags such as `Bluetooth SCO`, `Built-in mic`, `USB`

### Block B: Output Devices

Purpose:

- show detected playback-capable devices
- let user choose preferred playback device

UI:

- read-only status text: `Detected outputs: N`
- dropdown or radio-list
- first option: `System default`
- each device row shows:
  - product name
  - type label
  - optional tags such as `Speaker`, `Earpiece`, `Bluetooth`, `Wired`

### Block C: Routing Behavior

Controls:

- `Allow app audio capture`
- `Prefer communication-device routing on Android 13+`

Read-only state:

- `Selected input: ...`
- `Selected output: ...`
- `Active input: ...`
- `Active output: ...`
- `Last routing result: ...`

## Implementation Status Update

Based on the current codebase, Phase 1 is partially implemented already.

Implemented:

- `SettingsScreen` now includes an audio routing section with:
  - detected input/output counts
  - preferred input/output selection
  - `allowAppAudioCapture`
  - `preferCommunicationDeviceRouting`
  - selected/active route summary text
- `UserSettings` persists:
  - `preferredAudioInputDeviceId`
  - `preferredAudioOutputDeviceId`
  - `allowAppAudioCapture`
  - `preferCommunicationDeviceRouting`
- `SettingsManager` reads and writes those fields
- `AudioRoutingRepository` exists and currently:
  - enumerates inputs and outputs
  - maps `AudioDeviceInfo` to `AudioRouteDeviceUi`
  - reports selected labels
  - reports active communication-device output when available
- `HomeViewModel` exposes `audioRoutingStatus` and update methods for the new settings

Still missing:

- runtime application of preferred input/output devices
- `activeInputLabel` in `AudioRoutingStatus`
- meaningful `lastApplyMessage`
- capture-policy wiring for playback/TTS paths

## Current Audio I/O Inventory

The current codebase has multiple independent audio entry points.

### Microphone Input

- `RecognitionManager`
- `TranslateScreen`
- `SpeakerAsrController`

Each currently creates its own `AudioRecord` directly.

### Playback Output

- `MediaController`

This currently wraps `MediaPlayer` for wav playback and is used from multiple screens.

### TTS Output

- `Home`
- `TranslateScreen`
- `DataCollectScreen`
- `EditRecognitionDialog`
- `SpeakerPlaybackController`

Each currently owns its own `TextToSpeech` lifecycle.

### Routing State

- `AudioRoutingRepository`

This currently handles enumeration and route-state inspection only.

## Recommended Next Architecture

Do not collapse everything into one large god object.

Recommended structure:

- `AudioRoutingRepository`
  - audio device enumeration
  - active route inspection
  - preferred-route apply helpers
- `AudioIOManager`
  - orchestration / coordination layer
  - shared policy and conflict handling
- `AudioInputController`
  - `AudioRecord` creation
  - preferred mic routing
  - capture lifecycle helpers
- `AudioOutputController`
  - wav/media playback
  - preferred output routing
  - playback capture policy
- `SpeechOutputController`
  - shared `TextToSpeech` lifecycle
  - speech output state/callback handling

Rationale:

- mic capture, wav playback, and TTS are all audio I/O, but they have different lifecycle and callback semantics
- `AudioIOManager` should coordinate them, not absorb every implementation detail
- this keeps routing reusable and prevents a single oversized manager

## Next-Step Plan

### Step 1: Unify Microphone Capture First

Goal:

- remove duplicated `AudioRecord` setup across the codebase
- make preferred input routing actually take effect

Scope:

- centralize `AudioRecord` creation
- apply preferred input device during capture startup
- reuse the same capture helper in:
  - `RecognitionManager`
  - `TranslateScreen`
  - `SpeakerAsrController`
- expose apply result and active-input information back to UI/logging

Why first:

- this is where routing preference is most actionable today
- this area has the most duplication
- it delivers real user-visible value with limited architectural risk

### Step 2: Refactor Wav Playback

Goal:

- evolve `MediaController` into a routed playback controller

Scope:

- move playback logic behind a dedicated controller
- apply preferred output routing where Android APIs allow it
- prepare for `allowAppAudioCapture` policy wiring

### Step 3: Consolidate TTS

Goal:

- remove repeated `TextToSpeech` setup/shutdown logic

Scope:

- centralize `TextToSpeech` lifecycle management
- preserve screen-specific behaviors and callbacks
- coordinate TTS vs recording/playback interactions through `AudioIOManager`

## Delivery Strategy

Implement in phases and validate after each phase:

1. compile
2. targeted tests
3. manual route-behavior verification

Do not combine mic refactor, playback refactor, and TTS consolidation in a single change.

### Block D: Constraints / Notes

Short helper text in UI:

- preferred device is not guaranteed to become active
- Bluetooth microphone usually switches to SCO mode
- SCO may reduce sample rate to 8 kHz or 16 kHz
- non-symmetric routing reliability depends on Android version and device vendor behavior

## Android API Strategy

### Device Enumeration

Use:

- `AudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)`
- `AudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)`

Map common device types to user-facing labels:

- `TYPE_BUILTIN_MIC` -> `Built-in mic`
- `TYPE_BUILTIN_SPEAKER` -> `Phone speaker`
- `TYPE_WIRED_HEADSET` -> `Wired headset`
- `TYPE_BLUETOOTH_SCO` -> `Bluetooth SCO`
- `TYPE_BLUETOOTH_A2DP` -> `Bluetooth A2DP`
- `TYPE_USB_DEVICE` / `TYPE_USB_HEADSET` -> `USB audio`

### Input Apply Strategy

When phase 2 starts:

- locate selected input `AudioDeviceInfo`
- call `AudioRecord.setPreferredDevice(device)`

Target integration points likely include:

- [RecognitionManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/RecognitionManager.kt)
- [SpeakerAsrController.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerAsrController.kt)

### Output Apply Strategy

When phase 2 starts:

- if app owns an `AudioTrack`, call `setPreferredDevice(device)`
- if using communication-style output and API level is high enough, try:
  - `AudioManager.setCommunicationDevice(device)`

This should be guarded by:

- API level check
- device capability check
- explicit user setting `preferCommunicationDeviceRouting`

### Capture Policy Strategy

If app audio capture policy is exposed in settings:

- use `AudioAttributes.Builder`
- apply `setAllowedCapturePolicy(ALLOW_CAPTURE_BY_ALL)` where playback streams are created

Do not promise that this solves microphone sharing by itself. It only controls whether app output may be captured by other apps such as system screen recording.

## Failure Handling

The settings page should never imply hard guarantees. Runtime routing can fail because:

- selected device is disconnected
- Android ignores preferred route
- Bluetooth profile changed
- communication route is reserved by another mode
- selected output is not valid for communication routing

Recommended behavior:

- fall back to system default
- log failure reason
- surface short status text in settings

## Logging Plan

Add structured logs when this feature is implemented:

- `Audio routing inputs detected count=...`
- `Audio routing outputs detected count=...`
- `Audio routing preference updated inputId=... outputId=...`
- `Audio routing apply start inputId=... outputId=...`
- `Audio routing apply result inputApplied=... outputApplied=... communicationApplied=... message=...`

## Test Plan

### Unit Tests

Add unit tests for:

- device type to label mapping
- persistence mapping in `SettingsManager`
- fallback when selected device id does not exist

### Manual Device Matrix

At minimum test:

1. Phone mic + phone speaker
2. Bluetooth buds connected
3. Wired headset connected
4. Bluetooth input preferred + phone speaker preferred
5. Device disconnect after preference is saved

### Acceptance Criteria For Phase 1

- Settings page shows detected input devices
- Settings page shows detected output devices
- User can save preferred input/output
- Reopening app restores saved choices
- Missing devices degrade to `System default` display without crash

### Acceptance Criteria For Phase 2

- Recording path attempts preferred input device
- Playback path attempts preferred output device
- UI shows active route / apply result
- Unsupported routes fail safely and visibly

## File Change Plan

Phase 1 likely touches:

- [SettingsScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SettingsScreen.kt)
- [UserSettings.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/data/classes/UserSettings.kt)
- [SettingsManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/SettingsManager.kt)
- [HomeViewModel.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/HomeViewModel.kt)
- new `audio/AudioRoutingRepository.kt`
- `strings.xml`
- `values-zh-rTW/strings.xml`

Phase 2 likely also touches:

- [RecognitionManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/RecognitionManager.kt)
- [SpeakerAsrController.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerAsrController.kt)
- speaker playback / TTS integration points

## Recommended Next Step

Implement Phase 1 only:

1. add persisted fields to `UserSettings`
2. add `AudioRoutingRepository`
3. show detected input/output devices in `SettingsScreen`
4. allow saving preferred input/output
5. show helper text and current selection

This gets the app to a debuggable state on real devices before attempting non-symmetric runtime routing.
