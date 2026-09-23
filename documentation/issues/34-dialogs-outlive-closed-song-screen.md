# 34 — A sheet or dialog about a song stays up after the song has gone

**Severity:** stale UI that still writes (all platforms) · **Area:** `:presentation` (`CampfireViewModel.kt`)

**Read, not run.** This was found by reading the code at HEAD (2065e47f); it has not been reproduced in a running
build. The "Verification" section below is how to confirm it, and confirming it is the first step of the work.

## What the user sees

The song details screen is open with one of its sheets or dialogs up — setlist assignments, "Add tag", the language
picker, "Delete song", or the display options sheet — and the song leaves the library underneath it: a sync run
deletes or renames it on another device's say-so, or on the desktop and iOS the file is removed in the folder and the
resume rescans. The details screen closes itself, as it should. The sheet stays, still naming the song:

- the setlist picker still ticks: ticking a setlist writes the gone file's name into it, which then shows as a missing
  entry;
- "Delete song" confirms into a delete of a file that is not there;
- "Add tag" and the languages picker write nothing (`editSongText` finds no text) and say nothing.

The same happens with the setlist picker or "Delete song" opened from a row of the Songs or Setlists screen when that
song goes.

## Cause

The details screen closes itself when its songs are gone
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt:148-154`):

```kotlin
    LaunchedEffect(songs.isEmpty(), isLoading) {
        // Every song this screen was opened on is gone from the library, and the library has been read: there is
        // nothing left to show, so the screen goes the way it would have if the song had been deleted from here. ...
        if (songs.isEmpty() && !isLoading && viewModel.backStack.lastOrNull() == destination) onBack()
    }
```

`onBack` is `navigateBack` → `popBackStack` → `updateBackStack` (`CampfireViewModel.kt:864-870`), none of which touch
`visibleDialog`. The dialogs are not part of the screen: `CampfireDialogs` renders whatever `visibleDialog` holds,
above every screen (`CampfireApp.kt:502`), and five of its types are about one song
(`CampfireViewModel.kt:2261-2277`): `SetlistPicker(song, …)`, `SongDisplayControls(songFileName, …)`,
`DeleteSong(song)`, `AddSongTag(song)`, `SongLanguages(song)`. Nothing takes them down when that song goes.

## The change

Invoke the **`code-style`** skill before the first edit.

**Broader than the reviewer's first suggestion** (dismiss the tied dialogs in the details screen's self-closing
branch): the same sheets are opened from the list screens' rows, which do not close, and a dialog is the view
model's state rather than the screen's. So the rule goes where `visibleDialog` lives: a dialog about a song goes when
that song leaves the library. It is answered with state — the library as read — rather than with the screen closing,
so it holds whichever screen opened the dialog, and whichever order the screen and the dialog notice in.

In `CampfireViewModel`, next to the other collectors in `init` (after the `importQueue` consumer at `:829-840`):

```kotlin
        // A sheet or a dialog about one song goes when the song does - deleted or renamed by a sync run, or taken out
        // of the folder behind the app's back - whichever screen opened it: the details screen underneath closes
        // itself, but the dialogs are not its own, and a setlist picker left behind would write the name of a file that
        // is not there into every setlist ticked in it. Only against a library that has been read, and never for a song
        // this app is renaming, which is missing from the library for a few writes on purpose (songsBeingRenamed).
        viewModelScope.launch {
            combine(_visibleDialog, allSongs, isLoading, _songsBeingRenamed) { dialog, songs, isLoading, songsBeingRenamed ->
                val fileName = dialog?.songFileName
                dialog?.takeIf { fileName != null && !isLoading && fileName !in songsBeingRenamed && songs.none { it.fileName == fileName } }
            }.filterNotNull().collect(::dismissSheet)
        }
```

and, with the other private helpers:

```kotlin
    /** The song a dialog is about, for the ones that are about one, see the collector in `init`. */
    private val DialogType.songFileName: String?
        get() = when (this) {
            is DialogType.SetlistPicker -> song.fileName
            is DialogType.SongDisplayControls -> songFileName
            is DialogType.DeleteSong -> song.fileName
            is DialogType.AddSongTag -> song.fileName
            is DialogType.SongLanguages -> song.fileName
            else -> null
        }
```

Notes on the details:

- `dismissSheet(dialogType)` (`:1954-1956`) dismisses only while that dialog is still the one on screen, so a dialog
  the user replaced meanwhile is never taken down by this; it works for alert dialogs as well as sheets.
- `isLoading` covers the first read, which publishes the library in batches (a partial list must not dismiss
  anything); a rescan keeps the previous library on screen while it reads, so it is not a moment of missing songs.
- `allSongs` is the unfiltered library, so a song the filters hide is not "gone".
- Setlist dialogs (`SongPicker`, `EditSetlist`, `DuplicateSetlist`, `DeleteSetlist`) are deliberately left out: a
  `SongPicker` is opened on a setlist created a moment ago, before `setlists` has caught up with it
  (`DialogType.SongPicker` KDoc, `:2262-2267`), and this rule would close it on the spot. Their writes already refuse
  a setlist that has gone (`setSetlistSongs`, `editSetlist` → `Message.OperationFailed`).
- `combine` and `filterNotNull` are already imported; `DialogType` is nested, so the extension needs no import.

This does not interact with plan 32 (the update gate stops rendering dialogs without changing `visibleDialog`).

## Tests

- **No unit test is possible** (view model, untested by policy).
- Compile check: `./gradlew :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs :presentation:compileDebugKotlinAndroid :presentation:compileKotlinIosSimulatorArm64`

## Verification

**Desktop** is the easiest: the library is a folder and the window rescans on focus.

1. `./gradlew :app:desktop:run`. Open a demo song → ⋮ → Setlist assignments (the sheet is up).
2. Switch to Finder, move that song's `.cho` out of `~/Library/Application Support/Campfire/library/songs/`, switch
   back (the resume rescans).
   - **Before:** the details screen closes, the sheet stays; ticking a setlist adds a missing row to it.
   - **After:** the details screen closes and the sheet goes with it.
3. Repeat with "Add tag", the language chip's picker, ⋮ → Delete, and (narrow window) the display options sheet.
4. Repeat from the Songs screen: a row's ⋮ → Setlist assignments, then remove the file → the sheet goes.
5. **Rename regression:** a song whose file name differs from its header → ⋮ → Update file name, and immediately
   ⋮ → Setlist assignments on the details screen: the sheet stays (the song is being renamed, not gone).
6. **Sync** (two devices or the Dropbox web UI): delete the file remotely, sync with the sheet up → the sheet goes.
7. Regression: create a setlist from the Setlists screen with songs in the library → the song picker opens and stays.

## Docs

- `presentation/CLAUDE.md`, `ui/dialogs/Dialogs.kt` bullet, after the sentence about `dismissSheet(itsOwnDialog)`: "A
  dialog or sheet about one song (the setlist picker, the display options, the tag and language dialogs, the delete
  confirmation) is taken down by the view model when that song leaves the library, whichever screen it was opened
  from; the setlist dialogs are not, since the song picker opens on a setlist the library has not caught up with yet."

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/CLAUDE.md`

## Depends on

Nothing. Lane D order in `CampfireViewModel.kt`: 33, 31, 30, 29, **34**, 44. Plan 17 (lane B) may change how a
setlist entry is matched to a song (case / NFC folding); this rule matches the dialog's own `song.fileName`, which is
always a name the library listed, so it is unaffected.
