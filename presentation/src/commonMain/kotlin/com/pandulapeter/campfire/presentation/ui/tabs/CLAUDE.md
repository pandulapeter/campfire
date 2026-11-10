<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->


# :presentation — ui/tabs

Tabs follow the instrument; how a tab is moved, and which staffs get a notice, is `chordpro/tabs/CLAUDE.md`'s.

- Of multi-instrument tabs the player sees theirs, falling back (7/8/12-string → guitar, 5-string bass → bass), else
  the guitar's.
- Staff instrument: its selector, else the player's if string counts match, else bass (4–5) / guitar (6–8), unless its
  string names are another instrument's open strings (`A E C G` → ukulele).
- Keyboard and violin hide tabs with a line saying so; Other shows them as written. **No switch** for any of it.
- Details and PDF follow it; the editor's preview and field only rename strings in the reader's notation. The Tab
  button's staff is stored in standard notation on Save.
- Sheet music's tab staves follow the same player (`abcTablature`): its strings and the capo the frets are counted
  from (`tabCapoFor`), names in the reader's notation; the editor's preview keeps each voice's own strings. How abc2svg
  is made to fret them is `libs/abc/implementation/CLAUDE.md`'s.
