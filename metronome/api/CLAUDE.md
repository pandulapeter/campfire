<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :metronome:api

The contract of the click, and nothing else: depends on nothing but coroutines, so no data or domain type leaks in and
the engine reads no settings of its own. Explicit API mode.

- `Metronome`: **every call returns at once and never throws**; a start that cannot happen ends in `Stopped(reason)`.
  `beats` emits when a click is *heard*, not scheduled. `update` applies timing from the next beat (or, with
  `fromNextBar`, after the current bar as beat one of a new count) and sound, volume, and accents from the next click.
  `setStartable` matters only to the web output, which needs a user gesture for audio.
- The engine has no notion of what a click is played for: the screen holding the controls starts and stops it.
- `MetronomePattern`: volume 0 keeps the click running silently for the visual beat, which is why there is no separate
  mute. `canSound` / `hasUnmutedBeat` let the app stop a click it can no longer show, sound, or vibrate.
- Enum `id`s are stored settings; never change them. `TimeSignature` is written and parsed as `"7/8"`.
- **The tempo counts the clicks of the bar**: 6/8 at 120 is six clicks a bar at 120 a minute; the unit only decides the
  default accents (one, and every group of three in a compound meter).
- `TapTempo` is pure and tested with a `TestTimeSource`.
