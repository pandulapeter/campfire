# 36 · A "Create" that is activated twice writes one file, lists it twice, and crashes the list

**Severity:** crash (all platforms; unlikely — it takes the confirmation of the New song / New setlist dialog being
activated twice before the dialog has left: the keyboard's Done key and the button together, Enter and a click on the
desktop, or two taps across a dropped frame on a slow phone) · **Area:** `:data:repository:implementation`
(`SongRepositoryImpl`, `SetlistRepositoryImpl`), `:presentation` (`Dialogs.kt`)

## Symptom

1. Songs → **New**, type a title, and activate the confirmation twice in quick succession: press Done on the
   keyboard in the artist field and tap **Create**, or tap **Create** twice while the app is busy (a first scan, a
   sync run) and a frame is late.
2. The app dies with `IllegalArgumentException: Key "song_<name>.cho" was already used. If you are using
   LazyColumn/Row please make sure you provide a unique key for each item.` — while the editor is sliding in over
   the Songs screen, or, where the list was not composed in that moment, on the way back from the editor.
3. The same for **New setlist** (`Key "setlist_<name>.setlist.json" was already used`), for **Duplicate**, and for
   the "new setlist" dialog inside a song's setlist picker.

The library itself is intact: there is one file, and a restart lists it once.

## Cause

A dialog that confirms does not leave with the tap: `viewModel.dismissDialog()` changes a `StateFlow`, and the dialog
is only removed by the recomposition that follows, a frame later. Until then every way of confirming it is live.
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt:610-618`
(`NewSongDialog`; `SetlistDetailsDialog` `:566-569` is the same with the button alone):

```kotlin
keyboardActions = KeyboardActions(onDone = { if (isValid) onCreate(title, artist) }),
…
TextButton(enabled = isValid, onClick = { onCreate(title, artist) })
```

Each activation is its own coroutine (`CampfireViewModel.launchLibraryChange`, `:1510`, serializes nothing), and the
name is chosen by check-then-write with a suspension in between
(`data/source/local/implementation/.../source/SongLocalSourceImpl.kt:79-83`, `FileNames.kt:53-73`):

```kotlin
val fileName = fileStorage.uniqueName(StorageDirectory.SONGS, songFileName(title = title, artist = artist))   // exists() on IO
fileStorage.writeText(StorageDirectory.SONGS, fileName, text)                                                 // a second hop
```

The second coroutine asks `exists` before the first has come back from its own check, let alone written, so both
are given the same free name and both write the same file. Then both append to the cache without looking
(`data/repository/implementation/.../SongRepositoryImpl.kt:56-60`, `SetlistRepositoryImpl.kt:42-46`) — the only two
cache updates in the repositories that are not `filterNot { it.fileName == … } + item`:

```kotlin
override suspend fun createSong(title: String, artist: String, text: String): Song {
    val song = songLocalSource.createSong(title = title, artist = artist, text = text)
    updateData { current -> current.orEmpty() + song }
```

The cached list now holds two items with one `fileName`, and the lists key their items by it
(`SongsScreen.kt:334` `key = { "song_${it.fileName}" }`, `SetlistsScreen.kt:296`).

The same check-then-write is in `importSong`, `renameSong`, `importSetlist` and `renameSetlist`. Imports are made one
at a time by the view model's import queue and the renames are menu entries that close their menu, so none of them
has a second activation to race against; they race a *create* at worst. They are put under the same lock below
because it is one line each and makes "the name it was given is free" true for every writer in the repository,
rather than for the two that happened to be found.

## Fix

Three layers, each enough on its own to stop the crash, and all three wanted: the lock makes two creations two
files, the cache update makes a repeated name harmless whatever produces one, and the dialog stops the second
activation from being a creation at all (with the first two alone, a double tap makes `song.cho` and `song_2.cho`,
and the editor opens on whichever of the two came back first — `openEditor` ignores the second).

1. **`SongRepositoryImpl.kt`** — a lock from picking a free name to having the file under it, and a cache update
   that replaces. Imports: `kotlinx.coroutines.sync.Mutex`, `kotlinx.coroutines.sync.withLock`.

   ```kotlin
       /**
        * Held by everything that asks the storage for a free name and then writes under it. The two are separate trips
        * to the file system with a suspension between them, and a second asker getting in there is given the same name:
        * one file, written twice, and listed twice.
        */
       private val nameMutex = Mutex()
   ```

   ```kotlin
       override suspend fun createSong(title: String, artist: String, text: String): Song = nameMutex.withLock {
           songLocalSource.createSong(title = title, artist = artist, text = text).also { song ->
               // Replaced rather than appended, like every other change to the list: the file name is what the lists key
               // their rows by, so a name that is in the cache already - a file deleted behind the app's back whose name
               // has just been given out again - must not end up in it twice.
               updateData { current -> current.orEmpty().filterNot { it.fileName == song.fileName } + song }
           }
       }

       override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean) = nameMutex.withLock {
           songLocalSource.importSong(fileName = fileName, text = text, shouldReplace = shouldReplace)
       }

       override suspend fun renameSong(song: Song): Song? = nameMutex.withLock {
           val renamed = songLocalSource.renameSong(song) ?: return@withLock null
           songContentRepository.invalidate(song.fileName)
           updateData { current ->
               current.orEmpty().filterNot { it.fileName == song.fileName || it.fileName == renamed.fileName } + renamed
           }
           renamed
       }
   ```

   `saveSong` and `deleteSong` stay outside: they are given their name rather than picking one. Nothing called under
   the lock takes it again (`Mutex` is not reentrant): the local source and `SongContentRepositoryImpl.invalidate`
   know nothing of it, and `updateData` does not suspend.

   Do **not** move the lock down into `SongLocalSourceImpl` / `FileNames.kt`: `uniqueName` is also what sync's
   conflict copies go through (`LibraryFileLocalSourceImpl.kt:44`), under a run that has its own ordering, and the
   repository is where the app's own writers meet.

2. **`SetlistRepositoryImpl.kt`** — after plan 33, `writeMutex` is the lock of every write to a setlist file;
   `createSetlist` and `importSetlist` join it, and `createSetlist` replaces instead of appending. Add one sentence
   to the end of `writeMutex`'s KDoc: "It also covers the creations and the imports, from the storage finding a name
   free to the file being there under it, so that two of them cannot be given the same one."

   ```kotlin
       override suspend fun createSetlist(title: String, description: String, priority: Int): Setlist = writeMutex.withLock {
           setlistLocalSource.createSetlist(title = title, description = description, priority = priority).also { created ->
               updateData { current -> current.orEmpty().filterNot { it.fileName == created.fileName } + created }
           }
       }

       override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean) = writeMutex.withLock {
           setlistLocalSource.importSetlist(setlist, shouldReplace)
       }
   ```

   The `priority` stays the use case's (`CreateSetlistUseCaseImpl` reads the highest one before it calls): two
   creations that do overlap get the same priority, which plan 35 makes a stable tie rather than a swap, and step 3
   removes the way two creations overlap at all. Moving "newest goes on top" into the repository for the sake of
   that tie is not worth the policy ending up in the wrong layer.

3. **`Dialogs.kt`** — a confirmation goes through once. New helper next to `rememberFirstFieldFocusRequester`:

   ```kotlin
   /**
    * Lets a dialog's confirmation through the first time and never again. A dialog that confirms does not leave with
    * the tap but with the next frame, and until then its button and the keyboard's Done key are both still live: on a
    * frame that comes late a second tap lands on a dialog that has already been answered, and creates the song or
    * the setlist a second time. The state is written as the event is handled, so the second event of the same frame
    * already reads it.
    */
   @Composable
   private fun rememberSingleConfirmation(): (confirm: () -> Unit) -> Unit {
       var hasConfirmed by remember { mutableStateOf(false) }
       return { confirm ->
           if (!hasConfirmed) {
               hasConfirmed = true
               confirm()
           }
       }
   }
   ```

   `remember`, not `rememberSaveable`: a dialog that was confirmed is gone, and one that comes back after the
   activity was recreated has not been.

   - `NewSongDialog`:

     ```kotlin
         val confirmOnce = rememberSingleConfirmation()
         val create = { if (isValid) confirmOnce { onCreate(title, artist) } }
         …
                     keyboardActions = KeyboardActions(onDone = { create() }),
         …
             TextButton(
                 enabled = isValid,
                 onClick = create,
             ) { Text(stringResource(Res.string.create)) }
     ```
   - `SetlistDetailsDialog` (which is New setlist, Edit, Duplicate and the picker's nested "new setlist" at once):
     `val confirmOnce = rememberSingleConfirmation()` and
     `onClick = { confirmOnce { onConfirm(setlistTitle.text, description) } }`.

   Every `onConfirm` / `onCreate` handed to these two dismisses the dialog in the same call, so "once" can never
   leave a dialog on screen that no longer answers. The other dialogs are left alone because a second activation
   of them changes nothing: `AddSongTagDialog` has the same Done-and-button pair, but tag edits are made one at a
   time under `CampfireViewModel.songWriteMutex` and the second finds the tag there (`edited == text`, nothing is
   written); a second **Delete** deletes what is gone; the import question clears `pendingImportPlan` on its first
   answer. Do not replace the guard with a debounce in `CampfireViewModel` either: the view model cannot tell a
   second activation from a second song with the same title, which is a legitimate thing to create.

## Tests

`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongRepositoryImplTest.kt`
— `FakeSongLocalSource.createSong` stops throwing and imitates the storage, gap included:

```kotlin
override suspend fun createSong(title: String, artist: String, text: String): Song {
    val fileName = generateSequence(1) { it + 1 }.map { if (it == 1) "$title.cho" else "${title}_$it.cho" }.first { it !in files }
    // The storage finds the name free on one trip and writes under it on another, and this is the gap between them.
    yield()
    files[fileName] = text
    return song(fileName)
}
```

- `two songs created at once get a name each` — empty source; `val created = listOf(async { repository.createSong("song",
  "", "a") }, async { repository.createSong("song", "", "b") }).awaitAll()`. `created.map { it.fileName }` is
  `["song.cho", "song_2.cho"]`, `localSource.files` holds `a` and `b` under them, and the last `repository.songs`
  state lists exactly those two names. (Without the lock both are `song.cho` and the state lists it twice.)
- `a created song whose name the list already holds is listed once` — source holds `song.cho`;
  `repository.loadSongsIfNeeded()`; `localSource.files.remove("song.cho")` (the file went away behind the app's
  back); `repository.createSong("song", "", "new")`. The last state lists `song.cho` once.

`SetlistRepositoryImplTest.kt` (created by plan 33) — its fake's `createSetlist` gets the same numbering and the
same `yield()` between finding the name and putting the setlist:

- `two setlists created at once get a name each` — as above with `createSetlist("Gig", "", 1)` twice:
  `gig.setlist.json` and `gig_2.setlist.json`, each listed once.
- `a created setlist whose name the list already holds is listed once` — as above.
- `a creation waits for the change being written` — with 33's `saveGate`: an `updateSetlist` in flight, then
  `launch { createSetlist(…) }`; after `runCurrent()` the fake has not been asked to create anything; after the gate
  both are there.

The dialog guard is UI: none.

## Verify

1. `./gradlew :data:repository:implementation:desktopTest`.
2. The second activation cannot be produced reliably by hand, so provoke it: in `Dialogs.kt`, temporarily wrap the
   call in `NewSong`'s `onCreate` as `repeat(2) { viewModel.createSong(title = title, artist = artist) }` (that is
   *inside* the guarded lambda, so it bypasses step 3 and exercises steps 1–2). `./gradlew :app:desktop:run`, create
   "Test": before the fix the app crashes with the duplicate key; after it there are `test.cho` and `test_2.cho`,
   listed once each. Same with `createSetlist`. Revert.
3. For step 3, temporarily make `onDone` call `create()` twice, create a song with the keyboard: one song in the
   list, not two. Revert.
4. By hand on Android, as far as hands allow: New song, type a title, move to the artist field, press Done and
   **Create** together a few times. One song each time.
5. Edit a setlist, duplicate one, and create one from a song's setlist picker: each still works, once, and each
   dialog closes.
6. Compile checks for all four targets.

## Docs

`data/repository/implementation/CLAUDE.md`: to the bullet about `SetlistRepositoryImpl`'s lock (as plan 33 leaves
it) add "Creating and importing a setlist take it as well, from the storage finding a name free to the file being
there under it." and add a bullet after it: "`SongRepositoryImpl` has the same kind of lock for the three writers
that pick a free name before they write (`createSong`, `importSong`, `renameSong`): finding the name and writing under
it are two trips to the storage, and a second asker in between is given the same name. Every change to either cached
list replaces by file name and never appends, since the file name is what the lists key their rows by."

`presentation/CLAUDE.md`: only if it describes the dialogs' confirmation (it does not at `29820b93`); otherwise none.

## Touches

- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongRepositoryImpl.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImpl.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongRepositoryImplTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImplTest.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`
- `data/repository/implementation/CLAUDE.md`

## Depends on

33 (it turns `writeMutex` into the lock of every setlist write, introduces the unlocked helpers this plan's
`createSetlist` must not collide with, and creates `SetlistRepositoryImplTest`). Shares `Dialogs.kt` with 33 and 35
(and with 55, by its title): 33 edits `EditSetlist`'s `onConfirm` call site and 35 the song picker's comparator —
neither of them the two dialog functions this plan edits. Plan 03 fixes the other duplicate key of the same grid
(`header_…`); the two are independent.
