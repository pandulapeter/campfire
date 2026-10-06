# Write the day into an undated setlist under the repository's locks, from a fresh read, instead of from the unlocked bulk read

**Challenged:** amended — added the launch-run timing note (the day may now reach the cloud one run later; harmless with plan 20) with an optional mutex around `writeDays`, and made the repository test's fake date-and-save in `loadSetlist` so the race test asserts the repository's behaviour rather than the fake's.

**Kind:** bug (race)  ·  **Severity:** low  ·  **Platforms:** all
**Files:**
- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/SetlistLocalSource.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SetlistLocalSourceImpl.kt`
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ParsedSetlist.kt` (KDoc)
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImplTest.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SetlistLocalSourceTest.kt`, `RenameTest.kt`, `LibraryListingTest.kt` (call sites of `loadSetlists()`)
- `data/repository/implementation/CLAUDE.md`, `data/source/local/implementation/CLAUDE.md`, `data/model/CLAUDE.md` (the `ParsedSetlist` mention)

## Problem

`SetlistLocalSourceImpl.loadSetlists()` reads every setlist file in one batch and, for each that names no day, saves it
dated (`toDatedModel` → `saveSetlist(setlist)`) — built from the text the batch read, at whatever moment the loop gets
to it:

```kotlin
files.zip(fileStorage.readTexts(StorageDirectory.SETLISTS, files.map { it.name })).mapNotNull { (file, answer) ->
    ...
        is BatchRead.Text -> SetlistDocumentFormat.decode(answer.text).toDatedModel(file.name, size = file.size)
```

Two callers in `SetlistRepositoryImpl` run that outside every lock:

```kotlin
override suspend fun loadDataFromLocalSource() = setlistLocalSource.loadSetlists()   // first read and rescan()
...
override suspend fun loadSetlistFileNamesNaming(songFileName: String): List<String> {
    ...
    val onDisk = setlistLocalSource.loadSetlists().filter { it.names() }.map { it.fileName }
```

Every other writer of a setlist file holds `LibraryFileLock` (the repository's `writing { }`, and `SyncEngine`'s
download/conflict writes). So an undated file read by this batch and then replaced — by a sync download of another
device's version, or by the user's own change through `updateSetlist` (which reads the file afresh under the locks and
dates it there) — is written back with the **old** content plus a date. The newer version is gone locally, and the next
sync run uploads the stale one over it (local hash ≠ index, remote unchanged → `Upload`). A batch of 200 setlists was
measured at 1.2 s on the desktop, so the window is real, though it needs an undated file *and* a write to that same
file during it — chiefly the first launch after updating from 4.6.1, while a launch sync run or an edit lands.

## Fix

Make the bulk read pure and do the dating write where every other write is done.

1. `SetlistLocalSource.loadSetlists()` returns `List<ParsedSetlist>` and **writes nothing**: each setlist dated in memory
   with today where the file names no day, `isDated` saying which. (`ParsedSetlist` already exists for the import with
   exactly this meaning; generalise its KDoc from "read for an import" to "a setlist document as read".)
   `loadSetlist(fileName)` keeps dating and saving: its two callers (`refresh`, under `libraryFileLock`, and `latest`,
   under `writing { }`) already hold the lock. Update both KDocs in the api.
2. `SetlistRepositoryImpl`:
   - `loadDataFromLocalSource()` maps to `.setlist` and records the undated names in a field
     (`private val undatedFileNames = mutableSetOf<String>()` guarded by a small `Mutex`, or an `atomicfu`/`MutableStateFlow<Set<String>>` — anything the code already uses).
   - A private `suspend fun writeDays()` drains that set and, for each name, runs
     `writing { latest(name)?.let { dated -> updateData { current -> current.orEmpty().filterNot { it.fileName == dated.fileName } + dated } } }`
     — `latest` reads the file afresh under both locks and `loadSetlist` dates and saves *that* content, or leaves a
     file that meanwhile got a day alone. It announces nothing (`libraryChanges` is not called), as today.
   - Call `writeDays()` at the end of `loadSetlistsIfNeeded()` and `rescan()`, **after** `loadDataIfNeeded()` /
     `reloadData()` have returned. It must not run inside `loadDataFromLocalSource`: `latest()` holds `writeMutex` and
     `libraryFileLock` while it calls `loadDataIfNeeded()` (the base read mutex), so taking either lock inside the
     read would deadlock against it.
   - `loadSetlistFileNamesNaming` maps `.setlist` and writes nothing (it only decides which files to ask; `updateSetlist`
     reads each again).
3. A name left in the set because the first read happened inside `latest()` (a change made before anything listed the
   setlists) is dated by the next `loadSetlistsIfNeeded()`/`rescan()`; until then it reads as today, as it does now
   between a failed write and the next read.
4. Timing against sync, checked: `writeDays` takes `writing { }` (writeMutex → `LibraryFileLock`) and inside it `latest`
   takes the base read mutex — the order every write already uses; nothing holding the base read mutex takes either of
   the other two any more, since the bulk read writes nothing. `refresh` (under `LibraryFileLock`) still dates through
   `loadSetlist`, and its `rescan()` fallback runs outside that lock. What changes is when the day lands: today the
   first read writes it before it publishes, and `SyncRepositoryImpl`'s first-read wait (`if (setlists.first() is
   Loading) loadSetlistsIfNeeded()`) makes the launch run see the dated files and upload them. Afterwards the state is
   `Idle` while `writeDays` is still writing, so the launch run can list a file still undated (= its index entry → no
   operation) and the day then waits for the next run, since the dating announces nothing. Every engine write is
   re-checked under `LibraryFileLock`, so nothing is lost, and with plan 20 a day another device uploads meanwhile is
   settled without a copy. Optional, if the executor wants the old timing back: hold a private `Mutex` in `writeDays`
   from draining the set to its last write, so a second `loadSetlistsIfNeeded()` (the run's wait) waits for the writes
   in flight instead of finding the set already drained. Do not make the run's wait call `loadSetlistsIfNeeded()`
   unconditionally: that would read again a repository whose read failed, which the run deliberately does not do.

Docs: `data/repository/implementation/CLAUDE.md`'s `SetlistRepositoryImpl` bullet ("`loadSetlistFileNamesNaming` reads
every setlist file afresh … outside the locks") gains that the reads write nothing and the day an undated file is given
is written under both locks from a fresh read; `data/source/local/implementation/CLAUDE.md`'s sentence "one read from the
library without one is given today's and saved with it at once (`SetlistLocalSourceImpl.toDatedModel`, …)" says it is
saved by the repository, under its locks, after the read. The root `CLAUDE.md` wording ("is given the day it is first
read on and saved with it at once, around the repository") changes to "saved with it right after that read, through the
repository's locks but announcing nothing".

## Tests

- `SetlistRepositoryImplTest` (its `FakeSetlistLocalSource`, whose `loadSetlists()` changes return type to
  `List<ParsedSetlist>`; give it a set of undated names and make its `loadSetlist` date-and-save those the way the real
  one does, recording each save): `loadSetlists()` reports `a.setlist.json` undated and, in a hook run after it has built
  its answer, replaces the file's entries (simulating a download) → after `loadSetlistsIfNeeded()` the one save is of the
  changed entries with the day, and no save carries the old entries. And: an undated
  setlist is saved dated exactly once after `loadSetlistsIfNeeded()`; `loadSetlistFileNamesNaming` saves nothing.
- `SetlistLocalSourceTest` (desktopTest): `loadSetlists()` on an undated file returns `isDated = false` with today and
  leaves the file's bytes untouched; `loadSetlist()` still saves it dated (replace the existing assertion at
  `assertEquals(listOf(today), setlistLocalSource.loadSetlists().map { it.date })` accordingly).

## Manual check

None that can be timed by hand reliably. Sanity check on the desktop: put an undated `*.setlist.json` into the library
folder, launch, and confirm the file has a `"date"` a moment after the setlists screen shows it and that a second launch
the next day keeps that day.
