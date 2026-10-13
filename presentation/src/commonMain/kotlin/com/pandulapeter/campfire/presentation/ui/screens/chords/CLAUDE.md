<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens/chords

The Chords tab: every chord the app can draw, browsed by instrument, tuning, key and name.

### What it shows

`ChordsScreen` is one lazy grid, its first item the controls (`ChordLibraryControls`), which scroll with the chords so
that a phone on its side keeps a row of diagrams above the keyboard: the instrument chip under an Instrument title
and the tuning chip after it (`components/InstrumentChipRow.kt`, opening the sheets every screen opens, see
`ui/CLAUDE.md`), the twelve keys
and a search field (`components/SearchField.kt`, the pickers' own, which never takes the focus on its own). An
instrument without shapes has a line naming it in the chords' place, pointing at the chip ("Bass: no chord diagrams",
Other a line of its own). **The instrument and its tuning are the one the songs' diagrams and the tuner follow**,
written at once, since somebody browsing ukulele chords has a ukulele in hand; the key, the search and the scroll position are the
session's (`ChordLibraryState`, held by the view model, since the tab leaves the back stack whenever another is picked).

The keys are spelled as the reader's accidentals preference asks (`chordLibraryKeys`: as a chart spells them, or all
sharp, or all flat) and named in the reader's notation, a numbering by its letters. Under them a key lists
`CHORD_LIBRARY_QUALITIES` in order, the everyday chords first — each a different set of notes, each among the ones the
tables were filled with — leaving out any the instrument has no shape for (`chordLibraryResults`, `ChordLibrary.kt`,
tested). A search that names a chord in the reader's notation (`F#m7b5`, `Lam`) is that chord alone, whatever key is
picked; any other narrows the key's chords to the names that hold it (`sus`, `m7`); one that does neither, or names a
chord no shape plays on the instrument, says so in the chords' place.

### The cells and the sheet

`ChordLibraryCell` is the chord's name in the chords' accent over its diagram, **drawn with the shape the songs draw it
with** (`selectShape`: the player's choice for the chord in that tuning, then the app's first), crossfading when that
changes. The shapes are looked up off the main thread, except where the tables answer every chord on screen
(`searchesShapes = false`), which is what the tab opens on, so its first frame is already the page; a key that needs
the search keeps the chords on screen until it is answered. A tap opens `ChordReferenceSheet`
(`DialogType.ChordReference`): the song details screen's chord sheet without a song around it, the same pager
(`dialogs/ChordShapePager.kt`), titled the same way and naming a tuning that is not the standard one, **whose page is
written at once as the player's shape for the chord** — so choosing a shape here changes it in every song, synced like
any other choice. It is offered in read only mode too, since it writes no file.

### Where it lives

A top level destination (`CampfireDestination.Chords`, `/chords` on the web), one of the tools
(`CampfireDestination.TopLevel.tools`, with the Metronome and the Tuner): where the navigation bar has no room for every
screen it is a tab behind the bar's Tools item (see the navigation chrome in `ui/CLAUDE.md`). It follows the Chord
diagrams switch in Settings → Features (`FeatureSwitches.isChordLibraryEnabled`), which the Chords switch disables.
