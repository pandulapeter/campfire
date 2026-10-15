# List the songs folder once per import batch, not once per song written

**Challenged:** amended — option A (a long-lived name cache in the local sources) dropped: it needs a hook in every
write, delete, and scan of three sources plus its own lock on the JVM, and its claimed invariant was wrong (a file added
outside the app under another case is made to look *free*, not taken). Recommended design is now the import passing the
identity keys it already holds down to `uniqueName`; the measurement gate is gone, since the module's own rule
("saving must not list the directory") is what the change broke.

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all, worst on web (OPFS) and iOS
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNames.kt`,
`.../implementation/source/SongLocalSourceImpl.kt`, `data/source/local/api/.../SongLocalSource.kt`,
`data/repository/api/.../SongRepository.kt`, `data/repository/implementation/.../SongRepositoryImpl.kt`,
`domain/implementation/.../importing/ImportFilesUseCaseImpl.kt`; tests and fakes: `FileNamesTest.kt`,
`data/source/local/implementation/src/desktopTest/.../source/ImportTest.kt`, `ImportFilesUseCaseImplTest.kt`
(its `FakeSongRepository`), `domain/implementation/src/commonTest/.../RepositoryStubs.kt`,
`data/repository/implementation/src/commonTest/.../RecordingSongRepository.kt` and `FakeSongLocalSource.kt`,
`data/sync/implementation/src/commonTest/.../FakeSyncCollaborators.kt`; `data/source/local/implementation/CLAUDE.md`
(`FileNames.kt` section)

## Problem

`f0bbc4bfd` made a name taken under any case or Unicode form (`identityKey`), which needs the directory's listing, and
now lists on every call (`FileNames.kt`, `uniqueName`):

```kotlin
// exists() still has the last word on a name the listing does not show, since it knows the file system's own folding.
val takenKeys = listNames(directory).filter { it != currentName }.mapTo(hashSetOf(), LibraryFiles::identityKey)
```

Before, a free name cost one `exists()`, and the listing came only once `_2` was taken. The commit removed the test
"a single collision is numbered without a listing", and the KDoc now says "That costs one listing per name given out".
The module's `CLAUDE.md` says "saving must not list the directory (`listNames`, `info`); on OPFS a listing [...] made an
import quadratic" — this is that again. `ImportFilesUseCaseImpl.writeSongs` writes songs one after another, each through
`SongRepositoryImpl.importSong` → `SongLocalSourceImpl.importSong`:

```kotlin
val name = if (shouldReplace) fileName else fileStorage.uniqueName(StorageDirectory.SONGS, fileName)
```

A 2,000-song SongbookPro backup into a 1,000-song library is 2,000 listings of up to 3,000 names, each also folded with
`identityKey` (NFC + case fold) — on OPFS an async iterator walk per song.

The import is the only bulk caller. The others give out one name per user action or per sync conflict and may keep
listing: `createSong`, `renameSong` / `renameSetlist` (and `moveFile`'s temporary name), `SetlistLocalSourceImpl`'s
create and import (a few setlists per batch), and `LibraryFileLocalSourceImpl.writeLibraryFileToFreeName` (one per
conflict copy, under `libraryFileLock`, with sync's own `isTaken` folded the same way via `SyncKey.folded()`).

## Fix

Let the import hand `uniqueName` the identity keys it already holds, so a batch lists nothing.

1. `uniqueName` gets `takenKeys: Set<String>? = null`: the identity keys of the directory's names where the caller
   already holds them; listed (as now) when null. Every candidate is still asked of `exists()` (and of `isTaken`), so a
   file the set does not know under its exact name, or under a spelling the file system folds (macOS, iOS, Windows case),
   is never overwritten. KDoc it honestly: the set can be stale in both directions — a file gone since makes a name look
   taken (one extra number), and a file added since under another case or form, on a file system that tells them apart
   (Android, Linux, OPFS; Windows for form), makes it look free, giving that file a case-variant sibling as before
   `f0bbc4bfd`. Restore the cost sentence: one listing per call unless the caller passes the keys.
2. `SongLocalSource.importSong` and `SongRepository.importSong` get `takenNames: Set<String>?` (identity keys; no default:
   the use case is the one caller), passed straight through to `uniqueName(..., takenKeys = takenNames)`; ignored when
   `shouldReplace`. Update both KDocs.
3. `ImportFilesUseCaseImpl.writeSongs`: today `libraryNames` (identity keys of `songRepository.loadSongsIfNeeded()`) is
   built only when some entry `isIdenticalToLibraryFile`. Build it always, as a `hashSetOf` it then grows: after every
   `importSong`, add `LibraryFiles.identityKey(storedName)`. Pass it as `takenNames`; pass null when
   `loadSongsIfNeeded()` returned null (the source then lists, as now). The existing `isLibraryFileGone` check keeps
   reading the library's keys only — keep a separate immutable snapshot for it, or check it before the batch's own names
   are added; do not let names written by this import make an identical song's library file look present.

The list `loadSongsIfNeeded()` returns is the one the review judged collisions against, kept current by every app
write and by the rescans sync and the desktop/iOS foreground refresh do; read at the start of `writeSongs`, not at plan
time, so a sync run during the review is included. Its gaps (a file the scan skipped as oversized or unreadable, or one
written by sync or from outside the app during the import) cost at most a case-variant sibling, never an overwrite.

Update `data/source/local/implementation/CLAUDE.md` (`FileNames.kt`): "One listing per call" becomes "one listing per
call, none where the caller passes the keys it holds (the import)".

## Tests

- `FileNamesTest` (counting `InMemoryFileStorage(foldsNames = false)`): with `takenKeys` holding `identityKey("Song.cho")`,
  `uniqueName(SONGS, "song.cho", takenKeys = …)` returns `song_2.cho` and `listCalls` stays 0; a file written to the
  storage as `a.cho` but missing from the set is still numbered (`exists` decides), with `listCalls` 0.
- `ImportFilesUseCaseImplTest`: the fake repository records `takenNames`; importing two songs into a library holding
  `Song.cho`, the first call's set holds `identityKey("Song.cho")` and the second also the first song's stored name. An
  identical entry whose library file is gone is still written (the existing gone-file test keeps passing).
- `ImportTest` (desktop, real storage): importing a batch of songs leaves names as before (existing assertions).
