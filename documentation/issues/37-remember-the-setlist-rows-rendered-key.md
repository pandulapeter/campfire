# Remember each setlist row's rendered key instead of transposing it again on every composition

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt`

Lane D: independent of the other plans.

## Problem

The Songs screen remembers a row's key, with the reason spelled out:

```kotlin
// SongsScreen.kt — "The key is remembered, since the row is composed again as every scroll starts and ends and it is a
// whole transposition to work out again."
val key = remember(song.key, song.transpose, transposition, capo, chordSpelling) {
    viewModel.renderKey(song = song, transposition = transposition, capo = capo.fret, spelling = chordSpelling)
}
```

The Setlists screen calls the same function straight inside the row's arguments:

```kotlin
is CampfireViewModel.SetlistWithSongs.Entry.Present -> SongListItem(
    ...
    key = viewModel.renderKey(
        song = entry.song,
        transposition = transpositions[entry.song.fileName, setlistWithSongs.setlist.fileName],
        capo = effectiveCapo(song = entry.song, setlistFileName = setlistWithSongs.setlist.fileName, capos = capos).fret,
        spelling = chordSpelling,
    ),
```

`renderKey` builds a `ChordProSong`, runs `transposeChordPro` and `convertChordProNotation` on it — per row, on every
composition of that row (every scroll start and end, every reorder drag frame that recomposes the item, every write to
any setlist that changes `transpositions` or `capos`).

## Fix

Inside the `Entry.Present` branch (a `when` branch may hold a `remember`), compute the inputs first and remember the key
on them, mirroring `SongsScreen`:

```kotlin
is CampfireViewModel.SetlistWithSongs.Entry.Present -> {
    val setlistFileName = setlistWithSongs.setlist.fileName
    val transposition = transpositions[entry.song.fileName, setlistFileName]
    val capo = effectiveCapo(song = entry.song, setlistFileName = setlistFileName, capos = capos).fret
    // Remembered as on the songs screen: the row is composed again as every scroll starts and ends.
    val key = remember(entry.song.key, entry.song.transpose, transposition, capo, chordSpelling) {
        viewModel.renderKey(song = entry.song, transposition = transposition, capo = capo, spelling = chordSpelling)
    }
    SongListItem(..., key = key, ...)
}
```

Keep the existing comment about the setlist's own transposition and capo next to the new values.

## Tests

None: a composition-level `remember`; `renderKey` itself is unchanged.

## Manual check

Open a long setlist with transposed and capoed entries; the keys on the cards are unchanged, change correctly when a
song's transposition is stepped inside the setlist, and scrolling stays smooth.
