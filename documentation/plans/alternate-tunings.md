<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Chord diagrams in alternate tunings — implementation plan

Written 2026-10-13 against `06475d270`, the commit that added the mandolin diagrams (`ChordInstrument.MANDOLIN`,
`playsChordOver`, `{define-mandolin}`); reviewed against the same commit. Today the diagrams are drawn for one standard
tuning per instrument, and a player in any other tuning is told to pick Other, which draws none. This plan lets them
pick their tuning instead, in three phases that each ship on their own:

1. **Shifted tunings**: the whole instrument moved by the same interval (half and full step down, the ukulele's D and
   baritone tunings and its low G, the mandolin's half step down). The shapes are the standard ones, so this phase is
   mostly the model refactor and the setting, which the other two phases build on.
2. **Drop tunings**: one or two strings moved (drop D, double drop D, drop C♯, drop C, and drop B). The table shapes are
   derived from the standard tables, the rest comes from the search.
3. **Open and modal tunings**: open G, D, E, A, and C, DADGAD, and the mandolin's AEAE and GDGD cross tunings. The
   search finds every shape, seeded with the few that are certain.

## 1. What is being built

- **A Tuning setting under Settings → Songs → Instrument**, for a fretted instrument only: a row of chips like the
  Tuner tab's tuning row, standard first, each named the way the tuner names it (`TuningName`, `tuningLabel`, the note
  in the reader's chord notation). The instrument rows keep saying their strings, now the strings of the tuning picked.
- **The diagrams, the chord sheet, the PDF, and the editor's Chord shape button follow the tuning**: every shape is a
  shape on the strings the player actually has, the root dots marked by those strings' notes.
- **The onboarding sets it**: the instrument answer already writes the tuner's tuning (keeping one the tuner has for
  that instrument, so drop D stays drop D), and it writes the diagrams' tuning from the same answer where the diagrams
  know that tuning, standard otherwise.
- **Other stays** for what none of the tunings covers (seven- and eight-string guitars, the bass, the banjo, and the
  violin). The instrument section's description ("The instrument you play, in standard tuning, …") loses "in standard
  tuning" in phase 1, and `ui/chords/CLAUDE.md`'s "its Other, for every other instrument and tuning" narrows with each
  phase.

### What is already there

- `ChordVoicingSearch.search` reads nothing of the instrument but `tuning` and `playsChordOver`: the four-fret window,
  the open strings, the finger count, the lowest note, and the minimum string count all work on any list of strings.
- `ChordShapeGeometry.pitchClasses` and `chordDiagramGeometryOf`'s root markers (`isRootAt`) compute each string's
  note from `instrument.tuning`, so a diagram marks its roots right on whatever strings it is handed.
- `:tuner:api`'s `InstrumentTuning` lists every tuning this plan covers, with ids (`guitar_drop_d`, `ukulele_baritone`,
  `mandolin_gdgd`, …) and strings as MIDI notes in the same order a chord chart reads them (its `GUITAR`, `UKULELE`,
  and `MANDOLIN` lists equal `ChordInstrument.tuning` today); `:presentation`'s `TunerInstrument`, `TuningName`, and
  `tuningLabel` name them in both languages, and `ui/tuner/NoteNames.kt` names a MIDI note in the reader's notation.
- `UserPreferences.chordVoicings` is keyed by an instrument id string and synced, and the sync merge keeps an id this
  version does not know (`data/sync/implementation/CLAUDE.md`), so new keys are safe to introduce.
- `UserPreferences.chordInstrument` is device-local (`SyncedPreferences` carries `chordVoicings` and nothing else of the
  chords), and so will the tuning be: a player may tune each instrument differently.
- `UserPreferencesDocumentFormat` decodes with `ignoreUnknownKeys`, so a new field costs an older version nothing but
  the field.

### Decisions — veto any of them before the work starts

1. **A chord name in a shifted tuning names the shape, except where players read that tuning in sounding names.** A
   song for a guitar tuned a half step down says "play G" for a G shape that sounds F♯, so the guitar's and the
   mandolin's half and full step down draw the standard shape of the chord as written. The baritone ukulele and the
   ukulele's D tuning are the other way around: their charts name the chord that sounds, so a G on the baritone is the
   soprano's C shape. Each shifted tuning carries which of the two it is (`namesShapes`), and this table is the default:

   | Tuning | Chords name | A `G` is drawn as |
   |---|---|---|
   | Guitar half / full step down | the shape | the standard G shape |
   | Mandolin half step down | the shape | the standard G shape |
   | Ukulele D (A D F♯ B, two semitones up) | the sound | the standard F shape (the chord two semitones below) |
   | Ukulele low G | the sound, which is the same | the standard G shape (only the fourth string's octave differs) |
   | Baritone ukulele (D G B E, five semitones down) | the sound | the standard C shape (the chord five semitones above) |

   The rule behind the third column: a `namesShapes` tuning draws the standard shape of the chord named; the other kind
   draws the standard shape of the chord moved by `-semitones`.
2. **A drop tuning names the sound below its shift.** Drop D is standard with the sixth string down a tone: chord
   names are what sounds, as everybody writes drop D songs. Drop C♯, drop C, and drop B are a half, a whole, and a step
   and a half down with the sixth string a further tone down: the shift part follows decision 1 (shape names, like the
   down-tunings they are built on), and the dropped string is then derived as in drop D. The alternative, sounding
   names throughout, is what metal tabs often use; it is one flag per tuning (`namesShapes`) either way.
3. **A song's own `{define}` is a standard-tuning shape.** ChordPro has no tuning on a definition (it lives in the
   program's configuration, not the song), and almost every file in the wild is written for standard tuning. So a
   definition is drawn in standard tuning and in every tuning whose shapes are the standard ones (the `namesShapes`
   shifted tunings, and the low G), and nowhere else: in a drop, open, or sound-named shifted tuning the cell falls back
   to the player's shape or the app's, exactly as a definition for another instrument does today. No `{meta: tuning}`
   of Campfire's own is invented.
4. **The editor's Chord shape button writes only what it can write truthfully.** In standard tuning and the tunings
   decision 3 draws definitions in, it is unchanged. In any other tuning a `{define}` of the shape on screen would be
   read by every other program, and by this one on another device, as a standard-tuning shape of that chord, which it
   is not; so there the button writes the chord's standard-tuning shape instead (the player's choice under the
   standard key, else the app's default), and where there is none, the line with the frets left to be typed, as it does
   for a chord it has no shape for. Its snackbar says "written for standard tuning".
5. **The player's own shapes are kept per tuning family**: `guitar` holds the choices for standard and every tuning
   decision 3 draws definitions in (the shapes are the same), and every other tuning has its own key (`guitar_drop_d`,
   `ukulele_baritone`, …), its id from `InstrumentTuning`. A shape chosen in drop D never appears in standard.
6. **Seven- and eight-string guitars are not in this plan**: they are instruments with more strings, a `{define}` of
   seven frets has no instrument to be read for, and the tables would be new from scratch. They stay Other.
7. **The tuning is remembered per instrument** (`UserPreferences.chordTunings`, instrument id to tuning id): a player
   who tunes the guitar to drop D and the ukulele to baritone switches between them without picking twice. One field
   holding one id would have to be cleared or misread as the instrument changes.

## 2. Phase 1 — the model, the setting, and the shifted tunings

### 2.1 `:chordpro`

- **`model/ChordTuning`** (new, public, explicit API): one entry per tuning the diagrams know, `id` (equal to the
  `InstrumentTuning` id, so the two can be matched by id across modules without `:chordpro` depending on `:tuner:api`),
  `instrument: ChordInstrument`, `strings: List<Int>` — **the real strings**, in a chord chart's order, which is what
  the root markers and the sheet read; the baritone's fourth string is an octave below a plain five-semitone shift and
  the low G's an octave below standard, so a shift describes pitch classes, never the MIDI notes — and `kind`:
  - `Standard`;
  - `Shifted(semitones, namesShapes)`, every string's pitch class moved by `semitones` from the standard (the low G is
    `Shifted(0)`: not a fret changes, only the lowest note, which the ukulele's `playsChordOver` never reads);
  - `Dropped(shift, namesShapes)` for phase 2 (per-string offsets from the shifted standard);
  - `Open` for phase 3.

  `ChordTuning.standard(instrument)` gives the standard one, the keyboard's included (no strings), so that one value
  can stand for "what the diagrams are drawn for" everywhere an instrument stands today; `voicingKey` is the key
  decision 5 names; `drawsDefinitions` is decision 3's rule (`Standard`, and `Shifted` where `namesShapes`). Phase 1
  adds the standard entries and the shifted ones only, so nothing unfinished can be picked.
- **`ChordInstrument.tuning` stays** as the standard tuning (it is what a `{define}` is read against: decision 3), so
  `ChordProDefinitions`, the transposer, and the selector logic are untouched.
- **`ChordVoicings`'s entry points take a `ChordTuning`** where they took an instrument (`default`, `needsSearch`,
  `all`, `read`; the keyboard keeps its own path, through its standard entry). For a `Shifted` tuning they answer the
  standard tuning's shapes of the chord the shape names: the chord itself where `namesShapes`, the chord moved by
  `-semitones` otherwise (`ChordProTransposer`'s arithmetic on the `Chord`, not on the text). The cache key becomes
  `Pair<Chord, ChordTuning>`; `MAX_CACHED_DEFAULTS` stays, a tuning change only refilling it.
- **`ChordShapeGeometry.pitchClasses`** gains an overload on a list of strings, which `:presentation` uses for the root
  markers.

### 2.2 `:data:model` and `:data:source:local:implementation`

- `UserPreferences.chordTunings: Map<String, String>` (decision 7), `UserPreferences.ChordInstrument.id` to the
  tuning's id, no entry for standard; device-local like `chordInstrument`. `UserPreferencesDocument` gains the field,
  the mapper passes ids through as strings (an id this version does not know reads as standard where it is used, and
  is kept for the version that does, as `chordVoicings` keeps its keys), and `UserPreferencesDocumentFormatTest` gets
  the new field.
- A tuning whose id belongs to another instrument than the key it is under reads as standard.

### 2.3 `:presentation`

- `ui/chords/SongChords.kt`: `toChordInstrument()` grows a sibling `chordTuningOf(preferences)`, resolving the id under
  the instrument picked to a `ChordTuning` of that instrument, else its standard; `SongLyricsInputs.chordInstrument`
  becomes `chordTuning`, and `songChordsOf`, `withSearchedShapes`, and `selectShape` take it (a definition counts only
  where `drawsDefinitions`; the stored shape is read under `voicingKey`).
- `chordDiagramGeometryOf` takes the strings the root markers are computed from: the tuning's where the diagram is a
  shape on those strings, and the standard ones for a `namesShapes` tuning, whose diagram is the standard shape of the
  chord named. Either way the dot marked is the root of the chord as named.
- `ChordShapeSheet`: the tuning's name under the chord's, only for a tuning other than standard; the note line stays
  the chord's notes (`spelledNoteNames`), which do not depend on the tuning. `setChordVoicing` (the view model's and
  `PreferencesController`'s) takes the `voicingKey` string in place of the `UserPreferences.ChordInstrument`, and
  writes under it.
- `ChordShapeInsertion`: decision 4, which needs the tuning and the stored shapes under both keys.
- Settings (`SongDisplaySection`): a `ChordTuningChoice` under `ChordInstrumentChoice`, shown for a fretted instrument,
  chips in a `FlowRow` (it scrolls with the tab, so the sheet height budget does not apply), labelled by the tuner's
  `tuningLabel`. The instrument rows' `GUITAR_STRINGS` / `UKULELE_STRINGS` / `MANDOLIN_STRINGS` give way to the names
  of the picked tuning's `strings` (`NoteNames.kt`, in the reader's notation, as today). The subsection's description
  loses "in standard tuning" in both languages; `settings_chord_instrument_other_description` ("No chord diagrams")
  stays.
- Onboarding (`OnboardingInstrument`, `InstrumentStep`): the answer writes the picked instrument's entry of
  `chordTunings` from the tuner's `tuningFor` result where a `ChordTuning` with that id exists, and removes it
  otherwise; `onboardingInstrumentOf` is unchanged, since the answer is still the instrument.
- PDF (`ui/print`, `PrintLayout.chordDiagramRows`): the diagram rows have no heading of their own today, so a tuning
  other than standard gets one line naming it above the first row, in the song's language-independent note names, so
  a printed sheet does not mislead whoever reads it later.
- Strings: the tuning names exist already (`TuningLabels.kt`); new are the sheet's and the PDF's "In %1$s" line and
  the Chord shape button's "written for standard tuning", in both `values` and `values-hu`.

### 2.4 Tests

- `:chordpro`: a `Shifted` tuning's shapes sound the chord they are drawn for once the shift is counted, for both
  `namesShapes` values (the baritone's G through the real strings); the cache answers per tuning.
- `:data:source:local:implementation`: the field round-trips; an unknown id is kept.
- `:presentation`: `chordTuningOf` (an id of another instrument reads as standard), `selectShape` with a definition in
  a shifted tuning (drawn) and a drop tuning (phase 2, not drawn), `chordShapeInsertion` under decision 4, the
  onboarding's write, and every `ChordTuning` id existing in `InstrumentTuning` with the same strings in the same
  order (a test in `:presentation`, which sees both).

## 3. Phase 2 — drop tunings

### 3.1 Derived tables (`:chordpro`, `ChordVoicingSearch`)

A drop tuning is its shifted standard with some strings lowered. A table shape becomes a drop-tuning shape by
re-fretting each lowered string, and is kept only where the result is still a shape of the chord:

1. A muted string stays muted. A string lowered by `d` semitones that sounds at fret `f` is stopped at `f + d` instead.
2. The result is kept where it passes the search's own rules (sounds only notes of the chord and all the required
   ones, bass or root lowest, four frets, four fingers, no muted string between two that sound, open strings only with
   a hand in the first five frets) and is holdable. Drop D's G `3 2 0 0 0 3` becomes `5 2 0 0 0 3`, which passes them
   all and is what the charts print; its D `x x 0 2 3 2` is unchanged.
3. Where the re-fretted string breaks a rule, the same shape with that string muted is tried, on the same conditions.
   Drop D's G7 `3 2 0 0 0 1` re-fretted spans five frets, and muted leaves B lowest, so nothing is derived from it and
   the search supplies the chord.
4. Fingering is kept only where every lowered string is muted in the shape, so nothing under a finger changed;
   otherwise it is left out (`fingers = null`), as the search's shapes are. A moved stopped string cannot keep its
   finger either: the F shape's barre finger cannot be at the first and the third fret at once. The price is that drop
   D's E, Em, G, and F lose their fingering; if the contact sheet shows that hurts, a finger reassigned for a lone
   finger on a re-fretted string is a refinement, not part of this phase.

A drop tuning also carries a seed table of its own, in `ChordVoicingTables`, for the shapes its charts print that no
standard shape derives into: the one-finger power chord (`D5 frets 0 0 0 x x x`, and `Eb5 frets 1 1 1 x x x`, a closed
shape moved to every root like any other). Without it a D5 in drop D would come up as the standard `x 5 7 7 x x`, which
derives unchanged and, being a table shape, would come before anything the search finds. The order is the seed table,
then the derived shapes in the tables' order, then the search's; all of it is computed once per tuning and chord, in the
same cache, and `needsSearch` runs the derivation (cheap) to answer whether the search would have to run.

### 3.2 The tunings

`GUITAR_DROP_D`, `GUITAR_DOUBLE_DROP_D` (the first string too), `GUITAR_DROP_C_SHARP`, `GUITAR_DROP_C`, and
`GUITAR_DROP_B` (decision 2 for their names). The ukulele's low G is phase 1's (`Shifted(0)`), not a drop tuning.

### 3.3 Tests

- Every derived shape of every table entry, in every drop tuning, sounds its chord and keeps the search's rules (the
  existing `every shape the search finds keeps its rules` loop, over tunings, its lowest-note check against the
  tuning's strings).
- Every seed table entry sounds its chord (`ChordVoicingTablesTest`, over tunings).
- The forty everyday chords in drop D are spot-checked against what a chart shows (D, G, A, Em, C, D5, …), as
  `ChordVoicingsTest` does for standard.
- The QA contact sheet (`ChordVoicingContactSheetTest`, `CAMPFIRE_CHORD_QA_DIR`) draws every drop tuning's defaults,
  for a player to look over.

## 4. Phase 3 — open and modal tunings

### 4.1 The search, and what it may need

Open G, D, E, A, and C, DADGAD, and the mandolin's AEAE and GDGD come from the search alone, seeded by a small table per
tuning holding only shapes that are certain: the open chord itself (`0 0 0 0 0 0` in open G is G), its barre moved up
the neck (every major chord, one closed entry), and the one-finger minor and seventh shapes every chart of that tuning
prints. These are typed into `ChordVoicingTables` per tuning and checked by the same table test, read against the
tuning's strings.

The search's rules were chosen for standard tuning, and three may need to move, each only if the contact sheet shows
it is needed:

- **The minimum string count** (`minimumStrings`: three for a chord of two pitch classes, four otherwise, every string
  on an instrument that `playsChordOver`): open-tuning shapes often leave the two lowest strings out. Three may be
  allowed in an `Open` tuning.
- **Open strings only with a hand in the first five frets** (`OPEN_POSITION_FRETS`): in an open tuning a chord at the
  twelfth fret with the open strings ringing is everyday playing. The rule may be lifted where every open string is a
  note of the chord.
- **Bass or root lowest**: drone strings put the tuning's root under everything; a chord whose lowest note is the
  fifth may be allowed where that note is an open string.

The search's order (lowest position first, then the most strings, then the smallest stretch) is kept; a
`preferOpenStrings` tie-break that puts shapes with more ringing open strings first is the one change that would make
the first shape the expected one more often.

### 4.2 Quality bar

Phase 3 ships behind a review by a player of each tuning, using the contact sheet; a tuning whose defaults do not pass
is left out of the list rather than shipped half right. Until then those tunings stay Other.

### 4.3 Tests

- The search's rules hold in every open tuning (the same loop).
- Each seeded table entry sounds its chord.
- Searching stays bounded: `all` for the test corpus (twelve roots by twenty-one qualities, 252 chords) in each tuning
  finishes within the time the standard tuning takes now (a bound on the shape count, not a timer).

## 5. Corner cases

- **A song with a `{define}` in a tuning that does not draw definitions** (decision 3) draws the player's or the app's
  shape, and the sheet says "Defined in this song for standard tuning" in the stepper's place, with a button to show
  the definition anyway. A definition is never translated between tunings, for the reason one is never translated
  between instruments.
- **A capo** works as it does now: it is counted into the transposition of the page and nothing else, in any tuning.
- **The keyboard** has no tuning; the chip row is hidden for it, and its `chordTunings` entry, if a file ever carries
  one, is ignored.
- **Switching the tuning while a song is open** rebuilds its Chords section like switching the instrument does now
  (`SongLyricsInputs` changes); a shape still being searched shows its empty frame, as today.
- **An older version reading preferences written by a newer one** (a downgrade, since the field is not synced) ignores
  the `chordTunings` field (`UserPreferencesDocumentFormat` decodes with `ignoreUnknownKeys`) and drops it on its next
  save, which costs only the tunings; synced shapes under a key it does not know are kept.
- **The tuner and the diagrams disagree** (the tuner on drop D, the diagrams on standard): allowed, as today. The tuner
  sheet over a song may offer "Draw the chords for this tuning" once the two differ, a later addition.

## 6. Order of work

1. Phase 1, in one series: `ChordTuning` and the `ChordVoicings` signatures (behaviour-preserving with standard only),
   the shifted entries, the preference, `:presentation`, docs (`chordpro/CLAUDE.md`, `ui/chords/CLAUDE.md`, the
   settings and onboarding `CLAUDE.md`s, the sync one for the new `chordVoicings` keys), and the tests.
2. Phase 2: the derivation, the seed tables, the drop entries, the contact sheet review.
3. Phase 3, one tuning at a time, each behind its review.

Phase 1 is the largest by files touched (mostly mechanical), phase 2 the most algorithmic but contained in
`ChordVoicingSearch`, and phase 3 the smallest in code and the largest in review.

## 7. Checks owed by hand

- Settings → Songs → Instrument with the chip row on the 360 × 640 dp phone, both themes, both languages, every
  notation (the chip names carry notes).
- The chord sheet and the PDF line showing the tuning; a printed drop D sheet read by somebody who did not set it.
- The Chord shape button in the baritone tuning writing the standard shape, and the snackbar saying so.
- A player of each tuning looking over the defaults (phase 2 drop D first; phase 3 per tuning).
- The onboarding run again from a drop D tuner: the diagrams follow it (phase 2; in phase 1 they stay standard).

## 8. Not in this plan

- Seven- and eight-string guitars, the bass, the banjo, and the violin (decision 6).
- Custom tunings typed by the player.
- A tuning written into the song file, and a definition that names one.
- Linking the diagrams' tuning to the tuner's beyond what the onboarding writes.
