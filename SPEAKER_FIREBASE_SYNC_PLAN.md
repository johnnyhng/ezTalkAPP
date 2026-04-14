# Speaker Firebase Sync Plan

## Goal

Add Firebase-backed speaker folder sync so one device can upload local speaker folders and another device can import them back into local storage.

Primary focus:

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/ui/speaker/SpeechFileExplorer.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/ui/speaker/SpeakerScreen.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerViewModel.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerLocalRepository.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerCloudRepository.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerSyncService.kt`

## Current Implementation Status

Implemented:

1. Firebase Auth is wired through the existing Google sign-in flow.
2. Speaker cloud sync uses `Cloud Firestore` only.
3. Local runtime source remains `filesDir/speech/<localUserId>/...`.
4. Remote data is isolated by Firebase UID.
5. Folder upload is per-folder, not upload-all.
6. Cloud import supports selecting multiple remote folders.
7. Local folder rename is supported.
8. Upload shows an overwrite confirmation dialog before writing to Firestore.

Not implemented:

1. Remote folder rename.
2. Remote folder delete.
3. Upload-all from the UI.
4. Conflict policy selection beyond overwrite.
5. Background cancellation for running import/upload jobs.

## Local Storage Model

```text
filesDir/
  speech/
    <localUserId>/
      <folderName>/
        <fileName>.txt
```

The app still reads and writes speaker content locally for playback, editing, and import.

## Remote Storage Model

Remote root:

```text
users/{firebaseUid}/speakerFolders/{folderName}
users/{firebaseUid}/speakerFolders/{folderName}/documents/{fileName}
```

Folder document fields:

- `folderName: String`
- `documentCount: Int`
- `updatedAt: Timestamp`

Document fields:

- `fileName: String`
- `content: String`
- `contentHash: String`
- `sizeBytes: Long`
- `updatedAt: Timestamp`

Notes:

1. The remote root uses Firebase UID, not email.
2. Email is not part of the folder path.
3. UI no longer displays UID in the cloud status label.
4. Single-document size is capped before upload to stay below Firestore document limits.

## Auth and Security

Auth model:

1. User signs in with Google.
2. Google identity is exchanged into Firebase Auth.
3. Firestore path isolation is based on `request.auth.uid`.

Recommended Firestore rules:

```text
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/speakerFolders/{folderId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;

      match /documents/{documentId} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }
    }
  }
}
```

## Component Responsibilities

### `SpeakerLocalRepository`

Owns local filesystem CRUD.

Current responsibilities:

- load local directories/documents
- list local folders for sync
- create folder
- rename folder
- create or update local txt files
- delete folder/document
- save document edits

### `SpeakerCloudRepository`

Owns Firestore CRUD.

Current responsibilities:

- upsert remote folder metadata
- upload document content into Firestore
- list remote folders
- list remote documents
- download remote document content

### `SpeakerSyncService`

Coordinates local and remote repositories.

Current responsibilities:

- upload one local folder to Firestore
- upload all local folders internally if needed
- import selected remote folders into local storage
- report sync progress

### `SpeakerViewModel`

Owns speaker explorer UI state.

Current sync-related state includes:

- Firebase sign-in status
- remote folder list
- cloud import dialog state
- local import progress
- cloud sync progress
- create-folder dialog state
- rename-folder dialog state

## Current UX

### Explorer Header

Current actions:

1. Create folder
2. Import files with file picker
3. Import from cloud

### Per-folder Actions

Current actions in each local folder row:

1. Refresh
2. Import txt files into this folder
3. Upload this folder to cloud
4. Rename local folder
5. Delete local folder

### Upload Flow

1. User taps the cloud upload icon on a folder row.
2. App shows a confirmation dialog explaining same-name files in Firestore will be overwritten.
3. On confirm, local folder contents are uploaded into Firestore under the signed-in Firebase UID.
4. Progress dialog is shown while syncing.
5. Success/failure toast is shown when the job finishes.

### Import Flow

1. User taps `Import from cloud`.
2. App loads remote folder list from Firestore.
3. User selects one or more folders.
4. App imports remote txt content back into the current local speaker root.
5. Explorer reloads local directories after success.

### Rename Flow

1. User taps the edit icon on a local folder row.
2. App shows a rename dialog.
3. Folder name is sanitized locally.
4. Rename is rejected if the target folder already exists.
5. This rename only affects local storage.

## Important Decisions

1. Firestore-only was chosen instead of `Storage + Firestore`.
   - Simpler billing model
   - Simpler security model
   - Good fit because speaker files are plain text

2. Firebase UID is the remote root key.
   - Stable within the project
   - Better for security rules
   - Email is not used as the primary key

3. Upload-all was intentionally removed from the current UI.
   - Per-folder upload is safer
   - Reduces accidental bulk overwrite

4. Cloud status avoids showing UID/token.
   - Signed-in state is still visible
   - Internal sync logic still uses Firebase UID

## Known Gaps / Next Steps

### Practical next steps

1. Add remote delete support.
2. Add remote rename policy or explicitly document that remote names are managed by re-upload.
3. Add better user-visible error messages for Firestore permission failures.
4. Consider adding a local-vs-remote stale data hint before overwrite.

### Optional future improvements

1. Add `SkipExisting` conflict policy for import.
2. Add remote folder last-sync metadata in the UI.
3. Add remote/local diff summary before upload.
4. Persist cloud import selection state more explicitly across configuration changes.

## Verification Checklist

Current baseline verification:

1. Sign out and sign back in with Google.
2. Confirm speaker header shows cloud signed-in state.
3. Upload one local folder.
4. Verify Firestore documents appear under:
   - `users/{firebaseUid}/speakerFolders/{folderName}`
5. On another device signed into the same Firebase account, use `Import from cloud`.
6. Confirm local folder and `.txt` files are recreated under:
   - `filesDir/speech/<localUserId>/<folderName>/`
