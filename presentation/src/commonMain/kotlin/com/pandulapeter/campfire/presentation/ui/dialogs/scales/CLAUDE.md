<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/dialogs/scales

- **The scale sheet is the one way a scale is shown, and it depends on no screen**: `DialogType.Scale` carries a scale
  id and a root name (standard notation, spelled as its opener spells it) only; instrument, tuning, notation, and labels
  are read when it composes, so it opens over the Scales tab, either chord sheet, or anything else. An unknown id closes
  it.
- The neck's dots carry degrees or note names (`ScaleLibraryState.labels`, session-wide), flipped by one icon toggle
  in the header, showing the mode it is in; the keyboard carries none, and its header has no toggle. The title
  wraps to two lines (`titleMaxLines`), so a long scale's name is never cut short.
- The chord rows (seven-note scales only) follow Chord diagrams and need an instrument with shapes; shapes come from the
  tables only, never a search during composition. A chord tap opens its `ChordReference` sheet over this one, without
  scales; opened from a chord sheet, this sheet has no chord rows (`showsChords`), so dialogs never stack deeper than two.
- The sheet's neck gives the dots the room: rows about a fret tall (`sheetHeight`), a narrow open column, fret numbers
  sized from the frets; inlays stay faint and small.
