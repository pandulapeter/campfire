# Settle a setlist that changed on both sides only in its date by taking the cloud folder's copy, rather than keeping a ` (2)` conflict copy of every setlist

**Challenged:** amended — added the second rule (step 4: a local change that is only the day an undated index version was given yields to a real remote edit, instead of a conflict copy of every setlist another device edited meanwhile), gave the contract that second member (`withoutDate`), named the ~60 `SyncEngine(...)` constructions in `SyncEngineTest` that need the new argument, spelled out what test 5 asserts, and added the first-connection case to the cost.

**Kind:** bug  ·  **Severity:** high  ·  **Platforms:** all (sync)
**Files:**
- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/SetlistComparison.kt` (new)
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SetlistComparisonImpl.kt` (new)
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SetlistComparisonImplTest.kt` (new; or `desktopTest` beside `SetlistLocalSourceTest.kt` if the module's common tests cannot reach `internal` there — they can, `SetlistMappersTest` is in `commonTest`)
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt` (constructor call)
- `data/repository/implementation/CLAUDE.md`, `data/source/local/implementation/CLAUDE.md`, `data/source/local/api/CLAUDE.md` (if it lists the contracts), root `CLAUDE.md` (Sync section)

## Problem

Since 46746e2b1 ("Enforce setlist dates"), every setlist file without a `date` is dated **on the device that reads it,
with the day it is read**, and saved at once:

`data/source/local/implementation/.../source/SetlistLocalSourceImpl.kt`
```kotlin
private suspend fun SetlistDocument.toDatedModel(fileName: String, size: Long): Setlist {
    val setlist = toModel(fileName, size = size, undatedDay = today())
    if (isDated) return setlist
    return try {
        saveSetlist(setlist)
    ...
private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())
```

Release 4.6.1 (the last tag) still has `Setlist.date: LocalDate?` and plants the demo setlist undated (59b4a5d80), and
every setlist written before dates existed is undated. So on the update after 4.6.1, a user with two synced devices gets:

1. Device A updates on day DA, reads its library, rewrites every undated setlist with `"date": "DA"`. Its next sync run
   sees each as changed locally and unchanged remotely → `SyncOperation.Upload`. The cloud folder now holds DA.
2. Device B updates on day DB ≠ DA and rewrites the same files with `"date": "DB"`. Its run finds each one changed
   locally (hash ≠ index) **and** remotely (revision ≠ index) → `SyncPlanner.operationFor` returns
   `SyncOperation.Resolve` ("Both moved").
3. `SyncEngine.resolve` only turns that into a record when the bytes are identical:
   ```kotlin
   if (isSameContent(provider, local, remoteFiles[key]?.contentHash)) { ... }
   ```
   and `resolveWith` likewise only for `remote.contentEquals(localBytes)`. The two documents differ in the date string,
   so it writes the cloud version next to the local one as `getting_started (2).setlist.json`,
   `summer_gig (2).setlist.json`, … — **one conflict copy for every setlist the user had**, on every device, the demo
   setlist included (it is reviewer R4's finding #1: each installation plants it and dates it with its own install or
   update day; a fresh installation connecting to an existing folder hits the same path, since with no index entry
   both sides count as changed).

The same happens with no update involved whenever a device without an index entry (a new connection) meets a dated copy
in the folder dated on another day — e.g. the demo setlist planted on two devices on two different days.

Giving the bundled `getting_started.setlist.json` a fixed date does **not** fix this and is not recommended: the
installations that already planted it (4.6.1) still date it with their update day, and Settings' "add the demo songs"
would then compare a dated incoming file against the library's differently dated one
(`ImportPlanner.holdsTheSameAs(other, isOtherDated = true)` compares dates) and ask a replace/keep-both question
where today it finds the setlist `IDENTICAL`. `DemoLibrary.isPresentIn` matches by file name only, so the offer itself
is unaffected either way.

## Fix

Teach the engine one equivalence: **two setlist documents that are the same apart from their `date` are the same
setlist**, and when a `Resolve` (or a download that found a save under its write) meets that case it **takes the cloud
folder's bytes** instead of keeping both.

1. `:data:source:local:api` — a new contract, since `LibraryFileLocalSource` deliberately never looks inside files and
   `SetlistDocument` is internal to the implementation:
   ```kotlin
   /** What sync needs to know about the inside of a setlist file, without seeing its format. */
   interface SetlistComparison {
       /**
        * Whether [first] and [second] are the same setlist document apart from the day they name - which every device
        * that read an undated file gave it on its own. False when either is not a setlist document.
        */
       fun isSameApartFromDate(first: ByteArray, second: ByteArray): Boolean

       /**
        * [bytes] as this version writes the document with no day in it, or null when they are not a setlist document:
        * what an undated file was before a read gave it a day, for a file the app wrote (see step 4).
        */
       fun withoutDate(bytes: ByteArray): ByteArray?
   }
   ```
2. `:data:source:local:implementation` — `@Single internal class SetlistComparisonImpl : SetlistComparison` (found by the
   module's existing `@ComponentScan`, bound to its interface by Koin Annotations): decode both with
   `bytes.decodeLibraryText()` and `SetlistDocumentFormat.decode`, catch `Exception` → `false`, and compare
   `first.copy(date = null, priority = 0) == second.copy(date = null, priority = 0)` (documents rather than models, so
   that the entry de-duplication of `toModel` does not make two different files equal; `priority` is the dropped field
   older versions wrote; `unknownFields` stay in the comparison, since they are part of the data class's equality).
   `isCountdownShown` is **not** ignored: no automatic step changes it. `withoutDate` decodes the same way and returns
   `SetlistDocumentFormat.encode(document.copy(date = null)).encodeToByteArray()` (null on `Exception`). Declare the
   contract a `fun interface` only if it keeps one member; with two, a plain interface and an `object` fake in tests.
3. `SyncEngine` gets the comparison as a constructor parameter:
   `internal class SyncEngine(libraryFileLocalSource, libraryFileLock, private val setlistComparison: SetlistComparison)`.
   In `resolveWith`, right after the existing `remote.contentEquals(localBytes)` check (so it covers both `resolve` and
   the `download` path that calls `resolveWith` with `changed`):
   ```kotlin
   if (key.kind == LibraryFileKind.SETLIST && setlistComparison.isSameApartFromDate(localBytes, remote)) {
       // Two devices that dated one undated setlist on different days: the same setlist, so the folder's day is taken
       // rather than a copy kept for every setlist the library had. Written only over the bytes it was compared with.
       val isWritten = libraryFileLock.withLock {
           val current = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
           (current != null && current.contentEquals(localBytes)).also { isSame ->
               if (isSame) {
                   libraryFileLocalSource.writeLibraryFile(key.kind, key.name, remote)
                   onLocalFileChanged(key)
               }
           }
       }
       return if (isWritten) {
           OperationOutcome(entries = mapOf(key to SyncIndexEntry(localContentHash(remote), revision)), summary = SyncSummary(downloaded = 1))
       } else {
           // Saved again since it was read: decided on a fresh listing in the next pass.
           OperationOutcome(isUnresolved = true)
       }
   }
   ```
   `SyncRepositoryImpl` takes `setlistComparison: SetlistComparison` as a constructor parameter (Koin resolves it; it is
   not a list) and builds `SyncEngine(libraryFileLocalSource, libraryFileLock, setlistComparison)`. Factor the guarded
   write above into a private `takeRemote(key, revision, localBytes, remote, onLocalFileChanged)` so that step 4 uses it
   too.
4. **The local change was only the automatic day; the remote one is a real edit.** The rule above only covers two
   versions with the same content. The commoner leftover of an update is a setlist another device *edited* since this
   device's last run, while this device's first read after updating gave its undated copy a day: local = index version +
   a day, remote = a different edit (dated by a new device, or undated from a 4.6.1 one) → both changed, contents
   differ → a conflict copy, for a change nobody made here. `resolve` gets the index entry (pass `index[key]` down from
   `runOperation`, which has it; `download` already calls `resolve` and has `index` too) and, for a setlist whose index
   entry exists, before the conflict-copy branch (after the remote bytes are in hand, in `resolveWith` or just before
   calling it):
   ```kotlin
   val isOnlyTheDayHere = indexEntry != null &&
       setlistComparison.withoutDate(localBytes)?.let(::localContentHash) == indexEntry.localHash
   if (isOnlyTheDayHere) return takeRemote(key, revision, localBytes, remote, onLocalFileChanged)
   ```
   It fires only where the index version was undated and the local file is that version plus a day — byte for byte as
   this version re-encodes it, which holds for every file the app wrote (`SetlistDocumentFormat` is unchanged since
   4.6.1 and the new entry fields are left out where null); a hand-written file never matches and keeps today's
   conflict copy, the safe direction. An undated remote written locally is dated again by the repository's `refresh` and
   uploaded by a later run, as in the third trace bullet below.

Why "keep the folder's copy" (recommended) rather than "keep the earlier date": both converge, but taking the
remote bytes needs no upload and no second contested write, and the folder's copy is already what every other device
has or will download. Trace of convergence:
- A dated DA and uploaded. B (DB) resolves → writes DA bytes locally, index `{hash(DA bytes), r2}`; next run: local
  hash = index, remote revision = index → nothing. C (DC) does the same. All three hold DA; no copy anywhere.
- B ran first instead: the folder holds DB and A adopts DB. Same result with the other day.
- The cloud copy is still undated (written by a device on 4.6.1) and the local one dated: the undated bytes are
  written locally, the repository's `refresh` of that file (`setlistLocalSource.loadSetlist`) dates it again under
  `LibraryFileLock`, and the next run uploads it as an ordinary local change. One extra write, no copy.
- The cost: two people who both moved the same setlist's date on purpose, offline, on two devices, lose one of the two
  days without a copy. That is a date a tap restores, against a copy of every setlist for every user; acceptable. The
  same holds for a day set on purpose on a device that then connects to a folder for the first time (no index, so both
  sides count as changed) and for step 4's case of a day set on purpose here while another device edited the setlist.
  A day changed on purpose on one device with nothing changed on the other is never touched: the planner calls that an
  `Upload`, not a `Resolve`.

Documentation: the root `CLAUDE.md` Sync bullet "A file changed on both sides is never merged: …" gains "— except a
setlist whose two versions differ only in the day they name, which every device gives an undated setlist on its own:
the cloud folder's day is taken"; `data/repository/implementation/CLAUDE.md` (the `sync/` paragraph on conflict copies)
says the same and names `SetlistComparison`; `data/source/local/implementation/CLAUDE.md` names `SetlistComparisonImpl`
next to `SetlistDocumentFormat`. Update `SyncEngine`'s and `SyncOperation.Resolve`'s KDoc ("Nothing is merged…") to
mention the exception.

## Tests

- `SyncEngineTest` (in-memory `FakeLibraryFileLocalSource`, `FakeSyncProvider`): construct the engine with a fake
  `SetlistComparison` that strips a `date:…;` token from both texts and compares the rest (the engine's behaviour is what
  is tested, not the JSON). Cases:
  1. `a setlist dated differently on two devices takes the folder's day and keeps no copy`: local and remote both
     differ from the index entry only by date → after the run the local file equals the remote bytes, no
     `"… (2).setlist.json"` exists locally or remotely, the index entry is `{hash(remote), remote revision}`, and
     `summary.conflicts` is empty.
  2. Same with no index at all (a first connection).
  3. `a setlist changed in more than its date still keeps both` — the existing conflict behaviour.
  4. A **song** whose texts the fake comparison would call equal still gets a conflict copy (the rule is setlists only).
  5. Save under the write: the local file changes between the remote download and the lock (use `provider.onDownload`
     like `a song edited while it waits to come down…`) → the newer local bytes are not overwritten (the first pass
     leaves the key unresolved; the second pass resolves the newer bytes: kept, with a conflict copy if they differ in
     more than the day).
  6. Step 4: index entry = hash of undated bytes `U1`; local = `U1` + a day (by the fake's `withoutDate`, which strips the
     day token); remote = a different edit `U2` → local becomes `U2`, no copy, index `{hash(U2), revision}`. And the
     negative: local = `U1` with a song added and a day → conflict copy as today.
  Every existing construction needs the third argument: about 60 `SyncEngine(local, LibraryFileLock())` calls in
  `SyncEngineTest` (and the one `SyncRepositoryImpl(...)` in `SyncRepositoryImplTest`, line ~1031). Add one test-side
  `NoSetlistComparison` object in the `sync` test package (`isSameApartFromDate = false`, `withoutDate = null`) and pass
  it everywhere except the new cases, so those tests keep pinning today's behaviour.
- `SetlistComparisonImplTest`: two encodings of one `SetlistDocument` with different `date`s → true; one undated, one
  dated → true; different `isCountdownShown`, title, entry order, an entry's transposition, or an unknown field → false;
  one side not JSON → false; a document with `"priority": 3` and one without → true. `withoutDate`: an undated document
  encoded as 4.6.1 wrote it (`SetlistDocumentFormat.encode` with `date = null`, entries with and without a tempo), then
  the bytes the read's dating writes for it (`decode` → `toModel(undatedDay = …)` → `toDocument` → `encode`) →
  `withoutDate(dated)` equals the undated bytes exactly (this is what step 4 rests on); not JSON → null.
- Docs: the root `CLAUDE.md` exception and `data/repository/implementation/CLAUDE.md` also name step 4 ("or where this
  device's only change is the day its read gave an undated file").

## Manual check

Two devices (or a device and the desktop build) connected to one Dropbox folder, both on 4.6.1 with a few setlists and
the demo one synced. Update one to this build and let it sync; change the other's system date to a later day, update
it, launch it and let it sync. Expect: no ` (2)` setlist files on either device or in the folder, and both devices showing
the first device's day on every formerly undated setlist.
