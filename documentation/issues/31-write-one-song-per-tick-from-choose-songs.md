# Write one song in or out per tick of Choose songs, so a tick never reverts what a sync run brought into the setlist

**Kind:** bug (data loss)  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/SetlistSongToggle.kt (new),
presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/SetlistSongToggleTest.kt (new),
presentation/CLAUDE.md

## Problem

The song picker (Choose songs, `SongPicker` in `Dialogs.kt`) seeds its selection once, when it opens, and writes the
whole of it on every tick:

`Dialogs.kt:1735-1736` and `:1828-1833` (8ee010b36)
```kotlin
val initialSongFileNames = rememberSaveable(setlist.fileName) { setlist.entries.map { it.songFileName }.distinct() }
var selectedSongFileNames by rememberSaveable(setlist.fileName) { mutableStateOf(initialSongFileNames) }
...
selectedSongFileNames = if (isChecked) selectedSongFileNames + fileName else selectedSongFileNames - fileName
viewModel.setSetlistSongs(setlistFileName = setlist.fileName, songFileNames = selectedSongFileNames)
```
`CampfireViewModel.kt:3474-3479`
```kotlin
fun setSetlistSongs(setlistFileName: String, songFileNames: List<String>) = launchLibraryChange {
    updateEditableSetlist(setlistFileName) { setlist ->
        val entriesBySongFileName = setlist.entries.associateBy { it.songFileName }
        setlist.copy(entries = songFileNames.map { entriesBySongFileName[it] ?: Setlist.Entry(songFileName = it) })
    } ?: sendMessage(Message.OperationFailed)
}
```
The transform is applied to the setlist as it is now (`SetlistRepositoryImpl.updateSetlist` runs it on `latest(fileName)`
inside its serialized `writing`), but the list it writes is the sheet's: every entry that is not in the sheet's list is
dropped, and the order is the sheet's.

Scenario: open a setlist's Choose songs and tick a song. The tick is a library change, so a sync run starts ten
seconds later (`SyncRepository.scheduleSynchronization`) with the sheet still open. If a bandmate added song X to the
same setlist on another device, or removed song Y, or reordered it, that run downloads it. The user's next tick writes the
sheet's list: X is dropped (Y comes back, the order reverts). Since only the local file changed since the index, the next
run uploads it as an ordinary edit, and every device loses X. A desktop rescan of a hand-edited library folder does the
same. Every other sheet that edits a file it took a snapshot of already guards against this: `setSongTags`,
`setSongLinks` and `setSongMetadata` keep what changed underneath them.

(The reviewer's second point, that `.distinct()` drops a song a setlist names twice, does not hold: the model never
holds a repeated entry — `SetlistMappers.kt` reads with `distinctBy { it.file }` — so there is nothing to drop.)

The whole-list write was introduced so that quick ticks do not lose each other (the KDoc of `setSetlistSongs`). A delta
applied inside the same serialized `updateSetlist` keeps that property: each tick's transform runs on the result of the
one before it.

## Fix

1. New pure helper `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/SetlistSongToggle.kt`:
   ```kotlin
   /**
    * The setlist with [songFileName] ticked in or out, as one tick of the song picker asks: a song ticked in goes to the
    * end, unless the setlist already names it; ticked out, its entry goes, with its slot and its overrides. Everything
    * else is the setlist as it is now, whatever another device or a rescan did to it while the picker was open.
    */
   internal fun Setlist.withSongTicked(songFileName: String, isTicked: Boolean): Setlist = when {
       isTicked && entries.none { it.songFileName == songFileName } -> copy(entries = entries + Setlist.Entry(songFileName = songFileName))
       isTicked -> this
       else -> copy(entries = entries.filterNot { it.songFileName == songFileName })
   }
   ```
2. `CampfireViewModel`: replace `setSetlistSongs(setlistFileName, songFileNames)` with
   ```kotlin
   fun setSetlistSong(setlistFileName: String, songFileName: String, isTicked: Boolean) = launchLibraryChange {
       updateEditableSetlist(setlistFileName) { it.withSongTicked(songFileName, isTicked) } ?: sendMessage(Message.OperationFailed)
   }
   ```
   and rewrite its KDoc: each tick is one song in or out, applied in order to the setlist as the library has it, so ticks
   never lose each other (the writes are serialized by `UpdateSetlistUseCase`) and nothing the sheet did not touch is
   written back from its snapshot. Keep the paragraph about a setlist that is gone not being brought back.
   `setSetlistSongs` has no other caller (`grep setSetlistSongs`), so remove it.
3. `Dialogs.kt` `SongPicker`: keep `selectedSongFileNames` for the boxes (it is still what keeps a box from flicking back
   for a round trip), and call `viewModel.setSetlistSong(setlistFileName = setlist.fileName, songFileName = fileName,
   isTicked = isChecked)`. Update the KDoc paragraph "the whole of it is written on every tick" to say each tick writes
   that one song, and drop the `.distinct()` on the seed (the model has no repeats). Optional, not required: a box for a
   song another device added while the sheet was open stays unticked until the sheet is reopened; that is harmless,
   since unticking it removes it and ticking it is a no-op.
4. `presentation/CLAUDE.md`: where the song picker is described (the `ui/dialogs/Dialogs.kt` entry), if it says the
   whole selection is written, say one song per tick instead.

## Tests

`SetlistSongToggleTest` (commonTest), with a setlist of entries `a`, `b` (with a transposition), `c`:
- ticking `d` appends `Entry(d)` at the end and leaves `b`'s transposition;
- ticking `b` again changes nothing (same instance or equal);
- unticking `b` removes it and keeps `a`, `c` in order;
- a setlist that gained `x` (another device) keeps `x` after ticking `d` and after unticking `a` — the regression.

## Manual check

Two devices on one Dropbox account. Device 1: open setlist S's Choose songs, tick a song, wait for its sync run.
Device 2: add song X to S and sync. Device 1, sheet still open: wait for the next run to bring X in (or tap Sync now
from another window on the desktop), then tick another song. Close the sheet: S holds X and both ticked songs, and after
both devices sync again X is still in S on device 2.
