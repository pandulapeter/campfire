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

The Chords tab: every chord the app can draw, browsed by instrument, tuning, key, and name. It follows a Features switch
of its own, whatever a song shows.

## What it shows

- The instrument line is the grid's first item, so it scrolls away and a phone on its side keeps a row of diagrams above
  the keyboard. The search under it is sticky (shared with the Scales tab), with the key pill before it: the way to
  every chord the key leaves out, so it stays reachable. The instrument, the key and search line, the heading, and the
  cells are one gap apart, as on the Scales and Tuner tabs. The page's top fade gives way as the search pins.
- The search never takes focus on its own; Ctrl / Cmd + F gives it the caret with its query selected. Focused with the
  keyboard up it is pinned once per focus. Escape and Back empty a filled query before leaving the tab.
- **The instrument and tuning pill is the app-wide preference**, written at once, and so is the capo after it (one with
  the Scales tab's, `ui/instrument`). Under a capo every chord keeps the name it sounds by and is drawn as the shape
  fingered above it (key A at capo 2: A, Bm, D… as G, Am, C shapes; `D/F#` as a C/E shape), crossfading as the capo
  moves (tested); its sheet names `G shape, capo 2`, and a pick there is stored for the shape's chord.
- Key, search, last results, and scroll are session state in the view model, since the tab leaves the back stack
  whenever another is picked; the key is shared with the Scales tab, so the player stays in their key between the two.
- Keys follow the reader's accidentals and notation (a numbering by its letters). A key lists the common qualities,
  omitting any the instrument has no shape for (tested).
- The key's chords stand under a "Common C chords" heading, so nobody takes them for all there are; a search turns it
  into "Chords matching “m7” in C". The placeholder names a slash chord as an example. The search narrows by name,
  ignoring case; a whole chord name the key does not list, in the reader's notation, is that chord too, above the
  heading, in any key and with any bass (`D/F#`, `Emaj7` with C picked), its root read as a capital so `d/f#` is not a
  minor. Names are always the key pill's, whatever case was typed. An instrument without shapes shows a line pointing
  at the chip.

## Cells and the sheet

- A cell is **drawn with the shape the songs draw it with** (the player's choice in that tuning, then the app's first),
  crossfading on change. Its description is set on its surface and its content's semantics cleared, never the other way
  round, or the click action and the button role go with them.
- Shapes are looked up off the main thread, except where the tables answer every chord on screen or the session's kept
  results are shown, so the tab's first frame is already the page. After a tuning or instrument change, a key that needs
  the search keeps the old chords, drawn for the tuning they were found for, until answered.
- A tap opens the song chord sheet's grid without a song. **A shape picked there is written at once as the player's
  shape for that chord**, changing it in every song (synced). Offered in read only mode, since it writes no file.
