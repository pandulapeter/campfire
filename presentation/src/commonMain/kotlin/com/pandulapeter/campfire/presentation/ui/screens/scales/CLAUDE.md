<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens/scales

The Scales tab: every scale of `chordpro/scales` in one key, browsed by family, on the player's instrument. A top level
destination (`CampfireDestination.Scales`, `/scales` on the web), the fourth tool behind the Tools item, following its
own Features switch (`areScalesEnabled`), not the chord ones. Offered in read only mode: it writes no file.

- One lazy grid: the controls (instrument and tuning chips, the shared app-wide preference written at once; the key
  row as the Chords tab's, `libraryKeys`, beside the chips as there; the family row, Common first, one line scrolling
  sideways, its scroll kept in `ScaleLibraryState`, with no title, since the heading names the family), a heading, a
  cell per scale, titled in the key ("C Dorian") as the Chords tab names each chord in the key. The controls scroll
  away, so a phone on its side keeps a row of cells. A cell's description is set on its surface and its content's
  semantics cleared, never the other way round, or the click action and the button role go with them.
- **Every stringed instrument draws its scales**, since a neck needs only the open strings (`InstrumentTuning.strings`):
  the basses, the longer guitars, and the violin too, where Chords draws nothing. The keyboard and Other draw a
  keyboard, Other with a line saying so.
- Key, family, scroll position, and the sheet's Degrees / Notes choice are session state (`ScaleLibraryState`, in the
  view model); nothing is saved or synced. The key is the Chords tab's one state.
- Everything is computed in composition (no dispatcher): the first frame is the page. A tuning or instrument
  change crossfades each cell's diagram.
- The search (the Chords tab's pinned field, `pinnedSearchField`) finds scales by their name in the app's language,
  their English name, or an alias in either language, ignoring case and accents, across every family
  (`scaleLibraryResults`, tested); picking a family empties it. **Aliases are translated** (`scale_aliases_<id>`, one
  comma-separated string per scale, read by `scaleAliases`); the catalogue's English ones stay as search terms. Ctrl / Cmd + F, Escape, and Back work as on the Chords tab (`ToolSearch`).
- **The name strings hold the form a title uses** ("C major", "D Dorian", "C-dúr", the Hungarian title hyphenated), and
  a name standing alone is capitalized in code (`standaloneScaleName`). Hungarian steps are "1 1 ½", not letters that
  read as notes.
