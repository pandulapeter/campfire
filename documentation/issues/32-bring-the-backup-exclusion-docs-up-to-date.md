# Bring the iOS (and Android, and root) CLAUDE.md sentences about what stays out of the device backup up to date

**Challenged:** sound

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** iOS, Android
**Files:** `app/ios/CLAUDE.md`, `app/android/CLAUDE.md`, `CLAUDE.md` (root, the opening paragraph),
`app/android/src/main/res/xml/data_extraction_rules.xml` (its leading XML comment only — no rule changes)

## Problem

Since the editor draft and the cover copies were added, four files/folders are kept out of the iOS backup by
`FileStorage.keepOutOfDeviceBackup`, not one:

- `SyncStateLocalSourceImpl` — `sync-index.json` and the forget-pending note
  (`fileStorage.keepOutOfDeviceBackup(StorageDirectory.PREFERENCES, INDEX_FILE_NAME)` and `…FORGETTING_OWED_FILE_NAME`)
- `EditorDraftLocalSourceImpl` — `editor-draft.json`
  (`fileStorage.keepOutOfDeviceBackup(StorageDirectory.PREFERENCES, FILE_NAME)`)
- `CoverArtLocalSourceImpl` — every file in `covers/`
  (`fileStorage.keepOutOfDeviceBackup(StorageDirectory.COVERS, key)`)

(all in `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/`).
`data/source/local/implementation/CLAUDE.md` and the root `CLAUDE.md`'s library-layout paragraph already say so. These
still say less:

- `app/ios/CLAUDE.md`, the `Info.plist` paragraph: "Both directories are in the device backup, except the sync index,
  which `IosFileStorage` marks as excluded, and the credentials, which are a device-bound Keychain item." The editor
  draft and the covers (Application Support on iOS, next to the preferences — `IosFileStorage.rootPath`) are missing,
  and so is the forget-pending note.
- `app/android/CLAUDE.md`: "each is an allow-list for `library/` and `preferences/preferences.json`, so the encrypted
  sync credentials, the sync index and the `covers/` copies stay behind." The editor draft (and the forget-pending
  note) stay behind too — the allow-list already does it — but the sentence reads as if the list were complete.
- `data_extraction_rules.xml`'s leading comment (`full_backup_content.xml` defers to it for the reasons): "that is how the two files that must not
  travel are kept out" and then explain only `sync-credentials.bin` and `sync-index.json`. Behavior is right (allow-list);
  the comment undercounts.
- Root `CLAUDE.md`, opening paragraph: "the sync credentials and the sync index are not part of it on either
  (`app/android/src/main/res/xml`; on iOS a device-bound Keychain item and a file marked as excluded from backup)".
  Same omission; the layout paragraph further down is already complete.

## Fix

Text only; no code or rule changes.

- `app/ios/CLAUDE.md`: replace the sentence with: "Both directories are in the device backup, except what
  `IosFileStorage` marks as excluded each time it is written — the sync index and the forget-pending note
  (`SyncStateLocalSourceImpl`), the editor draft (`EditorDraftLocalSourceImpl`) and every cover copy
  (`CoverArtLocalSourceImpl`) — and the credentials, which are a device-bound Keychain item."
- `app/android/CLAUDE.md`: "…so everything else under the files directory stays behind: the encrypted sync
  credentials, the sync index and the forget-pending note, the editor draft and the `covers/` copies."
- The `data_extraction_rules.xml` comment: replace "the two files that must not travel" with "everything else - the sync files, the
  editor draft and the cover copies -" and add one line each for the draft (a copy of unsaved text belongs to this
  device's session) and the covers (downloaded again from the address in the song).
- Root `CLAUDE.md` opening paragraph: "the sync credentials, the sync index, the editor draft and the cover copies are
  not part of it on either (`app/android/src/main/res/xml`; on iOS a device-bound Keychain item and files marked as
  excluded from backup)".

No other `CLAUDE.md` carries the stale wording (`data/source/local/api/CLAUDE.md` already says the draft is kept out;
`data/source/local/implementation/CLAUDE.md` names all three callers).

## Tests

None; documentation and XML comments only.

## Manual check

None needed. Optionally, on an iOS simulator, `xattr -l` or a `URLResourceValues` read of `editor-draft.json` and a file
in `Library/Application Support/…/covers/` should show `isExcludedFromBackup`, confirming the corrected sentence.
