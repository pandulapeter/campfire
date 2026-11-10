<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 56th review sweep: what is left

The other 15 plans landed (`abf024d5c..0ea67646a`). One was skipped at execution:

| # | Plan | Why skipped |
|---|------|-------------|
| 07 | [Draw a system's strokes as few paths](07-draw-a-systems-strokes-as-few-paths.md) | Implemented as written and timed (`drawNotationSystem` onto a CPU `ImageBitmap` at scale 2, 100 warm runs, twice each way): a 32-system tab tune went from 6.23–6.46 ms to 9.03–9.24 ms (about 45% slower) although its stroke entries fell from 1,088 to 64; a 97-system tune was unchanged (11.8–11.9 ms vs 11.9–12.1 ms). Building each `Stroke` once without merging changed nothing either. Reverted; the premise does not hold on desktop. |
