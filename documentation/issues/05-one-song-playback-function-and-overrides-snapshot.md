# Work out "how this song is played here" in one pure `songPlaybackOf` over one `PlayingOverridesSnapshot` flow

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all
**Challenged:** amended — the new file goes into `ui.playing` (the package-move pass's home for the override types,
where plan 02 also puts `SongOverrides`); the sites that read only `transpositions` (`SongDetailsScreen` ~`:216`,
`ChordShapesSheet`, `SongPlayingDialog`) are left on it, since moving them to the three-way snapshot would recompose
them on every step of a held tempo or capo stepper; sites named as they are after the file splits.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
(`transpositions`, `tempos`, `capos`, new `playingOverrides`); new `ui/playing/SongPlayback.kt`;
`screens/songs/SongList.kt` (split out of `SongsScreen.kt`: the three `collectAsStateWithLifecycle` at ~`:133-135` and
the row block ~`:280-282`), `screens/setlists/SetlistList.kt` (~`:178-180`, `:443`, `:528`),
`screens/songDetails/SongDetailsScreen.kt` (`:671-673`, `:720-746`, `:964-981`, `:1048-1058`; **not** `:216`, which
reads only the transpositions), `screens/songDetails/SongMetadataActions.kt`
(`rememberSongPlayingControls`, ~`:55-67`); new `commonTest/.../overrides/SongPlaybackTest.kt`
**Depends on:** 02 (uses `SongOverrides`/`SongPlace`; can be written against `Tempos`/`Capos`/`Transpositions` if 02
has not landed, but then 02 has to touch it again)

## Problem

What a song is played at where it is opened — the transposition, the effective capo and the effective tempo — is
assembled by hand in four places from three separately collected flows, each repeating the
"setlist entry, else library override, else file" lookup and the `setlistFileName` threading:

```kotlin
// SongsScreen
val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
val capos by viewModel.capos.collectAsStateWithLifecycle()
val tempos by viewModel.tempos.collectAsStateWithLifecycle()
…
val transposition = transpositions[song.fileName, null]
val capo = effectiveCapo(song = song, setlistFileName = null, capos = capos)
val tempo = effectiveTempo(song = song, setlistFileName = null, tempos = tempos)
val key = remember(song.key, song.transpose, transposition, capo, chordSpelling) {
    viewModel.renderKey(song = song, transposition = transposition, capo = capo.fret, spelling = chordSpelling)
}
```

`SetlistsScreen` does the same per entry (with the setlist's file name), `SongDetailsScreen` does it three times
(the pager page at ~`:720`, the app bar header at ~`:1048-1058`, and once more for `currentTempo` at ~`:981`), and
`rememberSongPlayingControls` takes the pieces as five separate parameters. Three flows also mean three
recompositions' worth of state reads where one would do, and a new playing value would have to be added in all four.

## Fix

1. Add a pure value and function (`ui/playing/SongPlayback.kt`):
   ```kotlin
   @Immutable internal data class PlayingOverridesSnapshot(val transpositions: Transpositions, val capos: Capos, val tempos: Tempos)
   @Immutable internal data class SongPlayback(val transposition: Int, val capo: EffectiveCapo, val tempo: EffectiveTempo)
   internal fun songPlaybackOf(song: Song, setlistFileName: String?, overrides: PlayingOverridesSnapshot) = SongPlayback(
       transposition = overrides.transpositions[song.fileName, setlistFileName],
       capo = effectiveCapo(song = song, setlistFileName = setlistFileName, capos = overrides.capos),
       tempo = effectiveTempo(song = song, setlistFileName = setlistFileName, tempos = overrides.tempos),
   )
   ```
2. Add `internal val playingOverrides = combine(transpositions, capos, tempos, ::PlayingOverridesSnapshot).asState(PlayingOverridesSnapshot(Transpositions(), Capos(), Tempos()))`
   to the view model (or `PlayingOverrides` after plan 01). Keep the three existing flows: the metronome collector and
   `effectiveTempoOf`/`effectiveCapoOf` read them.
3. In each of the four places — the ones that collect all three flows today — collect `playingOverrides` once and call `songPlaybackOf(song, setlistFileName, overrides)`;
   pass `SongPlayback` to `rememberSongPlayingControls` instead of `transposition`, `tempo`, `capo`. Keep every
   `remember(…)` key that exists today, using the snapshot's fields, so a row still skips when only another song's
   override changed (the `remember` around `renderKey` in `SongsScreen` keys on the values, not the snapshot). The
   rendering of the sounding key stays a call to `renderKey` (or `SongRenderer.renderKey` after plan 04): it needs the
   chord spelling and is a separate concern. Leave every composable that collects only `transpositions` (the pager at
   `SongDetailsScreen` ~`:216`, `ChordShapesSheet`, `SongPlayingDialog`) on that flow: the snapshot changes on every
   tempo or capo step, and they would recompose for nothing. One commit.

## Tests

`SongPlaybackTest`: a library song reads library overrides and never a setlist's; a setlist song reads only that
setlist's entry; an override equal to the file's own is reported as the default (`EffectiveCapo.isDefault`,
`EffectiveTempo.isDefault`); the transposition wraps (`+7` reads `-5`). Existing `SongTempoTest`, `SongCapoTest`,
`TranspositionLabelTest`.

## Manual check

Set a transposition, capo and tempo for one song in the library and different ones for the same song in a setlist;
confirm the Songs card, the Setlists card, the song details app bar line, the first-section steppers and the click all
show the library values from the library and the setlist values from the setlist, as before.
