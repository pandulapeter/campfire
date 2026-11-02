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

The Scales tab: every scale of `chordpro/scales` in one key, on the player's instrument. It follows its own Features
switch (`areScalesEnabled`), not the chord ones, and is offered in read only mode: it writes no file.

- The capo (the Chords tab's, `ui/instrument`'s "The tools' capo") keeps the scales' sounding names; the frets below it
  are veiled on the cells and in the sheet they open. The controls scroll away, so a phone on its side keeps a row of
  cells.
- A cell's description is set on its surface and its content's semantics cleared, never the other way round, or the
  click action and the button role go with them.
- **Every stringed instrument draws its scales** (a neck needs only the open strings), the violin too, where Chords draws
  nothing. The keyboard and Other draw a keyboard, Other with a line saying so.
- Key, family, search, and scroll are session state, nothing saved or synced; the key is shared with the Chords tab.
- Everything is computed in composition (no dispatcher), so the first frame is the page. A tuning or instrument change
  crossfades each cell's diagram.
- Search (tested, `scaleLibraryResults`): the name in the app's language, the English name, or an alias in either,
  ignoring case and accents, across every family; picking a family empties it. A leading note in the reader's notation,
  set apart by a space or a hyphen (`d dorian`, `a-moll`), finds the rest on that note whatever the key; where the rest
  finds nothing, the whole search is a name. **Aliases are translated** (`scale_aliases_<id>`, comma-separated); the
  catalogue's English ones stay as search terms.
- **The name strings hold the form a title uses** ("C major", "C-dúr", Hungarian hyphenated); German names start with a
  capital, the noun first where an adjective qualifies it (`A-Moll harmonisch`); a name standing alone is capitalized in
  code (`standaloneScaleName`). Hungarian steps are "1 1 ½", not letters that read as notes.
