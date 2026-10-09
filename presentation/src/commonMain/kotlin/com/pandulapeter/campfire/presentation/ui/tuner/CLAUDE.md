<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/tuner

What the Tuner tab (`ui/screens/tuner/TunerScreen`) and the song details screen's tuner sheet (`dialogs/TunerSheet`)
share.

- `TunerController` (a view model holder) — the settings (`UserPreferences.tunerSettings`, saved through a
  `DebouncedPreference` like the metronome's and written with the others on the way out), listening on the screens'
  say, the tones, and `hasRequestedMicrophone`, whether a tap asked for the microphone in this run. It follows the
  settings: a listening tuner reads against the new ones, a tone ends with its instrument and moves with the reference
  pitch. **Nothing of the tuner outlives its screen**: the view model stops it whenever the top of the back stack
  changes and whenever the tuner sheet stops being the dialog on screen, and `AppExitController` with the view model;
  the screens' `TunerListeningEffect` stops it as they stop (the app out of sight, the screen locked) and starts it
  again as they start, so the system's recording indicator is lit exactly while a tuner shows. The web's
  `setStartable` follows the Tuner tab or the sheet being on top.
- `TunerNotice.kt` — pure: which notice the page shows in place of the display (`tunerNoticeOf`: a reason listening
  stopped wins, then a refusal, then a question never asked — on the platforms that cannot say, one not asked by a tap
  in this run) and whether the microphone may be opened without a tap (`canListenWithoutTap`). Tested.
- `TunerNoticeCard` — one sentence and at most two buttons: **Use the microphone** (the platform's request, or opening
  the input on the desktop and the web, where that is the question), **Open settings** where the platform has a page
  to open and **Ask again** where the system would still ask, **Try again** for a missing, busy or failed microphone.
- `TunerDisplay` / `TunerMeter` — the note with its octave, the cents meter (a marker on a spring, a notch that closes
  into a check in tune, so it is never said by color alone, the second accent color with it), and the frequencies; a
  tone being played is named instead and the meter rests. Compact (note beside meter) in a short window and in the
  sheet. Read out as one live region.
- `TunerOptions` — the input issue line (`SILENT`, with Open settings, and `WAITING_FOR_GESTURE`), `InstrumentChoice`,
  `TunerStrings` (a chip per string, the tone's or the heard one selected, a tap toggling its tone) and
  `ReferencePitchSetting` (a `Stepper` reset by a tap on its value, and a chip playing A4).
- `NoteNames.kt` — a MIDI note in the reader's chord notation with sharps (`H` in German, `La` in Latin, letters for a
  numbering) and its octave in scientific numbering. Tested.
- `tunerAction` — the song details screen's entry: in its overflow menu (let out into the bar where it has room), in
  read only mode too, since nothing about it writes a file.

The microphone permission is `ui/platform/MicrophonePermission.kt`, an `expect` read where a tuner shows: Android's
`checkSelfPermission` and the Activity's launcher, iOS's `recordPermission` and `requestRecordPermission`, each read
again on every resume, with the app's page of the system settings; the web's `navigator.permissions.query`, asked as
the app starts (`CampfireWebApp`) so the tab's first frame knows; the desktop always unknown, with the macOS and
Windows microphone privacy pages. Nothing asks at launch or by opening a tab: only the notice's button.

The tab (`ui/screens/tuner/`) pins the display above a `SettingsPage` whose first row is the notice while there is one
(the display giving way to it), keeps the screen on while it hears, and scrolls to the top on a second tap of its item.
The sheet scrolls all of it under its header, the display first.
