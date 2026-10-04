# Duplicate a setlist from the entries it holds now rather than from the copy the Duplicate sheet was opened with

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt

**Challenged:** sound

## Problem

The Duplicate setlist sheet holds the `Setlist` it was opened with (`DialogType.DuplicateSetlist(val setlist: Setlist)`,
`CampfireViewModel.kt:3669`; passed back as `setlist = dialog.setlist` in `Dialogs.kt:328-334`). `duplicateSetlist`
(`CampfireViewModel.kt:2963-2969` at 800ebde0b) checks that the setlist still exists but then copies the sheet's
snapshot of its entries:

```kotlin
fun duplicateSetlist(setlist: Setlist, title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = launchLibraryChange {
    reorderingSetlistFileName = null
    // An archived setlist is copied too, since a set that has been played is the likeliest start for the next one.
    if (setlists.value.none { it.fileName == setlist.fileName }) return@launchLibraryChange
    val copy = createSetlist.invoke(title = title, description = description, date = date, isCountdownShown = isCountdownShown)
    saveSetlist(copy.copy(entries = setlist.entries))
}
```

Anything that changed the setlist's entries while the sheet was open is lost from the copy: a sync run that added,
removed or reordered songs or changed a transposition, and above all a song rename (`RenameSongFileUseCase` rewrites
every setlist entry naming the old file), after which the copy names the old file and shows it as a missing song.
Every other setlist write here (`editSetlist`, `setSetlistSongs`, `reorderSetlist`) deliberately reads the setlist as
it is now for exactly this reason (see the KDoc of `editSetlist` and `setSetlistSongs`).

## Fix

Look the setlist up once and copy its entries:

```kotlin
// The sheet's copy is the setlist as it was when the sheet opened; a sync run or a song rename since then has moved
// the entries on, and the copy is made of the setlist as it is.
val current = setlists.value.firstOrNull { it.fileName == setlist.fileName } ?: return@launchLibraryChange
val copy = createSetlist.invoke(title = title, description = description, date = date, isCountdownShown = isCountdownShown)
saveSetlist(copy.copy(entries = current.entries))
```

Keep the existing archived comment. Optionally change the parameter to `setlistFileName: String` (and the call in
`Dialogs.kt`) to match `editSetlist`, which makes the stale copy impossible to reach for; not required.

## Tests

None: a view model intent with no pure logic to extract.

## Manual check

Open a setlist's Duplicate sheet and leave it up; from a second device synced to the same folder, add a song to that
setlist (or rename one of its songs with Update file name there, which rewrites the setlist file) and let this device
sync while the sheet stays open. Confirm the duplicate: the copy holds the added song, and no entry shows as missing.
