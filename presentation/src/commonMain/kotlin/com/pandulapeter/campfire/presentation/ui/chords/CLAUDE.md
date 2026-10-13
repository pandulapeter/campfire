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

Chord diagrams.

### Chord diagrams

**Chord diagrams** (`ui/chords/`, `screens/songDetails/SongChordsSection.kt`, `components/ChordDiagram.kt`,
`dialogs/ChordShapeSheet.kt`). `prepareSongLyrics` collects a song's chords with the rest of the model; the first model
of a page, built in place, leaves out the shapes only the search finds (`searchesShapes`, a chord `isShapePending`),
which follow from `Dispatchers.Default` (`SongLyricsModel.withSearchedShapes`, the same sections, so only the Chords
section is measured again, at the size it had: the cell is drawn as the empty frame until then) — a chord the tables
lack shows its frame alone for a moment the first time it is seen in a session (`songChordsOf`, tested: every chord
once, however the page spells it, under the name it is first played with, in the order it is first played, read from the
song before its notation is applied — `CampfireViewModel.transposedSong`, which `renderSong` is followed by
`notatedSong` — since a step of a key says nothing about the notes without the stretch it stands in, and named as the
page names it (`ChordProNotation.shownNames`), a numbered chord with its letters after it in the cell, with its `Chord`,
the shape the app shows it with on the instrument in `SongLyricsInputs`, and the song's own definition of it on that
instrument where there is one that is drawn — one a transposition moved only while a hand can hold it; a song of more
than 48 chords is a songbook and gets none).

On the keyboard the page is already in the chords that sound (see How a song is played on the keyboard in
`ui/CLAUDE.md`), so the diagrams have no capo of their own to count. `SongLyrics`, handed
`ChordDiagrams`, puts `RenderSection.Chords` after the metadata section, keyed as one section whatever it holds (each of
its chunks by its slot, so a shape chosen or the fold composes nothing afresh but the rows that come back), and **cut
between its rows of diagrams like any section**: it is drawn as slots (`chordSlotCount`, up to `MAX_SONG_CHORDS`, 48),
the first headed by the pill, each holding the row of cells its index names at the width it is measured at
(`chordRowStarts`, `chordSlotRows`, `ChordRows.kt`, tested) and the last every row left, the cells drawn rather than
composed (`ChordCellLayouts`: names measured by the page's measurer, diagrams by `drawChordDiagram`), so that an
intrinsic measurement at a candidate width already counts the rows they wrap into, every slot answering for the whole
section's width; a screen reader reads it row by row.

It is a pill folding it (`UserPreferences.isChordSectionFolded`, one fold for every song, in read only mode too) and
rows of cells — the name in the chords' style and accent over its diagram, growing with the text size, wrapping rather
than scrolling sideways into as few rows as the width allows, shared out between them as evenly as their order lets them
(`balancedRowStarts`: nine of which six fit a line are five over four, not six over three, and the PDF breaks its rows
the same way). **Every cell opens its own chord's sheet when tapped** (`ChordDiagrams.onChordClicked`), outside read
only mode (performance mode, or a song read from an archived setlist): the cells are drawn, so the tap is found by
`chordCellAt` (`ChordRows.kt`, tested; a cell is hit across its row's height, the gaps hit nothing), the cell pressed is
lit behind it once the press has lasted the tap timeout, or briefly on a quicker tap, so a scroll started on a diagram
never lights it (a mouse press lights it at once), a screen reader gets one custom action per cell, and the tap is
consumed so it does not also turn the page — a tap between the cells, or on any of them in read only mode, still does.

Which shape a cell draws is `selectShape` (`ChordSelection.kt`, tested): the song's definition, then the player's stored
shape for the chord's id (`UserPreferences.chordVoicings` under the tuning's `voicingKey`, where it still reads as a
shape of that instrument), then the app's own. A cell whose chord is drawn with another shape than before (one picked in
its sheet, a tuning picked for the instrument) crossfades into it (`ChordShapeCrossfade.kt`, `changedShapeCells`
tested); a shape the search finds arriving into the empty frame, and a cell that is another chord now, do not.
`ChordDiagram` draws a `ChordDiagramGeometry` (`ChordDiagramGeometry.kt`, pure and tested): strings and four
frets or as many as the shape spans, up to 24, the nut or the base fret's number, a dot per stopped string with the root
in the second accent, an open string on the root ringed in it too, barres from the fingering or from the lowest fret
where the shape needs one, `×` and `○` above the nut; a keyboard of two octaves or as many as its keys reach, up to
four, a key past them left out, white keys the lighter of the two theme colours in either theme, every pressed key
filled whole — the root in the second accent, the rest (a slash chord's bass included) in a shade of it moved away from
the white keys (deeper in the dark theme, paler in the light one).

The **chord sheet** (`ChordShapeSheet`, `DialogType.ChordShape`, closed with the song; its pager, `ChordShapePager.kt`,
is shared with the Chords tab's sheet, see `ui/screens/chords/CLAUDE.md`) is titled "Choose chord shape -
G", the label of the screen reader action that opens it, and reads the song as the page plays it, finding the chord by
its name (gone, it closes): the chord larger, with its
fingers and notes, its shapes the pages of a `HorizontalPager` through `ChordVoicings.all` (searched off the main
thread, the pages decided once they are there, a stored shape the app does not list first) and a `Stepper` under it
reading the page and turning it, for a mouse, a keyboard, and a screen reader. The page it settles on is written at once
by `setChordVoicing` (settling on the app's first shape removes the entry), never highlighted and with no reset, since
which shape is the default means nothing to the player; a chord the song defines has one page and "Defined in this
song" (and by how many frets it was moved) in the stepper's place. The **editor**'s first toolbar row
ends in Chord shape (`chordShapeInsertion`, tested), which writes a `{define}` for the chord at the caret into the
header, filled in with the shape the player would be shown and its frets selected, or sends the caret to the frets of
the line already there; the preview's Chords section holds every definition of the text and nothing else, each on its
own instrument, whatever the switches say.

## Which shape a chord is drawn with

**The instrument and the tuning the diagrams are drawn for are the two chips heading the song's first section**
(`SongInstrumentChips`, the `InstrumentChips` as tall as a section's pill, `ChordDiagrams.onInstrumentClicked` and
`onTuningClicked`, read only mode included; see `ui/CLAUDE.md`), above the transposition and the capo, which are as
much about the whole song, and for every song with chords, so an instrument without shapes, which leaves no Chords
section, keeps them to bring the diagrams back (`RenderSection.Metadata.instrumentChips`, `withChordsSection`'s
`hasChords`, `SongLyricsModel.hasChords`). The section's content holds what the chips name (`InstrumentChipsContent`: the
instrument, the tuning the tuning chip shows, and the notation), since how wide their labels are decides how many lines
they take and a section's measured size is kept by its content; a leaving tuning chip is named until it has faded out
(`SongInstrumentChips`' `tuningChipVisibility`), since it is laid out until then. **Every chip reaches out of its column as a section's pill does**, so that
one starting a line has its name on the keyline the lyrics, the section titles, and the controls' labels stand on, and
a chip that does not fit beside the other goes under it whole rather than being cut short.

### How every chord of a song is fingered is shown at its top

**How every chord of a song is fingered is shown at its top**, on the guitar, the ukulele, the mandolin, the banjo, or the keyboard (the instrument
in the player's hands, `UserPreferences.instrument`; every other instrument — a seven- or eight-string guitar, the
basses, the violin, Other — draws no diagram at all, as the Features switch does, and the editor's Chord shape button
writes a guitar's): a Chords section after the controls of how it is played, one diagram per chord in the order they are
first played, folded by one preference for every song and cut between its rows of diagrams wherever a page ends inside
it, like any other section. Nothing is shipped for it and nothing is fetched: `:chordpro` reads what notes a chord name
stands for (`ChordProChords`) and finds its shapes (`ChordVoicings`) — a hand-typed table of the shapes everybody knows
first, a search for every other one after it. Which shape a chord is drawn with is, in order, the song's own `{define}`
(or `{chord}`) for that instrument, which ChordPro has for "in this song the G is played this way" and which travels
with the file; the player's own choice from the chord's sheet, **one per chord and instrument for the whole
library**, since which F somebody plays is a habit of their hands rather than a reading of one song, stored as the shape
rather than its number and keyed by the chord's notes so every spelling shares it (`UserPreferences.chordVoicings`,
synced, see `data/sync/implementation/CLAUDE.md`); and the app's first shape.

A definition for another instrument is not used on the page, nor translated: a guitar shape's fingers make another chord
on a ukulele, and a keyboard plays the chord's own notes. Four frets with no selector are the ukulele's, so the
mandolin's definitions name it (`{define-mandolin}`, which the editor's Chord shape button writes); five frets are the
banjo's, its short fifth string first and only ever open or muted. A definition follows its chord through every transposition —
the reader's, the file's `{transpose}` and the editor's transpose action, which rewrites the line in place — along the
neck, in whichever octave a hand can hold it, and one a transposition left needing a fifth finger gives way to the
player's shape — the keyboard's capo, counted into the transposition there, included. The editor writes definitions (its Chord
shape button), marks one it cannot read and draws every one in its preview. The PDF prints them under each song's
heading where the export screen's Chord diagrams box is ticked (as every option is for a new user; offered only while
the feature is on and a song has a chord, and only with the chords printed), drawn as the song details screen draws
them, their names left out of the file's selectable text.

## Tunings

### The diagrams are drawn for the tuning the player has

**The diagrams are drawn for the tuning the player has**: the tuning picked for the instrument in the tuning sheet, its
tunings named the way the tuner names them (`ChordTuningLabels.kt`: a `ChordTuning` is the tuner's `InstrumentTuning` of the same id, which
`ui/instrument/InstrumentsTest` holds it to, strings included). One tuning per instrument (`UserPreferences.tunings`,
written by `withTuning`, which picks the tuning's instrument too, read by `tuningOf` and `chordTuning()` in
`ui/instrument/Instruments.kt`: an id of another instrument or one this version does not know is standard), kept on the
device like the instrument, and the tuner's too. Every tuning the tuner knows for an instrument with shapes is one the
diagrams know (`Instrument.tunings`): half and full step down, drop D to drop B, double drop D, open G, D, E, A, and C,
and DADGAD on the guitar, the ukulele's low G, D, and baritone tunings, the mandolin's half step down and its AEAE and
GDGD cross tunings, and the banjo's double C, C, open D, and G modal; what the shapes are in each is `:chordpro`'s
`ChordTuning`.

Everything that drew for an instrument draws for a tuning: `ChordDiagrams.tuning`, `SongLyricsInputs.chordTuning`, the
cells, the sheet, the PDF and the onboarding's G; the page keeps the cells it has until the song's chords have been
collected for the new tuning, away from the main thread, so a tuning picked crossfades once, from the old shapes to the
new, and never through the old shapes drawn on the new strings. A chord name always names the chord that sounds, so a
half step down an A is drawn `x 1 3 3 3 1`, the shape that sounds A on those strings, and the root dots are read against
the tuning's own strings. The Chords section's chip names a tuning that is not the standard one after its instrument
("Guitar · Drop D"), the chord sheet says it under the title ("Tuning: Drop D"), and the PDF prints a line above every
song's diagrams naming the instrument where it is not the guitar (the twelve-string is drawn as one) or the keyboard,
and the tuning where it is not the standard one ("Mandolin · Tuning: GDGD", "Tuning: Drop D"), so a sheet read by
somebody who did not set it is not read for the wrong instrument or strings.

### A song's own definition is a standard tuning shape

**A song's own definition is a standard tuning shape**: ChordPro gives a `{define}` no tuning, and nearly every file is
written for standard tuning, so a definition is drawn only where a standard tuning shape sounds what it says, standard
tuning and the ukulele's low G (`ChordTuning.drawsDefinitions`) and is otherwise kept as `SongChord.undrawnDefinition`: the cell draws the player's
or the app's shape, and the chord's sheet says "Defined in this song for standard tuning" under it with a button that
shows the definition, named as standard tuning, in the pager's place, and back. The player's choices made in such a
tuning are kept under the tuning's own key and never turn up in standard tuning. The editor's Chord shape button writes
the standard tuning shape in every tuning (the player's choice under the instrument's key, else the app's first), since
every program reads a definition that way, and where the diagrams are in a tuning that does not draw definitions the
snackbar says it was written for standard tuning (`TextInsertion.isForStandardTuning`,
`Message.ChordShapeWrittenForStandardTuning`).
