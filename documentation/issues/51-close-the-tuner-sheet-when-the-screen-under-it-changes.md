# Close the tuner sheet when the screen under it changes, instead of leaving a sheet that never listens again

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`

## Problem

The view model stops the tuner whenever the top of the back stack changes (`CampfireViewModel.kt:375-379` at
b5c8ed3b5):

```kotlin
addOnBackStackChanged { previousTop, stack ->
    // Nothing of the tuner outlives the screen it is used on: the tab, or the song its sheet is opened over. The
    // screen starts it again once it is composed, and only that screen does.
    if (previousTop?.contentKey != stack.lastOrNull()?.contentKey) stopTuner()
}
```

But the tuner sheet is a dialog (`CampfireDialogs.kt:159`, `DialogType.Tuner -> TunerSheet(...)`), drawn outside
`NavDisplay`, so it stays up over whatever screen arrives. Its `TunerListeningEffect` never starts it again: the effect
only listens from `LaunchedEffect(isStarted, canListen)` (`tuner/TunerListeningEffect.kt:37-39`), and neither key
changes. The result is a sheet whose display reads "Play a note" forever, over a screen it does not belong to.

`DialogHost.startClosingWithSong` does not catch it either: `DialogType.Tuner` is a `data object` with no song, so its
`songFileName` is `null` (`DialogHost.kt:127-136`, the `else -> null` branch).

The back stack changes under an open sheet in at least two ways the user does not drive through the sheet:
- **A sync run deletes the song** being read: `SongDetailsScreen.kt:116-121` calls `onBack()` once every song of the
  destination is gone, which pops the details screen and leaves the sheet over the list.
- **An "open with" import** of one song (`ImportController.kt:335`, or the snackbar's Open in `Messages.kt:139`) calls
  `Navigator.openImportedSong`, which replaces a details entry on top with a new one
  (`Navigator.kt:336-339`, `removeAt(lastIndex)` then `add(CampfireDestination.SongDetails(...))`) — a different
  content key, so the tuner stops and the sheet stays.

## Fix

The sheet is only ever opened over a song details screen (`SongDetailsAppBar.kt:180`), so a different screen on top
means its screen has gone: close it in the same listener. Go through the view model's own `dismissSheet` (Navigator
holds a private `dialogHost` of its own, so qualify the call to avoid resolving against it):

```kotlin
addOnBackStackChanged { previousTop, stack ->
    // Nothing of the tuner outlives the screen it is used on: the tab, or the song its sheet is opened over. The
    // screen starts it again once it is composed, and only that screen does - which a sheet left over another screen
    // never is, so the sheet goes with the song it was opened over.
    if (previousTop?.contentKey != stack.lastOrNull()?.contentKey) {
        stopTuner()
        this@CampfireViewModel.dismissSheet(DialogType.Tuner)
    }
}
```

`dismissSheet` only acts while `DialogType.Tuner` is the dialog on screen (`DialogHost.dismissSheet`), and closing it
runs the existing `previousDialog is DialogType.Tuner` listener, whose second `stopTuner()` is a no-op.

Docs: `ui/tuner/CLAUDE.md`, the `TunerController` bullet: "the view model stops it whenever the top of the back stack
changes — closing the sheet with it, since the sheet belongs to the song under it — and whenever …".

## Tests

None: a back stack listener in the view model, which is not tested.

## Manual check

1. Two devices synced to one Dropbox folder: open a song on device A, open Tuner from its overflow menu, delete the
   song on device B and sync both. On A the details screen closes and the sheet closes with it.
2. On Android, with a song and its tuner sheet open, open a `.cho` file from the Files app with Campfire: the new song
   opens without a tuner sheet over it. Opening Tuner from its menu listens normally.
