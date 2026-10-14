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

The Chords tab: every chord the app can draw, browsed by instrument, tuning, key, and name. A top level destination
(`CampfireDestination.Chords`, `/chords` on the web) and one of the tools behind the Tools item (see `ui/CLAUDE.md`);
follows `FeatureSwitches.isChordLibraryEnabled` (the Chord diagrams switch, disabled by the Chords switch).

## What it shows

- One lazy grid whose first item is the controls (`ChordLibraryControls`), so they scroll away and a phone on its side
  keeps a row of diagrams above the keyboard. The search field never takes focus on its own; once focused with the
  keyboard up it is scrolled to the top once per focus.
- **The instrument and tuning chips are the app-wide preference** (the one the songs' diagrams and the tuner follow),
  written at once. The key, search, last results, and scroll position are session state (`ChordLibraryState`, in the
  view model, since the tab leaves the back stack whenever another is picked).
- Keys follow the reader's accidentals preference (`chordLibraryKeys`) and notation (a numbering by its letters). A key
  lists `CHORD_LIBRARY_QUALITIES`, omitting any the instrument has no shape for (`chordLibraryResults`,
  `ChordLibrary.kt`, tested).
- A search that names a chord (in the reader's notation) shows that chord alone whatever key is picked, named
  canonically (`ChordProNotation.canonicalName`: `c7` is `Cm7`); any other text narrows the key's chords by name
  (`sus`, `m7`). An instrument without shapes shows a line pointing at the chip.

## Cells and the sheet

- `ChordLibraryCell` is **drawn with the shape the songs draw it with** (`selectShape`: player's choice in that tuning,
  then the app's first), crossfading on change.
- Shapes are looked up off the main thread, except where the tables answer every chord on screen
  (`searchesShapes = false`) or the session's kept results are shown, so the tab's first frame is already the page.
  After a tuning or instrument change, a key that needs the search keeps the old chords, drawn for the tuning they were
  found for, until answered (`ChordLibraryShown`).
- A tap opens `ChordReferenceSheet` (`DialogType.ChordReference`), the song chord sheet's grid without a song. **A
  shape picked there is written at once as the player's shape for that chord**, changing it in every song (synced).
  Offered in read only mode, since it writes no file.
