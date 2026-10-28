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

- **Two Shortcuts rows** (`EditorToolbar.kt`): a button or menu entry for every directive `ChordProParser` understands
  and the app shows, the two rows about as long as each other. Second row: what goes into nearly every line: Chord,
  Annotation, Chord shape; Bold, Italic, and Underline as icons (labelled for screen readers); the sections in song
  order, Repeat chorus after Chorus. First row: what a song needs now and then: Title, Artist, **More details** (the
  credits, album, year, duration, cover); Key, Capo, Key change, Tempo, Time signature; Tag, Language, Link; Tab, Grid,
  **Sheet music** (an `abc` block, named like the reader's section, not for its notation), and a **Comment** menu (plain,
  italic, boxed). What is reached for less often goes into a menu rather than lengthen its row.
- Bold, Italic, and Underline toggle markup like their shortcuts, see the editor's own notes.

### Shortcut rules

- Toolbar buttons and menus (`EditorToolbarMenuButton`) **take no focus**, or the keyboard closes on every tap and the
  insertion lands where the caret used to be. The rows scroll horizontally rather than wrap.
- A write that leaves a value to type (`leavesValueToType`: a new selection, or the caret inside a directive's braces or
  a chord's brackets) brings the keyboard up and scrolls to it (`TypingRequest`), even if the user had put it away;
  sections, tabs, Repeat chorus, wrapping a selection, and the formatting buttons never do.
- Everything in braces goes on a line of its own (the only way ChordPro reads a directive).
- Deliberately absent: `{new_song}` (one song per editor) and `{new_page}` / `{column_break}` (Campfire flows sections
  into columns itself). `{transpose}` is offered only as **Key change**, written at the caret, never in the header: one
  at the top would silently shift every chord away from what the file spells.
- Header directives don't write at the caret: `ChordProHeader` picks the line (in the row's order, not moving the user's
  own arrangement) and the caret follows. **No button is ever disabled**: one for a directive the song may hold once
  that the file already carries (in any spelling, anywhere) selects that line's value instead (`ChordProHeader.write`);
  Tag, Language, and Link add another. A selection is *copied* in as the value (a line of lyrics or a pasted heading
  stays part of the song), one line, no braces. Tempo and Time signature go into the header while it has none of their
  kind, otherwise at the caret's line as a change (`ChordProHeader.insertChangeable`).
- Tab asks what the tab is for (Any instrument, or one with frets → `{start_of_tab-<id>}`, closed by plain
  `{end_of_tab}`) and writes one empty bar named by `emptyTabStaffFor`: that instrument's strings in standard tuning
  (Any: the player's instrument's, a guitar's for a fretless one), whatever the player's own tuning. With a selection
  it only wraps it (likely a pasted tab).
- With nothing selected, Sheet music (`sheetMusicHeader`) writes `X:1`, the song's meter, `L:1/8`, and its key rebuilt
  as root, minor, or mode in standard letters (abc2svg draws no tune without `X:`, fails on `K:H`, and reads
  `K:A-moll` as A major), the caret below; with a selection it only wraps it.
- Chord shape (`chordShapeInsertion`) sits with the chords, though it writes into the header, and asks which
  instrument (`chordShapeInstruments`: every one with shapes, no Any, the twelve-string a guitar). It writes a `{define}` for the chord at the caret into the
  header, **always the standard-tuning shape** (ChordPro gives a definition no tuning): the player's choice for that
  instrument, else the app's first, frets selected; or it moves the caret to an existing line's frets. With the caret in
  no chord it writes a placeholder C, name selected (a line to edit teaches the syntax), or selects a C already defined
  for that instrument. The preview's Chords section shows every definition, each on its own instrument, whatever the
  switches.

### Folding

- Windows whose shorter side is under 600dp open with the Shortcuts folded (shorter side, not width class: a phone on
  its side is "wide" but has the least height). On small screens and short windows they fold once when the keyboard
  appears; closing the keyboard does not restore them. The chevron stays reachable while typing.
- Over a preview the chevron is disabled, not hidden, so the segments keep their width. Above compact width it is
  labelled "Shortcuts" (a noun, so the width doesn't change as it turns).
