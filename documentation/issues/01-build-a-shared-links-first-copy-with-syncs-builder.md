# Build a shared link's first copy with the builder sync rebuilds it with, and take the shared-copy mode out of the export

**Challenged:** amended — re-checked at 05f341cc5 (`IMAGES_FOLDER` is now `LibraryFiles.ARCHIVE_FILES_FOLDER`, shared
by sync, the export, and the import); corrected what a share/run mismatch costs (one upload per share, not one per
run); added the callers and tests the plan missed (`Device.kt`, `TestSyncRepository.kt`, `ExportControllerTest.kt`,
`SharedLinksRepositoryImplTest`'s `repository()` helper and its 45 `share(..., copy, ...)` calls, `SimulatedSharing.share`);
said what the builder shares with the fingerprint check (`filesNamedBy`), how the share answers an unreadable setlist
(`Missing`) and an unreadable file (`Failed`, a behaviour change), that `share` now answers `Missing`/`TooLarge` too
(KDoc), that `exportedArchive`'s `isDated` parameter goes, and which docs name the old rule (`data/model/CLAUDE.md`
too); dropped the optional `built` on the new entry; noted the overlap with plan 06 and the fixer's edits in
`ExportController.kt`.

**Kind:** architecture / duplication  ·  **Severity:** medium  ·  **Platforms:** all
**Files:**
- `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedCopySync.kt`
- `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedCopyBuilder.kt` (new)
- `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksRepositoryImpl.kt`
- `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/Module.kt` (`sharedCopySync(...)` passes the builder)
- `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/CLAUDE.md`
- `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SharedLinksRepository.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/sharing/CreateShareLinkUseCaseImpl.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/export/ExportedImages.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/export/ExportedArchive.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/export/ExportSongsUseCaseImpl.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/export/ExportSetlistUseCaseImpl.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/export/ExportLibraryUseCaseImpl.kt`, `IsExportedWithPicturesUseCaseImpl.kt` (the `isSharedCopy = false` arguments, `isDated = true`)
- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/export/ExportSongsUseCase.kt`, `ExportSetlistUseCase.kt` (parameter and KDoc)
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/library/ExportedFile.kt` (`leftOutAttachmentCount`, `isTooLargeToShare`: at 05f341cc5 only `ExportedArchive.kt`, `CreateShareLinkUseCaseImpl.kt`, and `ExportedFileTest` read them)
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/library/SharedCopyAttachments.kt` (KDoc only, unless moved; see Fix 6)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/ExportController.kt` (three `isSharedCopy = false` arguments)
- Tests:
  - `domain/implementation/src/commonTest/.../sharing/CreateShareLinkUseCaseImplTest.kt`, `.../export/ExportSongsUseCaseImplTest.kt`, `.../export/ExportSetlistUseCaseImplTest.kt`, `.../importing/ImportFilesUseCaseImplTest.kt` (one call, `isSharedCopy = false`), `.../RepositoryStubs.kt` (`share` stub)
  - `presentation/src/commonTest/.../screens/export/ExportControllerTest.kt` (its fakes override `invoke(..., isSharedCopy: Boolean, ...)`)
  - `data/model/src/commonTest/.../library/ExportedFileTest.kt` (the `isTooLargeToShare` cases)
  - `data/sync/implementation/src/commonTest/.../shared/SharedCopySyncTest.kt`, `.../shared/SharedCopyFixtures.kt`, `.../shared/SharedLinksRepositoryImplTest.kt`, `.../SimulatedSharing.kt`, `.../Device.kt`, `.../TestSyncRepository.kt` (every `SharedCopySync(` and `SharedLinksRepositoryImpl(` construction)
  - `data/source/local/implementation/src/desktopTest/.../source/SharedCopyParityTest.kt` (KDoc) and `SharedCopyApp.kt` / `SharedCopyLibrary.kt` if they name the export
- Docs: `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/export/CLAUDE.md` (the "A zip is dated…" bullet), `data/model/CLAUDE.md` ("`sharedCopyAttachments`, the one rule for the export and sync"), the `shared/CLAUDE.md` above (Pictures, Attachments bullets), `domain/implementation/CLAUDE.md` if it names the export as the share's builder

## Problem

A shared link's copy is laid out by two pieces of code that must make the same bytes, or the first run after a share
uploads the copy again (the run compares the hash of what it builds with the copy in the folder; once it has uploaded
its own build it records `built` and every later run, on every device, compares run with run). So a mismatch costs one
upload per share, not one per run, but every layout rule has to be written twice and a 200-seed test is what notices
when the two drift.

- The **first** copy is the export run in a mode of its own: `CreateShareLinkUseCaseImpl` calls
  `exportSetlist(setlistFileName = fileName, isSharedCopy = true)` / `exportSongs(fileNames = listOf(fileName), isArchive = true, isSharedCopy = true) { _, text -> text }`,
  then `if (copy.isTooLargeToShare) return CreateShareResult.TooLarge`, reads the exported file back whole
  (`exportFileRepository.read(copy)`), and passes `copy.leftOutAttachmentCount` on. `ExportedImages` grows a
  shared-copy branch for it: `decideSharedAttachments(texts)` decides from every song before the first is written,
  `picked()` calls `sharedCopyAttachments(names = candidates, sizeOf = sizes::get)` with the *listed* sizes
  (`imageRepository.loadLibraryFileSizes()`), and `named(text)` strips left-out lines with
  `ChordProAttachments.withoutAttachments(text, leftOut)`. Its KDoc says it outright: "writes its songs without a line
  for them, as the sync run that rebuilds the copy does (`SharedCopySync`): both make the same bytes."
- **Every later** copy is `SharedCopySync.Pass.build`, which restates the same layout by hand: item first, then the
  setlist's songs once each under the setlist's spelling (`if (bytes != null && name !in files)`), re-encoded as the
  export writes text (`itemBytes.asExportedText()` = `decodeLibraryText().encodeToByteArray()`), pictures vs
  attachments split by `contents.pictureNamesOf`, attachments budgeted with
  `sharedCopyAttachments(names = attachments, onLeftOut = { read.remove(it) }) { name -> readFile(name)?.also { read[name] = it }?.size?.toLong() }`
  (sizes of what it *read*, not listed), left-out lines stripped with
  `files.keys.filter { kind == SharedLinkKind.SONG || it != entry.fileName }.forEach { name -> files[name] = contents.withoutAttachments(files.getValue(name), leftOut) }`,
  and `intended.forEach { name -> read[name]?.let { files[LibraryFiles.ARCHIVE_FILES_FOLDER + name] = it } }`.

Only `sharedCopyAttachments` (`:data:model`) and, since 05f341cc5, `LibraryFiles.ARCHIVE_FILES_FOLDER` are shared. The
two already differ where they read: which library files exist (export: `imageRepository.loadLibraryImageNames()`;
sync: `index.find(LibraryCollections.FILES, it) != null` in `filesNamedBy`), how a setlist's song is found (export: the
song list by `LibraryFiles.identityKey`; sync: `index.localNameOf`), an attachment that is listed but unreadable
(export counts its listed size against the budget, then `loadImage` returns null; sync's `sizeOf` answers null and does
not count it), and the size checks (`ExportedFile.isTooLargeToShare` vs `size > ImportLimits.MAX_SHARED_COPY_SIZE` /
`copy.size > MAX_SHARED_COPY_SIZE + MAX_SHARED_ATTACHMENTS_SIZE`).

The drift is guarded, not prevented: `data/source/local/implementation/src/desktopTest/.../source/SharedCopyParityTest.kt`,
over `campfire.sharedCopyParitySeeds` seeds (200 per build), connects (`SharedCopyApp.start`, no run yet, so no index),
shares items through the real `CreateShareLinkUseCase`, syncs, and fails where the run uploads a copy again, where a
copy holds anything but `SharedCopyLibrary.expectedCopy`, or where editing an unshared song changes a copy.

## Fix

**Option (a): one pure layout function in `:data:model`** beside `sharedCopyAttachments`, each side passing its own
reader and lookup. It shrinks the duplicated rules but keeps two builders, two lookups, and two size checks, and the
export keeps its shared-copy mode. Not recommended.

**Option (b), recommended: the share builds its first copy with sync's builder.**

1. **Extract the builder.** Move `SharedCopySync.Pass.build`'s layout (from `val files = linkedMapOf(...)` to
   `contents.pack(files)` and the size check after it) into `shared/SharedCopyBuilder.kt`: an internal class over
   `LibraryFileLocalSource` and `SharedCopyContents`, annotated `@Single` (no defaulted constructor parameter; the
   lookup is an argument of `build`, not a constructor value). One
   `suspend fun build(kind: SharedLinkKind, fileName: String, itemBytes: ByteArray, songNames: List<String>?, names: SharedCopyNames): SharedCopyBuilt`
   whose result holds the packed bytes (null where too large: over `MAX_SHARED_COPY_SIZE` before packing, or the packed
   copy over `MAX_SHARED_COPY_SIZE + MAX_SHARED_ATTACHMENTS_SIZE`), the fingerprint's parts (`songs` with their
   hashes, the `images` list as `build` computes it: `intended` when too large, else
   `intended.filter { it in read || it in leftOut }`), and the left-out set. `Pass.build` keeps the fingerprint call,
   the upload, `changes.update`, and `STATE_TOO_LARGE`, and calls the builder.
   - The lookup seam, `SharedCopyNames` (an internal interface in `shared/`): `localNameOf(kind: SyncCollection, name: String): String`
     and `holdsFile(name: String): Boolean`. The run's is today's `IndexedNames` (lifted out of `SharedCopySync`,
     implementing it; its `localNameOf`'s default `kind` argument is fine on a non-Koin class but goes with the
     interface). The share's is built once per share from `libraryFileLocalSource.loadLibraryFiles()`, keyed by
     `kind to LibraryFiles.identityKey(name)` exactly as `IndexedNames` keys the index, so both answer by identity and
     both see only what `SyncCollection.matches` admits.
   - `filesNamedBy(songs)` moves into the builder over the seam (`LibraryFiles.isLibraryFileName(it) && names.holdsFile(it)`,
     sorted), since `Pass.settleOrThrow` also calls it for the fingerprint check: one function, both callers.
   - The comments in `build` that name the export ("as the export that made the first copy", "as the share's export
     writes them (ExportedImages)") say instead that the share builds the first copy here too; `asExportedText` may
     keep its name or become `asCopyText`.
2. **`SharedLinksRepository.share(kind, fileName, title)`** (no `copy`, no `leftOutAttachmentCount`).
   `SharedLinksRepositoryImpl` gains `SharedCopyBuilder`, `SharedCopyContents`, and `LibraryFileLocalSource` as
   constructor dependencies (all `@Single`, all in this module or its API dependencies; no layering rule is crossed:
   `check_sync_seams.py` forbids only the engine packages importing `shared`). After `connectedFolder()` and
   `applyPendingJournal`, and inside the existing `try` (so the KDoc's "never throws but cancellation" holds):
   - read the item with `libraryFileLocalSource.readLibraryFile(kind.libraryKind, fileName)`; null answers
     `CreateShareResult.Missing`. For a setlist, `contents.setlistSongFileNames(bytes)`; null (not a setlist) answers
     `Missing` too, as the export answered for a setlist the library could not parse;
   - build with the listing seam; a null copy answers `CreateShareResult.TooLarge`, nothing uploaded;
   - upload, link, and add as now, the entry recording `leftOutAttachments = built.leftOut.size`, which is what the export
     screen and Settings → Sharing say (both read `Share.leftOutAttachmentCount`, mapped from the entry, never
     `ExportedFile`).
   - A file that is there but unreadable (`LibraryStorageException`) now fails the share (`Failed(UNKNOWN)` through
     `toSharingFailure`), as it leaves a run's copy unsettled, where the export left the file out silently. Accepted:
     it is transient (before the first unlock, a file being replaced), and Try again works.
   - Leave `built` null on the new entry: the first run then builds the copy once, finds the same hash, uploads nothing,
     and records `built`. Recording it here would need the build number and inputs that agree with the index's hashes,
     for the saving of one rebuild.
   - Update the interface KDoc: the copy is built here from the library's files, a missing item answers `Missing`, one
     too large `TooLarge`; drop the `[leftOutAttachmentCount]` sentence.
   `share` stays outside the run lock (`journal.whileApplying`), as now: a run whose listing predates the upload finds
   `remote == null` for the new entry and leaves it ("A share newer than the listing"), and the registry edit is against
   its revision, so a run in flight neither clobbers nor races the share. A run that changes a shared song after the
   share read it rebuilds and uploads, which is correct.
3. **`CreateShareLinkUseCaseImpl`** keeps its refresh and existing-share lookup, then returns
   `sharedLinksRepository.share(kind, fileName, title)`. It no longer needs `ExportSetlistUseCase`, `ExportSongsUseCase`,
   `ExportFileRepository`, or `Logger`.
4. **Remove the shared-copy mode from the export.** The `isSharedCopy` parameter of both use cases and their KDoc (only
   `CreateShareLinkUseCaseImpl` passes `true`; `ExportController.kt` passes `false` at three call sites;
   `ExportControllerTest`'s fakes and `ImportFilesUseCaseImplTest` line `invoke(listOf("a.cho"), isArchive = false, isSharedCopy = false)`
   follow). In `ExportedImages`: the `isSharedCopy` field and `of` parameter, `decideSharedAttachments`,
   `carried`/`leftOut`/`leftOutAttachmentCount`, `carriedAttachments()`'s caching and `picked()` (every library file a
   song names goes in, as for any export), `named`'s `withoutAttachments` call (it returns `text` unchanged, or becomes a
   `Unit` note and the callers keep their text), and the KDoc sentences about the shared copy and about `SharedCopySync`
   relying on the order. In `ExportedArchive.kt`: the `leftOutAttachmentCount = images.leftOutAttachmentCount` argument,
   and the `isDated` parameter of `exportedArchive` (every caller then passes `true`; `ArchiveRepository.pack`'s own
   `isDated` stays, sync packs undated through it). Delete `ExportedFile.leftOutAttachmentCount` and `isTooLargeToShare`
   with their `ExportedFileTest` cases. `ImageRepository.loadLibraryFileSizes` stays (`ExportLibraryUseCaseImpl` reads it).
5. **Memory**: the builder reads pictures one at a time up to `MAX_SHARED_COPY_SIZE` exactly as `build` does now. The
   first copy is then packed in memory (at most 24 + 50 MiB) instead of streamed into `exports/`; that is what every
   run already does on every device, and the share read the exported file back whole anyway.
6. `sharedCopyAttachments` then has one caller, the builder. Recommended default: leave it in `:data:model` and change
   its KDoc and `data/model/CLAUDE.md`'s "the one rule for the export and sync" to "the shared copy's rule"; moving it
   into `shared/` with its test is fine too, in the same commit.

Accepted edge: where the device's listing and the run's index disagree about a file (one the run leaves out of the index:
a name this file system cannot hold, a file past its collection's size limit, or two local spellings of one name on a
file system that tells them apart), the first run uploads its own build once; later runs compare with that.

**Ordering with other work**: plan 06 edits `ExportController.kt` (`printSongs`) and `ExportControllerTest`, and the
sweep's fixer may edit `ExportController.kt` preference decisions; this plan touches only the three `isSharedCopy`
arguments there and the two fakes' signatures, so rebase onto whichever lands first.

Check `python3 .github/scripts/check_sync_seams.py --all` after the change, and `check_sizes.py` (`SharedCopySync.kt`
shrinks from 383 lines; `SharedLinksRepositoryImpl.kt`, 386, gains about 20).

## Tests

- `SharedCopySyncTest` keeps passing with only the constructor change (the run's behaviour is the same). Add a
  `SharedCopyBuilderTest`: a setlist naming a song in another case is built under the setlist's spelling from the
  library's file with the listing seam and the index seam alike, and byte-for-byte equal; an attachment past the
  budget is left out of both and its line taken out of the songs.
- `SharedLinksRepositoryImplTest`: its `repository()` helper gets a `FakeLibraryFileLocalSource` holding
  `gig.setlist.json` (naming `song.cho`) and `song.cho`, and a `FakeSharedCopyContents`, so the 45 existing calls only
  drop `copy` and `leftOutAttachmentCount`; the case asserting `leftOutAttachmentCount == 2` becomes one whose song
  attaches a file past the budget. New cases: `share` of a missing item answers `Missing` and uploads nothing, of an
  oversized one `TooLarge` with nothing uploaded, and the uploaded copy is what `SharedCopyBuilder` builds.
- `SimulatedSharing.share` drops `copy = device.sharedContents.pack(mapOf(key.name to bytes))` and
  `leftOutAttachmentCount = 0`; `Device.sharing(...)` and `Device`/`TestSyncRepository`'s `SharedCopySync(` pass the
  device's `library` and `sharedContents` to a builder. The simulation then shares what the device's library holds,
  which is closer to the app than a copy of the item alone.
- `CreateShareLinkUseCaseImplTest`: drop the export stubs; the existing-share and refresh cases stay, plus one that the
  repository's `Missing` and `TooLarge` pass through. `RepositoryStubs`' `share` stub loses the two parameters.
- `ExportSongsUseCaseImplTest` / `ExportSetlistUseCaseImplTest`: delete the `isSharedCopy = true` cases, drop the
  argument elsewhere; `ExportControllerTest`'s fakes drop it from their overrides.
- **`SharedCopyParityTest` stays**, at 200 seeds: it shares before the first run (no index), through the real Koin graph
  (`SharedCopyApp`), and now proves the listing seam and the index seam agree (a run after a share uploads nothing),
  that a respelled song on an exact-name file system is found, and that a copy holds exactly what was shared. Update
  its KDoc ("A shared link's first copy comes from the export…", "10,000 by hand when sharing or the export changes")
  to say both copies come from `SharedCopyBuilder`, and the by-hand run is for changes to it or to the lookups.
