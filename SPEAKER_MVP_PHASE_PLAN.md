# Speaker MVP Phase Plan

## Goal
Build a usable `Speaker` experience that lets the user manage app-owned speech folders, import multiple `txt` files, read and edit content, and play text through local TTS.

The MVP starts with manual controls. ASR is explicitly deferred until the manual document-management and playback flow is stable.

## Current Product Direction

### Source of Truth
- Do not use arbitrary Android local folders as the primary runtime source.
- Store all speaker content under:
  - `filesDir/speech/<userId>/`
- Allow external content to be imported into that app-owned structure.

### Current Import Model
- Create folders inside app storage.
- Import multiple `txt` files into a target folder.
- Google Drive is treated as an import source through the system picker.
- Import is not restricted to the same Google account as the app login.

### Current Reading / Playback Model
- Show folders and files in the upper pane.
- Show selected file content in the lower pane.
- Support:
  - full-document play
  - pause
  - stop
  - tap a specific line to immediately read that line
- Highlight the currently playing line.
- Auto-scroll the highlighted line into view.

## Updated Scope

### In Scope for MVP
- `Speaker` tab and navigation
- App-owned speech folder management
- Multi-file `txt` import
- Split-screen explorer and content view
- Manual TTS playback
- Resumable segmented playback
- Inline text editing with save / cancel
- Single-file delete
- Folder delete
- Playback safety locks during active or paused playback
- Behavior tests for key `Speaker` interactions

### Out of Scope for Current MVP
- Voice-based document selection
- Gemini API integration
- Semantic document matching
- Background media notification controls
- Full `SpeakerViewModel` / repository modularization
- Drive-native folder browsing via API

## Current User Flow
1. User opens the `Speaker` tab.
2. User creates a local folder or imports multiple `txt` files into an existing folder.
3. User expands one folder in the upper pane.
4. User selects a `txt` file.
5. The lower pane shows the file content as a clickable line list.
6. User can:
   - play the whole document
   - pause / resume
   - stop
   - tap a single line to immediately play that line
   - edit, save, or cancel edits when playback is inactive
7. The current playback line is highlighted and auto-scrolled into view.

## Current MVP Requirements

### UI Requirements
- Add a `Speaker` tab.
- Use a vertically split screen:
  - upper pane: folder and file explorer
  - lower pane: selected content and playback / edit controls
- Both panes scroll independently.
- Upper pane:
  - one-level folder structure only
  - accordion behavior
  - folder row actions:
    - refresh
    - import files
    - remove folder
  - file row actions:
    - select file
    - delete file
- Lower pane:
  - selected file name in header
  - `play / pause / stop`
  - `edit / save / cancel edit`
  - reading mode uses clickable line rows instead of one large text block
  - currently playing line is highlighted
  - highlighted line auto-scrolls into view

### Data Requirements
Each folder should support at least:
- `id`
- `displayName`
- `isExpanded`
- `documents`

Each text item should support at least:
- `id`
- `displayName`
- `fullText`

Current UI model files:
- `SpeakerDirectoryUi`
- `SpeakerDocumentUi`

### Behavior Requirements
- Only `.txt` files are loaded.
- Files are sorted by file name by default.
- Initially:
  - all folders are collapsed
  - no file is selected
- Only one folder can be expanded at a time.
- Selecting a file updates the lower content pane.
- Only one TTS playback target is active at a time.
- Starting a new playback stops the previous one.
- Tapping a line stops current TTS and immediately reads that line.
- Empty files or blank lines are not played.
- Leaving the screen stops current playback.
- While playback is active or paused:
  - edit is disabled
  - single-file delete is disabled
  - folder delete is disabled

### Error Handling
- Invalid or duplicate folder names show inline dialog errors.
- Empty text files show a playback warning instead of speaking.
- Import failure shows a failure message.
- TTS-not-ready state shows a warning message.
- Import progress is shown in a non-cancel import dialog.

## Updated Phase Plan

## Phase 1
Create the new `Speaker` tab and basic navigation shell.

Status:
- Completed

Delivered:
- `NavRoutes.Speaker`
- bottom-tab entry
- initial `SpeakerScreen` placeholder

## Phase 2
Replace the placeholder with the split-screen explorer/content layout.

Status:
- Completed

Delivered:
- upper/lower split layout
- accordion folder explorer
- lower-pane content area
- independent scrolling

## Phase 3
Move from arbitrary local-folder selection to app-owned speech folders.

Status:
- Completed

Delivered:
- app-owned root:
  - `filesDir/speech/<userId>/`
- create-folder dialog
- duplicate-name validation
- folder scanning and file listing

## Phase 4
Implement multi-file import flows.

Status:
- Completed

Delivered:
- top cloud action for multi-file import into a target folder
- per-folder upload action for multi-file import into that folder
- import progress dialog
- extracted import helper:
  - `SpeakerImportManager.kt`

## Phase 5
Implement manual TTS playback.

Status:
- Completed

Delivered:
- play / pause / stop
- segmented resumable playback
- switching playback targets correctly stops previous TTS

## Phase 6
Implement lower-pane editing and deletion controls.

Status:
- Completed

Delivered:
- edit / save / cancel edit
- save writes back to local `txt`
- cancel edit uses close icon
- single-file delete
- folder delete
- playback safety locks for destructive actions

## Phase 7
Improve reading UX in the lower pane.

Status:
- Completed

Delivered:
- clickable line list
- tap-to-read current line
- highlight current playback line
- auto-scroll highlighted line into view

## Phase 8
Stabilize with behavior tests and UI decomposition.

Status:
- Partially completed

Delivered:
- `SpeakerScreenBehaviorTest`
- `SpeakerComponentsBehaviorTest`
- split `SpeakerScreen` into:
  - `SpeechFileExplorer`
  - `SpeakerContentScreen`
  - shared `SpeakerUiModels`

Remaining:
- add behavior tests for auto-scroll / highlighted line visibility if needed
- add end-to-end tests for delete locks if they become regression-prone

## Phase 9
Refactor runtime logic out of `SpeakerScreen`.

Status:
- Not started

Target:
- move playback orchestration out of `SpeakerScreen.kt`
- move file-system operations out of `SpeakerScreen.kt`
- introduce `SpeakerViewModel` and repository boundaries

## Phase 10
Add ASR coexistence without Gemini.

Status:
- Not started

Target:
- add microphone entry point inside `Speaker`
- use local ASR to produce text
- resolve spoken target document with simple local rules
- reuse the same playback entry point as manual playback

## Phase 11
Add Gemini fallback for ambiguous ASR results.

Status:
- Not started

Target:
- only call Gemini when local matching is inconclusive
- send ASR text plus candidate file metadata
- resolve best file or return ranked candidates

## Recommended Next Order
1. Finish Phase 8 only where tests are missing for recent line-playback behavior.
2. Start Phase 9 and move playback / file logic out of `SpeakerScreen.kt`.
3. Only after that, start Phase 10 ASR integration.

## Architecture Note
The manual and future ASR-triggered flows should still converge on a shared playback entry path.

Recommended long-term flow:
- Manual document play:
  - user taps `play` or taps a line
- Future ASR document play:
  - microphone
  - local ASR text
  - target resolution
  - shared playback entry

This keeps ASR integration from rewriting the manual playback system that is already working.
