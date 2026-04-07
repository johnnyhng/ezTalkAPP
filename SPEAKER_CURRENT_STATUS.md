# Speaker Current Status

## Overview
- `Speaker` is now an app-owned text explorer and local TTS / local ASR workspace.
- Runtime content lives under:
  - `filesDir/speech/<userId>/`
- The screen is split into:
  - `SpeechFileExplorer`
  - `SpeakerContentScreen`

## Current Screen Layout
- The screen uses accordion-style pane layout.
- Only one major pane is expanded at a time:
  - `explorer`
  - `content`
- At least one pane is always shown.
- Initially:
  - `explorer` is shown
  - `content` is hidden
  - no file is selected

## Explorer Behavior

### Folder Behavior
- All folders are collapsed initially.
- Only one folder can be expanded at a time.
- Expanding one folder collapses the others.
- If a selected file exists and:
  - a different folder is expanded, or
  - the selected file's folder is collapsed
  then the lower content is cleared and hidden.

### File List Behavior
- Explorer shows file names only.
- File extensions are hidden in the list.
- No preview text is shown in the file list.
- File rows support:
  - select file
  - delete single file

### File Selection Behavior
- Selecting a file immediately:
  - clears previous content playback context
  - sets the selected file
  - auto-expands the `content` pane

## Content Behavior

### Visibility
- `content` is hidden until a file is selected.
- `content` is cleared and hidden when:
  - a different folder is expanded
  - the selected file's folder is collapsed
  - the selected file disappears

### Content Display
- Reading mode uses clickable line rows.
- Each line is individually tappable.
- Blank lines are not playable.
- The current playing line is highlighted.
- The highlighted line auto-scrolls into view during playback.

### File Navigation
- `content` header supports:
  - previous file
  - next file
- Switching to previous / next file:
  - stops current TTS first
  - updates selection
  - keeps the user in `content`

### Editing
- `content` supports:
  - edit
  - save
  - cancel edit
- Save writes directly back to the selected local `txt`.
- Cancel edit restores the current file content.
- Cancel edit uses a close icon.

## Folder Management
- New folders are created with a popup dialog.
- Folder names are validated inline.
- Duplicate folder names are rejected inline in the dialog.
- Folder row actions support:
  - refresh
  - import files
  - remove folder

## Import Behavior

### Cloud Import
- Top cloud action opens a target-folder dialog.
- After confirmation, it opens multi-file selection.
- Multiple `txt` files can be imported at once.
- Google Drive or any system picker provider can be used.
- Import is not restricted to the same Google account as app login.

### Per-Folder Import
- Folder row upload action uses the `arrow up` icon.
- It supports multi-file `txt` import into that folder.

### Import Progress
- Import shows a modal progress dialog.
- The dialog shows:
  - target folder
  - current count / total count
  - linear progress bar
- Import actions are disabled while importing.

### Import Implementation
- Multi-file import logic lives in:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerImportManager.kt`

## TTS Behavior

### Playback
- Playback uses local Android `TextToSpeech`.
- Supports:
  - play full document
  - pause
  - stop
  - tap a specific line to play only that line

### Playback Model
- Full-document playback is segmented.
- Pause is resumable from the current segment.
- Stop clears the playback state.
- Starting a different playback target stops the current one first.

### Playback Safety
- While playback is active or paused:
  - edit is disabled
  - single-file delete is disabled
  - folder delete is disabled
- Tapping a line stops the current TTS first, then starts line playback.

## Local ASR Behavior

### Widget Placement
- `LocalASRWidget` exists in both panes.
- Explorer widget is shown only when at least one folder is expanded.
- Content widget is shown only when:
  - a file is selected
  - not in edit mode

### Local ASR Runtime
- `Speaker` now uses a dedicated local-only ASR flow.
- It no longer depends on `Home` transcript persistence behavior.
- It does not write:
  - `wav`
  - `jsonl`
- It does not call remote recognition.

### Recording Rules
- Only one `Speaker` ASR widget can record at a time.
- If one widget records, the other is disabled.
- If the active widget disappears, ASR stops immediately.

### ASR and TTS Mutual Exclusion
- If TTS starts, ASR is stopped immediately.
- While TTS is playing, both ASR widgets are disabled.
- ASR becomes available again after:
  - pause
  - stop
  - playback completion

### ASR Result Display
- Recording state is visible even without waveform.
- The widget shows:
  - recognizing state while waiting for text
  - live partial/final recognized text when available

### Local ASR Command Routing
- Lower-pane ASR final text supports local command routing for:
  - `播放`
  - `暫停`
  - `停止`
  - `播放第 N 行`
  - simple Chinese-number variants such as `播放第三行`
- This is local rule-based only.
- Gemini is not yet involved.

## Global Stop Rules
- TTS and ASR are stopped when:
  - selected content changes
  - selected content disappears
  - the screen is disposed
  - app goes to background (`ON_STOP`)

## Current Technical Structure
- Main orchestration:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerScreen.kt`
- Explorer pane:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeechFileExplorer.kt`
- Content pane:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerContentScreen.kt`
- Shared UI models:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerUiModels.kt`
- Import helper:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerImportManager.kt`
- TTS controller:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerPlaybackController.kt`
- Local-only ASR controller:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerAsrController.kt`
- Voice command resolver:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerCommandResolver.kt`
- ViewModel:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerViewModel.kt`

## Current Tests
- `SpeakerScreenBehaviorTest`
  - initial collapsed / no selection
  - accordion folder behavior
  - save / cancel edit behavior
- `SpeakerComponentsBehaviorTest`
  - clickable line behavior
  - edit disabled while playing / paused
  - delete disabled flags
- `SpeakerCommandResolverTest`
  - play / pause / stop resolution
  - numeric and Chinese-number line resolution

## Recent Speaker Commits
- `566fbfc5` Clear speaker content when folder collapses
- `b5a8b594` Stop speaker playback and ASR on context changes
- `47cf6cf9` Lock speaker ASR during TTS playback
- `8b1a9dd0` Add speaker document navigation
- `6e436298` Auto-open speaker content on selection
- `dd86b661` Use accordion layout for speaker panes
- `61606536` Add speaker voice command routing
- `1b0fdc45` Tune speaker local ASR responsiveness
- `16e35a96` Use local-only ASR flow in speaker
- `fb716b0b` Integrate local ASR into speaker widgets

## Known Notes
- Untracked file still present:
  - `app/src/main/res/drawable/ic_google_drive.xml`
- Current header icon usage:
  - create folder: folder icon
  - cloud import: pure cloud icon
  - folder import: arrow up icon

## Next Likely Improvements
- Add voice-based document selection from the explorer side.
- Add behavior tests for pane auto-open / auto-hide rules.
- Add behavior tests for TTS and ASR mutual exclusion.
- Decide whether command routing should support file navigation and folder actions.
- Keep Gemini as a later fallback phase, not as the primary routing path.
