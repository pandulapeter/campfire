<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/chords

Chord diagrams (`ui/chords/`, `songView/SongChordsSection.kt`, `dialogs/instrument/ChordShapeSheet.kt`). Nothing is
shipped or fetched: `:chordpro` reads what notes a name means (`ChordProChords`) and finds shapes (`ChordVoicings`: a
hand-typed table first, a search for the rest).

## Which instruments

Guitar (and the twelve-string, drawn as a guitar), ukulele, mandolin, banjo, and keyboard draw diagrams. Seven- and
eight-string guitars, basses, violin, and Other draw none, as if the Features switch were off. The instrument and tuning
pill heads the song's first section for every song with chords, even an instrument without shapes, so the diagrams can
be brought back (`RenderSection.Metadata.instrumentPill`). On the keyboard the page is already in the sounding chords
(see `ui/instrument/CLAUDE.md`), so diagrams count no capo.

## Which shape a chord is drawn with

`selectShape` (`ChordSelection.kt`, tested), in order:

1. The song's own `{define}` / `{chord}` for that instrument (and tuning, below). 2. The player's choice, **one per
chord and instrument for the whole library** (a habit of the hands, not a reading of one song):
`UserPreferences.chordVoicings` under the tuning's `voicingKey`, stored as the shape rather than its index and keyed by
the chord's notes so every spelling shares it; synced. Used only while it still reads as a shape of that instrument. 3.
The app's first shape.

Definitions:

- One for another instrument is neither used nor translated (a guitar shape is another chord on a ukulele); which
  instrument a definition is for, and how a banjo's is drawn, is `ChordProDefinitions`' (see `chordpro/voicings/CLAUDE.md`).
- A definition follows its chord through every transposition (the reader's, the file's `{transpose}`, the editor's
  transpose action) in whichever octave a hand can hold; one left needing a fifth finger gives way to the player's
  shape.
- **A definition is a standard tuning shape**: ChordPro gives `{define}` no tuning, so it is drawn only where a standard
  shape sounds right (`ChordTuning.drawsDefinitions`), else kept as `SongChord.undrawnDefinition` and offered behind a
  button in the chord's sheet.

## Tunings

- One tuning per instrument (`UserPreferences.tunings`, device-only), written by `withTuning`, read by `tuningOf` /
  `chordTuning()` in `ui/instrument/Instruments.kt`; an unknown or foreign id is standard.
- A `ChordTuning` is the tuner's `InstrumentTuning` of the same id (`ui/instrument/InstrumentsTest` holds them together,
  strings included): **every tuning the tuner knows for an instrument with shapes is one the diagrams know**.
- **A chord name always names the chord that sounds**: half step down, an A is drawn `x 1 3 3 3 1`; roots are read
  against the tuning's own strings. Player choices made in a tuning are kept under its own key.
- On a tuning change the page keeps its cells until chords are re-collected for the new tuning off the main thread, so
  it crossfades once, never drawing old shapes on new strings.
- `PreferencesController` reads the player's tuning's table off the main thread, ahead of the first song's first page.

## The Chords section

- `songChordsOf` (tested): every chord once however spelled, in first-played order, read from the song *before* notation
  is applied (a numbering's step means nothing without its stretch's key), named as the page names it
  (`ChordProNotation.shownNames`). **More than 48 chords (`MAX_SONG_CHORDS`) is a songbook and gets no section.**
- A page's first model is built in place without the shapes only the search finds (`isShapePending`, empty frame); those
  follow from `Dispatchers.Default` (`withSearchedShapes`), keeping the same sections so only the Chords section
  re-measures.
- **Cut between rows like any section**: drawn as slots (`chordSlotCount`, `ChordRows.kt`, tested), each holding the row
  its index names at the measured width, the last holding the rest.
- Rows are balanced (`balancedRowStarts`: nine where six fit is five over four); the PDF breaks rows the same way.
- **Each cell names its chord's degree** after it, as a numbering's letters follow its step (`SongChord.degree`,
  `withDegrees`, tested), in Roman notation's numerals (`ChordProNotation.romanNumeral`: `V7`, `♭VII`), counted against
  `degreeKeyOf`: the scale suggestions' home key (so a capo chart counts its shapes' key), else the transposed `{key}`
  by the same capo rule (`SongScaleGroups.keyOfShapes`), else none. One key a song, a chromatic chord by its step
  (`II7`, never `V/V`); none in a numbering. Either second name stands on a line over the name, as the scale sheet's
  numerals do (three quarters of its size, normal weight), a line every cell keeps where any has one. Only this
  section and the PDF's diagrams: lyrics and the chord sheet keep their names. Chord
  degrees (on by default) takes it.
- Folded per song (`CHORDS_FOLD_KEY` in `UserPreferences.foldedSections`), read only mode included.
- **Every cell opens its chord's sheet** outside read only mode, hit-tested by `chordCellAt` (tested). The press
  highlight waits for the tap timeout (immediate for a mouse), so a scroll started on a diagram never lights it; the tap
  is consumed so it doesn't also turn the page. A screen reader gets one custom action per cell.
- A cell whose chord changes shape (picked, retuned) crossfades (`ChordShapeCrossfade.kt`, `changedShapeCells` tested);
  a searched shape arriving into the empty frame, or a cell that is now another chord, does not.
- `ChordDiagramGeometry` (pure, tested) draws the shape: at least four frets, up to 24; the root in the second accent; a
  keyboard of two to four octaves.

## The chord sheet

`ChordShapeSheet` (`DialogType.ChordShape`) finds the chord by name in the song as the page plays it, and closes if it
is gone.

- Shapes are searched before the sheet opens, so it slides up whole; a stored shape the app doesn't list goes first.
- A tap writes the player's choice at once; picking the app's first shape removes the entry. No reset button.
- A chord the song defines shows its definition alone, not pickable.
- Both chord sheets end in the scales built on the chord's root that hold its tones (`ScaleChipRow`), root
  as the chord spells it, while Scales is on; a chip opens the scale sheet over the chord's.
  **One hop only**: a chord sheet opened from a scale sheet offers no scales (`ChordReference.showsScales`), and a scale
  sheet opened from a chord sheet no chords (`DialogType.Scale.showsChords`), so the two never chain.
- Chord sheets offer scales only while Scale suggestions is on (`FeatureSwitches.areScaleSuggestionsEnabled`).
