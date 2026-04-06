# Speaker Current Status

## Current UI
- The `Speaker` screen uses a vertical split layout.
- The upper pane is now a dedicated `SpeechFileExplorer`.
- The lower pane is now a dedicated `SpeakerContentScreen`.
- Both panes scroll independently.

## Current Folder Behavior
- All folders are collapsed initially when entering the screen.
- No file is selected initially.
- The folder list uses accordion behavior:
  - only one folder can be expanded at a time
  - opening one folder collapses the others
  - tapping an expanded folder collapses it

## Current File List Behavior
- The upper pane shows only file names.
- File preview text is not shown in the list.
- Each file row supports:
  - select file
  - delete single file
- The lower pane shows the selected file content only after the user selects a file.

## Current Folder Management
- New folders are created under:
  - `filesDir/speech/<userId>/`
- Folder creation uses a popup dialog.
- If the folder name is invalid or duplicated, the dialog shows the error inline.
- Folder rows support:
  - refresh
  - import files
  - remove folder

## Current Import Behavior

### Header Cloud Button
- The top-right cloud button opens a dialog asking for the target local folder name.
- After confirmation, it opens a multi-file picker.
- The user can select multiple `txt` files at once.
- All selected files are imported into the specified local folder.
- The import source can be Google Drive or any provider shown by the system picker.
- The import is not restricted to the same Google account as the app login.

### Folder Row Import Button
- The folder-row upload button uses `arrow up`.
- It opens a multi-file picker.
- The user can select multiple `txt` files at once.
- All selected files are imported into that specific local folder.

### Import Progress
- Multi-file import shows a modal progress dialog.
- The dialog shows:
  - target folder
  - current count / total count
  - linear progress bar
- Import triggers are disabled while import is in progress.

### Import Implementation
- Multi-file import logic has been extracted from `SpeakerScreen.kt`.
- Current import helper file:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerImportManager.kt`

## Current Playback Behavior
- Playback uses local Android `TextToSpeech`.
- The lower pane supports:
  - play full document
  - pause
  - stop
  - tap a specific line to immediately read that line
- Playback is resumable:
  - text is segmented into chunks
  - pause stops at the current segment
  - play resumes from the paused segment
  - stop clears playback state
- Reading mode shows the document as a clickable line list.
- The currently playing line is highlighted.
- The highlighted line auto-scrolls into view while playback advances.

## Current Editing Behavior
- The lower pane supports:
  - edit
  - save
  - cancel edit
- Cancel edit uses a `close` icon.
- Save writes directly back to the selected local `txt` file.
- Cancel edit discards the current draft and restores the file content view.

## Current Playback Safety Rules
- While playback is active or paused:
  - edit is disabled
  - single-file delete is disabled
  - folder delete is disabled
- Tapping a line to read it stops the current TTS first, then starts reading that line.

## Current Technical Structure
- Main orchestration screen:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerScreen.kt`
- Upper pane component:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeechFileExplorer.kt`
- Lower pane component:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerContentScreen.kt`
- Shared UI models:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerUiModels.kt`
- Import helper:
  - `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerImportManager.kt`

## Recent Speaker Commits
- `c15d4566` Auto-scroll highlighted speaker line
- `02983b6e` Highlight current speaker playback line
- `cd9f09e2` Add clickable line playback in speaker content
- `18018c39` Use close icon for speaker edit cancel
- `6cd7cce1` Lock speaker edits and deletes during playback
- `a83d5793` Add single-file deletion in speaker explorer
- `12ae0e5b` Split speaker explorer and content screens
- `0bcb463a` Add speaker document editing

## Known Notes
- There is an unused untracked file in the repo:
  - `app/src/main/res/drawable/ic_google_drive.xml`
- The current header uses built-in icons:
  - create folder: folder icon
  - cloud import: pure cloud icon
  - folder import: arrow up icon

## Next Likely Improvements
- Add behavior tests for single-file delete and clickable line playback.
- Move playback logic out of `SpeakerScreen.kt`.
- Move file-system folder management out of `SpeakerScreen.kt`.
- Add ASR-driven document resolution after the current manual flow is stable.
