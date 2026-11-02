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

The editor's Shortcuts rows and its control row.

## The Shortcuts rows

- A button or menu entry for every directive `ChordProParser` understands and the app shows, in two rows about as long
  as each other: the second holds what goes into nearly every line (chords, formatting, sections in song order), the
  first what a song needs now and then. What is reached for less often goes into a menu rather than lengthen its row.
  Sheet music is named like the reader's section, not for its notation.
- Buttons and menus **take no focus**, or the keyboard closes on every tap and the insertion lands where the caret used
  to be. The rows scroll sideways rather than wrap.
- A write that leaves a value to type (a new selection, or the caret inside braces or brackets) brings the keyboard up
  and scrolls to it, even if the user had put it away; sections, tabs, Repeat chorus, wrapping a selection, and the
  formatting buttons never do.
- Everything in braces goes on a line of its own (the only way ChordPro reads a directive).
- Deliberately absent: `{new_song}` (one song per editor), `{new_page}` / `{column_break}` (Campfire flows sections into
  columns itself). `{transpose}` is offered only as **Key change**, at the caret, never in the header: one at the top
  would silently shift every chord away from what the file spells.
- Header directives don't write at the caret: `ChordProHeader` picks the line (in the row's order, never moving the
  user's own arrangement). **No button is ever disabled**: one for a directive the file already carries once (any
  spelling, anywhere) selects that line's value; Tag, Language, and Link add another. A selection is *copied* in as the
  value (one line, no braces), since it stays part of the song. Tempo and Time signature go into the header while it has
  none of their kind, else at the caret's line as a change.
- Tab asks what the tab is for (Any, or a fretted instrument → `{start_of_tab-<id>}`, closed by plain `{end_of_tab}`)
  and writes one empty bar in that instrument's standard tuning (Any: the player's instrument's, a guitar's for a
  fretless one), whatever the player's tuning. With a selection it only wraps it.
- Sheet music with nothing selected writes `X:1`, the song's meter, `L:1/8`, and its key as root, minor, or mode in
  standard letters (abc2svg draws nothing without `X:`, fails on `K:H`, and reads `K:A-moll` as A major); with a
  selection it only wraps it.
- Chord shape asks which instrument (every one with shapes, no Any, the twelve-string a guitar) and writes a `{define}`
  into the header, **always the standard-tuning shape** (ChordPro gives a definition no tuning): the player's choice,
  else the app's first, frets selected; or moves to an existing line's frets. With the caret in no chord it writes a
  placeholder C, name selected (a line to edit teaches the syntax). The preview's Chords section shows every definition,
  each on its own instrument, whatever the switches.

## Folding

- Windows whose shorter side is under 600dp open with the Shortcuts folded (shorter side, not width class: a phone on
  its side is "wide" but has the least height). On small screens and short windows they fold once when the keyboard
  appears; closing it does not restore them. The chevron stays reachable while typing.
- Over a preview the chevron is disabled, not hidden, so the segments keep their width. Above compact width it is
  labelled "Shortcuts" (a noun, so its width doesn't change as it turns).
