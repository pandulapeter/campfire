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

The Metronome tab. The click's lifecycle is in `ui/metronome/CLAUDE.md`.

- **The tab's `MetronomePanel` is never hidden**, so the click plays as on a song and can be stopped however far the
  page is scrolled. Drawn `isProminent`, its play button sits where the list screens' New button does (under the tools'
  button group too, as the Tuner's play circle), at the window's end whatever the page width cap.
- A beat tap cycles accent, plain, and muted, stored per signature here (a song's panel stores them for that song).
- Tempo: never highlighted and no reset, since it overrides nothing; the Italian marking is notation, not translated.
  Subdivision is clicks per beat (1 to 4), not note names, which depend on the bar and do not fit a phone.
- `MetronomePlaybackSection` (`ui/metronome`, also the Preferences' Metronome page, drawn the same) applies to a song's
  click too, Tempo change (next bar or next beat for a new song or stretch) only to it. Volume zero is the mute (no mute
  switch); Vibrate shows only where there is a vibrator.
- The screen is kept on while a click plays. Every way onto the tab clears the back stack, so no song is behind it.
