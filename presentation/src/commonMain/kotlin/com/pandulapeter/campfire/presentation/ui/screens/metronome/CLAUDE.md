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

The Metronome tab.

### The Metronome tab

**The Metronome tab** (`ui/screens/metronome/`) is the third top level destination and the whole instrument: the song
details screen's own `MetronomePanel` pinned at the top and never hidden (`isVisible` always true) — the beat row
(`BeatRow`, every block outlined in `outline` so that it meets 3:1 against the page, resting in fainter shades of the
second accent color and lit from the heard beats in the full one — shades
of one color, since every palette but the app's own has no second accent apart from the primary — a tap cycling a beat
through accent, plain and muted, stored per signature, and a bar that changes length gaining or losing its blocks one
after another at its end while the rest make room) with play and stop at its end — so that the click is played the same
way on both screens and can be stopped wherever the page has been scrolled to, capped at the page's width
(`SettingsWidthLayout.pageMaxWidth`) and drawn `isProminent`: a 56dp row and button at the page's own 16dp margins,
where the song details bar's are 32dp and 48dp.

Under it a `SettingsPage` of two sections scrolls, side by side where the window has room for two columns: what is
played (`MetronomeBarOptions`) — the line saying why nothing is heard where so (`audioIssue`), the tempo
(`TempoSetting`: the song details screen's own `TempoStepper`, Tap segment and all, but never highlighted and with no
reset, since its tempo overrides nothing, next to the word Tempo and the Italian marking, not translated since it is
notation, with a slider across the range under them), the time signature (common ones as chips, and for the rest two
steppers side by side with a slash between them, which reads as the signature and so takes no labels; its description is
the one line saying that the beats of the pinned bar are tapped to accent or mute them) and the subdivision, a
`SegmentedChoice` of the clicks per beat as numbers (1 to 4), since the note value a beat is cut into depends on the bar
and the words for them do not fit a phone side by side — and how it reaches the player (`MetronomeSoundOptions`, on a
`SettingsCard` as the library tab's sync section is, since unlike the first section it is how every click is played, a
song's included): the sound (a tap previews it) as chips, the volume — whose zero is the mute, so there is no switch for
one — and the Animate and Vibrate switches (the latter where `rememberBeatHaptics` has a vibrator). The screen is kept
on while a click plays. Every way onto it clears the back stack, so no song is behind it.
