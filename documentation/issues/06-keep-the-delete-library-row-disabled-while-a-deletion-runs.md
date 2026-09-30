# Keep the delete-library row disabled while a deletion runs

**Challenged:** dropped — the harm the plan names does not happen. `SongRepositoryImpl.deleteAllSongs` takes `LibraryFileLock` first and only then lists the directory (`songLocalSource.loadSongFileSizes().keys`), so a second deletion that waited behind the first finds an empty folder and deletes nothing: no "file is gone" failure per file, no "The operation failed" message (the same holds for `deleteAllSetlists`). What is left is a second typed `DELETE` that does nothing, and with plan 01 a redundant `cancelSynchronization()` before the first's run has even started. The row staying enabled for a few seconds is cosmetic; the new state flow, the cover-cache row change and the CLAUDE.md sentence are not worth a guard against a no-op.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SettingsScreen.kt`,
`presentation/CLAUDE.md` where it describes the Library section of Settings

## Problem

The dialog that deletes the library confirms once (`rememberSingleConfirmation`) and then closes:

```kotlin
val deleteLibrary = {
    confirmOnce {
        viewModel.deleteLibrary()
        viewModel.dismissDialog()
```

`CampfireViewModel.deleteLibrary` launches the use case and forgets it:

```kotlin
fun deleteLibrary() = launchLibraryChange {
    deleteLibrary.invoke()
}
```

and the Settings row that opens the dialog only looks at the counts, the import and performance mode:

```kotlin
isEnabled = summary.songCount + summary.setlistCount > 0 && !isImporting && !isPerformanceModeEnabled,
onClick = { viewModel.showDialog(CampfireViewModel.DialogType.DeleteLibrary) },
```

The use case is `NonCancellable` and takes as long as deleting every file takes — on the web, one OPFS call per file
through the worker; on a phone with a thousand songs, seconds — and the counts only drop as the repositories update
their state part of the way through. Until then the row is enabled, the dialog can be opened again and `DELETE` typed
again, and a second deletion runs beside the first: it waits on `LibraryFileLock` behind the first one's pass, then
deletes files that are gone, which `deleteAllSongs` reports as a failure for each, and `launchLibraryChange` shows
"The operation failed" for a library that was deleted exactly as asked. With plan 01 in place the second deletion also
cancels the first one's sync run and starts another, which is harmless but pointless.

## Fix

A state-based guard, like the import's `isImporting`. In `CampfireViewModel`:

```kotlin
private val _isDeletingLibrary = MutableStateFlow(false)
/** True from the typed confirmation until every file the library held is gone, or has failed to go. */
val isDeletingLibrary = _isDeletingLibrary.asStateFlow()

fun deleteLibrary() {
    if (!_isDeletingLibrary.compareAndSet(expect = false, update = true)) return
    launchLibraryChange {
        try {
            deleteLibrary.invoke()
        } finally {
            _isDeletingLibrary.value = false
        }
    }
}
```

In `SettingsScreen`, collect it and add `&& !isDeletingLibrary` to the row's `isEnabled`, and pass the same flag to the
cover cache row (the cache is deleted after the library read that finds no song naming a cover, so the row would flicker
enabled and disabled in the middle of the deletion otherwise; check its `isEnabled` expression and add the flag where
it is computed). The dialog needs no change: `deleteLibrary` refusing a second call is enough, and the dialog cannot
be opened while the row is disabled. Add a sentence to `presentation/CLAUDE.md`'s Settings paragraph, next to where
the typed word is described: the row is disabled while the deletion runs.

## Tests

None: a `StateFlow` set and reset around one call. The `presentation` tests do not build the view model.

## Manual check

Desktop build with a library of about two thousand generated songs: type `DELETE`, and while the counts are still
dropping try the row again. It is disabled until the library is empty, and no "operation failed" message is shown.
