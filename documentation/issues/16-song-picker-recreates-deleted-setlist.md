# 16 · Ticking a song in the song picker of a setlist that is gone recreates the setlist from the copy the sheet was opened with

**Severity:** wrong behaviour with a data-loss edge (all platforms; unlikely — the picker has to be open while a sync
run deletes the setlist, or while the setlists cannot be read; much more reachable once 02 has landed, see Depends
on) · **Area:** `:presentation` (`CampfireViewModel.setSetlistSongs`, `Dialogs.kt` `SongPicker`)

## Symptom
1. Two devices share a Dropbox folder. On the tablet, delete the setlist `gig`; sync.
2. On the phone, open `gig` → "Song assignments" (the song picker). Leave it open while the phone's sync run brings
   the deletion down (**Sync now** from the side panel on a wide screen, or the launch-time run on a slow network).
3. Tick a song. The setlist is back — with the title, description, archived state, order and transpositions it had
   when the sheet was opened, plus the tick — and the next run uploads it, so it is back on the tablet too.

Every other change to a setlist that is gone (reorder, archive, transpose, add from the setlist picker, remove, edit)
writes nothing; `editSetlist`'s KDoc says so outright ("One that is gone by now is not brought back"). Only the song
picker brings it back.

The same fallback also fires when the setlists cannot be read at all: `latest()` → `loadDataIfNeeded()` re-reads after
a failed read and returns `null` if it fails again (`BaseLocalDataRepository.kt:79-81`, `:173-178`), `updateSetlist`
returns `null`, and the picker's snapshot is written over whatever the file holds now.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:1393-1399`:

```kotlin
fun setSetlistSongs(setlist: Setlist, songFileNames: List<String>) = launchLibraryChange {
    fun Setlist.withSongs(): Setlist { … }
    updateSetlist(setlist.fileName) { it.withSongs() } ?: saveSetlist(setlist.withSongs())
}
```

with the KDoc `@param setlist What to write into while the library has not caught up with a setlist that was created a
moment ago.` That case does not exist at the repository: `SetlistRepositoryImpl.createSetlist` (`:46-50`) puts the new
setlist into the cache before it returns, under the same lock `updateSetlist` takes, and a rescan that overlaps it
reads again when an `updateData` landed while it ran (`BaseLocalDataRepository.read`, `:149-153`). What lags behind a
creation is only the view model's derived `setlists` state, and the sheet already covers that on its own side
(`Dialogs.kt:947`, `setlists.firstOrNull { … } ?: dialog.setlist`).

So `updateSetlist` returns `null` only for a setlist whose file is gone or for a library that cannot be read, and in
both the fallback writes `dialog.setlist` — the snapshot from when the sheet opened, since `setlists` no longer holds
it — back as a new file.

## Fix
1. `CampfireViewModel.setSetlistSongs`: take the name rather than the setlist, drop the fallback, and say so when there
   was nothing to write into:

   ```kotlin
   /**
    * … (the first two paragraphs as they are) …
    *
    * A setlist that is gone by now — deleted by a sync run while the sheet was open, or unreadable — is not brought
    * back from the sheet's copy: that copy is the setlist as it was when the sheet opened, and nothing else in the app
    * recreates a setlist by changing it.
    */
   fun setSetlistSongs(setlistFileName: String, songFileNames: List<String>) = launchLibraryChange {
       updateSetlist(setlistFileName) { setlist ->
           val entriesBySongFileName = setlist.entries.associateBy { it.songFileName }
           setlist.copy(entries = songFileNames.map { entriesBySongFileName[it] ?: Setlist.Entry(songFileName = it) })
       } ?: _messages.send(Message.OperationFailed)
   }
   ```

   Remove the `@param setlist` line. `saveSetlist` stays in the constructor (other functions use it).
2. `Dialogs.kt:996`: `viewModel.setSetlistSongs(setlistFileName = setlist.fileName, songFileNames = selectedSongFileNames)`.
   The sheet keeps `?: dialog.setlist` for what it shows.

Do **not** close the sheet from the view model when the setlist disappears: `dismissSheet` belongs to the sheet's own
hide animation (see `presentation/CLAUDE.md`, `CampfireBottomSheet`), and the message is enough to explain why a tick
did nothing.

## Tests
None (`:presentation` is untested). The repository side is covered by 02's
`a change to a setlist whose file is gone writes nothing and drops it from the list`.

## Verify
1. Desktop (`./gradlew :app:desktop:run`), a setlist `gig` with two songs. Open its song picker. In a terminal, delete
   `~/Library/Application Support/Campfire/library/setlists/gig.setlist.json` (the desktop data directory,
   `FileStorage.desktop.kt:28-35`); with 02 in, the next tick finds the file gone; without it, trigger a rescan
   first (import any file). Tick a song.
   Before: `gig.setlist.json` is back in the folder. After: it is not, and "Something went wrong" shows (`error_operation_failed`).
2. Ordinary use: open the picker of an existing setlist, tick and untick quickly — the file follows the last tick, the
   transpositions of entries that stay are kept (unchanged behaviour).
3. "New setlist" from the Setlists screen opens the picker on the new setlist: ticks are written into it (no failure
   message) — the case the fallback claimed to exist for.

## Docs
`presentation/CLAUDE.md`, the `ui/dialogs/Dialogs.kt` bullet, after "(`CampfireViewModel.setSetlistSongs`, one write at
a time behind a `Mutex`)": add "; a setlist that is gone by then is not brought back from the sheet's copy".

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing to build on, but land it with or after 02: 02 makes `updateSetlist` read the file, so a sync deletion makes
it return `null` at once instead of only after the next rescan, which is exactly when this fallback recreates the
setlist. 04 touches the repository under the same call (no shared lines).
