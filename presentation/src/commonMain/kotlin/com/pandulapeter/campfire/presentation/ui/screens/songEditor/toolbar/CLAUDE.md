<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/screens/songEditor/toolbar

The editor's Shortcuts toolbar and its control row.

### The Shortcuts rows

- **Two Shortcuts rows** (`EditorToolbar.kt`): a button for every directive `ChordProParser` understands and the app
  shows. First row: what the song *is* (header directives, then Tag, Language, Link, then Chord shape); second: the song
  body (chords, sections, Tab and Grid, comments, then Key change, Tempo, and Time signature).

### Shortcut rules

- Toolbar buttons and the Tab menu (`EditorToolbarMenuButton`) **take no focus**, or the keyboard closes on every tap
  and the insertion lands where the caret used to be. The rows scroll horizontally rather than wrap.
- Everything in braces goes on a line of its own (the only way ChordPro reads a directive).
- Deliberately absent: `{new_song}` (one song per editor) and `{new_page}` / `{column_break}` (Campfire flows sections
  into columns itself). `{transpose}` is offered only as **Key change**, written at the caret, never in the header: one
  at the top would silently shift every chord away from what the file spells.
- Header directives don't write at the caret: `ChordProHeader` picks the line (in the row's order, not moving the user's
  own arrangement) and the caret follows. A button is disabled once the file carries what it writes (read from the text
  as typed, so hand-written directives count); Tag and Language stay tappable. Tempo and Time signature go into the
  header while it has none of their kind, otherwise at the caret's line as a change (`ChordProHeader.insertChangeable`).
- Tab asks what the tab is for (Any instrument, or one with frets → `{start_of_tab-<id>}`, closed by plain
  `{end_of_tab}`) and writes one empty bar named by `emptyTabStaffFor` (see `ui/CLAUDE.md`'s Tabs follow the
  instrument). With a selection it only wraps it (likely a pasted tab).
- Chord shape: see `ui/chords/CLAUDE.md`.

### Folding

- Windows whose shorter side is under 600dp open with the Shortcuts folded (shorter side, not width class: a phone on
  its side is "wide" but has the least height). On small screens and short windows they fold once when the keyboard
  appears; closing the keyboard does not restore them. The chevron stays reachable while typing.
- Over a preview the chevron is disabled, not hidden, so the segments keep their width. Above compact width it is
  labelled "Shortcuts" (a noun, so the width doesn't change as it turns).
