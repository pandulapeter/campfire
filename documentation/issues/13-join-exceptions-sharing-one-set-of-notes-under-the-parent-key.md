<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Exceptions sharing one set of notes are one moment, named by the parent key

**Kind:** design  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleSuggestions.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleSuggestionsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`

Written at HEAD `0a0babf27`. Lane S; run after plans 06 and 12. Independent of lane G's plan 01: it shows its value on
Blue Bossa only once that plan lands, but a chart hits it today (below), so it needs no "drop if".

## Problem

`SongScaleSuggestions.chordMomentsOf` joins the chords a player changes scale for into one moment only where they are
answered with the same scale on the same root:

```kotlin
    private fun chordMomentsOf(groups: List<PlayedGroup>, totals: Totals): List<ChordMoment> = groups.flatMap(::chordMomentsOf)
        .distinctBy { it.exception.chordName }
        .groupBy { ChordProChords.pitchClass(it.exception.rootName) to it.exception.shownScale.id }
        .values
        .map { same ->
            val first = same.first()
            val exception = first.exception.copy(otherChordNames = same.drop(1).map { it.exception.chordName })
```

Two exceptions whose scales are the same notes on different roots stay two rows that say the same thing, and eat the
three-moment budget. Today, `{key: C}` `C F#m7b5 D7 G7 C F#m7b5 D7 G7` (a ii–V of the V):

```
## 13 Two exceptions of one set (C: F#m7b5 and D7)
  [HOME] [Verse] C: major, major_pentatonic, harmonic_major, major_hexatonic (+2)
      over F#m7b5 → F# locrian, locrian_natural_2, …
      over D7 → D mixolydian, lydian_dominant, …
```

F♯ Locrian and D Mixolydian are both G major. The same in F with `F Bm7b5 G7 C7`: B Locrian and G Mixolydian, both C
major. Once lane G's plan 01 lands, Blue Bossa (`{key: Cm}`
`Cm7 Cm7 Fm7 Fm7 Dm7b5 G7 Cm7 Cm7 Ebm7 Ab7 Dbmaj7 Dbmaj7 Dm7b5 G7 Cm7 G7`) reads C natural minor with three exception
rows, "over E♭m7 → E♭ Dorian", "over A♭7 → A♭ Mixolydian", "over D♭maj7 → D♭ major": the ii–V–I in D♭ said three times,
the whole budget gone on one key.

## Fix

In `chordMomentsOf(groups, totals)`:

1. **Join by the pitch-class set of the shown scale on its root**, not by root and id. Each `ChordMoment` already
   carries `notes` (the chord's); add the scale's set as the key:

   ```kotlin
   .groupBy { moment -> Scales.pitchClasses(moment.exception.shownScale, ChordProChords.pitchClass(moment.exception.rootName) ?: 0) }
   ```

   (`ChordProChords.pitchClass` returns null only for a name that is no note; every `rootName` here is one.)

2. **Name the joined moment.** Where every joined exception shares the root (a V and its V7 on B harmonic minor; Autumn
   Leaves' `Fm7` and `Bb7` on G Phrygian, the minor home's own scale), nothing changes: the first's `rootName` and
   `scales`. Where the roots differ:
   - an exception on the group's own root (`first.group.rootPitchClass`) names the moment, since the key's own scale
     (a major home's natural minor over its borrowed chords, a minor home's Phrygian) is what a player calls it: take
     that exception's `rootName` and `scales`;
   - else the parent major key: for the first exception whose shown scale is a mode of the major scale
     (`shownScale.modeOf?.first == "major"`, Dorian, Phrygian, Lydian, Mixolydian, natural minor, Locrian),
     `rootName = ScaleSpelling.parentRootName(shownScale, exception.rootName)` and
     `scales = listOf(Scales.fromId("major")!!)`; a scale that already is the major scale is its own parent;
   - else the first exception's (the most played, as `ScaleExceptions.of` orders them).

   List the chords in play order: `val played = same.sortedBy { it.first }` (`ChordMoment.first` is the index of the
   chord's first play in its group; today's `same` is in `ScaleExceptions.of`'s order, the most played first, which the
   review's "over E♭m7, A♭7 & D♭maj7" asks to replace), and build the moment from `played.first()` as today, with
   `exception = ScaleException(chordName = played.first().exception.chordName, rootName = …, scales = …,
   otherChordNames = played.drop(1).map { it.exception.chordName })` and `first = played.first().first`. The "first
   exception" of the naming rules above is `played.first()` too. `ScaleSpelling` is in the package; its
   `parentRootName(scale, rootName)` spells the parent by letter (`Eb` for B♭ Mixolydian, `G` for F♯ Locrian, `Db` for
   E♭ Dorian).

3. The rest of the moment (`weight = same.maxOf`, `count = same.sumOf`, `plays`, `notes = same.flatMapTo`) stays as it
   is; `chosenOf`'s `isPlayable` reads `exception.chordNames` and `notes`, which are unchanged in meaning.

Expected after the fix:

```
## 13 Two exceptions of one set (C: F#m7b5 and D7)
      over F#m7b5 & D7 → G major
## 13 Two exceptions of one set in F (Bm7b5 and G7)
      over Bm7b5 & G7 → C major
## 13 Hotel California F# and F#7 (Bm)
      over F#7 & F# → B harmonic_minor, melodic_minor      (unchanged)
## 13 Autumn Leaves (Gm jazz)
      over Fm7 & Bb7 → G phrygian                           (unchanged)
```

and Blue Bossa, once plan 01 lands, "over E♭m7, A♭7, and D♭maj7 → D♭ major" as one of its moments (the viewer's
`song_details_scales_over_several` already says a list of three). Where the joined exceptions came from different
groups (`chordMomentsOf(group)` is flattened over every group), `rowsOf` places the moment by `chord.group`, the
first's; unchanged.

Update the KDoc of `chordMomentsOf(groups, totals)` ("those a viewer would offer one scale on one root over joined" →
"those whose scales are one set of notes joined into one moment, named by the key's own scale where one of them is on
the group's root, else by the parent major key, else by the first") and the class KDoc's sentence "together with the
chords a viewer would offer the same scale on the same root over (a V and its V7)" → "together with the chords whose
scales are the same notes (a V and its V7; a ii–V of another key, named by that key)".

## Tests

In `SongScaleSuggestionsTest` (`suggestions(vararg sections, key)`; `moments` is the triple `chordName, rootName,
scales.first().id`):

- `` `chords whose scales are one set of notes are one moment, named by the parent key` ``:
  `suggestions("Verse" to "C F#m7b5 D7 G7 C F#m7b5 D7 G7", key = "C").single()` → `moments ==
  listOf(Triple("F#m7b5", "G", "major"))` and `exceptions.single().otherChordNames == listOf("D7")`; and
  `suggestions("Verse" to "F Bm7b5 G7 C7 F Bm7b5 G7 C7", key = "F").single()` → `Triple("Bm7b5", "C", "major")` with
  `otherChordNames == listOf("G7")`.
- `` `chords the key's own scale answers keep its name when joined` ``: Autumn Leaves,
  `"A" to "Cm7 F7 Bbmaj7 Ebmaj7 Am7b5 D7 Gm Gm"`,
  `"B" to "Am7b5 D7 Gm Gm Cm7 F7 Bbmaj7 Bbmaj7 Am7b5 D7 Gm7 C7 Fm7 Bb7 Am7b5 D7 Gm"`, `key = "Gm"` → the moment whose
  `chordName == "Fm7"` has `rootName == "G"`, `scales.first().id == "phrygian"`, `otherChordNames == listOf("Bb7")`.
- The existing `chords one scale on one root serves are one moment, named by each` (F♯7 and F♯ on B harmonic minor)
  stays and must pass.

`CLAUDE.md` (`chordpro/scales`), the `SongScaleSuggestions` bullet: change "chords offered one scale on one root are one
(a V and its V7)" to "chords whose scales are one set of notes are one (a V and its V7; a ii–V of another key, named by
that key's major, or by the home's own scale where one of them is on the home root)".
