# Leave a stored transposition out of the Song defaults overrides of a song that has no chords

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongPlayingDialog.kt` (`songPlayingOverrides`)

Lane D: apply after 33 (same file, different function) and before 38.

## Problem

Root `CLAUDE.md`: "an override nobody is shown is neither named nor reset there." A transposition is only ever shown for
a song with chords — the stepper exists only where `rememberSongPlayingControls` computes
`val canTranspose = shouldShowChords && song.hasChords`, and the app bar's key needs `shouldShowChords && it.hasChords`.
But `songPlayingOverrides`, which fills both the Song defaults sheet's card and the About the song sheet's Song defaults
group (`Dialogs.kt`, `overrides = overrides.labels`), checks only the switch:

```kotlin
val transposition = transpositions[song.fileName, setlistFileName].takeIf { shouldShowChords } ?: 0
...
transposition.takeIf { it != 0 }?.let {
    stringResource(Res.string.song_details_playing_override_transposition, transpositionLabel(transposition = it, key = null))
},
```

A transposition stored for a song that has no chords (stepped before its chords were taken out, or a setlist entry's
`transposition` written by another device) is therefore named ("Transposed +2") and offered for Reset on a song where
nothing anywhere shows or uses it.

The read-only line's "Capo 0" on a lyrics-only song was reviewed alongside and is not a bug: the capo stepper is shown
for every song while the Chords switch is on (`capo = if (shouldShowChords) SongCapoControl(...)`, no `hasChords`), so
the read-only line saying the capo matches the editable page.

## Fix

```kotlin
val transposition = transpositions[song.fileName, setlistFileName].takeIf { shouldShowChords && song.hasChords } ?: 0
```

The `onReset` lambda already keys off the same `transposition` value, so a hidden transposition is no longer reset
either. Extend the KDoc of `songPlayingOverrides` ("the transposition and the capo go with the chords") with "and the
transposition with a song that has some".

## Tests

None: `songPlayingOverrides` is a composable reading view model state; the change is one condition.

## Manual check

Open a song with chords from the library, step the transposition to +2, then remove every chord from it in the editor and
save. Open Edit song defaults (and the About the song sheet's Song defaults group): no "Transposed" label and no Reset
card for it. Put a chord back: the +2 is named again.
