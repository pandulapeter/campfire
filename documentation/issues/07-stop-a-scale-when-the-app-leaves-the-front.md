<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Stop a playing scale when the app leaves the front, as the tuner's own tones stop

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/scales/ScaleSheet.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/scales/CLAUDE.md`

Independent of plan 05 (that one ends a run from inside `toggleScale`; this one calls `stopScale`, which works the same
either way).

## Problem

The Tuner tab and the tuner sheet stop their tones with the screen's lifecycle: `TunerListeningEffect`'s
`onStopOrDispose { currentOnListeningChanged(false) }` reaches `TunerController.stopTuner()`, which stops listening and
the tone. The scale sheet has nothing of the kind (`ScaleSheet.kt:99-114` at 396acf923 collects `playingScaleNote` and
nothing else); the run is stopped only by a dialog change (`CampfireViewModel`'s
`if (previousDialog is DialogType.Scale && dialogType != previousDialog) tunerController.stopScale()`) and on exit. So
a scale started just before the app goes to the background, or the phone's screen is turned off, plays on out of
sight, and on the web a hidden tab throttles the run's `delay`s to about a second each, so a run of eight or more notes
sounds for 8–16 s from a tab nobody is looking at — the one tone of the app that outlives being seen.

## Fix

In `ScaleSheet`, next to the `playingScaleNote` collection, stop the run when the app stops:

```kotlin
// A scale sounds only while its sheet can be seen, as the tuner's tones do (TunerListeningEffect): out of sight the
// run would play on, and a hidden web tab stretches each of its notes to a second.
LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.tunerController.stopScale() }
```

(`androidx.lifecycle.compose.LifecycleEventEffect`, as `CampfireApp` already uses for ON_STOP; `stopScale` does
nothing when no scale plays.) ON_STOP rather than ON_PAUSE, as for the metronome: a notification shade or Control
Center pulled over the app pauses it with the sheet still in view. The web's composition maps a hidden page to ON_STOP,
which is what `TunerListeningEffect` already relies on there.

`dialogs/scales/CLAUDE.md`, the play toggle bullet: "stops when the sheet closes or is covered" becomes "stops when the
sheet closes or is covered, and when the app leaves the front (ON_STOP)".

## Tests

None: the behaviour is a lifecycle effect, and the UI test harness (`tools/screenshots`, `SceneOwner`) keeps its scene
RESUMED and its `ScriptedTuner` plays no tones, so a test would need both changed for one line of wiring.
