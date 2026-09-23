# 29 — Quick taps on a setlist song's transposition stepper are lost

**Severity:** lost input (all platforms, most visible on the web) · **Area:** `:presentation` (`CampfireViewModel.kt`,
`screens/songDetails/SongDisplayControls.kt`, `screens/songDetails/SongDetailsScreen.kt`)

**Read, not run.** This was found by reading the code at HEAD (2065e47f); it has not been reproduced in a running
build. The "Verification" section below is how to confirm it, and confirming it is the first step of the work.

Merges two findings of the presentation state review: F1 (taps lost) and F7 (a setlist that is gone fails silently).

## What the user sees

1. A song opened from a setlist, the transposition stepper pressed + five times quickly. The setlist file ends up at
   `+2` or `+3` rather than `+5`, and the stepper settles on that. On the web, where every write goes through OPFS in a
   worker, two taps a fraction of a second apart are enough. `documentation/testing/00-core-functional.md` CORE-103
   already expects `"transposition": 5` after five fast taps, so this is a test that fails today on slow storage.
2. A song opened from a setlist whose file has since gone (deleted by a sync run, deleted in the Files app or the
   desktop folder and picked up by the rescan on resume): the details screen stays open — it pages through
   `destination.songFileNames`, not through the setlist — the stepper reads `0`, and pressing + does nothing at all,
   with no message.

A song opened from the library (transposition stored in the preferences) is not affected in practice, see Cause.

## Cause

The stepper sends an **absolute** value computed from what it was last drawn with.
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDisplayControls.kt:136-144`:

```kotlin
    canDecrease = transposition > CampfireViewModel.MIN_TRANSPOSITION,
    onDecrease = { onTranspositionChanged(transposition - 1) },
    increaseIcon = painterResource(Res.drawable.ic_add),
    increaseLabel = stringResource(Res.string.song_details_transpose_up),
    canIncrease = transposition < CampfireViewModel.MAX_TRANSPOSITION,
    onIncrease = { onTranspositionChanged(transposition + 1) },
    resetLabel = stringResource(Res.string.song_details_transpose_reset),
    onReset = { onTranspositionChanged(0) },
```

`transposition` comes from `viewModel.transpositions` (`SongDetailsScreen.kt:187`, `SongDisplayControls.kt:88`),
which for a setlist song is read out of `setlists` (`CampfireViewModel.kt:463-470`). The write
(`CampfireViewModel.kt:1354-1375`):

```kotlin
    fun setTransposition(songFileName: String, setlistFileName: String?, transposition: Int) = launchLibraryChange {
        val clamped = transposition.coerceIn(MIN_TRANSPOSITION, MAX_TRANSPOSITION)
        if (setlistFileName == null) {
            userPreferences.value?.let { preferences ->
                saveUserPreferences(
                    ...
                )
            }
        } else {
            updateSetlist(setlistFileName) { setlist ->
                setlist.copy(
                    entries = setlist.entries.map { if (it.songFileName == songFileName) it.copy(transposition = clamped) else it }
                )
            }
        }
    }
```

For a setlist the new value only reaches the stepper after `SetlistRepositoryImpl.updateSetlist` has read the latest
file, written it (`writing { latest(fileName)?.let(transform)?.let { write(it) } }`, `SetlistRepositoryImpl.kt:68-70`,
under a mutex and on `NonCancellable`), updated its cache, and the result has come through `GetScreenDataUseCase`'s
combine, `setlists`, `transpositions` and a recomposition. Every tap inside that round trip reads the same
`transposition` off the stepper and asks for the same absolute number: five taps become "set 1" three times and
"set 2" twice. `UpdateSetlistUseCase` already serializes the writes and hands the transform the latest setlist — the
transform just throws that value away and writes the stale absolute one.

The library path is safe in practice: `BaseLocalDataRepository.writeData` publishes the new preferences
synchronously *before* it writes (`BaseLocalDataRepository.kt:128-129`), and `setTransposition` reads
`userPreferences.value` at the moment of the tap. It still takes the absolute number from the stepper, though, so it is
changed the same way for one rule in both places.

F7: `updateSetlist` returns null for a setlist that is not there any more, and `setTransposition` ignores it, while
`setSetlistSongs` (`CampfireViewModel.kt:1725-1730`) and `editSetlist` (`:1738-1739`) turn the same null into
`Message.OperationFailed`.

## The change

Invoke the **`code-style`** skill before the first edit.

Send a **change** rather than a value: a step of ±1 or a reset, applied to whatever the store holds when the write
runs. For a setlist that is the entry `UpdateSetlistUseCase` hands the transform (the latest file, one write at a time,
in the order asked), so five taps are five steps whatever the round trip costs. This is the same shape as
`adjustFontScale(steps)` / `setFontScale(DEFAULT)` next to it, and the same reasoning `setSetlistSongs` and
`reorderSetlist` already document ("a toggle worked out from `setlists` would lose the one before it").

### `CampfireViewModel.kt`

Replace `setTransposition` (`:1353-1375`) with:

```kotlin
    /** One step of the transposition stepper. A song opened from a setlist transposes inside that setlist; one opened from the library, in the preferences. */
    fun stepTransposition(songFileName: String, setlistFileName: String?, semitones: Int) =
        changeTransposition(songFileName = songFileName, setlistFileName = setlistFileName) { it + semitones }

    /** The stepper's value tapped: the song goes back to the key its file is written in. */
    fun resetTransposition(songFileName: String, setlistFileName: String?) =
        changeTransposition(songFileName = songFileName, setlistFileName = setlistFileName) { 0 }

    /**
     * Applies [change] to the transposition the store holds when the write runs rather than to the one the stepper was
     * drawn with. A setlist's entry only reaches the screen once its write has been round tripped through the
     * repository, and every tap inside that round trip read the same number off the stepper, so five quick taps were
     * written as two. The setlist's transform is handed the latest version of the file, one write at a time
     * ([UpdateSetlistUseCase]); the preferences are published before they are written, so [userPreferences] already
     * holds the previous tap.
     *
     * A setlist that is gone by now is not brought back, and saying nothing would leave a stepper that does nothing.
     */
    private fun changeTransposition(songFileName: String, setlistFileName: String?, change: (Int) -> Int) = launchLibraryChange {
        if (setlistFileName == null) {
            userPreferences.value?.let { preferences ->
                val transposition = change(preferences.transpositions[songFileName] ?: 0).coerceIn(MIN_TRANSPOSITION, MAX_TRANSPOSITION)
                saveUserPreferences(
                    preferences.copy(
                        transpositions = if (transposition == 0) {
                            preferences.transpositions - songFileName
                        } else {
                            preferences.transpositions + (songFileName to transposition)
                        }
                    )
                )
            }
        } else {
            updateSetlist(setlistFileName) { setlist ->
                setlist.copy(
                    entries = setlist.entries.map { entry ->
                        if (entry.songFileName == songFileName) {
                            entry.copy(transposition = change(entry.transposition).coerceIn(MIN_TRANSPOSITION, MAX_TRANSPOSITION))
                        } else {
                            entry
                        }
                    }
                )
            } ?: sendMessage(Message.OperationFailed)
        }
    }
```

`Setlist.Entry.transposition` is an `Int` (it is what `transpositions` reads at `:467`), so no import changes.

### `SongDisplayControls.kt`

`TranspositionControls` (`:121-145`) takes the two intents instead of an absolute value, like `FontScaleControls`
beside it:

```kotlin
@Composable
internal fun TranspositionControls(
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    transposition: Int,
    /** The key the song sounds in after transposing, shown next to the amount when the file declares one. */
    key: String? = null,
    onStep: (semitones: Int) -> Unit,
    onReset: () -> Unit,
) = Stepper(
    ...
    onDecrease = { onStep(-1) },
    ...
    onIncrease = { onStep(1) },
    resetLabel = stringResource(Res.string.song_details_transpose_reset),
    onReset = onReset,
)
```

`canDecrease` / `canIncrease` stay on the drawn value: a tap past the limit is clamped by the view model anyway.

The sheet's call (`:99-103`):

```kotlin
                    TranspositionControls(
                        transposition = songTransposition,
                        key = transposedKey,
                        onStep = { viewModel.stepTransposition(song.fileName, dialog.setlistFileName, it) },
                        onReset = { viewModel.resetTransposition(song.fileName, dialog.setlistFileName) },
                    )
```

### `SongDetailsScreen.kt`

The app bar's call (`:275-283`):

```kotlin
                        TranspositionControls(
                            modifier = Modifier.padding(end = INLINE_CONTROL_SPACING),
                            isCompact = true,
                            transposition = currentTransposition,
                            key = currentKey,
                            onStep = { semitones -> currentSong?.let { viewModel.stepTransposition(it.fileName, destination.setlistFileName, semitones) } },
                            onReset = { currentSong?.let { viewModel.resetTransposition(it.fileName, destination.setlistFileName) } },
                        )
```

Not in scope: a setlist that still exists but no longer holds the song (unticked in the picker from another screen
while this one is open) writes an unchanged setlist and says nothing. That is plan 34's territory (a screen outliving
what it was opened on) and rare enough to leave.

## Tests

- **No unit test is possible.** The view model is untested by policy and `:presentation` has no test source set; the
  serialization that makes the fix work is `UpdateSetlistUseCase`'s, which is already the documented contract.
- Compile check for every target:
  `./gradlew :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs :presentation:compileDebugKotlinAndroid :presentation:compileKotlinIosSimulatorArm64`

## Verification

Confirm the bug first, then the fix.

1. **Web** (slowest storage, the clearest repro): `./gradlew :app:web:wasmJsBrowserDevelopmentRun`, planted demo
   library. Setlists → the demo setlist → open its first song. Window at least 840dp wide so the stepper is inline
   in the app bar.
   - Press + five times as fast as possible.
   - **Before:** the stepper settles below `+5`. Export the setlist (⋮ → Export), unzip, and the entry's
     `"transposition"` is below 5.
   - **After:** `+5`, and the exported file says 5. Press the value to reset: `0`, and the entry has no transposition.
   - Press + fifteen times fast: stops at `+11`, the file says 11.
2. **Desktop** (`./gradlew :app:desktop:run`), the same on a setlist song, then read
   `~/Library/Application Support/Campfire/library/setlists/*.setlist.json` (macOS) — CORE-103.
3. **Narrow window / phone** (Android emulator or a narrow desktop window): the same through the "display options"
   sheet, since that is the other caller.
4. **Library song**: open a song from the Songs screen, + five times fast → `+5` in `preferences/preferences.json`;
   reset → the key is gone from `transpositions`.
5. **F7**: desktop, open a setlist song, then (with the app in the background) delete the setlist's file from
   `library/setlists/`, bring the window back (the resume rescans). Press + → the "something went wrong" snackbar
   (`Message.OperationFailed`). Before the fix: nothing.

## Docs

- `presentation/CLAUDE.md` does not name `setTransposition`; nothing to change there.
- `documentation/testing/00-core-functional.md` CORE-103 already states the expected result.

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDisplayControls.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`

## Depends on

Nothing functionally. Landing order inside lane D: after 33, 31 and 30, before 34 (all touch
`CampfireViewModel.kt`, different functions); before 39 (also `SongDetailsScreen.kt`) and 43 (also
`SongDisplayControls.kt`, KDoc only). Plan 17 (lane B) touches `CampfireViewModel.kt` only if its option (a)
is chosen (the setlist entry lookup); no overlap with `setTransposition`. Plan 28 (lane C) does not touch it.
