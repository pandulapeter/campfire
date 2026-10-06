# Remember the content of every demo file the app plants, and let a sync conflict on a file still holding exactly that content take the cloud folder's version instead of making a " (2)" copy

**Kind:** bug · **Severity:** medium · **Platforms:** all
**Challenged:** amended — the planted exception now applies only where the index has no entry for the file (with one,
a demo the user edited, synced and then edited back to the planted bytes would silently lose that change to another
device's edit); `saveUserPreferences` is no longer used by the view model once the first-run save becomes an update, so
its injection goes (the plan said to keep it); the first run's preferences document now exists from the record on,
which is noted as an accepted window.
**Files:**
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/UserPreferences.kt`
- `data/model/CLAUDE.md`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/UserPreferencesDocument.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappers.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappersTest.kt`
- `data/source/local/implementation/CLAUDE.md`
- `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SyncRepository.kt`
- `data/repository/api/CLAUDE.md`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `data/repository/implementation/CLAUDE.md`
- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/RememberDemoLibraryFilesUseCase.kt` (new)
- `domain/api/CLAUDE.md`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/SyncUseCaseImpls.kt`
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/DeleteLibraryUseCaseImplTest.kt` (its `FakeSyncRepository` gains the new override)
- `domain/implementation/CLAUDE.md`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/DemoLibrary.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/CLAUDE.md`
- `CLAUDE.md` (root: the Sync section's conflict bullet and the demo-library bullet in Conventions)

## Problem

The app plants two demo songs and one setlist into an empty library on a first run
(`presentation/.../ui/DemoLibrary.kt`, `CampfireViewModel.plantDemoLibraryOnFirstRun`), through the ordinary import,
which prettifies them. The bundled demo files change between releases: both songs differ between 4.5.0 and 4.6.0, and
again between 4.6.1 and 3eaa29e24 (`git diff 4.6.1..HEAD -- presentation/src/commonMain/composeResources/files/demo`:
link names added to Home on the Range, `{tempo: 76}` -> `{tempo: 236}`, a `{duration}` and an intro grid in House of
the Rising Sun). A change to `ChordProPrettifier` output changes the planted bytes too.

So a user who installs a new version on a new device (or reinstalls) and connects the Dropbox folder their older
devices already sync — which holds the older, untouched demo songs under the same names — gets a conflict for each
demo song. With no index entry and the file on both sides, `SyncPlanner.operationFor`
(`data/repository/implementation/.../sync/SyncPlanner.kt`) plans a `Resolve`:

```kotlin
val hasChangedLocally = indexEntry == null || indexEntry.localHash != local.hash
val hasChangedRemotely = indexEntry == null || indexEntry.remoteRevision != remote.revision
...
else -> SyncOperation.Resolve(key, remote.revision)
```

and `SyncEngine.resolveWith` (`data/repository/implementation/.../sync/SyncEngine.kt:663-682` at 3eaa29e24) avoids a
conflict copy only for equal bytes or a setlist that differs in its date:

```kotlin
if (remote.contentEquals(localBytes)) {
    return OperationOutcome(entries = mapOf(key to SyncIndexEntry(localContentHash(localBytes), revision)))
}
if (key.kind == LibraryFileKind.SETLIST) {
    ...
    if (isOnlyTheDayHere || setlistComparison.isSameApartFromDate(localBytes, remote)) {
        return takeRemote(key = key, revision = revision, localBytes = localBytes, remote = remote, onLocalFileChanged = onLocalFileChanged)
    }
}
// Free on both sides, not only here: ...
val copyName = libraryFileLock.withLock {
    libraryFileLocalSource.writeLibraryFileToFreeName(key.kind, key.name, remote) { name -> ... }
}
```

Everything else falls through to the copy: the new device's demo keeps the name and goes up over the folder's, the
folder's comes down as `traditional_american-house_of_the_rising_sun (2).cho` (and the same for Home on the Range),
and both copies reach every synced device. Nobody edited anything; the user sees two duplicate songs per demo and a
conflict summary. It happens again with every release that edits a demo file or changes the prettifier.

## Fix

### What "planted and untouched" means, and where it lives

**Recommended:** record, at the moment the app plants a demo file, the content hash of the bytes the import actually
wrote, keyed by the file's library path; a sync conflict on a file whose local bytes still hash to that record takes
the cloud folder's version (`takeRemote`, as the date-only setlist does). A content check, never a name check: a demo
song the user edited (a tag, a transposition written into the file, a verse) no longer matches and keeps the ordinary
conflict copy.

- The record is a new **local-only** `UserPreferences` field. It is not in `SyncedPreferences`, so it never reaches
  the cloud folder's `preferences.json`, and `preferences.json` is outside `library/`, so it is never exported. It is
  in the Android/iOS device backup together with `library/`, which is right: a restored library holds the same planted
  bytes the restored record names.
- Recorded on **both** planting paths — the first run's and Settings' / the empty state's "add the demo library"
  (`importDemoLibrary`). Both write the app's own bundled content that the user has not touched yet; the re-add only
  writes what is missing, and the record is only taken for files the import actually wrote under the demo's own names
  (`ImportResult.importedSongFileNames` / `importedSetlistFileNames` filtered to `DemoLibrary`'s names), so a user's
  own file under a demo name that the import disregarded, skipped or numbered past is never recorded.
- Applied in `resolveWith` **only where the index has no entry for the file** (`indexEntry == null`): the first time
  this device compares that name with this folder — a fresh connection, a reconnection (the index goes with the
  connection), a restored device, a demo re-added after its deletion went through (the deletion drops the entry). With
  an entry, the planner only plans a `Resolve` when the local hash differs from the index's, and a local file back at
  its planted bytes then means the user changed it *back*: add a tag on device A, sync, take it off again offline while
  device B edits the verse — an unconditional rule would take B's version with the tag still on and drop A's change
  without a copy. That is a change the user made, and "a file changed on both sides is never merged" keeps both. The
  narrowing loses nothing the bug needs: every case in Problem has no index entry. `download`'s "edited under the
  write" path passes `index[key]` into `resolveWith` too and is covered the same way.
- The hash is `localContentHash` (`:data:source:remote:api`'s `hashing/LocalContentHash.kt`, SHA-256 hex) — the same
  function the index uses — computed in the data layer, so `:presentation` never hashes anything.
- Applies to setlists too (the record is per kind and name). The demo setlist did not change between releases, and one
  that differs only in its date is already handled; this just keeps the next content change of
  `getting_started.setlist.json` from becoming a copy.

Alternatives considered:

- **Compare against the bundled resources at sync time** (an interface in `:data:repository:api` implemented by
  `:presentation`, or the demo files moved into a data module). Rejected: what is on disk is the *prettified* import
  output of the version that planted it, not the current bundle, so a device that planted 4.6.0's demo and was then
  updated would not recognise its own untouched file; it also drags resource reading into the data layer.
- **Ship a table of the planted hashes of every past release.** Would also cover devices planted before this fix
  (see "Limits" below), but every release (and every prettifier change) has to regenerate it by importing old demo
  files with old code; not worth it for two songs. Could be added later on top of the recommended record.
- **Name check (`{tag: Demo}` or the file name).** Rejected by the requirement: it would discard a demo song the user
  edited.
- **Record in `sync-index.json`.** Rejected: that document is per account and adopted/dropped with the connection,
  planting happens before any account exists, and it is not in the device backup while the library is.

Limits (accepted, say so in the docs): a device that planted its demos *before* this fix has no record, so when *it*
is the side with the older demo (an old installation updated, then connected to a folder another device filled with
newer demos) it still makes copies. The usual case — a new installation connecting to an existing folder — is fixed.
The record is taken right after the import's write, so an edit saved in the few milliseconds between the write and the
record would be taken for untouched; the first run's import happens behind the launch screen, and the Settings re-add
behind its own disabled row, so this is not reachable by hand. On the first run the record is the first write of the
preferences document, a moment before the `seenWhatsNewVersions` update that used to be the first: a process killed
between the two (milliseconds, behind the launch screen) starts next time as no first run, so without the welcome and
with What's new for the installed version once. Accepted; folding the record into the first-run write would need the
hashes to travel back out of `applyImportPlan` for one path only.

### 1. `:data:model` — the field

`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/UserPreferences.kt`, after
`seenWhatsNewVersions`:

```kotlin
    /** Versions already introduced here, including the first installed version whose introduction is skipped. */
    val seenWhatsNewVersions: Set<String> = emptySet(),
    /**
     * The content hash of every demo file this device planted, as it was written, keyed by its library path
     * (`songs/<file name>`, `setlists/<file name>`: the folder is `LibraryFileKind.id`). Sync takes the cloud folder's
     * version of a file that still has exactly that content instead of keeping both, see `SyncRepository`. Never
     * exported or synced.
     */
    val demoLibraryContentHashes: Map<String, String> = emptyMap(),
```

Add one line about it to `data/model/CLAUDE.md` where the other `UserPreferences` fields are listed (local only, never
exported or synced, what sync uses it for).

### 2. `:data:source:local:implementation` — document and mapper

`model/UserPreferencesDocument.kt`, after `val seenWhatsNewVersions: Set<String> = emptySet(),`:

```kotlin
    val demoLibraryContentHashes: Map<String, String> = emptyMap(),
```

`mapper/UserPreferencesMappers.kt`: in `UserPreferencesDocument.toModel()` after
`seenWhatsNewVersions = seenWhatsNewVersions,` add
`demoLibraryContentHashes = demoLibraryContentHashes.filterValues { it.isNotBlank() },` and in
`UserPreferences.toDocument()` after `seenWhatsNewVersions = seenWhatsNewVersions,` add
`demoLibraryContentHashes = demoLibraryContentHashes,`. Mention the field (defaults to empty for older documents) next
to the `seenWhatsNewVersions` sentence in `data/source/local/implementation/CLAUDE.md`.

### 3. `:data:repository:api` — the contract

`SyncRepository.kt`, after `forgetStoredConnection()`:

```kotlin
    /**
     * Writes down what the demo files the app has just planted hold, so that a run which finds a different version of
     * one of them in the cloud folder - planted there by an older version of the app - takes that version instead of
     * keeping both, for as long as this device's file still holds exactly what was planted. Names of files that are not
     * there are ignored. Never fails: a record that could not be taken only means a conflict copy later.
     */
    suspend fun rememberDemoLibraryFiles(songFileNames: Collection<String>, setlistFileNames: Collection<String>)
```

Add it to the `SyncRepository` bullet of `data/repository/api/CLAUDE.md`.

### 4. `:data:repository:implementation` — the record and the engine

`SyncRepositoryImpl.kt`: keep the `userPreferencesRepository`, `libraryFileLocalSource` and `libraryFileLock`
constructor parameters as they are (they are already there) but make them `private val` so that the new method can use
them, and build the engine with the lookup:

```kotlin
    private val engine = SyncEngine(libraryFileLocalSource, libraryFileLock, setlistComparison) { key ->
        userPreferencesRepository.loadUserPreferencesIfNeeded()?.demoLibraryContentHashes?.get(key.path)
    }
```

and implement:

```kotlin
    override suspend fun rememberDemoLibraryFiles(songFileNames: Collection<String>, setlistFileNames: Collection<String>) {
        try {
            val keys = songFileNames.map { SyncKey(LibraryFileKind.SONG, it) } + setlistFileNames.map { SyncKey(LibraryFileKind.SETLIST, it) }
            // Read under the lock the repositories write under, so the bytes recorded are the ones the import left.
            val hashes = libraryFileLock.withLock {
                keys.mapNotNull { key -> libraryFileLocalSource.readLibraryFile(key.kind, key.name)?.let { key.path to localContentHash(it) } }
            }
            if (hashes.isNotEmpty()) {
                userPreferencesRepository.updateUserPreferences { it.copy(demoLibraryContentHashes = it.demoLibraryContentHashes + hashes) }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not remember the demo library: ${exception.message}")
        }
    }
```

(imports: `LibraryFileKind`, `localContentHash` from `com.pandulapeter.campfire.data.source.remote.api.hashing`). This
changes no field `SyncedPreferences.of` reads, so `SyncedPreferencesSync.localChanges` does not schedule a run for it.

`sync/SyncEngine.kt`: one new constructor parameter with a default, so that none of the existing
`SyncEngine(local, LibraryFileLock(), NoSetlistComparison)` calls in the tests change:

```kotlin
internal class SyncEngine(
    private val libraryFileLocalSource: LibraryFileLocalSource,
    private val libraryFileLock: LibraryFileLock,
    private val setlistComparison: SetlistComparison,
    /** What the app planted under a name, as a content hash, or null for a file it did not plant; see [resolveWith]. */
    private val plantedContentHash: suspend (SyncKey) -> String? = { null },
) {
```

In `resolveWith`, between the `remote.contentEquals(localBytes)` check and `if (key.kind == LibraryFileKind.SETLIST)`:

```kotlin
        // A demo file this device planted and nobody has touched since, met in this folder for the first time: the
        // folder's version is an older or newer demo that another device planted under the same name (or the user's
        // edit of one), and keeping both would be two copies of every demo song on every device. Nothing anybody wrote
        // is lost by taking it, which a content check rather than a name check is what guarantees. Not with an index
        // entry: a file the last run saw with other bytes and that is back at its planted ones was changed back on
        // purpose, and that change is kept like any other.
        val plantedHash = if (indexEntry == null) plantedContentHash(key) else null
        if (plantedHash != null && plantedHash == localContentHash(localBytes)) {
            return takeRemote(key = key, revision = revision, localBytes = localBytes, remote = remote, onLocalFileChanged = onLocalFileChanged)
        }
```

Extend `resolveWith`'s KDoc and the class KDoc's last paragraph ("[setlistComparison] is the one look...") with one
sentence each on the planted exception. `takeRemote` already writes only over the bytes it was compared with and
counts the file as a download. Update the conflict paragraph of `data/repository/implementation/CLAUDE.md` ("A setlist
is the exception: ...") to name the second exception, and the root `CLAUDE.md` Sync bullet "A file changed on both
sides is never merged ... except a setlist whose two versions differ only in the day ..." likewise ("... or a demo
file this device planted that still holds exactly what was planted, met in the folder for the first time: the cloud
folder's version is taken").

### 5. `:domain` — the use case

New `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/RememberDemoLibraryFilesUseCase.kt`
(MPL header as every file):

```kotlin
interface RememberDemoLibraryFilesUseCase {

    /** See `SyncRepository.rememberDemoLibraryFiles`: what the demo files just planted under these names hold. */
    suspend operator fun invoke(songFileNames: Collection<String>, setlistFileNames: Collection<String>)
}
```

In `domain/implementation/.../useCases/SyncUseCaseImpls.kt`, beside `ForgetSyncConnectionUseCaseImpl`:

```kotlin
@Factory
class RememberDemoLibraryFilesUseCaseImpl internal constructor(
    private val syncRepository: SyncRepository,
) : RememberDemoLibraryFilesUseCase {

    override suspend operator fun invoke(songFileNames: Collection<String>, setlistFileNames: Collection<String>) =
        syncRepository.rememberDemoLibraryFiles(songFileNames, setlistFileNames)
}
```

Add `override suspend fun rememberDemoLibraryFiles(songFileNames: Collection<String>, setlistFileNames: Collection<String>) = Unit`
to the `FakeSyncRepository` in `DeleteLibraryUseCaseImplTest.kt`. Name the new use case in the sync paragraph of
`domain/api/CLAUDE.md` ("Sync adds `GetSyncStateUseCase`, ...") and in `domain/implementation/CLAUDE.md` where the sync
use case file is described. No Koin module changes: the `@Factory` is found by the existing scan.

### 6. `:presentation` — record what was planted

`DemoLibrary.kt`: add

```kotlin
    /** The songs of [result] the import wrote under the demo's own names, see [setlistFileNamesWrittenBy]. */
    fun songFileNamesWrittenBy(result: ImportResult) = result.importedSongFileNames.filter { it in songFileNames }

    /** The same for the setlist: a numbered copy of either is not the demo's, and is not remembered as one. */
    fun setlistFileNamesWrittenBy(result: ImportResult) = result.importedSetlistFileNames.filter { it == SETLIST_FILE_NAME }
```

`CampfireViewModel.kt`:

- inject `private val rememberDemoLibraryFiles: RememberDemoLibraryFilesUseCase,` (constructor, next to
  `forgetSyncConnection`);
- `ImportRequest` gains `val isDemoLibrary: Boolean = false,` (document it in the class KDoc: "the files are
  [DemoLibrary]'s, and what of them is written is remembered for sync");
- `plantDemoLibraryOnFirstRun` builds its request with `isDemoLibrary = true`;
- `enqueueImport` gains `isDemoLibrary: Boolean = false` passed into the `ImportRequest`, and `importDemoLibrary`
  calls `enqueueImport(files, isDemoLibrary = true)`;
- in `applyImportPlan`, inside the existing `withContext(NonCancellable)` block, so that the record is taken however the
  view model goes away:

  ```kotlin
            val result = withContext(NonCancellable) {
                importFiles.invoke(
                    plan = plan,
                    resolution = resolution,
                ) { if (request.shouldAnnounceResult) _importProgress.value = it }
                    .also { result ->
                        if (request.isDemoLibrary) {
                            rememberDemoLibraryFiles(
                                songFileNames = DemoLibrary.songFileNamesWrittenBy(result),
                                setlistFileNames = DemoLibrary.setlistFileNamesWrittenBy(result),
                            )
                        }
                    }
            }
  ```

- in `plantDemoLibraryOnFirstRun`, replace the whole-document save that follows the planting

  ```kotlin
                userPreferencesState.first { it !is DataState.Loading }.data?.let {
                    saveUserPreferences(it.copy(seenWhatsNewVersions = it.seenWhatsNewVersions + CAMPFIRE_VERSION_NAME))
                }
  ```

  with a read-modify-write, since `userPreferencesState` is a view-model `StateFlow` that can lag the repository by a
  dispatch and saving a copy of it would write the record just taken back out:

  ```kotlin
                updateUserPreferences { it.copy(seenWhatsNewVersions = it.seenWhatsNewVersions + CAMPFIRE_VERSION_NAME) }
  ```

  `UpdateUserPreferencesUseCase` reads the preferences first (again after a failed read) and changes nothing if they
  cannot be read, which is what the comment above it asks for ("With nothing read there is nothing to write"); keep
  that comment, adjusted. The set is empty on a first run, so the transform always changes something and is written.
  This was the view model's **only** `saveUserPreferences` call (checked at 3eaa29e24), so remove the
  `saveUserPreferences: SaveUserPreferencesUseCase` constructor parameter and its import; the use case itself stays
  (the plugin does not care about an unused `@Factory`, and the repository's whole-document save is still documented).

In `presentation/CLAUDE.md`'s `ui/DemoLibrary.kt` bullet add one sentence: both ways of planting remember what they
wrote under the demo's own names (`RememberDemoLibraryFilesUseCase`), which is what lets sync take another version's
untouched demo from the cloud folder instead of a copy of each. In the root `CLAUDE.md` Conventions bullet "The app is
shipped with two songs and one setlist" add the same in a clause.

Nothing is platform specific: all of this is common code over `preferences.json` and the library, which the four
platforms store in their own way (OPFS on the web).

## Tests

All in `desktopTest` runs; `./gradlew :data:source:local:implementation:desktopTest :data:repository:implementation:desktopTest :domain:implementation:desktopTest`.

`SyncEngineTest.kt` (build like the existing date-only setlist tests: `FakeLibraryFileLocalSource`, `FakeSyncProvider`,
`SyncEngine(local, LibraryFileLock(), NoSetlistComparison) { key -> ... }`, `SyncIndexDocument()` for a first
connection, `indexOf(...)` for an earlier run, the `song(n)` key helper). Both of the first two were run against this
design in a scratch worktree at 3eaa29e24 and pass with the change, alongside the 78 existing tests:

```kotlin
    @Test
    fun `an untouched planted demo song takes the cloud folder's version and keeps no copy`() = runTest {
        val planted = "Demo, as this version plants it".encodeToByteArray()
        val there = "Demo, as an older version planted it".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to planted))
        val provider = FakeSyncProvider(files = mapOf(song(1) to there))
        val result = SyncEngine(local, LibraryFileLock(), NoSetlistComparison) { key ->
            if (key == song(1)) localContentHash(planted) else null
        }.synchronize(
            provider = provider,
            document = SyncIndexDocument(),
            accountId = ACCOUNT_ID,
            onProgress = {},
            onIndexChanged = {},
            onLocalFileChanged = {},
            deletionPolicy = SyncDeletionPolicy.ASK,
        )
        val completed = assertIs<SyncEngine.Result.Completed>(result)
        assertContentEquals(there, local.files[song(1)])
        assertEquals(setOf(song(1)), local.files.keys)
        assertEquals(setOf(song(1)), provider.files.keys)
        assertContentEquals(there, provider.files.getValue(song(1)).first)
        assertEquals(emptyList(), completed.summary.conflicts)
        assertEquals(localContentHash(there), completed.index.entries.getValue(song(1).path).localHash)
    }

    @Test
    fun `a planted demo song edited here is kept next to the cloud folder's version`() = runTest {
        val planted = "Demo, as this version plants it".encodeToByteArray()
        val edited = "Demo, with the user's own verse".encodeToByteArray()
        val there = "Demo, as an older version planted it".encodeToByteArray()
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to edited))
        val provider = FakeSyncProvider(files = mapOf(song(1) to there))
        val result = SyncEngine(local, LibraryFileLock(), NoSetlistComparison) { key ->
            if (key == song(1)) localContentHash(planted) else null
        }.synchronize(/* same arguments as above */)
        assertContentEquals(edited, local.files[song(1)])
        assertContentEquals(there, local.files[SyncKey(kind = LibraryFileKind.SONG, name = "song_1 (2).cho")])
        assertContentEquals(edited, provider.files.getValue(song(1)).first)
        assertEquals(listOf("song_1 (2).cho"), assertIs<SyncEngine.Result.Completed>(result).summary.conflicts)
    }
```

Add also:

- `a file planted under another name does not make this one yield`: the lookup answers a hash for `song(2)` only;
  `song(1)` with different content on both sides still gets its `" (2)"` copy (guards against keying by anything but
  the exact key).
- `a planted demo song changed back here after a synced edit keeps its copy`: the lookup answers
  `localContentHash(planted)` for `song(1)`; the library holds `planted`, the provider `there` at a new revision, and
  the index (`indexOf(...)`) records `song(1)` with the hash of an edited version and the old revision; the run makes
  the `" (2)"` copy and names it in the conflicts, as without the lookup (guards the `indexEntry == null` condition).
- `a planted demo setlist that changed in more than its day takes the folder's version`: `GIG` with
  `NoSetlistComparison`, the lookup answering `localContentHash(here)` for `GIG`; no copy, the folder's bytes locally.

`SyncRepositoryImplTest.kt` (its `repository(...)` helper already takes a `FakeLibraryFileLocalSource` and a
`FakeUserPreferencesRepository`):

- `rememberDemoLibraryFiles records the content of the files that are there`: library holds
  `songs/a.cho` and `setlists/b.setlist.json`; call with `listOf("a.cho", "missing.cho")` and `listOf("b.setlist.json")`;
  `userPreferencesRepository.current.demoLibraryContentHashes` is exactly
  `mapOf("songs/a.cho" to localContentHash(a), "setlists/b.setlist.json" to localContentHash(b))`, and an earlier entry
  for another path survives (merge, not replace).
- `a run takes the folder's version of a remembered demo song`: remember `a.cho`, connect the fake provider holding a
  different `a.cho`, run `synchronize()` to completion the way the file's other run tests do, and check the library
  holds the provider's bytes and the outcome names no conflict — the end-to-end wiring of the lookup.

`UserPreferencesMappersTest.kt`: `demoLibraryContentHashes` round-trips through `toDocument()` / `toModel()`, and a
legacy document without the field reads as an empty map (follow the existing `seenWhatsNewVersions` test).

The view model wiring (`isDemoLibrary`, the filtering in `DemoLibrary`) is UI code and is checked by hand.

## Manual check

Needs a Dropbox-enabled build (`campfire.dropbox.appKey` in `local.properties`) and a test Dropbox account.

1. Prepare a folder with the **older** demo: install release 4.6.1 (or build that tag) on device A with a fresh data
   directory, let it plant the demo, connect Dropbox, sync. The cloud folder's `songs/` holds the two 4.6.1 demo songs.
2. On device B (another platform, or the desktop with a fresh data directory), install the build with this fix,
   fresh. The demo is planted. Connect the same Dropbox account. After the first run: the Songs list shows exactly two
   demo songs and no `(2)` copies; the sync summary names no conflict; House of the Rising Sun reads `{tempo: 76}` and
   no intro grid (the folder's version). Device A, synced again, also has no copies.
3. Edited demo is kept: repeat 2 on a fresh B, but before connecting toggle a tag on House of the Rising Sun (or edit
   a line). After the first run that song has its ` (2)` copy next to it (the folder's version), Home on the Range has
   none.
4. Re-add path: on B, delete one demo song, sync (the deletion reaches the folder), use Settings → Library → add the
   demo library; then on A replace that song's file in the folder with another version (edit it on A, sync). Sync B:
   the re-added, untouched song takes A's version without a copy.
5. Check `preferences/preferences.json` on B holds `demoLibraryContentHashes` with `songs/…` and `setlists/…` keys,
   the cloud folder's `preferences.json` does not, and an exported library zip does not contain it.
6. Smoke the four platforms' first run (Android, iOS, desktop, web): the demo is planted, the welcome sheet shows, and
   Settings → What's new does not reappear on the next launch (the `seenWhatsNewVersions` save now goes through
   `updateUserPreferences`).
