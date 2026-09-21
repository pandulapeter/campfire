# 33 · Saving a setlist's title or description writes back the songs the dialog was opened with

**Severity:** data loss (all platforms; a narrow window — the setlist has to change between the Edit dialog opening
and its Save, which a sync run at startup or a second device does; what is lost is another device's entries,
transpositions and archived state, and the next run uploads the loss) · **Area:** `:data:repository:api` /
`:implementation` (`SetlistRepository.renameSetlist`, `saveSetlist`, `deleteSetlist`), `:domain:api` /
`:implementation` (`EditSetlistUseCase`, `RenameSongFileUseCaseImpl`, `DeleteSongUseCaseImpl`), `:presentation`
(`CampfireViewModel.editSetlist`)

## Symptom

A. **The edit dialog.** The app starts and runs its automatic sync. Open a setlist's menu → **Edit** straight away and
   spend ten seconds typing a description. Meanwhile the run downloads a newer version of that setlist (three songs
   added on the tablet) and rescans. Press **Save**: the file is written with the title and description just typed
   and with the entries, `isArchived` and `priority` the setlist had *when the dialog opened*. The three songs are
   gone here, the file now differs from the sync index, and the next run uploads it — they are gone everywhere. If the
   run had deleted the setlist instead, Save brings it back.
B. **A change still being written.** Tick a song in the picker, or tap a transposition inside a setlist, and confirm
   the Edit dialog with a new title while that write is still on its way (slow storage, the web). The rename writes
   the new file and deletes the old one; the earlier write then lands under the old name. The setlist is in the list
   twice, once under each title.
C. **Renaming or deleting a song.** *Update file name* or *Delete* on a song walks every setlist holding it and
   saves a corrected copy. A change to one of those setlists whose write is in flight at that moment (the same tick
   or tap as in B) is overwritten by the copy, or overwrites it — whichever write finishes last wins, and the other
   change is lost without a message.

## Cause

`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/EditSetlistUseCaseImpl.kt:23-24`
takes the whole `Setlist` from the caller, and the caller is a dialog that has been holding it since it opened
(`presentation/.../ui/dialogs/Dialogs.kt:186`, `DialogType.EditSetlist(val setlist: Setlist)`,
`CampfireViewModel.kt:1296-1298`):

```kotlin
override suspend operator fun invoke(setlist: Setlist, title: String, description: String): Setlist =
    setlistRepository.renameSetlist(setlist = setlist.copy(description = description.trim()), title = title.trim())
```

`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImpl.kt:57-63`
hands that snapshot to the local source, which serializes all of it
(`SetlistLocalSourceImpl.kt:73-87`, `saveSetlist(renamed)`), and does so outside the lock every other change to a
setlist is made under:

```kotlin
override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist) = writeMutex.withLock {
    loadDataIfNeeded()?.firstOrNull { it.fileName == fileName }?.let(transform)?.also { saveSetlist(it) }
}

override suspend fun renameSetlist(setlist: Setlist, title: String): Setlist {
    val renamed = setlistLocalSource.renameSetlist(setlist, title)      // no writeMutex, the caller's snapshot
```

This is the write `data/repository/api/CLAUDE.md` rules out in so many words ("never by saving a copy the caller
read earlier"); `renameSetlist` predates `updateSetlist` and was never moved over.

The two song use cases have the milder form. `RenameSongFileUseCaseImpl.kt:39-51` and
`DeleteSongUseCaseImpl.kt:31-35` read the cache at that moment, which is current, but write through
`setlistRepository.saveSetlist(setlist.copy(entries = …))`, and `saveSetlist` (`SetlistRepositoryImpl.kt:48-51`) takes
no lock either, so it interleaves with an `updateSetlist` that has read its setlist and not yet written it.
`deleteSetlist` (`:71-74`) has the same hole from the other side: an update in flight writes the file back after the
deletion removed it.

Nothing in `CampfireViewModel.launchLibraryChange` (`:1510`) orders these: each change is its own coroutine.

## Fix

Steps 1–5 are one change (the signatures change together).

1. **`data/repository/api/.../SetlistRepository.kt`** — `renameSetlist` takes the name of the setlist and the two
   things the user says about it, the way `updateSetlist` takes a name and a transform. Replace the declaration and
   the KDoc of `saveSetlist`:

   ```kotlin
       /**
        * Creates the file or overwrites it, and updates that one entry of the cached list. For a setlist the caller owns
        * as a whole, which is one it has just created or copied; a change to one that is already there goes through
        * [updateSetlist] or [renameSetlist]. It waits its turn among those like any other write.
        */
       suspend fun saveSetlist(setlist: Setlist)
   ```

   ```kotlin
       /**
        * [updateSetlist] for the one change that may move the file: the latest version of the setlist gets [title] and
        * [description] and nothing else of it changes, and its file moves to the name the title gives it (see
        * `SetlistLocalSource.renameSetlist`), so the setlist that comes back may have a different `fileName` than the
        * one that was asked for. Null when there is no such setlist, in which case nothing is written: a setlist that
        * was deleted while its title was being typed stays deleted.
        */
       suspend fun renameSetlist(fileName: String, title: String, description: String): Setlist?
   ```

   Also extend the KDoc of `deleteSetlist` (it has none): `/** Waits for a change to the setlist that is being
   written, which would otherwise put the file back. */`.

2. **`SetlistRepositoryImpl.kt`** — every write takes `writeMutex`. `kotlinx.coroutines.sync.Mutex` is not
   reentrant, so the locked functions share private helpers that do not lock; do **not** call `saveSetlist` from
   inside `updateSetlist` any more.

   ```kotlin
       /**
        * Held from reading a setlist to having its write in the cache, so that the next change reads what this one
        * wrote. The cache is the one place that is right straight after a write: anything observing [setlists] only
        * catches up a few hops later, and a file write is plenty of time for a second tap to land in between. Every
        * write to a setlist file takes it, the ones that read nothing included, since a save, a move or a deletion
        * crossing a change that is halfway through is how a setlist ends up holding the older of the two, twice in the
        * library, or back after it was deleted.
        */
       private val writeMutex = Mutex()
   ```

   ```kotlin
       override suspend fun saveSetlist(setlist: Setlist) = writeMutex.withLock { write(setlist) }

       override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist) = writeMutex.withLock {
           latest(fileName)?.let(transform)?.also { write(it) }
       }

       override suspend fun renameSetlist(fileName: String, title: String, description: String) = writeMutex.withLock {
           latest(fileName)?.let { setlist ->
               setlistLocalSource.renameSetlist(setlist = setlist.copy(description = description), title = title).also { renamed ->
                   updateData { current ->
                       current.orEmpty().filterNot { it.fileName == fileName || it.fileName == renamed.fileName } + renamed
                   }
               }
           }
       }
   ```

   ```kotlin
       override suspend fun deleteSetlist(fileName: String) = writeMutex.withLock {
           setlistLocalSource.deleteSetlist(fileName)
           updateData { current -> current.orEmpty().filterNot { it.fileName == fileName } }
       }

       /** The setlist as the cache has it. Only meaningful under [writeMutex], where no write can be halfway to it. */
       private suspend fun latest(fileName: String) = loadDataIfNeeded()?.firstOrNull { it.fileName == fileName }

       /** Callers hold [writeMutex]. */
       private suspend fun write(setlist: Setlist) {
           setlistLocalSource.saveSetlist(setlist)
           updateData { current -> current.orEmpty().filterNot { it.fileName == setlist.fileName } + setlist }
       }
   ```

   `createSetlist` and `importSetlist` stay as they are here; plan 36 moves both under the same lock, for the sake
   of the free name each of them picks before it writes. `rescan()` must **not** take `writeMutex`: a write landing during
   a read is already handled by the base class (`updateData` makes the read run again), and a scan holding the lock
   would make every tap in a setlist wait for it.

   `SetlistLocalSource.renameSetlist(setlist, title)` and `SetlistLocalSourceImpl` do **not** change: the local source
   is handed a setlist and writes it, and which setlist that is was the repository's mistake.

3. **`domain/api/.../useCases/EditSetlistUseCase.kt`**:

   ```kotlin
       /**
        * Writes what the user can say about a setlist: its title, and the description that may be blank. The setlist is
        * named rather than handed over, because whoever asks has usually been holding it for as long as a dialog was
        * open, and everything else it carries - the entries, their transpositions, whether it is archived - is taken
        * from the library as it is at the moment of the write.
        *
        * Only the title reaches the file name, which is why this is a move as well as a write - nothing in the library
        * points at a setlist by file name, so the move costs nothing to follow, but the returned setlist may have a
        * `fileName` the caller has not seen before, and sync will carry it across as a deletion and a new file. Null
        * when the setlist is no longer there, and nothing has been written then.
        */
       suspend operator fun invoke(fileName: String, title: String, description: String): Setlist?
   ```

4. **`EditSetlistUseCaseImpl.kt`** (the `//` comment above it goes; the `Setlist` import stays for the return type):

   ```kotlin
       override suspend operator fun invoke(fileName: String, title: String, description: String): Setlist? =
           setlistRepository.renameSetlist(fileName = fileName, title = title.trim(), description = description.trim())
   ```

5. **`CampfireViewModel.kt:1291-1298`** and **`Dialogs.kt:185-186`**. The dialog type keeps its `Setlist`: it is what
   fills the two fields in, and that is all it is used for from now on.

   ```kotlin
       /**
        * The title and the description are written together, since they are the whole of what the user gets to say
        * about a setlist. Only the title reaches the file name, so the setlist that comes back may be under a name
        * this one has never seen. The setlist is named rather than passed: the dialog has held its copy since it was
        * opened, and the rest of the setlist may have moved on since. One that is gone by now is not brought back.
        */
       fun editSetlist(setlistFileName: String, title: String, description: String) = launchLibraryChange {
           editSetlist.invoke(fileName = setlistFileName, title = title, description = description) ?: _messages.send(Message.OperationFailed)
       }
   ```

   ```kotlin
               onConfirm = { setlistTitle, description ->
                   viewModel.editSetlist(setlistFileName = dialog.setlist.fileName, title = setlistTitle, description = description)
                   viewModel.dismissDialog()
               },
   ```

6. **`RenameSongFileUseCaseImpl.kt:39-51`** — the list read up front only says *which* setlists to visit; what is
   written is built on the latest version of each, under the lock:

   ```kotlin
           setlistRepository.loadSetlistsIfNeeded().orEmpty()
               .filter { setlist -> setlist.entries.any { it.songFileName == song.fileName } }
               .forEach { setlist ->
                   attempt(failures) {
                       setlistRepository.updateSetlist(setlist.fileName) { latest ->
                           latest.copy(
                               entries = latest.entries.map { entry ->
                                   if (entry.songFileName == song.fileName) entry.copy(songFileName = renamed) else entry
                               },
                           )
                       }
                   }
               }
   ```

7. **`DeleteSongUseCaseImpl.kt:31-35`**, the same way:

   ```kotlin
           setlistRepository.loadSetlistsIfNeeded().orEmpty()
               .filter { setlist -> setlist.entries.any { it.songFileName == fileName } }
               .forEach { setlist ->
                   setlistRepository.updateSetlist(setlist.fileName) { latest ->
                       latest.copy(entries = latest.entries.filterNot { it.songFileName == fileName })
                   }
               }
   ```

   Keep the `filter`: `updateSetlist` writes whatever the transform returns, so visiting every setlist would rewrite
   every file for nothing (and hand sync a library of "changed" files if the serialization ever differs by a byte).
   What the filter can miss is a setlist that gains the song in the instant between the read and the walk; that
   leaves an entry drawn as missing, which is what such an entry is, and loses nothing.

What is left calling `saveSetlist` afterwards: `createSetlistWithSong`, `duplicateSetlist` and the fallback in
`setSetlistSongs` — all three write a setlist that was created a line earlier, which is what the function is for.

## Tests

New file
`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImplTest.kt`
(MPL header; modelled on `SongRepositoryImplTest` next to it). A private `FakeSetlistLocalSource(setlists:
List<Setlist>) : SetlistLocalSource` keeps a `val files: MutableMap<String, Setlist>`; `loadSetlists()` returns its
values; `saveSetlist` first awaits an optional `var saveGate: CompletableDeferred<Unit>?` and then puts the setlist;
`renameSetlist(setlist, title)` removes `setlist.fileName` and puts `setlist.copy(title = title, fileName =
"${title.lowercase()}.setlist.json")`, returning it; `deleteSetlist` removes; `createSetlist` puts an empty setlist
under `"${title.lowercase()}.setlist.json"`; the rest throw `UnsupportedOperationException`. A helper
`setlist(fileName, vararg songs)` builds one with `title = "Gig"`, `description = ""`, `priority = 1`,
`isArchived = false`.

- `a rename keeps what the setlist gained since the caller read it` — source holds `gig.setlist.json` with entry
  `a.cho`. `updateSetlist("gig.setlist.json") { it.copy(entries = it.entries + Setlist.Entry("b.cho"), isArchived =
  true) }`, then `renameSetlist("gig.setlist.json", "Summer", "For the lake")`. The result and
  `files["summer.setlist.json"]` carry entries `a.cho, b.cho`, `isArchived = true`, the new title and description;
  `gig.setlist.json` is in neither `files` nor the last `setlists` state.
- `a rename of a setlist that is gone writes nothing` — empty source; the call returns null and `files` is empty.
- `a rename waits for the change being written` — `saveGate` set; `launch { updateSetlist(…) { add b.cho } }`,
  `runCurrent()`, `launch { renameSetlist(…, "Summer", "") }`, `runCurrent()`: `files` still holds only
  `gig.setlist.json` with `a.cho`. Complete the gate, `advanceUntilIdle()`: `files.keys == setOf("summer.setlist.json")`
  and it holds `a.cho, b.cho`.
- `a deletion waits for the change being written` — same gate, `launch { deleteSetlist(…) }` second; after the gate
  `files` is empty and the last state holds no setlist.
- `a save waits for the change being written` — gate, update adding `b.cho` first, then
  `launch { saveSetlist(setlist("gig.setlist.json", "c.cho")) }`; afterwards the file holds `c.cho` only (the save is
  the later of the two and wins whole, rather than the two writes racing).

`data/source/local/implementation/src/desktopTest/.../source/RenameTest.kt` calls the *local source's*
`renameSetlist`, which is unchanged; it needs no edit.

## Verify

1. `./gradlew :data:repository:implementation:desktopTest`, then the compile checks (the use case signature is
   consumed by `:presentation` on all four targets).
2. The dialog is modal, so the only thing that changes a setlist behind it is a sync run; this needs a build with
   `campfire.dropbox.appKey` and two devices (the desktop app and the web build will do) on one account. Make a
   setlist with two songs on A and sync both. On B add a third song to it and sync. On A press **Sync now** in
   Settings, go straight to Setlists and open **Edit** on the setlist before the run has finished (a larger library
   makes that easy), wait for the run to end behind the dialog, type a description and save. The third song is
   there, in the app and in `library/setlists/<name>.setlist.json`; before the fix the file is back to two entries.
3. Rename a setlist through the dialog (title `Summer set` → `Autumn set`): one file, `autumn_set.setlist.json`,
   entries and transpositions intact; change only the capitals of a title: same file name, new title inside.
4. *Update file name* on a song that is in two setlists, and delete a song that is in two setlists: both setlists
   follow, nothing is drawn as missing.
5. The "setlist is gone" branch cannot be reached by hand without a sync run deleting the setlist behind the dialog
   (same setup as step 2, deleting instead of adding): Save then shows "Something went wrong" and the setlist stays
   deleted. The unit test covers the repository half.

## Docs

- `data/repository/api/CLAUDE.md`, the `SetlistRepository` bullet: "`saveSetlist` is for a setlist the caller owns as
  a whole — one just created, copied or renamed." becomes "`renameSetlist` is the same thing for the title and the
  description, the one change that may move the file: it takes the setlist's name and reads the rest of it itself.
  `saveSetlist` is for a setlist the caller owns as a whole — one just created or copied."
- `data/repository/implementation/CLAUDE.md`, the `SetlistRepositoryImpl.updateSetlist` bullet: start it
  "`SetlistRepositoryImpl` makes every write to a setlist file — `updateSetlist`, `renameSetlist`, `saveSetlist`,
  `deleteSetlist` — under one lock, held from reading the setlist out of its own cache to having the write back in
  that cache, so a second change reads what the first one wrote, and a move or a deletion cannot cross a change that
  is halfway through." and keep its last sentence.
- `domain/api/CLAUDE.md`, the `EditSetlistUseCase` / `RenameSongFileUseCase` bullet: after "only the first of them
  decides where the file goes" add "— and it is told which setlist by file name rather than handed one, so that the
  entries it writes back are the library's and not the ones a dialog was opened with".
- `domain/implementation/CLAUDE.md`, the `RenameSongFileUseCaseImpl` bullet: add "Both walks change each setlist
  through `updateSetlist`, so they build on the latest version of it and wait for a change that is being written."

## Touches

- `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SetlistRepository.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImplTest.kt` (new)
- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/EditSetlistUseCase.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/EditSetlistUseCaseImpl.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/RenameSongFileUseCaseImpl.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/DeleteSongUseCaseImpl.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`
- `data/repository/api/CLAUDE.md`
- `data/repository/implementation/CLAUDE.md`
- `domain/api/CLAUDE.md`
- `domain/implementation/CLAUDE.md`

## Depends on

Nothing. Plan 36 builds on this one (it moves `createSetlist` under the `writeMutex` this plan turns into the lock
of every write, and adds its cases to `SetlistRepositoryImplTest`), so 33 lands first. Plan 08 is the other half of
the same loss: it makes a failed sync run rescan, so that the cache `renameSetlist` and `updateSetlist` read from is
not behind the disk in the first place; the two are independent.
