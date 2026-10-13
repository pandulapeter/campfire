# Skip the second walk over a song's chords when the chords have already been collected

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyricsModel.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RenderSectionsTest.kt`
(or `SongModelBuildTest.kt` in the same folder)

## Problem

`prepareSongLyrics` (`SongLyricsModel.kt` at 72d48deb7) collects the chords for the Chords section and then asks a
second question about the same chords:

```kotlin
chords = if (shouldShowChords && chordTuning != null) {
    songChordsOf(standardSong, notation, chordTuning, searchesShapes = searchesShapes)
} else {
    emptyList()
},
chordTuning = chordTuning,
hasChords = shouldShowChords && hasSongChords(standardSong, notation),
```

`hasSongChords` (`ui/chords/SongChords.kt`) runs `ChordProNotation.shownNames(song, notation)` and parses every name
again, a second full pass over the song's chords that `songChordsOf` has just made. It is only needed where there is no
tuning to collect them for (an instrument without shapes; its KDoc: "Asked where there is no tuning to collect them
for"). The first model of a page is built in place, on the main thread, in the frame being waited for
(`rememberSongLyricsModel`), so the extra pass lands on that frame on every page opened, transposed, or re-spelled.

The two answers are not interchangeable everywhere: `songChordsOf` counts a chord twice where the song defines two
spellings of it differently, `hasSongChords` once. A probe at 72d48deb7 with 48 distinct chords plus `B#` defined
differently from `C`: `songChordsOf` returned no chords (49 entries, a songbook) while `hasSongChords` answered true.
But whenever `songChordsOf` returns any chord, `hasSongChords` is true (at most 48 entries means at most 48 distinct
chords, and at least one).

## Fix

Ask `hasSongChords` only when the collected chords cannot answer:

```kotlin
val chords = if (shouldShowChords && chordTuning != null) songChordsOf(…) else emptyList()
…
hasChords = shouldShowChords && (chords.isNotEmpty() || hasSongChords(standardSong, notation)),
```

This keeps every answer exactly as it is today (a song with chords skips the second pass; a song without, or a songbook,
or an instrument without shapes, still asks) — no behaviour change, so the chips of the songbook edge case above stay.
Restructure the expression so `chords` is a local used for both arguments of `SongLyricsModel`.

## Tests

Add to `RenderSectionsTest.kt` (it already builds models with `prepareSongLyrics`): for `{sov}\n[G]la [C]la\n{eov}`,
`prepareSongLyrics(…, chordTuning = ChordTuning.GUITAR).hasChords` and `prepareSongLyrics(…, chordTuning = null).hasChords`
are both true; for a song without chords both are false. (The saved pass itself is not observable from a test.)

## Manual check

None beyond a smoke test: open a song with chords on the guitar and on the bass (Settings → Songs → instrument): the
instrument chips head the first section in both, and the Chords section is there on the guitar.
