<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Draw the tuner's in-tune color in the draw phase

**Challenged:** amended — the text keeps Material's `Text` (the note's is `autoSize`d, and a `BasicText` rewrite would have
to reproduce `Text`'s style merging): the animated color is handed down as a `State` and read only by the `Text` calls,
which recomposes those alone. The meter is handed the target accent rather than the animated one, which otherwise
recomposes it every frame and has its own notch spring chase a moving target.

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerDisplay.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerMeter.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`

## Problem

`TunerDisplay.kt`: `val accent = animateColorAsState(...).value` and `TunerMeter.kt`: `val notchColor = animateColorAsState(...).value`
are read in composition. The display already recomposes at ~30 Hz with the readings; each crossing in or out of tune adds a
recomposition of the whole display on every frame of the color spring (~300 ms), constantly while hovering at pitch. The
meter's `Canvas` has no layer of its own either, so each needle frame re-records the enclosing layer with the 96sp note and
the reading text.

## Fix

1. `TunerDisplay`: keep `val accent = animateColorAsState(...)` as the `State` (do not read `.value` there), and hand it
   to `TunerNoteLabel` → `TunerNoteName` and `TunerReadingLine`, which read `accent.value` at their `Text` calls. The
   display then recomposes only with the readings (30 Hz, documented in `ui/tuner/CLAUDE.md`), and each frame of the
   spring recomposes those `Text`s only; a color-only change of `Text` invalidates draw, not the note's `autoSize`
   layout. Font, style, `autoSize` and semantics stay as they are.
2. `TunerMeter`: pass `accentColor = LocalSecondAccentColor.current` (the target), not the animated accent, so the
   meter's parameters hold still through the spring; its own `notchColor` spring already eases the notch to it. Keep
   that as the `State` and read `.value` inside the `Canvas` draw lambda, and give the `Canvas` `.graphicsLayer {}` so
   the needle's per-frame redraws (`position`, `needleAlpha`, `check` are already read in draw) re-record only its own
   layer, not the one holding the 96sp note.
3. `checkPath.rewind()` instead of `reset()`; the `Stroke` may be remembered keyed on density, or left (it is a small
   value object).

## Tests

`TunerNotice`, `tunerReadingKindOf` and announcement tests are unaffected. Check by eye that the note, reading and notch turn
to the in-tune color together.
