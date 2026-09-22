# 17 · An import in progress is abandoned half way without a word when the Android activity finishes

**Severity:** minor (Android. Back on the root screen during an import finishes the activity on Android 9-11 (minSdk
28), which clears the view model. Rare, and recoverable by importing the same files again, since files already in the
library are disregarded - but nothing tells the user to, and an archive cut off between its songs and its setlists
arrives without any of its setlists) · **Area:** `:presentation` (`CampfireViewModel.applyImportPlan`)

## Symptom
1. Android 9-11: import a large archive, and press Back on the song list while the progress bar runs.
2. The activity finishes and the view model is cleared. The songs written so far stay; the rest of the songs, and
   every setlist of the archive, are silently not imported. Reopening the app shows a partial library and no message.

(On Android 12+ Back on the root moves the task to the back instead, and swiping the app away normally kills the
process, which no scope survives; so this plan covers the case where the process stays alive.)

## Cause
The consumer, `resolveImport` and `applyImportPlan` all run in `viewModelScope`
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:751-761`,
`:1446-1453`, `:1461-1484`), which `onCleared` cancels. The write itself is cancellable
(`CampfireViewModel.kt:1463`):

```kotlin
val result = importFiles.invoke(plan, resolution)
```

`ImportFilesUseCaseImpl` writes the songs first and the setlists second, and only its final rescan is
`NonCancellable` (`domain/implementation/.../useCases/ImportFilesUseCaseImpl.kt`, the `finally` block), so a
cancellation lands between two files.

## Fix
Make the write the point of no return it already is everywhere else in the view model (`writeEditorText`, and the
rename and delete use cases, run their writes on `NonCancellable`). Planning and the conflicts question stay
cancellable - nothing has been written yet then.

`CampfireViewModel.kt:1463`, in `applyImportPlan`:

```kotlin
            // Not cancellable once it has started writing: the view model going away with the Android activity is no
            // reason to leave an archive half imported - its setlists come after all of its songs - and the
            // repositories the files go into outlive it, so whatever screen comes back finds the whole import.
            val result = withContext(NonCancellable) { importFiles.invoke(plan, resolution) }
```

`withContext` and `NonCancellable` are already imported (`CampfireViewModel.kt:95`, `:118`). After the block, a
cleared view model's `sendMessage` and `openImportedSong` only touch its own state and are harmless; `_isImporting`
is reset in `finally` as before.

Why not an application-scoped scope as `SyncRepository` has: the import is a few seconds of local writes, and the
view model's own dispatcher keeps running after it is cleared (the main thread outlives the activity). A foreground
service would only matter for a process the system kills, and swiping from recents kills it regardless.

Do not wrap `prepareImport` or the whole `import`: reading a large archive can take a while, it writes nothing, and a
user who leaves before anything is written has lost nothing.

## Tests
None (UI).

## Verify
1. Android 11 emulator (API 30): import an archive of a few hundred songs and a setlist (export a large library to
   make one). Press Back on the song list while the progress bar runs. Reopen the app from the launcher: every song and
   the setlist are there.
2. Same on API 34: Home during the import, return: the import finished; the "import finished" snackbar shows if the
   view model survived (it does, the activity was not finished).
3. Cancel on the conflicts question still leaves the library untouched.

Compile: `:presentation:compileKotlinDesktop`, `:app:android:assembleDebug`.

## Docs
`presentation/CLAUDE.md`, the `ui/CampfireViewModel.kt` bullet, after "…which turns a write that failed into a
`Message.OperationFailed` snackbar instead of an uncaught exception that would take the app down on Android." add:
"An import's writes run on `NonCancellable` once they start, so the view model going away with a finished Android
activity does not leave an archive half imported; planning it and the conflicts question are still dropped with it,
since nothing has been written by then."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 41 edits the import code in the same file; run them one after another.
