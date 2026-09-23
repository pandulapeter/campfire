# 09 — One library file the storage cannot read stops every sync run

**Severity:** sync broken until the file is removed (all platforms; easiest on macOS/Linux desktop and iOS) ·
**Area:** `:data:repository:implementation` (`sync/SyncEngine.kt`), `:data:source:local:implementation`
(`storage/file/FileStorage.kt`, `JvmFileStorage.kt` ×2), `:data:source:local:api` (`LibraryFileLocalSource.kt`)

**Read, not run.** This was found by reading the code at HEAD (`2065e47f`); it has not been reproduced in a running
build. The "Verification" section below is how to confirm it, and confirming it is the first step of the work.

> **Work in progress at review time.** `SyncEngine.kt`, `SyncEngineTest.kt`, `FakeSyncProvider.kt`, the root
> `CLAUDE.md`, `data/repository/implementation/CLAUDE.md` and `documentation/sync.md` had uncommitted modifications by
> another agent (remote deletions batched into one provider call) when this plan was written. Everything quoted below
> is from `git show HEAD:<path>`. Before implementing, re-locate every quoted block in whatever is committed by then —
> the line numbers will have moved, and `DeleteRemote` may no longer be a per-operation call — and apply the change to
> that code rather than to the quotes.

Merges reviewer findings 2-local#1 and 3-sync#3.

## What the user sees

Two ways in, one symptom each.

1. **A local file the storage cannot read.** Put a song called `AC\DC.cho` into the library folder on a Mac
   (`~/Library/Application Support/Campfire/library/songs/`), on Linux, or through the Files app on an iPhone — a
   backslash is an ordinary character on all three. The song never appears in the song list (the scan skips it with a
   log line), and from then on **every** sync run ends with "The sync did not finish." — nothing is uploaded or
   downloaded for any other song, on any run, until the user finds and renames a file the app never showed them. A
   file that is there but unreadable for another reason (permissions taken away, a file locked by another process)
   does the same, reported as "The library could not be read or written."
2. **A remote file this device cannot store.** A file called `a\b.cho` in the Dropbox folder (put there from another
   client) passes the "can this device hold the name" filter on every platform, is planned as a download, and fails
   on every run with an `IllegalArgumentException` — named in the summary every time, where the docs promise such a
   file is "named once in the run's summary rather than failed on every run".

## Cause

### A: one unreadable file ends the whole run

`SyncEngine.readLocalStates` (HEAD `SyncEngine.kt:268-286`) reads every listed file in parallel, with nothing around
the read:

```kotlin
    private suspend fun readLocalStates(): LocalListing = coroutineScope {
        val (tooLarge, files) = libraryFileLocalSource.loadLibraryFiles().partition { it.size > MAXIMUM_FILE_SIZE }
        tooLarge.forEach { println("Skipped \"${it.name}\": ${it.size} bytes is more than a library file can hold.") }
        // In batches for the same reason as the song scan in SongLocalSourceImpl: unbounded, a large library is
        // thousands of open handles and all of its bytes in memory at once.
        val states = files
            .chunked(READ_BATCH_SIZE)
            .flatMap { batch ->
                batch.map { file ->
                    async {
                        val key = SyncKey(kind = file.kind, name = file.name)
                        libraryFileLocalSource.readLibraryFile(file.kind, file.name)
                            ?.let { LocalFileState(key = key, hash = localContentHash(it)) }
                    }
                }.awaitAll()
            }
            .filterNotNull()
        LocalListing(states = states, tooLarge = tooLarge.mapTo(mutableSetOf()) { SyncKey(kind = it.kind, name = it.name) })
    }
```

It is called from `synchronize` (HEAD `:141`), outside `runOperation`'s per-file `try`, so any exception from one
read escapes `synchronize` and `SyncRepositoryImpl.runSynchronization` (`SyncRepositoryImpl.kt:434-446`) ends the run
as `SyncOutcome.Failure(exception.toFailureReason())`.

Why the backslash file throws: listings keep every regular file (`JvmFileStorage.list`, desktop `:45-55`, keeps
`it.isFile`; iOS `list` builds `"$directoryPath/$name"`; `LibraryFileKind.matches` checks the extension only), but
every read goes through `requireValidFileName` (`FileStorage.kt:100-102`):

```kotlin
internal fun requireValidFileName(name: String) = require(
    name.isNotEmpty() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\')
) { "Invalid file name: \"$name\"." }
```

called from `JvmFileStorage.file()` (`:162-165`), iOS `filePath()` (`FileStorage.ios.kt:148-151`) and OPFS
`fileHandle()` (`FileStorage.wasmJs.kt:127-130`). An `IllegalArgumentException` maps to
`SyncFailureReason.UNKNOWN` (`SyncRepositoryImpl.kt:657-663`). An unreadable file throws `LibraryStorageException`
(`JvmFileStorage.kt:74-76` → `failingAsStorage`), which maps to `STORAGE` — the same dead end with a different
sentence.

The docs disagree about which is intended:

- HEAD `SyncEngine.kt:100-103` (KDoc): "A failure on one file does not end the run. A song the storage cannot read must
  not keep the other four hundred from travelling".
- Root `CLAUDE.md` (HEAD `:354-355`): "A file that fails on its own is named in the run's summary rather than ending
  it". `documentation/sync.md:58`: "A file that cannot be moved does not hold up the others: Settings names it".
- `data/source/local/implementation/CLAUDE.md:50-51`: "The song scan skips such a song with a log line; **a sync run
  stops and reports a storage failure**."
- And a test asserts the stopping behaviour: HEAD `SyncEngineTest.kt:1088`,
  `` `a file that is there but cannot be read stops the run instead of being deleted remotely` ``.

**Which statement becomes the truth:** the root `CLAUDE.md`, `sync.md` and the engine's KDoc. A file that fails on
its own is one file's failure. The local implementation's `CLAUDE.md` sentence and the test are what change. The test
exists for a real reason — an unreadable file must never be taken for a *deleted* one (that deletion would reach every
device) — and that invariant is kept, strengthened in fact: see the rules below.

### B: a remote name no storage can address passes the filter

The engine splits the remote listing on `libraryFileLocalSource.canHoldFileName` (HEAD `SyncEngine.kt:150-157`),
which is `FileStorage.canHoldFileName` (`FileStorage.kt:83-89`):

```kotlin
    fun canHoldFileName(name: String): Boolean = true
```

and on the JVM (`JvmFileStorage.kt:100-102`, both copies):

```kotlin
    override fun canHoldFileName(name: String) = !isWindows || (
        name.none { it in WINDOWS_RESERVED_CHARACTERS || it < ' ' } && !name.endsWith(' ') && !name.endsWith('.')
    )
```

Neither says no to a name `requireValidFileName` refuses, so `a\b.cho` (or, from a hand-edited listing, `.`/`..`)
is "storable" on every platform, planned as a download and thrown on in `writeLibraryFile`, every run.

## The change

Invoke the **`code-style`** skill before the first edit.

### 1. The storage says no to what it cannot address (B)

`FileStorage.kt` — split the predicate out of `requireValidFileName` and make it the default answer of
`canHoldFileName`:

```kotlin
/** Whether [name] is a name rather than a path or nothing: what every storage can be asked about at all. */
internal fun isValidFileName(name: String) =
    name.isNotEmpty() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\')

internal fun requireValidFileName(name: String) = require(isValidFileName(name)) { "Invalid file name: \"$name\"." }
```

and in the interface:

```kotlin
    /**
     * Whether a file called [name] can exist in this storage at all. No storage takes a path or nothing for a name
     * ([isValidFileName]: a `/` or `\` anywhere in it, `.`, `..`), and Windows also refuses `? : * " < > |`, which are
     * legal on iOS, macOS, Linux, Android and OPFS. Asked rather than attempted because the attempt is an
     * `IllegalArgumentException` from [requireValidFileName] or an `InvalidPathException` from deep inside the JVM,
     * neither of which is a storage failure the caller could tell from any other.
     */
    fun canHoldFileName(name: String): Boolean = isValidFileName(name)
```

`JvmFileStorage.kt` (desktop **and** Android copy, which must stay identical):

```kotlin
    override fun canHoldFileName(name: String) = isValidFileName(name) && (
        !isWindows || (name.none { it in WINDOWS_RESERVED_CHARACTERS || it < ' ' } && !name.endsWith(' ') && !name.endsWith('.'))
    )
```

iOS and OPFS keep the default. (OPFS refuses `\` itself; iOS could technically hold one, but a name with a backslash
cannot be exported into a zip either — `ZipReader` reads `\` as a separator — nor reach a Windows PC or the web, so
widening `requireValidFileName` on iOS/macOS is **not** recommended: it would make the file visible here and a
problem everywhere else.)

`LibraryFileLocalSource.kt:50-55` KDoc: replace "Only Windows answers no, for a name another platform allows (`? : * "
< > |` among others)" with "Every platform answers no for a name that is a path or nothing (a `/` or `\` in it, `.`,
`..`), and Windows also for a name another platform allows (`? : * " < > |` among others)".

### 2. A file that cannot be read is left out on both sides and named (A)

In `SyncEngine.kt` (HEAD numbers; re-locate them). The rule, stated once so the code can follow it:

- A local file that is listed and whose read **throws** (anything but a cancellation) is *unreadable*. It is never
  absent: it is folded onto like a too-large file (so a remote spelling of it is recognised as it), left out of the
  remote side of the plan, and left out of the index the planner sees — so nothing is planned for it, neither a
  deletion on either side nor a download over it.
- Unlike a too-large file, its **index entry is kept** in the index the run hands back (the planner just does not see
  it): the file is most likely readable again next time (a lock, a permission), and with the entry that run makes an
  ordinary decision — including honouring a deletion made elsewhere meanwhile — instead of a first-sync comparison
  that would bring back a deleted song or take a remote edit for a conflict.
- It is named among the run's failures on every pass it is unreadable in (`SyncSummary.plus` already de-duplicates).
- If **no** listed file could be read and at least one read failed, the storage is what failed, not a file: the first
  failure is rethrown and the run ends as it does today (`STORAGE` for a `LibraryStorageException`). A library that is
  locked as a whole — an iPhone before its first unlock — then says "The library could not be read or written" once
  rather than naming four hundred files.

`readLocalStates` becomes:

```kotlin
    /**
     * Reading and hashing every file is the slow part of the preparation, so the files are read in parallel. A file
     * over [MAXIMUM_FILE_SIZE] is not read at all: nothing the app writes is that large, so it was put into the folder
     * from outside, and it is no song any other device would download.
     *
     * A file whose read fails is one file's failure, like any other in a run, and is handed back as unreadable rather
     * than left out: left out, it would be a file gone from this device, which the planner carries out as a deletion
     * on every other one. Only a library of which not one file could be read ends the run, since that is the storage
     * failing rather than a file.
     */
    private suspend fun readLocalStates(): LocalListing = coroutineScope {
        val (tooLarge, files) = libraryFileLocalSource.loadLibraryFiles().partition { it.size > MAXIMUM_FILE_SIZE }
        tooLarge.forEach { println("Skipped \"${it.name}\": ${it.size} bytes is more than a library file can hold.") }
        // In batches for the same reason as the song scan in SongLocalSourceImpl: unbounded, a large library is
        // thousands of open handles and all of its bytes in memory at once.
        val reads = files
            .chunked(READ_BATCH_SIZE)
            .flatMap { batch -> batch.map { file -> async { readLocalState(SyncKey(kind = file.kind, name = file.name)) } }.awaitAll() }
        val states = reads.mapNotNull { (it as? LocalRead.Read)?.state }
        val failures = reads.filterIsInstance<LocalRead.Failed>()
        if (states.isEmpty() && failures.isNotEmpty()) throw failures.first().exception
        LocalListing(
            states = states,
            tooLarge = tooLarge.mapTo(mutableSetOf()) { SyncKey(kind = it.kind, name = it.name) },
            unreadable = failures.mapTo(mutableSetOf()) { it.key },
        )
    }

    private suspend fun readLocalState(key: SyncKey): LocalRead = try {
        libraryFileLocalSource.readLibraryFile(key.kind, key.name)
            ?.let { LocalRead.Read(LocalFileState(key = key, hash = localContentHash(it))) }
            ?: LocalRead.Absent
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not read \"${key.path}\": ${exception.message}")
        LocalRead.Failed(key, exception)
    }

    /** What reading one listed file came to. [Absent] is a file deleted since the listing, which really is gone. */
    private sealed interface LocalRead {
        class Read(val state: LocalFileState) : LocalRead
        data object Absent : LocalRead
        class Failed(val key: SyncKey, val exception: Exception) : LocalRead
    }

    /** What [readLocalStates] found: the files it read, the ones it left alone for their size, and the ones it could not read. */
    private class LocalListing(val states: List<LocalFileState>, val tooLarge: Set<SyncKey>, val unreadable: Set<SyncKey>)
```

In the pass loop of `synchronize` (HEAD `:136-244`):

```kotlin
            val localListing = readLocalStates()
            val local = localListing.states
            val tooLarge = localListing.tooLarge
            val unreadable = localListing.unreadable
            if (pass == 0) summary = summary.plus(SyncSummary(failed = tooLarge.map { it.name }))
            // Every pass: a file can be readable in the first one and not in the second.
            summary = summary.plus(SyncSummary(failed = unreadable.map { it.name }))
            // Neither a file too large to read nor one that could not be read is absent, and both are folded onto,
            // so that a remote spelling of one is recognised as that file and left out with it.
            val excluded = tooLarge + unreadable
            ...
            val remote = foldRemoteNamesOntoLocal(
                local = local + excluded.map { LocalFileState(key = it, hash = "") },
                remote = storable.map { ... },
            ).filterNot { it.key in excluded }
            index = foldIndexNamesOntoListings(
                index = index,
                listed = (local.map { it.key } + excluded + remote.map { it.key }).toSet(),
            )
            index = index - tooLarge - unstorableKeys
            ... (KEEP_AND_UPLOAD / KEEP_AND_DOWNLOAD filters unchanged; an unreadable key is neither in `localKeys`
                 nor in `remoteKeys`, so both keep its entry) ...
            // The entry of a file that could not be read is kept for the run that can read it, but the planner does
            // not see it: with it, a file gone from this side's listing and unchanged on the other is a deletion.
            val planningIndex = index - unreadable
            val plan = SyncPlanner.plan(local = local, remote = remote, index = planningIndex)
```

and the two guards use `planningIndex` wherever they use `index` today (`index.isNotEmpty()`, `index.size`,
`hasLostEverythingLocally`). `apply(...)` is still given the full `index` (`index = index`), so the entries of
unreadable files stay in the pass's `updated` map, in every snapshot `onIndexChanged` hands out, and in the
`Result.Completed` index; no operation is ever planned for their keys, so `apply` never touches them.

Also update the class KDoc (HEAD `:100-103`) — it is right already; add one clause: "…from travelling: it is left out
of the plan on both sides, its index entry kept for the run that can read it, and named in the summary."

## Tests

`data/repository/implementation/src/commonTest/.../sync/SyncEngineTest.kt`, with the existing
`FakeLibraryFileLocalSource(onRead = …)`:

1. **Replace** `` `a file that is there but cannot be read stops the run instead of being deleted remotely` `` (HEAD
   `:1088`) with `` `a file that is there but cannot be read is named and neither deleted nor overwritten` ``: library
   `librarySongs(3)`, `onRead` throws `LibraryStorageException` for `song(2)`, provider holds the same three plus a
   remote edit of `song(3)` (`provider.files[song(3)] = "Three, edited".encodeToByteArray() to "r5"`), index =
   `syncedIndexOf(library)`. Assert: `Result.Completed`; `summary.failed == listOf("song_2.cho")`; `provider.files`
   still holds `song(2)` with its original bytes; `local.files[song(3)]` is the edit (the rest of the run moved);
   `provider.downloadCounts[song(2)]` is null; `song(2).path in completed.index.entries`.
2. `` `a remote edit to a file that cannot be read here is not downloaded over it` ``: `song(1)` unreadable locally,
   changed remotely; assert no download of it, the local bytes untouched, `song_1.cho` in `failed`.
3. `` `a library of which no file can be read ends the run as a storage failure` ``: every read throws;
   `assertFailsWith<LibraryStorageException>`, and `provider.files.keys == library.keys` (what the old test checked).
4. `` `a file that cannot be read is not taken for a deletion when the index knows it` ``: `song(1)` unreadable, index
   knows all three, remote unchanged; assert `provider.deleteCalls`/the remote still has `song(1)` (use whatever the
   committed fake exposes for deletions — at HEAD simply `song(1) in provider.files`).

`data/source/local/implementation/src/desktopTest/.../storage/file/JvmFileStorageTest.kt`:

5. `` `refuses to hold a name that is a path on every platform` ``: for `isWindows` both `true` and `false`,
   `canHoldFileName("a\\b.cho")`, `canHoldFileName("a/b.cho")`, `"."`, `".."`, `""` are all false. Extend
   `` `holds every name on a file system that is not Windows` `` only by noting in its KDoc that "every name" means every
   name `isValidFileName` lets through.

Run: `./gradlew :data:repository:implementation:desktopTest :data:source:local:implementation:desktopTest`, then the
root unit test command from `CLAUDE.md`.

## Verification

Confirm the bug first (desktop, macOS or Linux):

1. Connect a Dropbox test account, sync a small library.
2. Quit. Put `AC\DC.cho` (any ChordPro text) into `library/songs/` (macOS: `printf '{title: T}\n' > ~/Library/Application\ Support/Campfire/library/songs/'AC\DC.cho'`).
3. Start, Sync now. **Before:** "The sync did not finish."; edit another song on a second device, sync there, Sync now
   here — it does not arrive. **After:** the run completes, Settings says "1 file could not be synced. Among them:
   AC\DC.cho", and the other device's edit arrives.
4. Remote side: on dropbox.com rename a song in `Apps/Campfire…/songs/` to `x\y.cho` (or upload one). **Before:**
   named on every run and a download attempted each time (log: `Could not sync "song/x\y.cho"`). **After:** named in
   the summary, no download attempted (no such log line).
5. Permission variant (macOS/Linux): `chmod 000` one song file, Sync now: the run completes and names it; `chmod 644`
   and Sync now: nothing is re-uploaded or deleted (the index entry survived), the name is gone from the summary.

## Docs

- `data/source/local/implementation/CLAUDE.md:50-51` — replace "The song scan skips such a song with a log line; a
  sync run stops and reports a storage failure." with: "The song scan skips such a song with a log line; a sync run
  leaves it out on both sides, keeps its index entry for the run that can read it again, and names it among the
  run's failures — unless not one file of the library could be read, which is the storage failing, and ends the run
  as a storage failure." Also in the JVM bullet (`:58-64`): "A name Windows cannot hold at all … is refused by
  `canHoldFileName`" — add before it: "A name that is a path or nothing (`/`, `\`, `.`, `..`) is refused by
  `canHoldFileName` on every platform, since no storage here can address it."
- `data/repository/implementation/CLAUDE.md` (HEAD `:96-101`) — after "…and named among the run's failures." add: "So
  is a local file whose read fails: it is folded onto and left out like a too-large one, but its index entry is kept —
  the planner just does not see it — so the run that can read it again decides as usual; a library of which no file
  can be read ends the run instead." And change "(`LibraryFileLocalSource.canHoldFileName`, which only Windows answers
  no to)" to "(`LibraryFileLocalSource.canHoldFileName`: no for a name that is a path on every platform, and for
  `? : * " < > |` on Windows)".
- Root `CLAUDE.md` (HEAD `:327-329`) — "A remote file whose name the device cannot hold (`? : * " < > |` on Windows)"
  → "(a `\` anywhere, or `? : * " < > |` on Windows)". The sentence at `:354-357` is now true as it stands.
- `documentation/sync.md:22-24` — after the Windows sentence add: "A file with a `\` in its name is left where it is on
  every device, the same way." `:58` is now true as it stands; add: "A song on this device that cannot be read — a
  file another app is holding, one whose permissions were taken away — is named the same way and left alone on both
  sides until it can be."
- `LibraryFileLocalSource.kt:50-55` and `FileStorage.kt:83-89` KDocs as given in **The change**.
- Note for the implementer: `data/source/local/api/CLAUDE.md:38-41` ("one that is there and cannot be read throws
  `LibraryStorageException`, since sync would carry a missing file out as a deletion") stays true.

## Files touched

- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.kt`
- `data/source/local/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`
- `data/source/local/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorageTest.kt`
- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/LibraryFileLocalSource.kt`
- `data/source/local/implementation/CLAUDE.md`, `data/repository/implementation/CLAUDE.md`, `CLAUDE.md`,
  `documentation/sync.md`

## Depends on

- Nothing to land first. Lands **before 16** (both edit `SyncEngine.kt`; 16 is the smaller diff to rebase) and
  independently of 10 and 11, which make fewer reads fail and the remaining failures say `STORAGE`; with 10 a file
  deleted during the listing is absent (correct) rather than unreadable.
- Must be rebased onto the other agent's committed batch-deletion work in `SyncEngine.kt` / `SyncEngineTest.kt` /
  root `CLAUDE.md` / `data/repository/implementation/CLAUDE.md` / `documentation/sync.md` (see the note at the top).
- Out of scope, noted: the **song list** still silently omits a song with a backslash in its name (the scan skips it
  with a log line). Surfacing it is a presentation question for a later review.
