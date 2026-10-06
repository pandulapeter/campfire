# Spell the Chord shapes sheet's notes by their degree from the root's letter, the sounding root's on a capoed keyboard

**Challenged:** amended — `spelledNoteNames` reads its name in the standard notation only (the German reading would turn the standard `B` into B flat); added the `B` in `GERMAN` test.
**Kind:** bug (music theory)  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProChords.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProChordsTest.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/SongChords.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ChordShapesSheet.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/chords/SongChordsTest.kt`, `chordpro/CLAUDE.md`

## Problem

Each cell of the Chord shapes sheet names the chord's notes under its diagram:

```kotlin
text = ChordProChords.noteNames(chord.chord, notation, preferFlats = chord.isSpelledWithFlats).joinToString(" "),
```

with, in `songChordsOf`, `isSpelledWithFlats = name.getOrNull(1) == 'b'`. `noteNames` spells every pitch class from one
table (`sharpNames` or `flatNames`), so any chord whose root is not itself a flat gets sharps for its flat degrees.
Probe at dac1d9d59:

| chord | sheet shows | a chart writes |
|---|---|---|
| `Cm` | C D# G | C Eb G |
| `C7` | C E G A# | C E G Bb |
| `Gm` | G A# D | G Bb D |
| `F7` | F A C D# | F A C Eb |
| `Fm` | F G# C | F Ab C |

(`Bb`, `Eb7`, `F#m` come out right.) On the keyboard with a capo the flag is the *page* chord's while the notes are
the *sounding* chord's: page `A` with capo 1 sounds `Bb`, flagged sharp, so the sheet shows "A# D F".

## Fix

1. In `:chordpro`, add a spelling that reads the letter a name gives its root and names every other note by its
   degree from it:
   ```kotlin
   /**
    * The notes of the chord [name] stands for, as [noteNames] lists them, each spelled from the letter of the root as
    * [name] writes it by the degree it is: `Cm` is `C Eb G` and `F7` is `F A C Eb`, as a chart spells them, where
    * [noteNames] can only choose sharps or flats for the whole chord. A note that would need a double sharp or flat,
    * or would be one of `Cb`, `Fb`, `E#`, `B#`, is named as [noteNames] would with the root's accidental (`Gbm` is
    * `Gb A Db`), and the bass of a slash chord as the name writes it. Null where [name] is no chord.
    */
   fun spelledNoteNames(name: String, notation: ChordNotation = ChordNotation.STANDARD): List<String>?
   ```
   [name] is **always in the standard notation** (`SongChord.spelling` is), and [notation] only says how the notes
   come out: read the name with `ChordProNotation.read(name, isGerman = false)`, never with `parse(name, notation)`,
   which in German would take the standard `B` (B natural) for B flat. Say so in the KDoc ("[name] in the standard
   notation, its notes named in [notation]").
   Implementation sketch: let `Reader` also keep the root and the bass as handed to `root(note)` / `bass(note)` (they
   arrive in the standard notation, after `ChordProNotation.read`; uppercase the bass's letter). For each interval of
   the chord take the letter step: `0→0, 1→1, 2→1, 3→(1 if 4 is also an interval, the sharp ninth; else 2), 4→2, 5→3,
   6→(3 if 7 is also an interval, the sharp eleventh; else 4), 7→4, 8→(5 if 7 is also an interval, the flat
   thirteenth; else 4, the sharp fifth), 9→5 (a diminished seventh's `bb7` is written as the sixth, as charts do),
   10→6, 11→6`. The letter is `"CDEFGAB"[(rootLetter + step) % 7]`; the accidental is the pitch class minus that
   letter's natural pitch class, mod 12: `0 → ""`, `1 → "#"`, `11 → "b"`, anything else (and the four white-key
   enharmonics) → the fallback above (`flatNames` where the root is spelled with `b`, otherwise `sharpNames`). Finish
   each with `ChordProNotation.shownName(…, notation)`, as `noteNames` does. Keep `noteNames` (it names a single key on
   the keyboard diagram, `SongChordsSection.kt`, and is tested).
2. In `SongChords.kt`, replace `isSpelledWithFlats: Boolean` on `SongChord` by the standard name its notes are spelled
   from:
   ```kotlin
   /** @property spelling The chord the notes are spelled from, in the standard notation: the page's, or on a capoed keyboard the one that sounds. */
   val spelling: String? = null,
   ```
   and in `songChordsOf` set `spelling = if (soundingShift == 0) name else ChordProChords.transposedName(name, soundingShift, preferFlats = preferFlats)`
   (`name` is the key of `shownNames`, already standard and letters even under a numbering; the second is the same
   standard sounding name `soundingName` is made from, before `shownName`).
3. In `ChordShapesSheet.kt`:
   ```kotlin
   text = (chord.spelling?.let { ChordProChords.spelledNoteNames(it, notation) } ?: ChordProChords.noteNames(chord.chord, notation)).joinToString(" "),
   ```
4. `chordpro/CLAUDE.md`, the `ChordProChords` paragraph: add "`spelledNoteNames` spells a chord's notes by degree from
   the root's letter, which the Chord shapes sheet shows under each diagram". Update the `SongChord` KDoc in
   `SongChords.kt` (drop the `isSpelledWithFlats` property line).

Decision (see the report): whether the four white-key enharmonics fall back (`Db7` → `Db F Ab B`, recommended: a
player reads keys and strings, not theory) or stay theory-correct (`Cb`).

## Tests

`ChordProChordsTest`, new `` `notes are spelled by their degree from the root` ``: `Cm` → `C Eb G`, `C7` → `C E G Bb`,
`F7` → `F A C Eb`, `Fm` → `F Ab C`, `D` → `D F# A`, `F#m` → `F# A C#`, `Bb` → `Bb D F`, `Caug` → `C E G#`, `Cdim7` →
`C Eb Gb A`, `C7(#9)` → `C D# E G Bb`, `Cm(b6)` → `C Eb G Ab`, `D/F#` → `D F# A` (the bass first only where it is not a
chord note, as `noteNames`: `C/Bb` → `Bb C E G`), `Gbm` → `Gb A Db`, `Bb` in `GERMAN` → `B D F`, `B` in `GERMAN` → `H D# F#` (the standard B natural, not B flat), `Am` in `LATIN` →
`La Do Mi`, and `spelledNoteNames("N.C.")` null.

`SongChordsTest`: replace `assertEquals(listOf(false, true), latin.map { it.isSpelledWithFlats })` by
`assertEquals(listOf("Am", "Bb7"), latin.map { it.spelling })`; in
`` `the keyboard draws the chord a capo makes sound and names it` `` add
`names("{key: D}\n[A]la", instrument = ChordInstrument.KEYBOARD, capo = 1).single().spelling` is `"Bb"` (key D with
a capo of 1 sounds Eb, a flat key; check `ChordProTransposer.prefersFlats` agrees and adjust the key if not), and
`ChordProChords.spelledNoteNames("Bb")` is `Bb D F`. Run `./gradlew :chordpro:desktopTest :presentation:desktopTest`.

## Manual check

Open a song with `[Cm]la [F7]la`, open the Chord shapes sheet from its Chords section: the note rows read
"C Eb G" and "F A C Eb". Switch the instrument to the keyboard and set capo 1 on a song with `[A]` in a flat-leaning
key: the row reads "Bb D F".
