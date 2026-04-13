# Audio I/O Smoke Test

## Goal

Run this in 10 minutes or less to catch obvious regressions after audio-related changes.

## Required Setup

- one real Android device
- microphone permission granted
- at least one existing wav item available for playback
- one short speaker document with multiple lines
- optional Bluetooth headset if available

## Core Checks

### 1. Settings

- Open `Settings`
- Expand `Audio Routing`
- Confirm input/output device lists are visible
- Change preferred input once
- Change preferred output once
- Toggle `Allow app audio capture`

Pass:

- no crash
- values visibly update

### 2. Home Recording

- Go to `Home`
- Start recording
- Speak one short sentence
- Stop recording
- Confirm transcript item appears

Pass:

- recording starts
- recording stops cleanly
- transcript is created

### 3. Translate Recording

- Go to `Translate`
- Start recording
- Speak one short sentence
- Stop recording / wait for final result

Pass:

- text appears during or after capture
- no stuck recording state

### 4. Wav Playback

- From `Home`, play a wav item
- From `Translate`, play the saved wav
- Stop playback manually once
- Let playback complete naturally once

Pass:

- audio is audible
- stop works
- playback state clears after completion

### 5. TTS

- Trigger TTS in `Home`
- Trigger TTS in `Translate`
- Trigger TTS in `Data Collect`

Pass:

- speech plays in each screen
- no crash moving between screens
- no permanently stuck speaking state

### 6. Speaker Playback

- Open `Speaker`
- Play a multi-line document
- Pause
- Resume
- Stop

Pass:

- segmented playback advances
- pause/resume works
- stop clears active playback state

### 7. Settings Feedback

- Return to `Settings`
- Check `Last routing result`
- Check `Active input`
- Check `Active output`

Pass:

- values are not obviously stale or empty after recent audio actions

## Optional Quick Bluetooth Check

If Bluetooth audio is available:

- connect headset
- refresh devices in `Settings`
- select Bluetooth input/output
- run one recording check
- run one playback check

Pass:

- app still works even if Android rejects the preferred route
- `Last routing result` updates

## Fail Fast Conditions

Stop and investigate immediately if any of these happen:

- app crash during record/play/TTS
- recording cannot start on any main screen
- playback never produces sound
- TTS gets stuck and blocks later audio actions
- speaker playback cannot pause/resume
- settings audio section becomes blank or unusable
