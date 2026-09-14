# 04 · Rotating the phone with the song picker open removes the songs ticked before the rotation

**Severity:** high (destructive write) · **Area:** `:presentation` (`Dialogs.kt`, `SongPickerSheet`)

## Symptom

Setlists → menu → **Song assignments** → tick three songs (each tick writes the file) → rotate → the three appear
unticked → tick a fourth → the setlist is written with only the fourth. On Android the Activity is recreated on
rotation (`AndroidManifest.xml` declares no `configChanges`); the ViewModel survives, so the sheet comes back.

## Cause

`Dialogs.kt:966–967`:

```kotlin
val initialSongFileNames = remember(dialog) { dialog.setlist.entries.map { it.songFileName }.distinct() }
var selectedSongFileNames by remember(dialog) { mutableStateOf(initialSongFileNames) }
```

`dialog.setlist` is the snapshot captured when the sheet was opened. After recreation the composition is new, the
`remember`s re-run, and the selection is re-seeded from that stale snapshot although `setlist` (line 965) already
reflects the writes. `onCheckedChange` (:1008) then writes `stale + fourth`.

## Fix

1. Seed from the **live** setlist and keep the selection saveable:

   ```kotlin
   val initialSongFileNames = rememberSaveable(setlist.fileName) { setlist.entries.map { it.songFileName }.distinct() }
   var selectedSongFileNames by rememberSaveable(setlist.fileName) { mutableStateOf(initialSongFileNames) }
   ```

   `List<String>` is Bundle-safe, so the default saver works. `initialSongFileNames` is what orders the ticked songs
   at the top of the list; it should stay what it was when the sheet opened, which the saveable gives you across
   recreation while still re-seeding when a different setlist's sheet is opened.

2. Look for the same pattern elsewhere in `Dialogs.kt`: `SetlistPickerSheet` (a song into setlists) if it holds a
   selection the same way, and issue 55 (`SongLanguagesDialog`). Every dialog text field is already `rememberSaveable`.

## Verification

Android emulator: tick three songs, rotate, tick a fourth; the setlist file holds four. Rotate back: still four.
