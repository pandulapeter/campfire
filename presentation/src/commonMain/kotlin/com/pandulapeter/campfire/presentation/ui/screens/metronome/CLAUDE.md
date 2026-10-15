<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens/metronome

The Metronome tab: the whole instrument. The click's lifecycle is in `ui/metronome/CLAUDE.md`.

- **The song details screen's own `MetronomePanel` is pinned at the top and never hidden** (`isVisible` always true), so
  the click is played the same way on both screens and can be stopped however far the page is scrolled. Drawn
  `isProminent` (56dp row and button) and capped at `PageWidthLayout.pageMaxWidth`.
- The `BeatRow` uses shades of one color (the second accent), since every palette but the app's own has no second accent
  apart from the primary. A tap cycles a beat through accent, plain, and muted, stored per signature here (a song's
  panel stores them for that song alone).
- Under it a `SettingsPage` of two sections scrolls, side by side where there is room:
  - What is played (`MetronomeBarOptions`): why nothing is heard (`audioIssue`); the tempo (`TempoSetting`: the song
    details screen's `TempoStepper`, typed into too, never highlighted and with no reset, since it overrides nothing;
    the Italian marking is not translated, being notation); the time signature; the subdivision as clicks per beat (1
    to 4) rather than note names, which depend on the bar and do not fit a phone.
  - How it reaches the player (`MetronomeSoundOptions`, on a `SettingsCard` because it applies to a song's click too):
    sound, volume (zero is the mute, so there is no mute switch), Animate, and Vibrate (only where
    `rememberBeatHaptics` has a vibrator).
- The screen is kept on while a click plays. Every way onto the tab clears the back stack, so no song is behind it.
- Performance mode leaves the tab fully usable.
