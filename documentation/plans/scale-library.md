<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Scale library — implementation plan

Written 2026-10-19 against `7f44fd547`, finalized the same day. Two things, in this order of importance:

1. **The scale sheet**: one bottom sheet that shows one scale in one key on the player's instrument, opened from
   anywhere a scale is worth suggesting. In this version from the Scales tab's cells, from the Chords tab's chord
   sheet, and from a song's chord sheet ("Scales for this chord", §6.5).
2. **The Scales tab**: a new top level screen that browses every scale the app knows, the way the Chords tab
   browses every chord: instrument and tuning chips, a key row, a family row, a grid of cells, each opening the
   sheet.

The scales themselves are a pure, tested catalogue in `:chordpro`, next to the chord tables. No network, no file, no
new module.

## 0. Feasibility, in short

Everything the feature needs already exists in some form:

- **Instrument and tuning**: `UserPreferences.instrument` + `tuningOf` give the open strings of *every* stringed
  instrument as MIDI notes (`InstrumentTuning.strings`), not only the five with chord shapes. A scale map needs
  nothing but the open strings, so **Scales works on the bass, the five-string bass, the seven- and eight-string
  guitars, and the violin too**, where Chords shows a "no diagrams" line. Only Other has no strings.
- **Key row, notation, accidentals**: `chordLibraryKeys`, `ChordProNotation.shownName`, and the `chordSpelling`
  preference name the twelve roots exactly as the Chords tab does (German `H`, Latin `Do Re Mi`, a numbering falls
  back to letters).
- **Drawing**: `ChordDiagram.kt` already draws a keyboard from a `ChordDiagramGeometry.Keyboard` (two to four
  octaves, root in the second accent). The keyboard view of a scale is that geometry with the scale's pitch classes
  filled across two octaves. The fretted view is new (a horizontal neck, not a vertical chord box), about the size
  of `drawFretted`.
- **Chrome**: a seventh top level destination costs one entry in six `when`s plus the tests that pin them
  (`CampfireDestination`, `TopLevelLabel`, `DestinationIcon`, `BrowserRoutes`, `FeatureSwitches`,
  `CampfireScreens`). The Tools item already groups the tools on a bar; four tools is the one thing to check by eye
  (§7.3).
- **Chords in a scale**: `ChordProChords.parse` plus `ChordVoicings.default` turn a chord name into a cell with the
  player's own shape, so the sheet can list the triads and sevenths of a seven-note scale as tappable
  `ChordLibraryCell`s, in the exact form the Chords tab draws them. The other direction, the scales that hold a
  chord, is a containment check over 62 entries, and `ChordShapeGrid` already takes a `footer` to put it in.

Nothing needs the main thread for long: the catalogue is arithmetic, no shape search runs (the chord row uses the
tables and marks the rest `isShapePending`-style, see §6.4). The first frame is the page.

Rough size: `:chordpro` ~600 lines plus tests; `:presentation` ~1,400 lines plus tests; ~110 strings in two
languages; about four working days in the order of §9.

## 1. Decisions (settled here; reopen only with the user)

1. **A top level screen, the fourth tool**: `CampfireDestination.Scales`, `/scales` on the web, in
   `TopLevel.tools` after Chords. Not a second tab inside Chords: the Chords tab's whole state (key, search, held
   results) is about chords, and the Tools item is built for exactly this.
2. **Its own Features switch**, `areScalesEnabled` (default on), independent of Chords and Chord diagrams: a
   violinist with chords off still reads scales; a singer can hide the tab. Row in Settings → Features after Chord
   diagrams, and in the onboarding's features page (`OnboardingFeatures`). Only the **Chords in this scale** row
   of the sheet follows the Chord diagrams switch (§6.4), since that row *is* chord diagrams.
3. **The catalogue lives in `:chordpro`** (`chordpro/scales/`), under explicit API, pure and tested, like
   `voicings/`. It reuses `Chord`, `ChordProChords`, and `ChordProNotation` and needs nothing else. No new module.
4. **Scales are 12-tone equal temperament only.** Maqamat and ragas with quarter tones are not drawable on a fret
   or a key and are not in the catalogue; their 12-TET relatives are (Hijaz = Phrygian dominant, Hijaz Kar = double
   harmonic).
5. **Browse by family, not by a flat list**: a family chip row (§5.2) over a grid of cells, each a scale of the
   family in the picked key with a thumbnail of it on the instrument. A tap opens the sheet. This is the Chords
   tab's shape (controls, heading, cells, sheet), which is what the user asked for.
6. **No circle of fifths in this version.** See §8 for why, and what it would take later.
7. **Note spelling follows the chords' rule** (`ChordProChords.spelledNoteNames`): one letter per degree, no double
   accidentals, and no `Cb`, `Fb`, `E#`, `B#`; a note that would need one falls back to the root's accidental. The
   same note is then spelled the same in a chord sheet and in a scale. If the user wants theory-correct spelling in
   the Scales tab (C♯ major with its E♯ and B♯), it is one flag in `ScaleSpelling` and a decision to document.
8. **The key, the family, the labels toggle, and the scroll position are session state** in the view model
   (`ScaleLibraryState`), like `ChordLibraryState`; the instrument and tuning are the app-wide preference, written
   at once by the shared chips. Nothing new is saved or synced.
9. **Other shows the keyboard.** A keyboard is the universal picture of a scale and needs no instrument; a line
   under the chips says the diagrams are a keyboard's until an instrument is picked. (Chords says "Choose an
   instrument" there because a chord shape *is* an instrument's; a scale is not.)
10. **Offered in read only mode** (Performance mode): the tab writes no file. Instrument and tuning chips stay, as
    they do on the Chords tab.
11. **The sheet is the one way a scale is shown, and it depends on no screen.** `DialogType.Scale` carries a scale
    id and a root name, nothing else; the instrument, tuning, notation, and labels come from the preferences and
    the session state when it composes. So it opens over the Scales tab, over the Chords tab, and over a song, and
    a later entry point (a song's key, a search) is one `showDialog` call.
12. **"Scales for this chord"** is a chip row at the bottom of both chord sheets (`ChordReferenceSheet` on the
    Chords tab and `ChordShapeSheet` on a song), root-anchored (§2.4), each chip opening the scale sheet in place.
    It is shown only while the Scales switch is on; a chord with no match on its root shows no row.

## 2. The catalogue (`:chordpro`, `chordpro/scales/`)

The sheet of §6 and the tab of §5 both read only this package and the geometry of §3.

### 2.1 Model

```kotlin
public enum class ScaleFamily(public val id: String) {
    MAJOR_MODES, HARMONIC_MINOR_MODES, MELODIC_MINOR_MODES, MINOR_AND_HARMONIC, PENTATONIC,
    BLUES_AND_HEXATONIC, SYMMETRIC, BEBOP, WORLD,
}

/**
 * @property id Stable, lowercase, what a string resource and any future address is keyed by.
 * @property intervals Semitones above the root, sorted, starting at 0, each once.
 * @property degrees One label per interval ("1", "b3", "#4", "bb7"): what the formula shows and what spells each
 *   note's letter (§2.3). A test holds every label to its interval.
 * @property modeOf The scale this is a rotation of and the degree it starts on (Dorian = Major, 2), or null.
 * @property aliases Other names it is found by (search, "Also called").
 */
public data class Scale(
    val id: String,
    val family: ScaleFamily,
    val intervals: List<Int>,
    val degrees: List<String>,
    val modeOf: Pair<String, Int>? = null,
    val aliases: List<String> = emptyList(),
)

public object Scales {
    public val all: List<Scale>            // catalogue order within families, families in ScaleFamily order
    public val common: List<Scale>         // the "Common" chip, §2.2, a curated shortlist of entries of `all`
    public fun of(family: ScaleFamily): List<Scale>
    public fun fromId(id: String): Scale?
    public fun pitchClasses(scale: Scale, root: Int): Set<Int>
    public fun steps(scale: Scale): List<Int>   // 2 2 1 2 2 2 1, the last step closing the octave
    public fun parentRoot(scale: Scale, root: Int): Int?   // the root of `modeOf`'s scale: D Dorian → C
}
```

`Scale` goes into `gradle/compose-stability.conf`. Every list is built once; the model is immutable.

### 2.2 Entries

62 scales. Intervals are semitones from the root; degrees are the formula. Aliases are search terms and the "Also
called" line; names are string resources (`scale_<id>`), the English and the proposed Hungarian given here (the
Hungarian follows the usual Hungarian theory terms; a native read is wanted before shipping).

**Common** (the first chip, in this order; each is an entry of its family below): major, natural minor, harmonic
minor, melodic minor, major pentatonic, minor pentatonic, blues, major blues, Dorian, Mixolydian, chromatic.

| id | Name (EN / HU) | Intervals | Degrees | Mode of | Aliases |
|---|---|---|---|---|---|
| **MAJOR_MODES** | | | | | |
| `major` | Major / Dúr | 0 2 4 5 7 9 11 | 1 2 3 4 5 6 7 | | Ionian |
| `dorian` | Dorian / Dór | 0 2 3 5 7 9 10 | 1 2 b3 4 5 6 b7 | major, 2 | |
| `phrygian` | Phrygian / Fríg | 0 1 3 5 7 8 10 | 1 b2 b3 4 5 b6 b7 | major, 3 | |
| `lydian` | Lydian / Líd | 0 2 4 6 7 9 11 | 1 2 3 #4 5 6 7 | major, 4 | |
| `mixolydian` | Mixolydian / Mixolíd | 0 2 4 5 7 9 10 | 1 2 3 4 5 6 b7 | major, 5 | Dominant scale |
| `natural_minor` | Natural minor / Természetes moll | 0 2 3 5 7 8 10 | 1 2 b3 4 5 b6 b7 | major, 6 | Aeolian |
| `locrian` | Locrian / Lokriszi | 0 1 3 5 6 8 10 | 1 b2 b3 4 b5 b6 b7 | major, 7 | |
| **HARMONIC_MINOR_MODES** | | | | | |
| `harmonic_minor` | Harmonic minor / Összhangzatos moll | 0 2 3 5 7 8 11 | 1 2 b3 4 5 b6 7 | | |
| `locrian_natural_6` | Locrian ♮6 / Lokriszi ♮6 | 0 1 3 5 6 9 10 | 1 b2 b3 4 b5 6 b7 | harmonic_minor, 2 | |
| `ionian_sharp_5` | Ionian ♯5 / Ión ♯5 | 0 2 4 5 8 9 11 | 1 2 3 4 #5 6 7 | harmonic_minor, 3 | Augmented major |
| `dorian_sharp_4` | Dorian ♯4 / Dór ♯4 | 0 2 3 6 7 9 10 | 1 2 b3 #4 5 6 b7 | harmonic_minor, 4 | Romanian minor, Ukrainian Dorian |
| `phrygian_dominant` | Phrygian dominant / Fríg domináns | 0 1 4 5 7 8 10 | 1 b2 3 4 5 b6 b7 | harmonic_minor, 5 | Spanish Phrygian, Hijaz, Freygish, Ahava Rabbah |
| `lydian_sharp_2` | Lydian ♯2 / Líd ♯2 | 0 3 4 6 7 9 11 | 1 #2 3 #4 5 6 7 | harmonic_minor, 6 | |
| `ultralocrian` | Ultralocrian / Ultralokriszi | 0 1 3 4 6 8 9 | 1 b2 b3 b4 b5 b6 bb7 | harmonic_minor, 7 | Super Locrian ♭♭7 |
| **MELODIC_MINOR_MODES** | | | | | |
| `melodic_minor` | Melodic minor / Dallamos moll | 0 2 3 5 7 9 11 | 1 2 b3 4 5 6 7 | | Jazz minor |
| `dorian_flat_2` | Dorian ♭2 / Dór ♭2 | 0 1 3 5 7 9 10 | 1 b2 b3 4 5 6 b7 | melodic_minor, 2 | Phrygian ♮6 |
| `lydian_augmented` | Lydian augmented / Bővített líd | 0 2 4 6 8 9 11 | 1 2 3 #4 #5 6 7 | melodic_minor, 3 | |
| `lydian_dominant` | Lydian dominant / Líd domináns | 0 2 4 6 7 9 10 | 1 2 3 #4 5 6 b7 | melodic_minor, 4 | Acoustic, Overtone |
| `mixolydian_flat_6` | Mixolydian ♭6 / Mixolíd ♭6 | 0 2 4 5 7 8 10 | 1 2 3 4 5 b6 b7 | melodic_minor, 5 | Aeolian dominant, Hindu |
| `locrian_natural_2` | Locrian ♮2 / Lokriszi ♮2 | 0 2 3 5 6 8 10 | 1 2 b3 4 b5 b6 b7 | melodic_minor, 6 | Half-diminished |
| `altered` | Altered / Alterált | 0 1 3 4 6 8 10 | 1 b2 b3 b4 b5 b6 b7 | melodic_minor, 7 | Super Locrian, Diminished whole tone |
| **MINOR_AND_HARMONIC** | | | | | |
| `harmonic_major` | Harmonic major / Összhangzatos dúr | 0 2 4 5 7 8 11 | 1 2 3 4 5 b6 7 | | |
| `double_harmonic` | Double harmonic / Kettős összhangzatos | 0 1 4 5 7 8 11 | 1 b2 3 4 5 b6 7 | | Byzantine, Hijaz Kar, Arabic, Gypsy major |
| `hungarian_minor` | Hungarian minor / Magyar moll | 0 2 3 6 7 8 11 | 1 2 b3 #4 5 b6 7 | double_harmonic, 4 | Gypsy minor, Double harmonic minor |
| `hungarian_major` | Hungarian major / Magyar dúr | 0 3 4 6 7 9 10 | 1 #2 3 #4 5 6 b7 | | |
| `neapolitan_major` | Neapolitan major / Nápolyi dúr | 0 1 3 5 7 9 11 | 1 b2 b3 4 5 6 7 | | |
| `neapolitan_minor` | Neapolitan minor / Nápolyi moll | 0 1 3 5 7 8 11 | 1 b2 b3 4 5 b6 7 | | |
| **PENTATONIC** | | | | | |
| `major_pentatonic` | Major pentatonic / Dúr pentaton | 0 2 4 7 9 | 1 2 3 5 6 | | |
| `minor_pentatonic` | Minor pentatonic / Moll pentaton | 0 3 5 7 10 | 1 b3 4 5 b7 | major_pentatonic, 5 | |
| `egyptian` | Egyptian / Egyiptomi | 0 2 5 7 10 | 1 2 4 5 b7 | major_pentatonic, 2 | Suspended pentatonic |
| `man_gong` | Man Gong / Man Gong | 0 3 5 8 10 | 1 b3 4 b6 b7 | major_pentatonic, 3 | Blues minor pentatonic |
| `ritusen` | Ritusen / Ritusen | 0 2 5 7 9 | 1 2 4 5 6 | major_pentatonic, 4 | Yo, Blues major pentatonic |
| `hirajoshi` | Hirajoshi / Hirajoshi | 0 2 3 7 8 | 1 2 b3 5 b6 | | |
| `in` | In / In | 0 1 5 7 8 | 1 b2 4 5 b6 | | Sakura |
| `insen` | Insen / Insen | 0 1 5 7 10 | 1 b2 4 5 b7 | | |
| `iwato` | Iwato / Iwato | 0 1 5 6 10 | 1 b2 4 b5 b7 | | |
| `kumoi` | Kumoi / Kumoi | 0 2 3 7 9 | 1 2 b3 5 6 | | |
| `chinese` | Chinese / Kínai | 0 4 6 7 11 | 1 3 #4 5 7 | | |
| **BLUES_AND_HEXATONIC** | | | | | |
| `blues` | Blues / Blues | 0 3 5 6 7 10 | 1 b3 4 b5 5 b7 | | Minor blues |
| `major_blues` | Major blues / Dúr blues | 0 2 3 4 7 9 | 1 2 b3 3 5 6 | | |
| `major_hexatonic` | Major hexatonic / Dúr hexaton | 0 2 4 5 7 9 | 1 2 3 4 5 6 | | |
| `minor_hexatonic` | Minor hexatonic / Moll hexaton | 0 2 3 5 7 10 | 1 2 b3 4 5 b7 | | |
| `prometheus` | Prometheus / Prométheusz | 0 2 4 6 9 10 | 1 2 3 #4 6 b7 | | Mystic |
| `tritone` | Tritone / Tritonusz | 0 1 4 6 7 10 | 1 b2 3 b5 5 b7 | | Petrushka |
| **SYMMETRIC** | | | | | |
| `whole_tone` | Whole tone / Egészhangú | 0 2 4 6 8 10 | 1 2 3 #4 #5 #6 | | |
| `augmented` | Augmented / Bővített | 0 3 4 7 8 11 | 1 #2 3 5 #5 7 | | Hexatonic |
| `diminished` | Diminished (whole–half) / Szűkített (egész–fél) | 0 2 3 5 6 8 9 11 | 1 2 b3 4 b5 b6 6 7 | | Octatonic |
| `dominant_diminished` | Dominant diminished (half–whole) / Szűkített (fél–egész) | 0 1 3 4 6 7 9 10 | 1 b2 b3 3 b5 5 6 b7 | diminished, 2 | |
| `chromatic` | Chromatic / Kromatikus | 0 1 2 3 4 5 6 7 8 9 10 11 | 1 b2 2 b3 3 4 b5 5 b6 6 b7 7 | | |
| **BEBOP** | | | | | |
| `bebop_dominant` | Bebop dominant / Bebop domináns | 0 2 4 5 7 9 10 11 | 1 2 3 4 5 6 b7 7 | | |
| `bebop_major` | Bebop major / Bebop dúr | 0 2 4 5 7 8 9 11 | 1 2 3 4 5 b6 6 7 | | |
| `bebop_dorian` | Bebop Dorian / Bebop dór | 0 2 3 4 5 7 9 10 | 1 2 b3 3 4 5 6 b7 | | |
| `bebop_melodic_minor` | Bebop melodic minor / Bebop dallamos moll | 0 2 3 5 7 8 9 11 | 1 2 b3 4 5 b6 6 7 | | |
| `bebop_harmonic_minor` | Bebop harmonic minor / Bebop összhangzatos moll | 0 2 3 5 7 8 10 11 | 1 2 b3 4 5 b6 b7 7 | | |
| **WORLD** | | | | | |
| `persian` | Persian / Perzsa | 0 1 4 5 6 8 11 | 1 b2 3 4 b5 b6 7 | | |
| `enigmatic` | Enigmatic / Enigmatikus | 0 1 4 6 8 10 11 | 1 b2 3 #4 #5 #6 7 | | |
| `oriental` | Oriental / Keleti | 0 1 4 5 6 9 10 | 1 b2 3 4 b5 6 b7 | | |
| `major_locrian` | Major Locrian / Dúr lokriszi | 0 2 4 5 6 8 10 | 1 2 3 4 b5 b6 b7 | | |
| `lydian_minor` | Lydian minor / Líd moll | 0 2 4 6 7 8 10 | 1 2 3 #4 5 b6 b7 | | |
| `leading_whole_tone` | Leading whole tone / Vezetőhangos egészhangú | 0 2 4 6 8 10 11 | 1 2 3 #4 #5 #6 7 | | |
| `flamenco` | Flamenco / Flamenco | 0 1 3 4 5 7 8 10 | 1 b2 b3 3 4 5 b6 b7 | | |
| `spanish_eight_tone` | Spanish eight-tone / Spanyol nyolcfokú | 0 1 3 4 5 6 8 10 | 1 b2 b3 3 4 b5 b6 b7 | | |

Tests that pin the catalogue (`ScalesTest`):

- every `intervals` list is sorted, starts at 0, is within 0..11, and has no repeats; `degrees.size == intervals.size`;
- **every degree label names its interval**: label → semitones (`1`→0, `b2`→1, `2`→2, `b3`→3, `#2`→3, `3`→4, `b4`→4,
  `4`→5, `#4`/`b5`→6, `5`→7, `#5`/`b6`→8, `6`→9, `bb7`→9, `b7`→10, `#6`→10, `7`→11) equals the interval;
- every `modeOf` is a real rotation: the parent's intervals rotated to start at degree *d* equal the mode's;
- no two scales share one interval set (aliases are the one way to a second name); `common ⊆ all`; ids unique.

### 2.3 Spelling (`ScaleSpelling`, tested)

`noteNames(scale, rootName, notation): List<String>`, `rootName` in the standard notation as the key row writes it
(`Eb`, `C#`). For each degree: the letter is the root's letter plus (degree number − 1); the accidental is the
pitch class minus the letter's natural pitch class, folded to −1..1. A note that needs ±2 or is one of `Cb`, `Fb`,
`E#`, `B#` is named from the sharp or flat names by the root's accidental (flats for a root with `b`, else sharps),
as `spelledNoteNames` does (decision 7). Then `ChordProNotation.shownName` for the notation. Tests: C major, A
natural minor, F major (B♭), E♭ Dorian, F♯ major (its E♯ reads `F`), B Lydian ♯2 (`C##` falls back), C blues
(`Gb G`), every scale in every key spells as many names as it has intervals.

### 2.4 Chords of a scale (`ScaleChords`, tested)

For a seven-note scale only: for each degree, the triad (degree, +2, +4 within the scale) and the seventh (+6),
as a `Chord(root, intervals)`; matched against a small table to a name suffix: `0 4 7` "", `0 3 7` "m",
`0 3 6` "dim", `0 4 8` "aug", `0 2 7` "sus2", `0 5 7` "sus4"; sevenths `maj7`, `7`, `m7`, `m7b5`, `dim7`,
`mmaj7`, `maj7#5`, `7#5`, `7b5`, `maj7b5`. A stack that matches nothing is skipped (several in the World family
do). The name is the spelled note (§2.3) plus the suffix, in the standard notation, so `ChordProChords.parse` reads
it and `ChordProNotation.shownName` shows it; the degree label is the Roman numeral from the quality (lowercase for
minor and diminished, `°` and `+` as `ChordNotation.ROMAN` writes them). Tests: C major gives
`C Dm Em F G Am Bdim` and `Cmaj7 Dm7 Em7 Fmaj7 G7 Am7 Bm7b5`; A harmonic minor's V is `E` / `E7` and its III is
`Caug`; every triad name parses back to the very pitch classes it was built from.

**Scales for a chord**: `scalesContaining(chord: Chord): List<Scale>`, the scales built on `chord.root` whose
pitch classes hold every one of `chord.intervals` (the bass of a slash chord is left out: it is a voicing, not a
tone of the chord), ordered `Scales.common` first and then catalogue order, so that C7 reads Mixolydian, Blues,
Lydian dominant, Phrygian dominant, Dominant diminished, Bebop dominant, and the rare ones last. Not root-free
containment: a C triad is in F major and A minor too, which is true and no help to a player asking what to play
over a C. Tests: C7 holds Mixolydian and not Major; Am7 holds Dorian and Natural minor and not Harmonic minor;
`D/F#` is D's list; C13 (seven tones) is Mixolydian and Bebop dominant; a chord with a `b9` and a `9` has an empty
list rather than an error; every scale returned holds every chord tone.

## 3. The diagram (`ui/scales/`)

### 3.1 Geometry (`ScaleDiagramGeometry.kt`, pure, tested)

```kotlin
internal sealed interface ScaleDiagramGeometry {
    /** A neck seen from the front, the highest string on top, the nut at the left, [fretCount] frets to the right. */
    data class Fretboard(
        val strings: Int,
        val fretCount: Int,              // 12: one octave shows every note; fixed, so every cell reads alike
        val isFretless: Boolean,         // the violin: positions drawn as faint dashed lines, no nut band
        val stringStarts: List<Int>,     // per row: the fret the string begins at (5 for the banjo's drone)
        val notes: List<Note>,           // every scale note on every string, open strings at fret 0
        val markers: List<Int>,          // 3 5 7 9 12: faint inlays under the strings, numbers along the bottom
    ) : ScaleDiagramGeometry {
        data class Note(val row: Int, val fret: Int, val degree: Int, val isRoot: Boolean)
    }
    /** [ChordDiagramGeometry.Keyboard] with the scale's pitch classes across two octaves, roots marked. */
    data class Keyboard(val keyboard: ChordDiagramGeometry.Keyboard) : ScaleDiagramGeometry
}

internal fun scaleDiagramGeometryOf(scale: Scale, root: Int, strings: List<Int>?, instrument: UserPreferences.Instrument): ScaleDiagramGeometry
```

- `strings` is `InstrumentTuning.strings` (null for Keyboard and Other, which draw the keyboard); rows are the list
  reversed, so a guitar has its high E on top and a ukulele its A on top and the re-entrant g at the bottom.
- The banjo's row for `InstrumentTuning.strings[0]` starts at fret 5 (`stringStarts`); the twelve-string is the
  guitar; the violin is `isFretless`.
- Tests: C major on a standard guitar puts a root at (row of the low E, fret 8), (A, 3), (D, 10), (G, 5), (B, 1),
  (high E, 8), and every note it places is in the scale; drop D moves the low string's notes by two; the banjo's
  drone row has nothing below fret 5; the keyboard geometry holds the pitch classes in both octaves and the roots
  at 0, 12 (and 24 is out of range, as `drawKeyboard` draws `0 until octaves * 12`).

### 3.2 Drawing (`ScaleDiagram.kt`)

`ScaleDiagram(modifier, geometry, labels: ScaleLabels, description)`: a `Spacer` with `drawBehind`, like
`ChordDiagram`; the keyboard case calls `drawChordDiagram` with the wrapped geometry (same colors, same theme
rule). The fretboard: a nut band at the left (thick line; the open-string notes stand left of it as rings/dots in a
column of their own, as a chord diagram's markers do), thin fret lines, strings as horizontal lines whose thickness
grows a little towards the lowest, inlay markers as faint discs on the background, fret numbers under the markers
in `onSurfaceVariant`. A note is a filled disc: the root in `LocalSecondAccentColor`, the rest in `onSurface`, with
its label (`ScaleLabels.DEGREES` → `1 b3 5`, `ScaleLabels.NOTES` → `C Eb G` in the notation, `NONE` for the
thumbnails) in the background color, measured once per size as `drawFinger` does. Lines never go under
`minimumStroke`. Width:height for six strings is about 12:4; the cell and the sheet both pass fixed dp sizes.

Accessibility: the diagram is one node whose description is the scale's name and its notes (`scaleCellDescription`),
never a node per dot.

## 4. Destination, chrome, routes, feature switch

Every place that names `CampfireDestination.Chords` names `Scales` too; each has a test to extend:

- `CampfireDestination.Scales : TopLevel` (`contentKey = "scales"`), in `TopLevel.entries` after Chords and in
  `tools` last; `isEnabled` → `features.areScalesEnabled`. `FeatureDestinationsTest`.
- `FeatureSwitches.areScalesEnabled` from `preferences?.areScalesEnabled != false`.
- `UserPreferences.areScalesEnabled: Boolean = true`; `UserPreferencesDocument` field and mapper both ways
  (`UserPreferencesMappersTest`); never exported or synced, like every Features switch;
  `PreferencesController.setScalesEnabled` and its `topLevelDestinations`.
- `BrowserRoutes`: `scales` both ways (`BrowserRoutesTest.statesWithPaths` gets the entry);
  `NavigationState.withoutDisabledFeatures` cuts a restored `/scales` with the switch off (comes free from
  `isEnabled`).
- `TopLevelLabel`: `Res.string.scales` ("Scales" / "Skálák"). `DestinationIcon`: a new `ic_scales.xml` (24dp, MPL
  header): ascending steps of three dots on a short neck, one icon for every instrument (unlike Chords, a scale's
  mark is not an instrument's). `Navigator.lastTool` and `toolsItemDestination` need no change; `ToolsTabRow`
  lists whatever `tools` holds.
- `NavigationBarItemsTest`: the "offers the tools behind one item" cases now count four tools; add a case where
  only two tools are on with five screens (no grouping) and one where Scales alone is on (not grouped,
  `count > 1` rule).
- `CampfireScreens`: an `entry<CampfireDestination.Scales>` inside `topLevelScreen` + `toolScreen`, with
  `settingsLayout` and `shellContentPadding`, as Chords.
- `ScreenSearches`: nothing in this version (no search field, §5.4).
- Settings → Features (`FeaturesSections`): `SwitchListItem` "Scales" / "Skálák", description "Show every scale
  and mode the app knows on your instrument, in the Scales tab." after Chord diagrams.
  Onboarding: `OnboardingFeatures.areScalesEnabled`, a row in `FeaturesStep`; check the page against the pinned
  height budget (it scrolls; one more row is fine on 360×640 with no keyboard).
- `ui/CLAUDE.md` Features list, `ui/chrome/CLAUDE.md` ("Metronome, Tuner, Chords, and Scales go behind one Tools
  item"), root `CLAUDE.md` product rules (one line, §10).

## 5. The screen (`ui/screens/scales/`)

### 5.1 Structure

`ScalesScreen(viewModel, layout: PageWidthLayout, contentPadding)`: one `LazyVerticalGrid`
(`GridCells.Adaptive(minSize = SCALE_CELL_MIN_WIDTH)`, 160dp, two columns on a 360dp phone, wrapped in
`widthIn(max = layout.pageMaxWidth(sections = 2))`), `fadingTopEdge`, `bounceScrollableContent`,
`rememberRetainedLazyGridState(state.scrollPosition)`, `ScrollToTopOnRequest` for `Scales`, the same content
padding as `ChordsScreen` (`CHORDS_PAGE_PADDING` moves to a shared `TOOL_PAGE_PADDING` in `ui/chrome/`, or Scales
imports it; prefer the move, two call sites). Items:

1. `ScaleLibraryControls` (full span): `InstrumentSetting` (shared chips; the tuning chip only where the instrument
   has more than one tuning, as everywhere), the key row (`SettingsSubsection("Key")`, the same twelve
   `ChoiceChip`s as `ChordLibraryControls`, which move to `ui/components/controls/KeyChipRow.kt` so both tabs
   share them), and the family row (`SettingsSubsection("Scales")`, chips "Common", then the families in
   `ScaleFamily` order).
2. A heading (full span): "Common scales in C" / "Modes of the major scale in C"
   (`scales_heading`, `%1$s` family, `%2$s` key as the key row writes it), styled as `ChordLibraryHeading`.
3. `ScaleLibraryCell` per scale of the family (`key = scale.id`, `contentType = "scale"`): the name in the second
   accent over a thumbnail (`ScaleDiagram` with `ScaleLabels.NONE`, 148×52dp for a neck, 148×64dp for a keyboard),
   on a `surfaceContainerLow` card, `listItemAnimation(gridState)`, a tap opening `DialogType.Scale`.
4. Where the instrument is Other, a one-line note under the controls (`scales_instrument_other`: "Shown on a
   keyboard. Choose an instrument to see the scales on its strings.").

Everything is computed in composition from `Scales.of(family)` and the geometry function; no `LaunchedEffect`, no
dispatcher, no held results: there is nothing to search for. A tuning or instrument change recomposes the cells
with new geometry, each thumbnail crossfading (`Crossfade(targetState = geometry)`), which is the one animation the
rule asks for.

### 5.2 State (`ScaleLibraryState`, in `CampfireViewModel` next to `chordLibrary`)

`keyIndex` (0, the same index space as `chordLibraryKeys`), `familyIndex` (0 = Common), `labels: ScaleLabels`
(DEGREES; the sheet's toggle, kept for the session), `scrollPosition`. Declared where `chordLibrary` is; one
`internal val scaleLibrary`.

### 5.3 Short window and keyboard

No text field on the tab, so no keyboard; the controls scroll away, and a phone on its side keeps a row of cells.
The sheet (§6) is the thing to size: its header plus a 328×110dp neck plus one line of notes fits 480dp.

### 5.4 Not on the tab in this version

A search field. The Chords tab's search exists to reach chords the key leaves out; here every scale is one chip
away. A search over names and aliases ("hijaz" → Phrygian dominant) is a later addition, in the sticky-item form
`chordLibrarySearchField` has, with `ScreenSearches` wired like the Chords tab's (§11).

## 6. The scale sheet (`ui/dialogs/scales/ScaleSheet.kt`, `DialogType.Scale`)

`DialogType.Scale(val scaleId: String, val rootName: String)`: plain values, like every dialog; `rootName` in the
standard notation (`Eb`, `C#`). The tab passes the key row's spelling; a chord sheet passes the chord's root as the
chord is spelled (`SongChord.spelling`), so a `D#m` suggests D♯ scales and an `Ebm` E♭ ones. An unknown id (a
restored dialog from another version) closes the sheet on composition, as `ChordReferenceSheet` closes with no
tuning. The sheet reads the instrument, its tuning, the notation, and `ScaleLibraryState.labels` when it composes
and never the back stack, which is what lets it open over any screen (decision 11).

`CampfireBottomSheet` titled "C Dorian" (`scales_sheet_title`: `%1$s %2$s` root then name; Hungarian "C dór" is
the same order), subtitle the spelled notes (§2.3) joined by spaces, the way `ChordReferenceSheet` names a chord's
notes. Scrolling content, `fadingTopEdge`, under the sheet's padding:

1. **The diagram**, full width up to the sheet's width, height from the string count (six strings 110dp, four
   80dp, eight 140dp; keyboard 96dp), with labels per `state.labels`, and under it a two-chip radio row
   **Degrees / Notes** (`SelectableChip`, `selectableGroup`), which writes `state.labels`; the labels crossfade.
   Not shown for the keyboard (no labels on keys in this version; the list below names them).
2. **Formula**: one line, `1 2 b3 4 5 6 b7` in the notation's accidental signs (`♭`/`♯` as `ChordProNotation`
   shows chords), then the steps `W H W W W H W` (`scales_steps_whole` "W", `scales_steps_half` "H", `scales_steps_augmented`
   "3" for a step of three semitones, Hungarian "E", "F", "3").
3. **Relation**: "Mode 2 of C major" (`scales_mode_of`) where `modeOf` is set, with `Scales.parentRoot`; "Also
   called Hijaz, Freygish" (`scales_also_called`) where aliases exist. Nothing else: no key signature, no
   circle (§8).
4. **Chords in this scale** (seven-note scales only, and only while `FeatureSwitches.isChordLibraryEnabled` and the
   instrument has chord shapes, i.e. `chordTuning() != null`): a heading and a row of `ChordLibraryCell`s (one per
   degree, triads; a second row of sevenths under a smaller heading), each with its Roman numeral over the name,
   drawn with `selectShape` (the player's shape, then the app's first), the shape looked up from the tables only
   (`ChordVoicings.needsSearch` → draw the empty frame; the common qualities here are all in the tables, so this is
   theory, not a visible state). A tap opens `DialogType.ChordReference(chord)` **in place of the sheet** (the
   dialog host shows one dialog at a time); coming back to the scale is a tap on its cell again. Document that in
   the package `CLAUDE.md`; a stacked sheet is not worth building for this.

The sheet reads the preferences each composition; nothing can change the instrument under it (device-only, its chips
are behind the sheet), and if something did it would be a recomposition, never a dismiss.

### 6.5 Scales for this chord (`ui/chords/ScaleChipRow.kt`, in both chord sheets)

A heading ("Scales for this chord", `song_details_chord_scales`) and a `FlowRow` of `SelectableChip`-styled chips
(none selected, `Role.Button`), one per scale of `scalesContaining(chord.chord)` in that order, each written as
the sheet's title would be ("C Mixolydian", notation applied to the root). It goes:

- at the bottom of `ChordReferenceSheet` (the Chords tab's chord sheet), through `ChordShapeGrid`'s existing
  `footer`, under every shape;
- at the bottom of `ChordShapeSheet` (a song's chord sheet), through the same `footer`, under the shapes or under
  the song's own definition; the chord is the one the page plays, after transposition and the keyboard's capo rule,
  since that is the chord the player hears.

A tap dismisses the chord sheet and shows `DialogType.Scale(scale.id, rootName)` (the one-dialog host; the plan's
documented replacement, §6.4, now in both directions, so scale and chord sheets hop back and forth). The row is
composed only while `FeatureSwitches.areScalesEnabled`; an empty list composes nothing, not a line saying so: the
row is a suggestion. In read only mode it stays, since the Chords tab's sheet is offered there and the song's chord
sheet is already gone with every other way to edit. The chips take no room from the shapes: `ChordShapeGrid` is a
lazy grid, and the footer scrolls in after the last shape.

## 7. Things to check by eye (not by test)

1. **360×640dp, Common family, guitar**: two columns of cells, the thumbnails' dots distinct, the root dots
   visibly the accent; the controls take about 230dp, so the first cells are on the first screen.
2. **The sheet on 360×640dp with Notes labels**: `Bb` and `F#` fit in a dot at 12 frets across 328dp (a fret is
   ~25dp, a dot ~18dp, the label ~9sp). If they do not, the sheet drops to 10sp or to degree labels only, and the
   plan says so here.
3. **Four tools on a 360dp bar's tab row**: "Metronome / Tuner / Chords / Scales" and "Metronóm / Hangoló /
   Akkordok / Skálák" with icons above, each tab ~90dp. `ToolsTabRow`'s note says three names fit; four is the
   open question. Fallbacks in order: `labelMedium` for the tab text; then `PrimaryScrollableTabRow` with no edge
   padding only when four tools are on. Settle before §9 step 4.
4. **A tall rail with seven items** (a 600×500dp desktop window, which is not short): the plain rail's seven items
   at 56dp + gaps fit 500dp; confirm nothing clips at 480dp, the short-window line, where grouping takes over.
5. **The violin's dashed positions** read as a fingerboard, not a broken drawing; the banjo's short string starts at
   the fifth fret with its open mark there.
6. **Dark theme**: root accent against `onSurface` dots on `surfaceContainerLow`; the keyboard case is already
   solved by `pressedKeyColor`.
7. **The chip row under a song's chord sheet on 360×640dp with the keyboard closed**: a C triad's twenty chips are
   about five rows; they must read as a footer after the shapes, not as the sheet's main content. If they crowd,
   the first fallback is capping the row at the Common matches with a "More" chip that opens the Scales tab on that
   key, and the plan says so here.

## 8. The circle of fifths: not needed, and why

What a circle gives a player: (a) a key picker, (b) the number of sharps or flats of a key, (c) the neighbours of
a key (its relative minor, its dominant and subdominant). In this app:

- (a) is the key row, which every tab already uses and which is a radio group a screen reader walks; a circle is a
  custom `Canvas` picker with twelve hit regions, its own focus handling, and no room on a 360dp phone next to the
  cells it would push down.
- (b) belongs to major and minor keys, not to a Hungarian major scale; the sheet's spelled notes show it anyway.
- (c) is one line of text where it matters: the sheet's "Mode 2 of C major" covers the modes; a "Relative minor:
  A minor" line could join it for `major` and `natural_minor` at no cost (worth adding in §6.3, it is two strings).

So the circle adds no information the tab cannot say in a line, and costs a component. If it is wanted later, it
is a picker, not a screen: `ui/scales/CircleOfFifths.kt`, drawn with `Canvas`, standing in for the key row on wide
layouts (`PageWidthLayout` with two sections) behind the same `keyIndex`, roots ordered `(index * 7) mod 12`, the
relative minors on an inner ring, selection animated by a sweep; everything else on the tab unchanged.

## 9. Order of work

1. **`:chordpro` `scales/`**: `ScaleFamily`, `Scale`, `Scales` (the catalogue of §2.2), `ScaleSpelling`,
   `ScaleChords` (both directions, §2.4), `chordpro/scales/CLAUDE.md`, the three tests, `compose-stability.conf`.
   `./gradlew :chordpro:desktopTest`.
2. **`ui/scales/`**: `ScaleDiagramGeometry` + test, `ScaleDiagram`, `ScaleLabels`, `ScaleLabels.kt` names
   (`scaleLabel(scale)`, `scaleFamilyLabel(family)`, the `when`s over the ids) and the strings of §2.2 in both
   `strings.xml`.
3. **Destination and switch** (§4), with every test extended, before any screen exists: the tab compiles as an
   empty `ScalesScreen` so that the chrome can be checked (§7.3, §7.4) on its own.
4. **The tab** (§5): move `ChoiceChip`/`ChipRow` out of `ChordLibraryControls` into a shared `KeyChipRow`, then
   `ScaleLibraryState`, `ScaleLibraryControls`, `ScaleLibraryCell`, `ScalesScreen`, `ui/screens/scales/CLAUDE.md`.
5. **The sheet** (§6): `DialogType.Scale`, `ScaleSheet`, `ScaleChordsRow`, its `CLAUDE.md` lines; the
   "Relative minor" line of §8(c). Then **scales for this chord** (§6.5): `ScaleChipRow`, the footer in
   `ChordReferenceSheet` and `ChordShapeSheet`, the `ui/chords/CLAUDE.md` line. Try the sheet over a song first:
   if it opens there with nothing but a `showDialog`, decision 11 holds.
6. **Docs**: root `CLAUDE.md` product rule, `ui/CLAUDE.md`, `ui/chrome/CLAUDE.md`, `ui/instrument/CLAUDE.md`
   (Scales reads `InstrumentTuning.strings` directly, the one reader besides the tuner), `README.md`'s feature
   sentence, `documentation/TO_DO.md` (drop the item). `spotlessApply`, `check_license_headers.py`,
   `check_sizes.py`, `check_doc_names.py`.
7. **Verify**: `./gradlew desktopTest`, compile every platform, the by-eye list of §7 on the desktop build at
   360×640 and 640×360 and on the Android emulator. At release time only: a store screenshot of the tab
   (`store-screenshots`), the website, the promo video, the Baseline Profile.

## 10. Lines for the `CLAUDE.md`s

- Root, product rules: "The Scales tab shows every scale of the catalogue on the player's instrument and tuning,
  every stringed instrument included, with the chords a seven-note scale holds; 12-TET only: `ui/screens/scales`,
  `chordpro/scales`."
- `chordpro/CLAUDE.md`, module layout: `scales` beside `chords` and `voicings`.
- `ui/CLAUDE.md`, Features: "**Scales** (`areScalesEnabled`): takes the Scales tab. Its chord row follows Chord
  diagrams."
- `ui/chrome/CLAUDE.md`: the Tools item sentence with four tools.
- `ui/screens/scales/CLAUDE.md`: the §5 rules (no search, session state, Other shows a keyboard, cells crossfade on
  a tuning change, the chord row's tap replaces the sheet).
- `ui/dialogs/scales/CLAUDE.md` (or the same file): the scale sheet is the one way a scale is shown, carries a
  scale id and a root name only, reads the instrument from the preferences, and opens over any screen.
- `ui/chords/CLAUDE.md`, "The chord sheet": "Both chord sheets end in the scales built on the chord's root that
  hold its tones (`scalesContaining`), while Scales is on; a chip opens the scale sheet in place of the chord's."


## 11. Not in this plan

- A search over scale names and aliases (§5.4).
- A "More" chip in the chord sheets' row that opens the Scales tab on the chord's root (only if §7.7 asks for it).
- Position boxes (CAGED, three-notes-per-string), a left-handed neck, more than twelve frets, labels on keyboard
  keys, a scale played aloud.
- User-defined scales; quarter-tone scales (decision 4).
- "Scales in this song's key" from the song details overflow (the key is `ChordProMetadata.key`, transposed and
  with the keyboard's capo rule applied as the page shows it): a natural follow-up, since with decision 11 it is
  one `showDialog(DialogType.Scale("major" or "natural_minor", root))`, or a chip row of the key's scales in About
  the song.
- Printing or exporting a scale; the PDF stays a song's.
