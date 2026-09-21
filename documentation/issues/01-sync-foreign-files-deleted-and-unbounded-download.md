# 01 · Sync deletes the user's own non-song files from their Dropbox folder, and downloads files of any size

**Severity:** data loss (all platforms; certain for anybody who keeps a PDF, a recording or a `.txt` next to their
songs in `Apps/Campfire/songs`), plus crash (Android, iOS; a file of a few hundred MB in that folder) ·
**Area:** `:data:model` (`LibraryFileKind`), `:data:repository:implementation` (`SyncEngine`),
`:data:source:local:implementation` (`LibraryFileLocalSourceImpl`)

## Symptom
The documentation tells the user that `Apps/Campfire` is a real folder they can open, so they keep
`wonderwall.pdf`, `backing-track.mp3`, `notes.txt` or `song.cho.bak` next to their songs in `Apps/Campfire/songs`
(or a `gigs.json` in `setlists`).

1. **Sync now** (or just starting the app): the file is downloaded into `library/songs/`, where the app never shows
   it.
2. The next run — the next launch — **deletes the file from the user's Dropbox.** Nothing asks: the wipe guard only
   counts local deletions. The downloaded copy stays in the app's private storage, which on Android, iOS and the web
   the user cannot open.

Separately, a very large file in either folder (a video, a zip, a mis-named `.cho`) is downloaded whole into memory,
six at a time. On a 2 GB phone that is an `OutOfMemoryError`, which no `catch (Exception)` in the run sees, so the
process dies a few seconds after every launch for as long as the file is there. On a slow link the 60 s request
timeout fires instead and ends every run as "could not reach the service" — and since downloads run before uploads,
that device never uploads anything again.

## Cause
The two listings a run compares do not apply the same rule. The local one leaves out what is not a library file
(`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/LibraryFileLocalSourceImpl.kt:31-36,64-67`):

```kotlin
fileStorage.list(kind.directory).filter { kind.matches(it.name) }.map { it.toLibraryFile(kind) }
```

The remote one does not. `DropboxSyncProvider.toRemoteFiles()`
(`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt:263-275`)
checks the tag, the folder and the depth, and `SyncEngine.synchronize`
(`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt:90-101`)
maps every `RemoteFile` straight into a `RemoteFileState`. So on the second run the file exists on one side only
*because of the filter*, has an index entry with the revision the remote still has, and `SyncPlanner.kt:137-142`
reads that as a local deletion:

```kotlin
remote != null -> when {
    indexEntry == null -> SyncOperation.Download(key, remote.revision)
    indexEntry.remoteRevision != remote.revision -> SyncOperation.Download(key, remote.revision)
    else -> SyncOperation.DeleteRemote(key, indexEntry.remoteRevision)
}
```

The size half: `RemoteFile.size` is carried from the listing and read nowhere (`grep size` in `sync/` finds
nothing), and `DropboxSyncProvider.download` (`:197-206`) ends in `response.readRawBytes()`.

Both findings verified against `29820b93`. One detail of the reports needed care, see step 4: the size cap must
**not** be implemented by leaving large files out of the remote listing the way foreign ones are. A large `.cho`
that an earlier run did manage to download is listed locally and is in the index, so filtered out of the remote
listing it would be planned as `DeleteLocal` — the mirror image of the bug being fixed.

## Fix
The rule is a property of a name (kind + file name), so applying it to the local listing, the remote listing and
the index alike makes all three agree by construction. It goes into the engine rather than into the Dropbox
provider so that a second provider inherits it.

1. **`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFile.kt`** — give
   `LibraryFileKind` the rule as a member, between the entries and the companion:

   ```kotlin
   enum class LibraryFileKind(val id: String) {
       SONG("songs"),
       SETLIST("setlists");

       /**
        * Whether [name] is a file of this kind, going by its extension alone. Both of the folders sync compares are
        * ones the user can open - the library folder on the desktop, the app folder of their cloud storage
        * everywhere - so either may hold whatever else they keep there, and that is left where it is. Every listing
        * of either side has to ask this one question: a file that one side lists and the other leaves out looks to
        * a sync run exactly like a file that was deleted there.
        */
       fun matches(name: String) = when (this) {
           SONG -> LibraryFiles.SONG_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }
           SETLIST -> name.endsWith(LibraryFiles.SETLIST_EXTENSION, ignoreCase = true)
       }

       companion object {
           fun fromId(id: String) = entries.firstOrNull { it.id == id }
       }
   }
   ```

2. **`LibraryFileLocalSourceImpl.kt`** — delete the private `LibraryFileKind.matches` extension and its KDoc
   (lines 60-67) and the now unused `LibraryFiles` import; `kind.matches(it.name)` in `loadLibraryFiles` resolves to
   the member. Do **not** touch `SongLocalSourceImpl.isSongFileName` or the filter in `SetlistLocalSourceImpl` here:
   plan 27 rewrites those two, and it has to put whatever it adds (hidden files, AppleDouble `._` files) into
   `LibraryFileKind.matches`, not into a private copy — a rule only the local side knows is this bug again.

3. **`SyncPlanner.kt`** — `RemoteFileState` learns the size, defaulted so that `SyncPlannerTest` does not change:

   ```kotlin
   /** A remote file, identified by whatever the provider calls a revision. Opaque here: only ever compared for equality. */
   internal data class RemoteFileState(
       val key: SyncKey,
       val revision: String,
       val contentHash: String?,
       /** In bytes. Never part of any decision about what changed; only what keeps the engine from downloading a video. */
       val size: Long = 0,
   )
   ```

4. **`SyncEngine.kt`**, all of the following.

   a. Imports: `com.pandulapeter.campfire.data.source.remote.api.model.RemoteFile`.

   b. In `synchronize`, replace lines 90-101 (from `val files = provider.list().files` to the end of the
      `foldRemoteNamesOntoLocal(...)` call) with:

      ```kotlin
      val listed = provider.list().files
      index = withoutForeignEntries(index = index, listed = listed)
      val local = readLocalStates()
      val remote = foldRemoteNamesOntoLocal(
          local = local,
          // The rule the local listing applies, applied here rather than in a provider so that every provider gets
          // it: a file listed on one side only is a deletion as far as the planner can tell.
          remote = listed.filter { it.kind.matches(it.name) }.map {
              RemoteFileState(
                  key = SyncKey(kind = it.kind, name = it.name),
                  revision = it.revision,
                  contentHash = it.contentHash,
                  size = it.size,
              )
          },
      )
      ```

   c. The `contentHashes: Map<SyncKey, String?>` parameter that `apply`, `runOperation`, `download` and `resolve`
      pass along becomes `remoteFiles: Map<SyncKey, RemoteFileState>`, built in `synchronize` as
      `remoteFiles = remote.associateBy { it.key }`. The two `isSameContent(provider, local, contentHashes[key])`
      calls become `isSameContent(provider, local, remoteFiles[key]?.contentHash)`. (Plans 09 and 10 are written
      against these names.)

   d. Both `provider.download(key.kind, key.name)` calls (in `download` and in `resolve`) become
      `downloadWithinLimit(provider, key, remoteFiles)`:

      ```kotlin
      /**
       * A song is a few kilobytes of text, and a download is held in memory whole. Refused here rather than left out
       * of the listing: a file the planner cannot see on the remote is a file it takes for deleted there, and a
       * large one that is already in the library would be deleted locally for it. Thrown, it is one file's failure
       * like any other - logged, the index left alone, tried again by the next run.
       */
      private suspend fun downloadWithinLimit(
          provider: SyncProvider,
          key: SyncKey,
          remoteFiles: Map<SyncKey, RemoteFileState>,
      ): ByteArray {
          val size = remoteFiles[key]?.size ?: 0
          if (size > MAXIMUM_REMOTE_FILE_SIZE) throw RemoteFileTooLargeException(size)
          return provider.download(key.kind, key.name)
      }
      ```

      with, next to `OperationOutcome`:

      ```kotlin
      private class RemoteFileTooLargeException(size: Long) :
          Exception("The remote file is $size bytes, which is more than the $MAXIMUM_REMOTE_FILE_SIZE a run downloads.")
      ```

      and in the companion:

      ```kotlin
      /**
       * The largest remote file a run downloads: what the largest song an import reads comes to, so that a song that
       * could be brought into one library can reach the others. Generous for ChordPro text, and small enough that
       * [CONCURRENT_TRANSFERS] of them in memory at once do not trouble a phone.
       */
      const val MAXIMUM_REMOTE_FILE_SIZE = 8L shl 20
      ```

      The existing generic `catch (exception: Exception)` in `runOperation` is what handles it; nothing is added
      there. (Plan 16 then counts it among the files that could not be synced, which is the right report for it.)
      The number is plan 15's `ImportLimits.MAX_TEXT_FILE_SIZE` (8 MiB, `:data:model`) on purpose. That plan is in
      another lane: if it has landed, make this constant `= ImportLimits.MAX_TEXT_FILE_SIZE` instead of repeating
      the number; if it lands later, it should do the same.

   e. The index entries an earlier build wrote for foreign files. With both listings filtered such a key is absent
      from `local` and from `remote`, and `SyncPlanner.operationFor` answers a key that only the index knows with
      `Forget` (`SyncPlanner.kt:144`) — never with `DeleteRemote`, which needs `remote != null`. So the first run
      after the fix cannot delete anything remotely even if this step were left out. The entries are still dropped
      explicitly, before the plan is made: it states the invariant ("the engine never sees a key that is not a
      library file") where a reader looks for it, it keeps them out of the wipe guard's `index.size`, and it is
      where the orphan copies can be tidied up. New private function:

      ```kotlin
      /**
       * Drops the index entries of files that are not library files, which only an index from before the remote
       * listing was filtered can hold: such a file was downloaded into the library folder, where nothing lists it.
       *
       * That copy is deleted where it is provably only a copy - still the bytes the index recorded, with the remote
       * file still there at the revision they came from. Anything else is left alone. In particular a foreign file
       * with *no* index entry may be the last copy of something, and cannot be told from a file the user put into
       * the library folder themselves.
       */
      private suspend fun withoutForeignEntries(
          index: Map<SyncKey, SyncIndexEntry>,
          listed: List<RemoteFile>,
      ): Map<SyncKey, SyncIndexEntry> {
          val foreign = index.filterKeys { !it.kind.matches(it.name) }
          foreign.forEach { (key, entry) ->
              val isStillRemote = listed.any { it.kind == key.kind && it.name == key.name && it.revision == entry.remoteRevision }
              try {
                  val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
                  if (isStillRemote && local != null && localContentHash(local) == entry.localHash) {
                      libraryFileLocalSource.deleteLibraryFile(key.kind, key.name)
                  }
              } catch (exception: CancellationException) {
                  throw exception
              } catch (exception: Exception) {
                  // Tidying up is not worth a run: the entry is dropped either way, and the file stays where it is.
                  println("Could not remove the local copy of \"${key.path}\": ${exception.message}")
              }
          }
          return index - foreign.keys
      }
      ```

      The filtered index reaches the disk on its own: `Result.Completed.index` is built from `index`, and the
      repository writes it after every completed run, including one whose plan was empty.

5. **What is deliberately not done about orphans with no index entry.** A user who has already been through both
   runs of the bug has the file deleted from Dropbox, no index entry (the `DeleteRemote` removed it), and the only
   remaining copy in `library/songs/`. The fix must never delete that file, and step 4e does not (no index entry,
   no deletion). It is not uploaded back either: on the desktop the library folder is one the user can put their own
   files into, and the rule that those are never uploaded stands. Dropbox keeps deleted files for 30 days, so the
   release notes for the version carrying this fix should say that a file that went missing from
   `Apps/Campfire/songs` can be restored from Dropbox's "Deleted files", and that it will now stay.

6. **`data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/SyncProvider.kt`**
   — add to the KDoc of `list()`: "Whatever else the user keeps in the folder may be listed too. The engine leaves
   out what `LibraryFileKind.matches` does not recognise and refuses to download what is too large to be a song,
   so a provider does neither." No provider code changes; `DropboxSyncProvider.toRemoteFiles` stays as it is.

Not covered on purpose: a file that grows past the limit between the listing and the download (one run's window,
and the next listing catches it), and local files of any size, which are plan 15's.

## Tests
`data/repository/implementation/src/commonTest/.../sync/FakeSyncProvider.kt`: add a constructor parameter
`private val sizes: Map<SyncKey, Long> = emptyMap()` and list with `size = sizes[key] ?: file.first.size.toLong()`,
so a test can declare a huge file without allocating one. Mention it in the class KDoc.

`SyncEngineTest`, new cases (add `fun foreign(name: String) = SyncKey(kind = LibraryFileKind.SONG, name = name)` to
the companion):

- `` `a remote file that is not a library file is left where it is` `` — remote: `song(1)`, `foreign("wonderwall.pdf")`;
  local and index empty. After the run: local holds `song(1)` only, the provider still holds both, the completed
  index has the one entry `songs/song_1.cho`, `summary.downloaded == 1`.
- `` `a foreign file an earlier run indexed is forgotten rather than deleted remotely` `` — remote:
  `foreign("wonderwall.pdf")` (revision `r1`); local: empty, as the real listing would report it; index: that key
  with `remoteRevision = "r1"` (build it with `indexOf(...)` and the same `copy(entries = ...mapValues { it.copy(remoteRevision = "r1") })`
  the unreadable-file test uses). Expect: the provider still holds the file, `summary.deletedRemotely == 0`, the
  completed index has no entries.
- `` `the local copy of a foreign file an earlier run downloaded is removed while the remote one is still there` `` —
  as above, but the fake library also holds the key with the same bytes (the fake lists everything, so give
  `FakeLibraryFileLocalSource.loadLibraryFiles` the real rule: `files.filterKeys { it.kind.matches(it.name) }`).
  Expect: gone locally, still there remotely. A second variant with different local bytes: kept.
- `` `a remote file too large to be a song is not downloaded` `` — remote: `song(1)`, `song(2)` with
  `sizes = mapOf(song(2) to 9L shl 20)`; `onDownload = { if (it == song(2)) fail("Downloaded") }`. Expect
  `Result.Completed`, `song(1)` downloaded, `song(2)` neither local nor in the index, still remote.
- `` `a remote file that grew too large does not take the local one with it` `` — local and remote hold `song(1)`
  with an index that has it in step locally and at `r0` remotely (what `indexOf` builds), `sizes` over the limit.
  Expect: the local bytes unchanged, `summary.deletedLocally == 0`, the index entry unchanged.

`SyncPlannerTest` needs nothing new: `a file gone from both sides is forgotten` already pins the `Forget`.

## Verify
- Unit tests (the command in the README), then the three compile checks.
- By hand, desktop build with a Dropbox key: put `notes.txt` and a `.cho` into `Apps/Campfire/songs` on
  dropbox.com. **Sync now** twice, restart, sync again: the song arrives, `notes.txt` is still in Dropbox, and
  `library/songs/` in the app's data directory never receives it.
- The migration: with a build from before the fix, sync once with `notes.txt` in the folder (it lands in
  `library/songs/` and in `preferences/sync-index.json`), then start the fixed build. After its run `notes.txt` is
  still in Dropbox, gone from `library/songs/`, and gone from `sync-index.json`.
- Upload a file of more than 8 MB named `big.cho` to the folder: the run completes, the log says it could not sync
  `songs/big.cho`, every other file moves.

## Docs
- `documentation/sync.md`, "What it sees": add a bullet — "Only song and setlist files are synced. Anything else
  you keep in those two folders is left exactly where it is, and so is a file too large to be a song (over 8 MB)."
- `data/repository/implementation/CLAUDE.md`, the `sync/` bullet: add that the engine applies
  `LibraryFileKind.matches` to the remote listing and to the index it loads, the same rule the local listing
  applies, because a file listed on one side only reads as a deletion; and that a download above
  `MAXIMUM_REMOTE_FILE_SIZE` is a per-file failure rather than a filtered listing, for the same reason.
- `data/model/CLAUDE.md`, the `LibraryFile` / `LibraryFileKind` paragraph: `LibraryFileKind.matches` is the one
  rule for "is this name a library file", shared by the local listing and the sync engine.
- Root `CLAUDE.md`, Sync section, first bullet: append "What is not a song or a setlist by its extension is
  invisible to the engine on both sides, so whatever else the user keeps in the folder is left alone."

## Touches
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFile.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/LibraryFileLocalSourceImpl.kt`
- `data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/SyncProvider.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncPlanner.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncProvider.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeLibraryFileLocalSource.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
- `documentation/sync.md`
- `data/repository/implementation/CLAUDE.md`
- `data/model/CLAUDE.md`
- `CLAUDE.md`

## Depends on
Nothing. Plan 27 (hidden and AppleDouble files) must add its rule to `LibraryFileKind.matches` once this has
landed, and plan 16 builds on the per-file failure of step 4d.
