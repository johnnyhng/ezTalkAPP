# How This App Works

This document explains the current structure and runtime flow of `ezTalkAPP` from a developer perspective.

## What the app does

`ezTalkAPP` is an Android app for speech recognition and speech data handling. It combines:

- local offline ASR using Sherpa-ONNX
- VAD-based utterance segmentation
- optional remote candidate generation
- optional LLM correction for Home and Translate
- optional Home Chinese-to-English translation
- optional backend feedback/upload
- local WAV + JSONL persistence per user

At a high level, the app records microphone input, detects speech boundaries, runs local recognition, saves the utterance to disk, and then lets the user review / play / speak / upload the result.

## Entry point and navigation

The app starts in [MainActivity.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/MainActivity.kt).

Main responsibilities there:

- request microphone permission on startup
- initialize VAD once in the background with `SimulateStreamingAsr.initVad(...)`
- render the Compose app shell
- host the bottom navigation and screen routing

The main navigation destinations are:

- `Home`
- `Translate`
- `DataCollect`
- `Speaker`
- `FileManager`
- `Settings`
- `Help`

## Core runtime pieces

There are four core layers:

1. UI screens
2. view models / managers
3. speech / storage / network utilities
4. persistence on disk and in DataStore

### 1. UI screens

The main screens live under [app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens).

Their roles are:

- [Home.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/Home.kt)
  - main list-based recognition workflow
  - subscribes to partial/final transcripts from `HomeViewModel`
  - fetches remote candidates in the background
  - runs optional background LLM correction without blocking the next recording
  - runs optional background English translation and English TTS
  - handles TTS confirmation, editing, playback, deletion, and backend feedback
- [TranslateScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/TranslateScreen.kt)
  - single-utterance editing flow
  - focuses on one active transcript at a time
  - fetches local and remote candidates for that transcript
  - runs optional LLM correction after initial recognition and after utterance variant refresh
- [DataCollectScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/DataCollectScreen.kt)
  - recording flow for a prescribed text prompt
  - supports sequence mode, retry, skip, and auto-advance
- [FileManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/FileManager.kt)
  - loads saved `.wav` / `.jsonl` pairs from disk
  - allows editing, playback, selection, deletion, and feedback upload
- [SpeakerScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/ui/speaker/SpeakerScreen.kt)
  - manages local speaker txt folders under `filesDir/speech/<userId>/`
  - supports create/import/rename/delete for folders
  - supports rename/delete/edit/playback for txt files
  - supports Firebase-backed cloud upload/import/delete for speaker folders
- [SettingsScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SettingsScreen.kt)
  - edits persisted user settings such as backend URL, model selection, timing, and TTS feedback mode

### 2. View models and managers

The main state/control classes live under [app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers).

- [HomeViewModel.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/HomeViewModel.kt)
  - the main coordinator for shared app state
  - exposes user settings, selected model, model loading state
  - bridges UI to `RecognitionManager`
  - emits `partialText` and `finalTranscript`
  - owns remote model dialog state, remote model download progress, and remote model update checks
- [RecognitionManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/RecognitionManager.kt)
  - owns microphone recording and speech segmentation
  - runs partial and final recognition
  - saves WAV and JSONL
  - returns final `Transcript` objects back to `HomeViewModel`
- [DataCollectViewModel.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/DataCollectViewModel.kt)
  - manages queue/sequence mode for prompt-based collection
  - supports import, next, previous, retry, skip, and persistence of queue state
- [SettingsManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/SettingsManager.kt)
  - persists `UserSettings` in Android DataStore
- [ModelManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/ModelManager.kt)
  - lists available local models per user
  - copies a default model from assets if needed
  - deletes models
- [RemoteModelRepository.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/RemoteModelRepository.kt)
  - lists remote models from backend
  - downloads selected model files into the local per-user model directory
- [SpeakerViewModel.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerViewModel.kt)
  - owns local speaker explorer state
  - owns local folder / txt rename dialogs and progress state
  - coordinates Firebase Auth state and Firestore speaker sync state

### 3. Utilities

The key utility files live under [app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils).

- [WavUtil.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/WavUtil.kt)
  - writes audio samples to `.wav`
  - writes metadata to `.jsonl`
  - reads `.jsonl`
  - deletes transcript file pairs
- [RecognitionUtils.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/RecognitionUtils.kt)
  - fetches remote candidates
  - caches them into the same `.jsonl`
  - merges remote candidates into `utteranceVariants` when that setting is enabled
- [Api.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/Api.kt)
  - upload / feedback / recognition HTTP calls
  - decides whether feedback uses `PUT /updates`, `POST /process_audio`, or `POST /transfer`
  - also implements remote model list / check-update / download helpers
- [BackendEndpoints.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/BackendEndpoints.kt)
  - centralizes API endpoint construction from a single `backendUrl`
- [MediaController.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/MediaController.kt)
  - centralized audio playback for saved WAVs
- [Utils.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/Utils.kt)
  - queue state persistence helpers for data collection
- [GoogleSignInManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/auth/GoogleSignInManager.kt)
  - exchanges the existing Google sign-in flow into Firebase Auth
  - keeps Firebase sign-in aligned with app sign-in state

### 4. Speech engine wrapper

[SimulateStreamingAsr.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/SimulateStreamingAsr.kt) is the app-level wrapper around Sherpa-ONNX.

It owns:

- the global offline recognizer instance
- the global VAD instance
- thread-safe wrappers around VAD reset / accept / pop

The recognizer is re-initialized when the selected model changes. VAD is initialized once and reused.

## Data model

The central runtime model is [Transcript.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/data/classes/Transcript.kt).

Important fields:

- `recognizedText`: original local ASR result
- `modifiedText`: current user-facing text after edits / confirmation
- `wavFilePath`: saved WAV path
- `checked`: whether the item has been reviewed / confirmed
- `mutable`: whether it is still editable
- `removable`: whether it has already gone through feedback flow
- `utteranceVariants`: distinct ASR variants collected during the utterance and optionally enriched with backend candidates
- `englishTranslation`: optional Home English translation for the current `modifiedText`
- `localCandidates`: optional locally re-recognized alternatives cached from saved audio
- `remoteCandidates`: optional remote alternatives

The central persisted settings model is [UserSettings.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/data/classes/UserSettings.kt).

Important settings include:

- `userId`
- `lingerMs`
- `partialIntervalMs`
- `saveVadSegmentsOnly`
- `inlineEdit`
- `backendUrl`
- `enableTtsFeedback`
- `selectedModelName`
- `enableHomeLlmCorrection`
- `enableTranslateLlmCorrection`
- `includeRemoteCandidatesInUtteranceVariants`
- `enableHomeEnglishTranslation`
- `geminiModel`

Current behavior:

- `backendUrl` is the single user-entered API base
- the app uses it as-is, only trimming whitespace and a trailing slash
- `effectiveRecognitionUrl` is derived from `backendUrl` as `backendUrl + /process_audio`
- model list / model download / feedback routes all use the same `backendUrl`

## Speaker feature flow

The Speaker feature is a separate text-based playback and practice workflow.

Core files:

- [SpeakerScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/ui/speaker/SpeakerScreen.kt)
- [SpeechFileExplorer.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/ui/speaker/SpeechFileExplorer.kt)
- [SpeakerLocalRepository.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerLocalRepository.kt)
- [SpeakerCloudRepository.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerCloudRepository.kt)
- [SpeakerSyncService.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerSyncService.kt)
- [SpeakerViewModel.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerViewModel.kt)

### Local speaker storage

Speaker text files are stored under:

```text
filesDir/
  speech/
    <localUserId>/
      <folderName>/
        <fileName>.txt
```

Current local operations:

- create local folder
- rename local folder
- delete local folder
- import one or more txt files into a folder
- rename local txt file
- delete local txt file
- edit txt content in place

### Speaker cloud sync

Speaker cloud sync uses Firebase Auth plus Cloud Firestore.

Current remote structure:

```text
users/{firebaseUid}/speakerFolders/{folderName}
users/{firebaseUid}/speakerFolders/{folderName}/documents/{fileName}
```

Each remote document stores:

- file name
- full txt content
- content hash
- size in bytes
- update timestamp

The app does not use Firebase Storage for speaker sync. txt content is stored directly in Firestore documents.

### Speaker cloud auth behavior

- cloud actions are only shown when Firebase Auth is signed in
- the header still shows cloud signed-in / signed-out status
- UI does not expose Firebase UID or token to the user
- Firestore isolation is based on the signed-in Firebase UID

### Speaker explorer UI behavior

Header actions:

- create folder
- import local files using Android file picker
- import from cloud when signed in

Per-folder actions:

- refresh
- import txt into the folder
- upload the folder to cloud when signed in
- rename local folder
- delete local folder

Per-document actions:

- select file
- rename local txt file
- delete local txt file

### Speaker cloud sync behavior

Upload flow:

1. user taps cloud upload on a local folder
2. app shows an overwrite confirmation dialog
3. app writes the folder and its txt files into Firestore under the current Firebase UID
4. app shows sync progress
5. app shows success/failure toast

Import flow:

1. user opens cloud import
2. app lists remote folders from Firestore
3. user can select one or more folders
4. app checks whether selected remote txt paths already exist locally
5. if there are conflicts, app asks whether local txt files should be overwritten
6. import writes remote txt content back into the local speaker folder tree

Remote delete flow:

1. user opens cloud import
2. each remote folder row has a delete action
3. app asks for confirmation
4. app deletes the remote folder document and its nested document collection from Firestore

Current limitations:

- no remote folder rename
- no remote diff preview beyond overwrite confirmation
- no cancellation of an already-running cloud job after the dialog is dismissed

## Main recognition flow

The most important end-to-end path is:

1. user starts recording
2. microphone audio is read continuously
3. VAD detects speech boundaries
4. partial recognition updates the UI while the user is speaking
5. once silence is long enough, the utterance is finalized
6. audio is saved as WAV
7. transcript metadata is saved as JSONL
8. UI receives a final `Transcript`
9. optional remote candidates are fetched
10. user can review, edit, speak, play, delete, or upload feedback

### Detailed recording path

The recording engine is in [RecognitionManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/RecognitionManager.kt).

What happens there:

- `startTranslate(...)` and `startDataCollect(...)` both call a private `start(...)`
- an `AudioRecord` instance is configured for 16 kHz mono PCM
- raw microphone samples are read into a channel
- a processing coroutine feeds the samples into VAD
- once speech is active, periodic partial decoding produces live transcript updates
- each non-blank partial/final result is added to an utterance variant buffer
- once silence exceeds `lingerMs`, final decoding happens

For finalization:

- the utterance is decoded one more time
- `originalText` is set from the recognizer result
- `utteranceVariants` is set from the deduplicated utterance variant buffer
- `modifiedText` is:
  - the same as `originalText` in translate/home mode
  - the prompt text in data collect mode
- the app saves:
  - a `.wav`
  - a `.jsonl`
- then emits a `Transcript` via `onFinalResult`

## Home screen flow

[Home.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/Home.kt) is the most feature-rich screen.

It does all of the following:

- subscribes to `partialText` and shows live in-progress entries
- subscribes to `finalTranscript` and replaces the last temporary item with a persisted one
- uses a background `recognitionQueue` to fetch remote candidates after an utterance is saved
- runs optional LLM correction and optional English translation as background jobs
- supports inline editing and dialog editing
- supports TTS confirmation
- supports backend feedback
- supports WAV playback and transcript deletion

### Home candidate flow

When a final transcript arrives:

- it is inserted into `resultList`
- if `effectiveRecognitionUrl` is configured, its `wavFilePath` is pushed into `recognitionQueue`
- if `enableHomeLlmCorrection` is enabled, a background correction job starts using the current `utteranceVariants`
- if `enableHomeEnglishTranslation` is enabled, a background translation job starts from the current `modifiedText`
- a background worker calls `getRemoteCandidates(...)`
- remote candidates are written into the item and also stored into the `.jsonl`
- when `includeRemoteCandidatesInUtteranceVariants` is enabled, remote candidates are merged into `utteranceVariants`
- if the merge changes `utteranceVariants`, Home queues another LLM correction pass with the richer variant set

When the user opens inline edit for a saved item:

- the app may re-run local recognition on the saved WAV
- the local result is written into `localCandidates`
- `local_candidates` is cached into the same `.jsonl`
- later saves preserve both `local_candidates` and `remote_candidates`

### Home LLM correction and English translation

Home LLM correction is intentionally asynchronous:

- the UI shows a linear progress indicator under the row while correction is running
- correction does not block countdown completion, the next recording, or real-time local ASR
- correction updates `modifiedText` only if the transcript is still mutable and the text has not changed since the job was launched
- if correction changes `modifiedText`, `englishTranslation` is cleared and translation is re-run when Home English translation is enabled
- every persisted update logs `utteranceVariants` count and content so logs can verify which variants were used

Home English translation is also asynchronous:

- it is controlled by `enableHomeEnglishTranslation`
- it stores output in `english_translation`
- it uses a separate English TTS controller
- it is invalidated and refreshed when `modifiedText` changes through correction, editing, dialog confirmation, or TTS confirmation

### Remote model flow

Remote model management is driven by `SettingsScreen` + `RemoteModelsManager` through `HomeViewModel`.

The current runtime path is:

1. user opens the remote model dialog from Settings
2. `HomeViewModel` calls `RemoteModelRepository.listRemoteModels(...)` on `Dispatchers.IO`
3. backend model list is fetched from:
   - `GET <backendUrl>/list_models/<user_id>`
4. the response body is expected to look like:
   - `{"models":["mobile", ...]}`
5. each returned model name is shown in `RemoteModelsManager`
6. when the dialog contains `mobile` and local `files/models/<userId>/mobile/model.int8.onnx` exists:
   - the app computes local file SHA-256
   - calls `GET <backendUrl>/check_update/<user_id>`
   - compares local hash with backend `server_hash`
   - if they differ, the `mobile` row is marked with an update-available icon
7. when the user downloads a selected remote model:
   - the app downloads `model.int8.onnx`
   - then downloads `tokens.txt`
   - both are stored under `files/models/<userId>/<modelName>/`

This means the remote model dialog is now backed by the backend list API rather than local placeholder data.

### Home TTS / feedback flow

Home has a dedicated TTS confirmation flow:

- pressing the row TTS button speaks `modifiedText`
- if `enableTtsFeedback` is on:
  - the app may call backend feedback
  - success locks the item with:
    - `checked = true`
    - `mutable = false`
    - `removable = true`
  - the latest state is written back to JSONL
- if the item is already `removable = true`:
  - TTS is still allowed
  - backend feedback is skipped

The edit dialog used by Home also routes its TTS-confirm behavior back into Home so that the same locking/feedback rules apply there.

## Translate screen flow

[TranslateScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/TranslateScreen.kt) is similar in purpose to Home, but centered around one current transcript instead of a list.

It:

- performs recording directly in the screen instead of via `RecognitionManager`
- stores one active `currentTranscript`
- finalizes one `.wav` / `.jsonl` only when the user presses stop
- fetches local and remote candidates when the active transcript changes
- lets the user edit the text field directly
- supports TTS confirmation and backend feedback

For saved utterances in this screen:

- VAD is used to trim the final saved audio range, but it does not auto-finalize on its own
- unlike Home / `RecognitionManager`, there is no linger/countdown-driven utterance finalization loop
- the initial local ASR text is also stored into `localCandidates`
- later whole-file local re-recognition reuses or refreshes `local_candidates`
- both local and remote candidates are preserved when the transcript is saved back to JSONL
- remote candidates may be merged into `utteranceVariants` when `includeRemoteCandidatesInUtteranceVariants` is enabled
- when `enableTranslateLlmCorrection` is enabled, correction first runs on the initial variants and can run again after candidate refresh changes `utteranceVariants`
- the post-refresh correction is guarded by the current text, so it does not overwrite user edits made while candidate loading is in flight
- when `enableTtsFeedback` is on, TTS feedback waits for the in-flight candidate fetch job before calling backend feedback, to avoid racing against remote-candidate persistence

This screen is useful when the user wants to focus on one utterance rather than a running transcript list.

## Data collect flow

[DataCollectScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/DataCollectScreen.kt) is for prompt-based recording.

Important differences from Home:

- the transcript text is driven by a prompt queue
- final `modifiedText` is the prompt text, not the recognizer output
- items are created as already checked and not mutable
- TTS feedback / backend candidate logic is not part of this screen
- although recognition still runs to segment/finalize the utterance, `local_candidates` are not stored for data collect items

[DataCollectViewModel.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/DataCollectViewModel.kt) handles:

- importing prompt files
- queue state
- previous/next navigation
- retry / skip support
- persisted queue restoration per user

## File manager flow

[FileManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/FileManager.kt) scans saved recordings from disk.

It reads each `.jsonl` to reconstruct:

- original text
- modified text
- checked
- mutable
- removable

It lets the user:

- play WAVs
- edit metadata
- select multiple files
- delete files
- submit feedback for selected files

Its batch feedback flow treats `removable = true` as already-synced and skips another backend call.

## Storage layout

The app uses app-private storage under `context.filesDir`.

### Models

Model files live under:

- `files/models/<userId>/<modelName>/`

Expected model files include:

- `model.int8.onnx`
- `tokens.txt`

### Recordings

Recordings live under:

- `files/wavs/<userId>/<timestamp>.wav`
- `files/wavs/<userId>/<timestamp>.jsonl`

The JSONL file is functionally a single-line JSON metadata file. Despite the name, the current implementation overwrites the file each time instead of appending multiple lines.

Typical fields in the JSONL metadata:

- `original`
- `modified`
- `checked`
- `mutable`
- `removable`
- `utterance_variants` if available
- `english_translation` if available
- `local_candidates` if available
- `remote_candidates` if available

### Data collect queue state

Queue state for prompt mode is also persisted in app-private storage via helper functions in [Utils.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/Utils.kt).

## Remote candidate flow

Remote candidates are handled by [RecognitionUtils.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/RecognitionUtils.kt).

The behavior is:

1. look for sibling `.jsonl`
2. if `remote_candidates` already exists, reuse it
3. otherwise call remote recognition
4. parse `sentence_candidates`
5. re-read JSONL to avoid overwriting newer edits
6. overwrite the JSONL with the same metadata plus `remote_candidates`, while preserving existing `utterance_variants`, `english_translation`, and `local_candidates`
7. Home / Translate may merge remote candidates into `utteranceVariants` when `includeRemoteCandidatesInUtteranceVariants` is enabled

This means remote candidates are cached locally and reused across later edits or screen reloads, and they coexist with cached local candidates.

## LLM correction flow

LLM correction is implemented by:

- [TranscriptCorrectionModule.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/llm/TranscriptCorrectionModule.kt)
- [TranscriptCorrectionProviderFactory.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/llm/TranscriptCorrectionProviderFactory.kt)
- [TranscriptCorrectionPromptBuilder.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/prompt/TranscriptCorrectionPromptBuilder.kt)

The correction input is `utteranceVariants`, not just `modifiedText`. The prompt asks the model to choose or reconstruct the intended utterance from variants and to output JSON:

```json
{
  "corrected_text": "corrected text",
  "confidence": 0.0,
  "reasoning": "short reason"
}
```

Only responses with non-blank `corrected_text` and `confidence >= 0.85` are automatically applied. The prompt also instructs the model to convert Simplified Chinese to Traditional Chinese when needed.

Race-condition guardrails:

- Home correction stores the expected `modifiedText` when the job starts and applies only if the row still matches it
- Translate post-refresh correction only runs when the text field still matches the transcript text captured before candidate loading
- remote candidate refresh patches `remoteCandidates` and `utteranceVariants`, but does not directly overwrite `modifiedText`
- JSONL writes preserve existing candidate and translation metadata where possible

## Backend feedback flow

Backend calls are implemented in [Api.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/Api.kt).

The key decision point is `feedbackToBackend(...)`:

- if the item already has `remote_candidates` in JSONL:
  - use `PUT /updates`
- if there are no remote candidates but there are `local_candidates`:
  - use `POST /process_audio`
- otherwise:
  - use `POST /transfer`

For `PUT /updates`:

- the payload still uses the current `modified` text as the sentence being confirmed
- the label payload also includes `candidates`
- `candidates` is built by merging `local_candidates` and `remote_candidates`
- merge order is local first, then remote
- duplicates are removed while keeping first-seen order

For `POST /process_audio` in local-only feedback mode:

- the payload includes:
  - `login_user`
  - `filename`
  - `label`
  - `num_of_stn`
  - `raw`
  - merged `candidates` when available
- this allows backend `process_audio` proxy logic to treat the request as an already-reviewed utterance when `label != "tmp"` and `candidates` are present

For `POST /transfer`:

- the app sends the upload payload without relying on candidate metadata
- this is the fallback route when neither local nor remote candidates exist

`Api.kt` also contains:

- packaging of WAV content into JSON or multipart uploads
- remote recognition POST requests
- debug logs that show which feedback path is being used

## Settings and model lifecycle

Settings are stored in Android DataStore via [SettingsManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/SettingsManager.kt).

That includes:

- ASR timing values
- backend URL
- selected model
- TTS feedback mode

Model selection flows like this:

1. settings load from DataStore
2. `HomeViewModel` lists models for the active user
3. if no model exists, `ModelManager` copies the default model from assets
4. when the selected model changes, `HomeViewModel.ensureSelectedModelInitialized()` re-initializes the recognizer

## Playback and TTS

There are two different audio output paths:

- saved WAV playback through `MediaController`
- spoken text output through Android `TextToSpeech`

The UI generally tries to avoid recording while playback or TTS is active. This prevents mixing states such as recording while a clip is being replayed or while synthesized speech is still speaking.

## Important design choices

These are worth knowing before changing behavior:

- `RecognitionManager` centralizes the Home/DataCollect recording engine, but `TranslateScreen` still has its own recording implementation.
- JSONL files are overwritten as full snapshots, not appended.
- `local_candidates` and `remote_candidates` are both treated as cached metadata on the same transcript artifact.
- `remote_candidates` are cached in JSONL and reused.
- `mutable` and `removable` are workflow-state flags, not just UI flags.
- Home’s TTS button can both speak and trigger backend feedback depending on settings.
- FileManager, Home, and Translate all operate on the same saved `.wav` / `.jsonl` artifacts.

## If you want to modify the app

Use these starting points:

- change recognition timing or segmentation:
  - [RecognitionManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/RecognitionManager.kt)
- change Home transcript review behavior:
  - [Home.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/Home.kt)
  - [CandidateList.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/widgets/CandidateList.kt)
- change dialog editing behavior:
  - [EditRecognitionDialog.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/widgets/EditRecognitionDialog.kt)
- change remote candidate behavior:
  - [RecognitionUtils.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/RecognitionUtils.kt)
  - [Api.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/Api.kt)
- change prompt-mode behavior:
  - [DataCollectScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/DataCollectScreen.kt)
  - [DataCollectViewModel.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/DataCollectViewModel.kt)
- change model discovery or default model copy:
  - [ModelManager.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/ModelManager.kt)

## Summary

The app is structured around one shared speech engine, local per-user persistence, and several review workflows built on top of the same transcript artifacts.

If you remember only one thing, it should be this:

- recognition produces a `Transcript`
- persistence stores it as `.wav` + `.jsonl`
- UI screens mutate that transcript state
- optional remote and backend flows enrich or upload the same underlying artifact
