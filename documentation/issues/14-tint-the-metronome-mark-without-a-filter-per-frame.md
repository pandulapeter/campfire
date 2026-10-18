<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Tint the pulsing metronome mark without a new color filter on every frame

**Challenged:** amended — the recommended two-pass draw is not the same color anywhere, not just at the edges:
Compose's `lerp(Color, Color, f)` interpolates in Oklab, while drawing the pulse at `alpha = pulse` over the tint blends in
sRGB. Replaced it with a table of filters at quantized pulse steps, which keeps the Oklab colors and allocates nothing
once filled. The two layers also share one filter per frame.

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** Android mostly
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomeIcon.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/CLAUDE.md`

## Problem

`MetronomeIcon.kt`: `val colorFilter = { ColorFilter.tint(lerp(tint, pulseColor, beat.pulse.coerceIn(0f, 1f))) }`, invoked in both
layers' `drawBehind`. While a click plays the pulse animates on every beat, so each frame allocates two `ColorFilter`s (native
`PorterDuffColorFilter` on Android) per mark (the navigation item and the song's metronome button).

## Fix

Remember a table keyed on the two colors, `remember(tint, pulseColor) { arrayOfNulls<ColorFilter>(PULSE_STEPS + 1) }`,
and in draw take the entry at `(beat.pulse.coerceIn(0f, 1f) * PULSE_STEPS).roundToInt()`, creating it on first use as
`ColorFilter.tint(lerp(tint, pulseColor, step / PULSE_STEPS.toFloat()))`. Both `drawBehind`s read the same entry, so
a frame makes at most one filter, and none once the steps of a pulse have been seen. `PULSE_STEPS = 32` keeps each step
well under a visible color difference, and the scale (`graphicsLayer`) still follows the pulse continuously. The
table is rebuilt only when `tint` or the theme's primary changes (a theme cross-fade, a selected navigation item's
color animation), as the filter is rebuilt now.

Not a second draw at `alpha = pulse`: it blends in sRGB where `lerp` interpolates in Oklab, which changes the mid-pulse
color across the whole mark, and doubles the overlap of anti-aliased edges.

## Tests

None; check the beat pulse by eye on the navigation item and the song's button, in light and dark themes, at a fast
tempo (the pulse is cut short every beat).
