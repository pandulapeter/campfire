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

Tabs follow the instrument.

- This package builds the `TabPlayer` and its notice text; `SongRenderer.transposedSong` applies them.
- Of multi-instrument tabs the player sees theirs, falling back (7/8/12-string → guitar, 5-string bass → bass), else the
  guitar's. How a tab is moved, and which staffs get a notice, is `chordpro/tabs/CLAUDE.md`'s.
- Staff instrument: its selector, else the player's if string counts match, else bass (4–5) / guitar (6–8) — unless its
  string names are another instrument's open strings (`A E C G` → ukulele).
- Keyboard and violin hide tabs with a line saying so; Other shows them as written. **No switch** for any of it.
- Details screen and PDF follow it; the editor's preview and field only rename strings in the reader's notation. The Tab
  button writes an empty staff in standard tuning (see `screens/songEditor/toolbar/CLAUDE.md`), stored in standard
  notation on Save.
