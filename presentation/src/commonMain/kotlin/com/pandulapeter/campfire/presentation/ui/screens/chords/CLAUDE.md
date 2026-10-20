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
(`CampfireDestination.Chords`, `/chords` on the web) and one of the tools behind the Tools item (see `ui/chrome/CLAUDE.md`);
follows `FeatureSwitches.isChordLibraryEnabled` (the Chord diagrams switch, disabled by the Chords switch).

## What it shows

- One lazy grid whose first item is the instrument (`ChordLibraryControls`, the tools' shared
  `ToolInstrumentSetting`: the pill alone, where the Tuner tab's stands, the line ending at its bottom), so it scrolls away and a phone on its side
  keeps a row of diagrams above the keyboard. The search under it is a sticky item (`pinnedSearchField`, shared with the
  Scales tab), with the key before it as the editor's transposing pill (`LibraryKeyStepper`, the twelve keys going
  round): the way to every chord the key leaves out, so it stays reachable. The instrument, the key and search line, the
  heading, and the cells are one gap apart (`TOOL_GROUP_GAP`), as on the Scales and the Tuner tabs. The chords fade under
  it, and the page's top fade gives way as it pins. It never takes focus on its own; Ctrl / Cmd + F gives it the caret
  with its query selected (`ToolSearch.focusSearch`). Once focused with the keyboard up it is pinned once per
  focus. Escape and Back empty a filled query before leaving the tab (the screen's back handler; the desktop's window
  handler through `ScreenSearches.closeCurrentSearch`).
- **The instrument and tuning pill is the app-wide preference** (the one the songs' diagrams and the tuner follow),
  written at once. The key, search, last results, and scroll position are session state (`ChordLibraryState`, in the
  view model, since the tab leaves the back stack whenever another is picked); the key is one state with the Scales
  tab's, so the player stays in their key moving between the two.
- Keys follow the reader's accidentals preference (`libraryKeys`) and notation (a numbering by its letters). A key
  lists `CHORD_LIBRARY_QUALITIES`, omitting any the instrument has no shape for (`chordLibraryResults`,
  `ChordLibrary.kt`, tested).
- The key's chords stand under a "Common C chords" heading, so nobody takes them for all there are, which a search
  turns into "Chords matching “m7” in C", as on the Scales tab; the placeholder names a slash chord as an example of
  what the search finds. The search narrows them by name, ignoring case (`sus`, `cmaj`). A search that is a whole chord name the key does not list, in the reader's notation, is that chord too,
  above the heading, in any key and with any bass (`D/F#`, `Emaj7` with C picked), its root read as a capital so
  `d/f#` is not a minor. Names are always the key pill's, whatever case was typed. An instrument without shapes shows a
  line pointing at the chip.

## Cells and the sheet

- `ChordLibraryCell` is **drawn with the shape the songs draw it with** (`selectShape`: player's choice in that tuning,
  then the app's first), crossfading on change. Its description is set on its surface and its content's semantics
  cleared, never the other way round, or the click action and the button role go with them (as on the Scales tab).
- Shapes are looked up off the main thread, except where the tables answer every chord on screen
  (`searchesShapes = false`) or the session's kept results are shown, so the tab's first frame is already the page.
  After a tuning or instrument change, a key that needs the search keeps the old chords, drawn for the tuning they were
  found for, until answered (`ChordLibraryShown`).
- A tap opens `ChordReferenceSheet` (`DialogType.ChordReference`), the song chord sheet's grid without a song. **A
  shape picked there is written at once as the player's shape for that chord**, changing it in every song (synced).
  Offered in read only mode, since it writes no file.
