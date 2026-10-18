<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Tint the pulsing metronome mark without a new color filter on every frame

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** Android mostly
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomeIcon.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/CLAUDE.md`

## Problem

`MetronomeIcon.kt`: `val colorFilter = { ColorFilter.tint(lerp(tint, pulseColor, beat.pulse.coerceIn(0f, 1f))) }`, invoked in both
layers' `drawBehind`. While a click plays the pulse animates on every beat, so each frame allocates two `ColorFilter`s (native
`PorterDuffColorFilter` on Android) per mark (the navigation item and the song's metronome button).

## Fix

Remember two fixed filters (tint, pulse color); draw the painter with the tint filter, then again with the pulse filter at
`alpha = pulse`. That is not pixel-identical to `lerp` at anti-aliased edges, so compare by eye and keep the old code if it
differs visibly. Alternative: cache the last filter and rebuild only when the lerped color moves by a visible step.

## Tests

None; check the beat pulse by eye on the navigation item and the song's button, in light and dark themes.
