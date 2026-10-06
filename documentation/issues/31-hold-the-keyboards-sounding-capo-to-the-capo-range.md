# Hold the capo the keyboard's Chords section sounds the chords by to the app's capo range, as the Chord shapes sheet does

**Challenged:** amended — the capo-3 test asserted the sounding name `D#`, but a keyless `[C]` capoed 3 sounds in E♭ major, which `prefersFlats` spells `Eb`; it now asserts the root and the name `Eb`. Noted the PDF's printed capo row, which this plan leaves unclamped.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/SongChords.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/chords/SongChordsTest.kt`

## Problem

The parser takes any non-negative `{capo}` (`ChordProParser.kt`: `"capo" -> if (capo == null) capo = value.toIntOrNull()?.takeIf { it >= 0 }`),
while the app's capo is `Song.CAPO_RANGE` (`0..12`, `data/model/.../domain/Song.kt`). On the keyboard the Chords
section draws the chords a capo makes sound, and the three callers of `songChordsOf` disagree about a file's
`{capo: 15}`:

- the song page, `prepareSongLyrics` (`SongLyrics.kt`): `songChordsOf(standardSong, notation, chordInstrument, standardSong.metadata.capo ?: 0)` — the file's 15, unclamped (an override from a setlist or the library is already in range, `withCapo(inputs.capoOverride)`);
- the PDF, `printChordsOf` (`PrintLayout.kt`): `songChordsOf(song, notation, instrument, song.metadata.capo ?: 0)` — 15 as well;
- the Chord shapes sheet (`ChordShapesSheet.kt`): `effectiveCapo(...).fret`, which is `song?.capo?.coerceIn(Song.CAPO_RANGE)` — 12.

In `songChordsOf`, `val soundingShift = if (instrument == ChordInstrument.KEYBOARD) capo.mod(12) else 0`: the page and
the PDF move every chord up 3 semitones (15 mod 12) and name the sounding chord (`[C]` → "C · D#"), the sheet moves
it by 0 (12 mod 12) and shows plain `C`. So the sheet, which says it "reads the song as the page plays it", lists
other chords than the page, and the steppers the capo control shows (clamped to 12) disagree with the diagrams.

## Fix

Clamp inside `songChordsOf`, so all three callers agree without touching `PrintLayout.kt` (lane P's file) or
`prepareSongLyrics`:

```kotlin
// A file may say any capo, but the app's capo stops at the twelfth fret, as its control and the sheet read it.
val soundingShift = if (instrument == ChordInstrument.KEYBOARD) capo.coerceIn(Song.CAPO_RANGE) % 12 else 0
```

(`Song` is `com.pandulapeter.campfire.data.model.domain.Song`, already reachable from `:presentation`, see
`SongCapo.kt`.) Mention the clamp in `songChordsOf`'s KDoc ("[capo], held to the app's capo range, …").

## Tests

`SongChordsTest`: `a capo past the twelfth fret is read as the twelfth` —
`songChordsOf(parse("{capo: 15}\n[C]la"), STANDARD, KEYBOARD, capo = 15).single()` has `soundingName == null` and
`chord == ChordProChords.parse("C")`, the same as with `capo = 12`; with `capo = 3` it still moves: `chord.root == 3`
and `soundingName == "Eb"` (a keyless song is keyed by its first chord, C, and C moved by 3 is E♭ major, a flat key, see
`ChordProTransposer.prefersFlats`). Every caller agrees after the change: the page's override and the PDF's setlist
entry come in already clamped (`effectiveCapo`), and the file's own `{capo}` is clamped here.

Out of scope, for the record: the PDF's playing row prints `song.metadata.capo` as written (`PrintLayout.kt`,
`"${labels.capo}: $it"`), so a file's `{capo: 15}` still prints "Capo: 15" above diagrams drawn for 12, while the song
details screen's line clamps it (`SongMetadata.kt`). Lane P's file; not changed here.

## Manual check

Write `{capo: 15}` into a song, choose the keyboard in Settings → Songs, and open it: the Chords section draws the
chords as written (no "· D#" after "C"), the same as the Chord shapes sheet, and an exported PDF with Chord diagrams
ticked does too.
