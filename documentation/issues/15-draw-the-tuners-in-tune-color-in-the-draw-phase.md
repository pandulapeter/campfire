<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Draw the tuner's in-tune color in the draw phase

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerDisplay.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerMeter.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`

## Problem

`TunerDisplay.kt`: `val accent = animateColorAsState(...).value` and `TunerMeter.kt`: `val notchColor = animateColorAsState(...).value`
are read in composition. The display already recomposes at ~30 Hz with the readings; each crossing in or out of tune adds a
recomposition of the whole display on every frame of the color spring (~300 ms), constantly while hovering at pitch. The
meter's `Canvas` has no layer of its own either, so each needle frame re-records the enclosing layer with the 96sp note and
the reading text.

## Fix

Meter: read `notchColor` inside the Canvas (pass the `State`) and give the Canvas `.graphicsLayer {}`. Note label and reading
text: pass a `ColorProducer` through `BasicText` (or tint in draw), which changes the `Text` call shape — keep the font, style
and semantics identical. Also replace `checkPath.reset()` with `rewind()` and remember the `Stroke`.

## Tests

`TunerNotice`, `tunerReadingKindOf` and announcement tests are unaffected. Check by eye that the note, reading and notch turn
to the in-tune color together.
