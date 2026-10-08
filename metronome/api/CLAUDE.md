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

The contract of the click, and nothing else: depends on nothing but coroutines (for `StateFlow` / `SharedFlow`), like
`:chordpro` depends on nothing, so that no data or domain type leaks into it and the engine reads no settings of its own.
It builds in explicit API mode (`explicitApi()`), so every public declaration says `public` and its type on purpose.

- `Metronome` — the one stateful interface: `playback` (`Stopped(reason?)` or `Playing(pattern, audioIssue?)`),
  `beats` (one `MetronomeBeat` per click, emitted when it is *heard*, subdivisions flagged), `start`, `update` (timing
  changes from the next beat, `restartBar` making it beat one; the sound, volume and accents from the next click),
  `preview` (one click of a sound, mixed into a playing click or on its own), `stop` and `setStartable` (whether a
  screen that can start a click is showing, which only the web's output uses, to listen for the gesture its audio
  needs only there). Every call returns at once and never throws: a start that cannot happen ends in
  `Stopped(reason)`.
- `model/` — `MetronomePattern` (complete: tempo within `BPM_RANGE` 30–300, `TimeSignature`, one `BeatLevel` per
  beat, `Subdivision`, `MetronomeSound`, volume — at 0 the click runs on silently, for the visual beat alone, which is
  why there is no mute of its own; `canSound` says whether a pattern is ever heard and `hasUnmutedBeat` whether it
  plays anything at all, for the app to stop a silent click it can no longer show or vibrate), `TimeSignature` (1–16 beats over 1/2/4/8/16, written and
  parsed as `"7/8"`, with the default accents: one, and every group of three in a compound meter), the enums with the
  stable `id`s a stored setting uses, `MetronomeStopReason` and `MetronomeAudioIssue`. The engine keeps no notion of
  what a click is played for: it is started and stopped by the screen that holds the controls, and there are two.
- `TapTempo` — taps to a tempo: a minute over the median of the last eight intervals, a new series after a two second
  gap, clamped to the range. Pure, tested with a `TestTimeSource`.

**The tempo counts the clicks of the bar**: 6/8 at 120 is six clicks a bar at 120 a minute; the unit only decides the
default accents.
