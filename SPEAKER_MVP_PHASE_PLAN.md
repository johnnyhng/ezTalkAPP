# Speaker MVP Phase Plan

## Goal
Add a new `演講者` tab that lets users select a local folder, load multiple `txt` files, and play selected text content through local TTS.

The MVP starts with button-based playback. ASR integration is deferred until the playback and file-management flow is stable.

## Product Scope

### In Scope for MVP
- Add a new `演講者` tab
- Let the user choose a local folder
- Load and display `.txt` files in that folder
- Play selected file content with local TTS
- Stop current playback
- Preserve architecture so ASR can be added later without rewriting the playback flow

### Out of Scope for Initial MVP
- Voice-based file selection
- Gemini API integration
- Semantic matching of spoken commands to documents
- Background audio playback controls
- Sentence-level playback progress

## User Flow
1. User opens the `演講者` tab.
2. User taps `選擇資料夾`.
3. The app opens the Android folder picker.
4. The app reads `.txt` files from the selected folder.
5. The app shows a list of available text files.
6. The user taps `播放` on one file.
7. The app reads the file content and plays it through local TTS.
8. The user can stop playback or play another file.

## MVP Requirements

### UI Requirements
- Add a new tab named `演講者`
- Use a vertically split screen:
  - upper pane for folder and text-file overview
  - lower pane for selected text content and playback controls
- Both upper and lower panes should support vertical scrolling independently
- Show folder rows with:
  - expand and collapse action using `+` or `-`
  - folder name
  - trailing actions for refresh and remove folder binding
- Only support one directory level in the overview tree
- Show expanded `.txt` file rows under each folder
- Highlight the currently selected text file
- Show a playback control area with:
  - selected file name
  - `播放`
  - `暫停`
  - `停止`
- Show full selected text content in the lower pane
- Reserve space for a future microphone action, but keep it hidden or disabled in MVP

### Data Requirements
Each folder should support at least:
- `id`
- `displayName`
- `uri`
- `isExpanded`
- `documents`

Each text item should support at least:
- `id`
- `directoryId`
- `displayName`
- `uri`
- `previewText`
- `fullText`
- `lastModified` optional

Suggested model names:
- `SpeakerDirectory`
- `SpeakerDocument`
- `SpeakerUiState`

### Behavior Requirements
- Only `.txt` files are loaded
- Files are sorted by file name by default
- Folder rows can be expanded or collapsed
- Selecting a file updates the lower content pane
- Only one file can be played at a time
- Starting a new playback stops the previous one
- Empty files should not be played
- Leaving the screen should stop current playback
- Folder access permission should be persisted when possible

### Error Handling
- If folder permission is lost, ask the user to reselect the folder
- If file reading fails, show an error message for that file
- If TTS initialization fails, show an error state
- If no `.txt` files exist, show an empty state

## Phase Plan

## Phase 1
Build the new `演講者` tab and wire navigation only.

Scope:
- Add `NavRoutes.Speaker`
- Add bottom-tab item for `演講者`
- Add `SpeakerScreen` placeholder page
- Show basic title and placeholder content

Done when:
- User can open the `演講者` page
- Existing tabs continue to work unchanged

## Phase 2
Build the split-screen overview UI and connect folder selection and `.txt` file loading.

Scope:
- Replace the placeholder with the split-screen layout
- Add upper-pane folder overview with expand and collapse behavior
- Add lower-pane content viewer and playback control placeholders
- Use Android SAF folder picker
- Persist folder `Uri` permission
- Scan `.txt` files in selected folder
- Show folder rows and expanded text-file rows with file name and preview
- Add empty-state and read-error handling

Done when:
- User can select a local folder
- The app can expand and collapse folders in the upper pane
- The app can select a text file and show its content in the lower pane
- Re-entering the screen can reload the last selected folder

## Phase 3
Add manual TTS playback with buttons.

Scope:
- Add `播放 / 暫停 / 停止` controls to the lower pane
- Ensure only one file plays at a time
- Stop previous playback before playing a new file
- Show which file is currently playing

Done when:
- Tapping `播放` reads the selected text file aloud
- Tapping `暫停` pauses when supported by the chosen TTS approach, or is kept as a disabled placeholder until implemented
- Tapping `停止` stops playback immediately
- Switching to another file behaves correctly

## Phase 4
Refactor state and playback flow so ASR can be added later cleanly.

Scope:
- Add `SpeakerViewModel`
- Add `SpeakerDocument`
- Add repository or file-loader abstraction
- Centralize playback through `playDocument(document)`
- Reserve target-resolution interface:
  - `resolveTargetDocument(commandText: String, documents: List<SpeakerDocument>): SpeakerDocument?`

Done when:
- Manual playback flow is modular
- File selection logic and playback logic are separated

## Phase 5
Let ASR and manual playback coexist without Gemini yet.

Scope:
- Add microphone entry point
- Use local ASR to produce text
- Match ASR text to documents with simple local rules:
  - file name contains spoken text
  - basic similarity against file name
  - preview or title-line matching
- If matched, reuse `playDocument(document)`

Done when:
- The same screen supports both button playback and voice-triggered playback
- ASR adds a new entry path without changing the existing playback pipeline

## Phase 6
Add Gemini as a fallback disambiguation layer.

Scope:
- Only call Gemini when local matching is inconclusive
- Send:
  - ASR text
  - candidate file names
  - preview text
- Receive:
  - best target file
  - or ranked candidates
- If confidence is low, show a candidate list for user confirmation

Done when:
- ASR control is more resilient to imperfect recognition
- Gemini acts as fallback rather than the primary control path

## Recommended Implementation Order
Start with:
1. Phase 1
2. Phase 2
3. Phase 3

This gets a usable first version into the app quickly. After that, continue with Phase 4 before adding ASR.

## Architecture Note
Manual playback and future ASR-triggered playback should use the same playback entry point.

Recommended flow:
- Manual: user taps `播放` -> `playDocument(document)`
- Future ASR: microphone -> local ASR text -> `resolveTargetDocument(...)` -> `playDocument(document)`

This separation reduces rework when ASR and Gemini are added later.
