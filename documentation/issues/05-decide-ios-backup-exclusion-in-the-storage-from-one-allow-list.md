# Let the iOS storage decide every file's device-backup mark from one allow-list instead of each writer remembering it

**Kind:** bug / architecture / privacy  ·  **Severity:** medium (picture loss from backups, see Problem)  ·  **Platforms:** iOS
**Challenged:** amended — the copy bug is confirmed on macOS Foundation and lands first as its own fix; existing files are reconciled once per launch (the pictures the bug already left out); sinks are marked at open; the predicate no longer reaches into `source/`; `DeviceBackupMarksTest` (d6ad84fbe) is replaced; the contract test reads marks only where no folder mark is inherited; the Manual check is dropped.
**Files:**
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/DeviceBackup.kt` (new: the predicate)
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/LibraryFileBackup.kt` (deleted, its function moved into the above)
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.kt` (`keepOutOfDeviceBackup`, `keepInDeviceBackup`, the `copyInto` KDoc)
- `data/source/local/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FoundationFileStorage.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/CoverArtLocalSourceImpl.kt`, `EditorDraftLocalSourceImpl.kt`, `ImageLocalSourceImpl.kt`, `LibraryFileLocalSourceImpl.kt`, `SharedLinksLocalSourceImpl.kt`, `SyncIndexLocalSourceImpl.kt`, `UserPreferencesLocalSourceImpl.kt` (the `.bad` copy's call, landed in `7578583b5`; `FILE_NAME` refers to the storage's constant)
- Tests: `data/source/local/implementation/src/commonTest/.../InMemoryFileStorage.kt` (`isKeptOutOfDeviceBackup`, `namesInDeviceBackup`, the two overrides), `.../source/DeviceBackupMarksTest.kt` (deleted), `.../source/ImageLocalSourceImplTest.kt`, `.../source/LibraryFileLocalSourceImplTest.kt` (find every use with `git grep -n "DeviceBackup" -- data/source/local/implementation/src/commonTest`), `.../storage/file/DeviceBackupTest.kt` (new), `data/source/local/implementation/src/iosTest/.../storage/file/IosFileStorageContractTest.kt`
- Docs: root `CLAUDE.md` (§ Privacy names `FileStorage.keepOutOfDeviceBackup`), `data/source/local/implementation/CLAUDE.md` (§ Directories, the `moveOut` bullet naming `keepInDeviceBackup`, and § Sources' `ImageLocalSourceImpl` bullet: "kept out of the iOS backup after every write", `isLibraryFileKeptOutOfDeviceBackup`)

## Problem

iOS backs up whole directories, so what must stay out is marked with `NSURLIsExcludedFromBackupKey`, file by file, by
whoever writes it. `FileStorage.keepOutOfDeviceBackup` is a no-op hook every writer must remember to call after
**every** write ("the exclusion is a file attribute and the atomic write replaces the file", local
`implementation/CLAUDE.md` § Directories). The callers at HEAD: `CoverArtLocalSourceImpl`,
`EditorDraftLocalSourceImpl`, `SharedLinksLocalSourceImpl`, `SyncIndexLocalSourceImpl` (twice),
`UserPreferencesLocalSourceImpl` (`preferences.json.bad`, added by `7578583b5` after the review found it unmarked),
`ImageLocalSourceImpl.keepOutOfDeviceBackupIfDue` (draft shelf, or an attachment by `isLibraryFileKeptOutOfDeviceBackup`),
and `LibraryFileLocalSourceImpl.writeLibraryFile` (sync's attachments). Moves need the opposite call, since a rename keeps
the mark: `ImageLocalSourceImpl.moveStagedFile` calls `keepInDeviceBackup` for a picture moved from `IMPORTS`.
`DeviceBackupMarksTest` (`d6ad84fbe`) pins each source's calls through `InMemoryFileStorage`; a new file in
`preferences/` that forgets the call still goes into the backup silently. Only `IMPORTS` is excluded as a folder, and
only when the app creates it:

```kotlin
// FoundationFileStorage.createDirectory
if (directory == StorageDirectory.IMPORTS && !NSURL.fileURLWithPath(path, isDirectory = true).setResourceValue(true, NSURLIsExcludedFromBackupKey, null)) {
```

**A bug comes with it.** Save copies a draft picture into the library
(`ImageRepositoryImpl`: `imageLocalSource.copyImage(from = ImageShelf.DRAFT, name = name, to = ImageShelf.LIBRARY, toName = candidate)`),
and `ImageLocalSourceImpl.copyImage` relies on "A copy is a new file, which carries no backup mark of the original's:
a draft picture reaches the backup this way" (and `FileStorage.copyInto`'s KDoc: "The copy is a new file: whatever
mark the original carried, such as [keepOutOfDeviceBackup]'s, it does not"). iOS's `copyInto` is
`fileManager.copyItemAtPath` to a temporary name, then `rename`. A probe on macOS Foundation (the simulator runs on
the host's file system) shows the mark is the extended attribute `com.apple.metadata:com_apple_backup_excludeItem`
and:

| Operation on a marked file | Mark afterwards |
|---|---|
| `copyItemAtPath` | **kept** on the copy |
| `rename` (`moveInto`, `takeInto`, `copyInto`'s last step) | kept |
| `NSDataWritingAtomic` write (`writeBytes`) | gone (a new file is renamed over it) |
| `fopen(path, "wb")` and writes in place (`PosixFileSink`) | kept |
| a file written into a marked folder | carries no mark of its own; copied out, the copy has none |
| a file inside a marked folder, asked for `NSURLIsExcludedFromBackupKey` | reads `true` (the folder's, inherited) |

So every picture added through the editor's Image button is copied into `library/files/` carrying the draft's mark,
and is left out of the iOS backup: restored onto a new phone, those songs show placeholders. `copyImage` only ever
sets marks, never clears them, and `InMemoryFileStorage.copyInto` (a `writeBytes` of the bytes) drops the mark, which
is why the commonTests never saw it. A real device is assumed to behave as the simulator; the fix below sets the mark
explicitly after every copy, move, and take, so it does not depend on that.

Apple's key on a directory excludes the directory and everything in it, files added later included; it is stored on
the item, so a rename or move keeps it, and an item moved out of an excluded folder is backed up unless it carries
its own mark.

## Fix

**First, its own commit: the bug.** `ImageLocalSourceImpl.copyImage` clears the mark of a picture copied into the
library, as `moveStagedFile` does (`if (to == ImageShelf.LIBRARY && !isLibraryFileKeptOutOfDeviceBackup(toName)) fileStorage.keepInDeviceBackup(to.directory, toName) else keepOutOfDeviceBackupIfDue(to, toName)`),
with the contract test case "a picture written to `DRAFT_IMAGES`, marked as today's draft writes do, and copied into
`FILES` is in the backup", written first and seen failing on the simulator. Fix `copyImage`'s and `copyInto`'s KDoc
("the copy carries the original's mark on iOS"). This lands even if the rest of the plan stalls.

**Then the allow-list.** Option (a), folder level only (mark `COVERS` and `DRAFT_IMAGES` as folders like `IMPORTS`),
cannot express `library/files/` (pictures in, attachments out, side by side) or `preferences/` (`preferences.json` in,
the sync index, mirror, journal, draft, `.bad`, and the forget-pending marker out); the per-writer calls would stay
for those. Option (b), recommended: one allow-list, applied by the iOS storage.

1. `storage/file/DeviceBackup.kt`: `internal fun isInDeviceBackup(directory: StorageDirectory, name: String): Boolean`,
   an allow-list matching root `CLAUDE.md` § Privacy: `SONGS` and `SETLISTS` yes; `FILES` yes unless
   `isLibraryFileKeptOutOfDeviceBackup(name)` (moved here from `source/LibraryFileBackup.kt`, which is deleted);
   `PREFERENCES` only `PREFERENCES_DOCUMENT_NAME = "preferences.json"`, a constant of this file that
   `UserPreferencesLocalSourceImpl.FILE_NAME` is set to (nothing in `storage/` imports `source/` today; keep it so);
   `COVERS`, `DRAFT_IMAGES`, `IMPORTS`, `EXPORTS` no. A file nobody listed is out by default, the safe failure.
2. `FoundationFileStorage`, through one private `markForBackup(directory, name)` and one per-directory first use:
   - On the process's first use of a directory (any call naming it, `createDirectory` included, remembered in a set;
     two threads racing both set the same mark, which is harmless): `COVERS`, `DRAFT_IMAGES`, and `IMPORTS` (`EXPORTS`
     is Caches, which no backup takes) get the folder mark when the folder is there, so one made by an older version
     is covered; `FILES` and `PREFERENCES` are reconciled: every file listed whose mark (read with the listing's
     resource keys, `NSURLIsExcludedFromBackupKey` added to them) differs from `!isInDeviceBackup` is set to it. That
     repairs the pictures the bug above already left out and the attachments and preferences files of earlier
     versions; it costs one listing per launch and a write only where a mark is wrong. Files inside a folder-marked
     directory get no mark of their own, so a file moved or copied out of them carries none.
   - In `FILES` and `PREFERENCES`, after `writeBytes`/`writeText`, `moveInto`, `copyInto`, and `takeInto`, set the
     target's mark to `!isInDeviceBackup(directory, name)`: `true` where out; `false` only after a move, copy, or take
     (which may bring a mark along), skipped after an atomic write of an allowed file (a new file carries none). In
     `SONGS`/`SETLISTS` clear it after a move, copy, or take only.
   - `openSink` sets the mark right after `fopen` where the file is to be out (in place writes keep it, the probe
     shows), and clears it where it is to be in. Today only `EXPORTS` and `IMPORTS` are written through sinks
     (`ExportFileLocalSourceImpl`, `ImportStaging`), so this is for the next caller; `PosixFileSink` stays as it is.
   - A mark that cannot be set or read is logged, as now, never a failed write.
3. Delete `keepOutOfDeviceBackup` and `keepInDeviceBackup` from `FileStorage`, `FoundationFileStorage`, and
   `InMemoryFileStorage` (with its `isKeptOutOfDeviceBackup` field and `namesInDeviceBackup`), and every call in the
   sources listed above (`keepOutOfDeviceBackupIfDue` and the first commit's `copyImage` branch go too). The
   `copyInto` KDoc says the storage sets the mark.
4. Docs: root `CLAUDE.md` § Privacy: replace "`FileStorage.keepOutOfDeviceBackup`" with "the iOS storage's
   allow-list (`DeviceBackup.kt`)". Local `CLAUDE.md` § Directories: replace the sentence naming the five sources that
   "call `keepOutOfDeviceBackup` after **every** write" with the allow-list, the folder marks, and the launch's
   reconciliation; drop "(`keepInDeviceBackup`, a rename keeping the mark)" from the `moveOut` bullet; in § Sources,
   drop "kept out of the iOS backup after every write" and the `isLibraryFileKeptOutOfDeviceBackup` sentence from the
   `ImageLocalSourceImpl` bullet (the storage decides now).

Android is untouched: its backup is the XML allow-list in `app/android/src/main/res/xml`, which names
`library/songs/`, `library/setlists/`, and `preferences/preferences.json`, and only `FoundationFileStorage` calls the
predicate. The desktop and the web have no backup.

## Tests

- `DeviceBackupTest` (commonTest): every `StorageDirectory`; each known `PREFERENCES` name (`preferences.json` in;
  `preferences.json.bad`, the sync index, the forget-pending marker, the shared-links mirror and journal, and the editor
  draft out, from the sources' constants, the list `DeviceBackupMarksTest`'s first case names); in `FILES` a picture,
  an attachment, and a name both could be (a picture). Delete `DeviceBackupMarksTest`, whose three cases this covers,
  and the mark lines of `ImageLocalSourceImplTest` and `LibraryFileLocalSourceImplTest`.
- `IosFileStorageContractTest` (run by `./gradlew :data:source:local:implementation:iosSimulatorArm64Test`). Read a
  file's mark only in `FILES` and `PREFERENCES`: in a folder-marked directory the key reads `true` by inheritance
  whatever the file carries. The first commit's case (draft picture copied into `FILES`, in the backup) stays. Rewrite
  "a staged file moved into the library is put back into the device backup or kept out of it" without the
  keepIn/keepOut calls. Add: an attachment written, copied, or moved into `FILES` is out; `preferences.json` is in, every other `PREFERENCES` file
  out; the `COVERS`, `DRAFT_IMAGES`, and `IMPORTS` folders are out after first use even when they existed before the
  storage was made; a picture in `FILES` carrying a mark before the storage was made is in after its first use, an
  attachment without one is out.
