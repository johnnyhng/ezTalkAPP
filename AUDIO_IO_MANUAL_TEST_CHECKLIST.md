# Audio I/O Manual Test Checklist

## Goal

Use this checklist to validate the current audio stack after the Audio I/O consolidation work:

- audio routing settings
- microphone capture
- wav playback
- TTS playback
- speaker segmented playback
- routing status feedback in settings

This checklist is designed for real-device verification, not emulator-only verification.

## Test Devices

Run as many of these combinations as available:

- Android phone with only built-in mic and speaker
- phone with wired headset
- phone with Bluetooth headset / earbuds
- phone with USB audio device if available

Recommended Android versions:

- Android 12 or lower
- Android 13+

## Pre-Test Setup

- install latest debug build
- grant microphone permission
- prepare at least one short transcript/wav item for playback
- prepare one speaker document with multiple lines / sentences
- pair Bluetooth device before starting the routing tests

## 1. Settings Inventory

- Open `SettingsScreen`
- Expand the `Audio Routing` section
- Confirm detected input count is shown
- Confirm detected output count is shown
- Confirm built-in mic appears in input list
- Confirm built-in speaker appears in output list
- If headset/Bluetooth/USB is connected, confirm it appears in the correct list
- Change preferred input to `System default`, leave screen, return, confirm value persists
- Change preferred output to `System default`, leave screen, return, confirm value persists
- Toggle `Allow app audio capture`, leave screen, return, confirm value persists
- Toggle `Prefer communication-device routing`, leave screen, return, confirm value persists

Expected:

- no crash
- no empty dropdown when devices exist
- persisted values survive navigation / process recreation if tested

## 2. Built-In Mic Capture

Use:

- `Home`
- `Translate`
- `Data Collect`
- `Speaker` local ASR path

Steps:

- Set preferred input to built-in mic
- Start recording in each screen
- Speak a short sentence
- Stop recording / wait for finalization
- Open `SettingsScreen`
- Check `Last routing result`
- Check `Active input`

Expected:

- capture works in all three mic entry paths
- no init failure toast/log-only silent breakage
- `Last routing result` reflects a built-in mic request or system-default path
- `Active input` is either built-in mic or unavailable, but never stale garbage text

## 3. Alternate Input Device Capture

Repeat with each available external input:

- wired headset mic
- Bluetooth headset mic
- USB mic

Steps:

- connect device
- refresh audio devices in settings
- select the external device as preferred input
- start recording in `Home`
- repeat in `Translate`
- repeat in `Speaker`
- check transcription still completes
- return to settings and inspect `Last routing result` and `Active input`

Expected:

- app remains usable even if Android rejects the preferred route
- no crash during start/stop
- rejected routes are surfaced as routing-result text
- if Bluetooth SCO behavior changes quality/sample rate, recording still functions

## 4. Wav Playback Routing

Use:

- `Home` candidate playback
- `FileManager`
- `Data Collect`
- `Translate`

Steps:

- set preferred output to built-in speaker
- play a wav item from each screen
- confirm audio comes from expected route
- inspect `Last routing result`
- switch preferred output to Bluetooth / wired / USB output if available
- replay the same item from each screen

Expected:

- playback works from all entry points
- no stuck `currentlyPlaying` state after completion
- route request result is reflected in settings
- if Android ignores the request, playback still succeeds on fallback route

## 5. Shared TTS Lifecycle

Use:

- `Home` TTS action
- `Translate` TTS button
- `Data Collect` TTS button
- `EditRecognitionDialog` speak-and-confirm

Steps:

- trigger TTS in one screen
- navigate away and trigger TTS in another screen
- repeat rapidly across screens
- verify no duplicated overlapping engine behavior
- in `EditRecognitionDialog`, use speak-and-confirm and verify confirm action still happens after speech completes

Expected:

- no crash on repeated TTS creation/disposal scenarios
- no leaked stale speaking state
- `EditRecognitionDialog` still confirms after speech completes
- recording screens correctly treat TTS as playback-active where expected

## 6. Speaker Segmented Playback

Use:

- `Speaker` content screen with a multi-line document

Steps:

- play full document
- verify line highlighting/progression advances across segments
- pause during playback
- resume same document
- play a single line
- stop while playing
- switch documents while playing

Expected:

- segmented playback still advances correctly
- pause preserves position
- resume continues from paused segment
- stop clears playback state
- switching documents does not leave stale highlighted lines

## 7. Capture While Playback/TTS Is Active

Use:

- `Translate`
- `Home`

Steps:

- start wav playback, then try recording
- start TTS, then try recording
- in `Translate`, confirm recording loop pauses/behaves safely while playback/TTS is active

Expected:

- no crash or deadlock
- no illegal-state errors from repeated stop/start
- UI state remains consistent

## 8. App Audio Capture Policy

This is partly observable and may require system-level tooling or screen recording.

Steps:

- set `Allow app audio capture` off
- play wav audio
- if you have a capture workflow, verify app audio capture is restricted
- set `Allow app audio capture` on
- replay wav audio
- verify app audio becomes capturable where supported

Expected:

- setting changes do not break playback
- behavior difference is observable where platform tools support it

## 9. Failure / Edge Cases

- unplug headset while selected as preferred input
- disconnect Bluetooth device while selected as preferred output
- start playback with stale preferred output id
- start recording with stale preferred input id
- change routing settings while app is already open on another screen
- background and resume app during playback

Expected:

- no crash
- fallback still works
- settings status refreshes to sensible values
- `Last routing result` stays useful rather than blank or misleading

## Pass Criteria

The build is acceptable for broader rollout if:

- all audio entry points still function
- no reproducible crash appears during routing changes
- settings reflect route requests/results consistently
- TTS is stable across screens
- speaker segmented playback behavior is unchanged from user perspective

## Known Interpretation Rules

- `Selected input/output` means user preference only
- `Active input/output` means best available runtime observation
- `Last routing result` means request/fallback message, not a hard guarantee that Android honored the route
