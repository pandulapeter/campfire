<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/progress

- `WorkProgressBar` is the one progress line for work on the library: a trackless line along the top of the window,
  over everything and every inset, drawn by `CampfireApp`. No screen draws a line of its own under its bar; the import
  screen's Loading and Importing stages keep their own progress text.
- It stands for an import, files read into a review, the selection's edits, and a sync run with files to transfer, in
  that order (`workProgress`, tested); a sync run that only lists never brings it up, and the demo import never shows.
- Work that knows how far it has got fills it (an import only while writing, whose count is the one that never restarts),
  with a faint segment running from the fill's end to the edge so a slow transfer still moves, fading as that room
  shrinks; other work runs the indeterminate line, and the two crossfade. Once shown it stays as long as the work and at
  least 500 ms.
