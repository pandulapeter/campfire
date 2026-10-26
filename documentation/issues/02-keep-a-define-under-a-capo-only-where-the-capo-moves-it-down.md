<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Draw a song's own shape under a capo other than its own only where that capo moves it down the neck, else the player's shape

**Challenged:** amended — the kept move is compared with `capo.mod(12)`, not `capo`: `Song.CAPO_RANGE` is `0..12`, and
at capo 12 `transposed` returns the definition unchanged (no move), which the plan's `written.movedBy - capo` check would
drop although the shape above a capo on the twelfth fret is the written one (probed: ukulele `C 0 0 0 3`, capo 12 → kept
before, dropped by the plan as written). A test pins it. Everything else probed as the plan says; widened by the user's ruling (a lower capo override draws the player's shape).

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all, fretted instruments (song page, chord sheet, PDF)
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/SongChords.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/chords/SongChordsTest.kt`
(no caller: the file's capo is read from the song each one already passes)

## Problem

`songChordsOf` moves a song's `{define}` down by the shape capo (`ui/chords/SongChords.kt:97-98` at 54829e6fd):

```kotlin
?.let { if (capo == 0) it else ChordProDefinitions.transposed(it, -capo) { shapeName -> shapeName } }
?.takeIf { it.movedBy == 0 || (it.voicing as? ChordVoicing.Fretted)?.frets?.let(ChordVoicings::isHoldable) != false }
```

`ChordProDefinitions.transposed` cannot move a shape with open strings down (its `moved` tries only `semitones.mod(12)`
for such a shape), so a capo of 2 moves it **up 10**, and `isHoldable` accepts anything up to fret 15. Probed in the
review: guitar `{define: G base-fret 1 frets 3 2 0 0 0 3}` with a device capo of 2 is drawn `[13,12,10,10,10,13]`
(movedBy 10, holdable), the same G an octave up at frets 12–15 above the capo, instead of the F shape the table gives;
ukulele `{define-ukulele: C frets 0 0 0 3}` with capo 2 is drawn `[10,10,10,13]`. The same lands in the chord sheet
(`ChordShapeSheet`) and the PDF (`printChordsOf`), which all call `songChordsOf`. The other way, a `{capo: 2}` file's G
opened with a capo of 0 is drawn moved up two (`[5,4,2,2,2,5]`, a barred A) where a player taking the capo off plays
open shapes.

The file-capo round trip is fine: the transposer moves the G up by the file's capo (`[5,4,2,2,2,5]`, no open string
left), and the capo's move takes it back down to `[3,2,0,0,0,3]`, movedBy 0 (pinned by `SongChordsTest`'s "a file's own
shape comes back to where it was written under the capo it names").

## Fix

One rule for both directions: with no capo override (the shape capo is the file's own `{capo}`) a definition is moved
exactly as today, whatever the reader's transposition; under any other capo it is kept only where the difference from
the file's capo moves it **down** the neck by exactly that many frets (a capo put on or raised, over a shape that can
go down). A capo lower than the file's (taken off or lowered) and an open shape that cannot go down both drop the
definition for this capo, the way one a transposition left needing a fifth finger is, and the chord is drawn with the
player's pick or the app's shape for `shapeChord`.

`songChordsOf` learns the file's capo from the song it is given, so **no caller changes**: every caller passes the song
before an override of its capo is written into it, and the transposer leaves `metadata.capo` alone. The song page passes
`standardSong` (`SongDetailsPage`: `transposed`, before `withCapo(inputs.capoOverride)`), `ChordShapeSheet` the file's
text transposed (`transposedSong`, no `withCapo`), and `printChordsOf` the export's `transposed` (before `withCapo` /
`withoutCapo`). The scale sheet does not call it. The value is coerced as `effectiveCapo` coerces `songFret`, so a
caller's `capo` with no override (`capo.fret`, or the export's `capo ?: effectiveCapo.songFret`) equals it:

```kotlin
val songCapo = (song.metadata.capo ?: 0).coerceIn(Song.CAPO_RANGE)
fun definitionOf(name: String, chord: Chord) =
    (definitions.lastOrNull { it.name == name } ?: definitions.lastOrNull { ChordProChords.parse(it.name) == chord })
        ?.let { written ->
            fun movedDown(definition: ChordDefinition, frets: Int) =
                if (frets == 0) definition else ChordProDefinitions.transposed(definition, -frets) { shapeName -> shapeName }
            when {
                // The keyboard takes no capo (its `capo` is always 0) and its definition is notes, not a shape: as today.
                written.voicing !is ChordVoicing.Fretted -> movedDown(written, capo)
                else -> {
                    // Where the file's own capo is played, its shapes come back to where it wrote them, as today.
                    val atSongCapo = movedDown(written, songCapo)
                    val override = capo - songCapo
                    when {
                        override == 0 -> atSongCapo
                        // A capo taken off or lowered plays the chords in other shapes, which are the player's.
                        override < 0 -> null
                        // A capo put on or raised takes the shape down the neck by its frets; one with open strings cannot
                        // go down, and the octave up the move would take instead is the same chord twelve frets away.
                        else -> movedDown(atSongCapo, override)
                            .takeIf { moved -> moved.movedBy == atSongCapo.movedBy - override.mod(12) }
                    }
                }
            }
        }
        ?.takeIf { it.movedBy == 0 || (it.voicing as? ChordVoicing.Fretted)?.frets?.let(ChordVoicings::isHoldable) != false }
```

(`movedDown(written, songCapo)` with `override == 0` is today's `transposed(it, -capo)`, line for line, so with no
override nothing changes whatever the transposition: a reader's transposition still moves a definition "in whichever
octave a hand can hold", and the round trip stays. `transposed` returns the definition unchanged where no move fits the
neck, so its `movedBy` is unchanged and the check drops it too. A capo difference of 12 (no file capo, capo 12) is no
move (`semitones.mod(12) == 0`), and the `override.mod(12)` check keeps the shape as written, an octave up above the
capo as it was before this change.)

A reader's transposition and an override together: the song arrives already moved by the sounding transposition
(reader's + file's capo, wrapped), its definitions with it; the first step undoes the file's capo as today, the second
judges only the override. File `{capo: 2}`, override 0, transposition -2: the sounding move is 0, so the definition is
the file's own `[3,2,0,0,0,3]`, and it still gives way (the override is lower): one rule by the capo's direction, not
by where the frets happen to land, and the shape `G` above no capo is the player's open G anyway. File `{capo: 2}`,
override 4: the G comes back to `[3,2,0,0,0,3]` and cannot go down 2 more, so the player's F is drawn.

Such a chord gets no `undrawnDefinition`: that one is offered as "the song's shape in standard tuning", which this is
not (`written` in the entries is already null).

**Ruled by the user on 2026-10-26, against the recommendation:** a `{capo: 2}` file with its own G-shape `{define}`
opened with a capo override lower than the file's (a setlist capo of 0) draws open shapes (the player's pick, else the
app's table shape for `shapeChord`), not the file's G moved up the neck (`[5,4,2,2,2,5]`, a barred A).

Update the KDoc of `songChordsOf` (its last paragraph: "a definition is moved down the neck by it as a transposition
moves one…" → "a definition is moved down the neck by it as a transposition moves one, so that a file's own shapes come
back to where it wrote them under the capo it names; under any other capo it is kept only where the difference moves it
down by exactly that many frets, so a capo lower than the file's, or an open shape that cannot go down, gives way to
the player's shape, and the app's and the player's shapes are those of the chord moved down by it"), and the sentence
in `ui/chords/CLAUDE.md` "A `{define}` is moved down by the capo as a transposition moves one, so a file's shape comes
back to where it was written under its own capo." → append, as one clause, "; under another capo it is kept only where
that capo moves it down exactly, else (a lower capo, an open shape) it gives way to the player's shape." (That file is
already past its word budget; add only this clause.)

## Tests

`SongChordsTest` (`./gradlew :presentation:desktopTest`), a new case "a song's shape under a capo other than its own is
kept only where that capo moves it down the neck":

- `{define: G base-fret 1 frets 3 2 0 0 0 3}\n[G]la` on guitar with `capo = 2` has `definition == null`,
  `undrawnDefinition == null`, and `defaultShape == ChordVoicings.default(F, GUITAR)`;
- the reverse: `{capo: 2}\n{define: G base-fret 1 frets 3 2 0 0 0 3}\n[G]la`, moved by the sounding transposition
  (`ChordProTransposer.transpose(…, 2)`, as the round-trip case does) and read with `capo = 0`, has
  `definition == null`, `undrawnDefinition == null`, and `defaultShape == ChordVoicings.default(A, GUITAR)`;
- a barre definition (`{define: A base-fret 1 frets 5 7 7 6 5 5}\n[A]la`, written as frets from the nut) with
  `capo = 2` is kept, moved down by two (`[3,5,5,4,3,3]`, movedBy -2);
- `{define-ukulele: C frets 0 0 0 3}\n[C]la` on the ukulele with `capo = 12` keeps its definition (`[0,0,0,3]`,
  movedBy 0).

The existing round-trip case ("a file's own shape comes back to where it was written under the capo it names") keeps
passing unchanged.
