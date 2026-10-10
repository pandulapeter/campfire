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
  `DebouncedPreference` like the metronome's and written with the others on the way out), listening on the screens' say,
  the tones, and `hasTurnedOnMicrophone`, whether a tap has ever asked for the microphone on this device (saved in
  `TunerSettings`, so it is never exported or synced). It follows the settings:
  a listening tuner reads against the new ones, a tone ends with its instrument and moves with the reference pitch.
  **Nothing of the tuner outlives its screen**: the view model stops it whenever the top of the back stack changes -
  closing the sheet with it, since the sheet belongs to the song under it - and whenever the tuner sheet stops being the
  dialog on screen, and `AppExitController` with the view model; the screens' `TunerListeningEffect` stops it as they
  stop (the app out of sight, the screen locked, a required update drawn over the app) and starts it again as they
  start, so the system's recording indicator is lit exactly while a tuner shows. Opening the sheet stops a click playing
  on the song, since the two never hold the audio together. The web's `setStartable` follows the Tuner tab or the sheet
  being on top.
- `TunerNotice.kt` — pure: which notice the page shows in place of the display (`tunerNoticeOf`: a reason listening
  stopped wins, then a refusal, then a question never asked — on the platforms that cannot say, one no tap on this
  device has asked) and whether the microphone may be opened without a tap (`canListenWithoutTap`). Tested.
- `TunerNoticeCard` — one sentence and at most two buttons: **Use the microphone** (the platform's request, or opening
  the input on the desktop and the web, where that is the question), **Open settings** where the platform has a page to
  open and **Ask again** where the system would still ask, **Try again** for a missing, busy or failed microphone; its
  sentence a polite live region, as is the input issue line of `TunerOptions`, since both arrive without a tap.
- `TunerDisplay` / `TunerMeter` — the note with its octave, the cents meter (a marker on a spring, a notch that closes
  into a check in tune, so it is never said by color alone, the second accent color with it), and the frequencies; a
  tone being played is named instead and the meter rests. Compact (note beside meter, in a label of one width so the
  meter never moves) in a short window and in the sheet. Explored as one node with the exact reading; a second node
  announces only the note and a coarse step (far flat, flat, in tune, sharp, far sharp, `TunerAnnouncement.kt`, tested)
  once it has held for 700 ms, since a reading every 33 ms would be a queue of speech the microphone hears too. With
  nothing heard it is read as the prompt, and while a tone plays as that tone. It reads the tuner's state itself (a
  `State`), so the readings, thirty a second, recompose the display and nothing around it; the screens pass the rest of
  the page only what changes with a note or a setting.
- `TunerOptions` — the input issue line (`SILENT`, with Open settings, and `WAITING_FOR_GESTURE`), `InstrumentChoice`,
  `TunerStrings` (a chip per string, the tone's or the heard one selected, a tap toggling its tone) and
  `ReferencePitchSetting` (a `Stepper` reset by a tap on its value, and a chip playing A4).
- `NoteNames.kt` — a MIDI note in the reader's chord notation with sharps (`H` in German, `La` in Latin, letters for a
  numbering) and its octave in scientific numbering, and, for screen readers, with the sharp spoken (`spokenNoteName`: "C
  sharp", Hungarian "Cisz", and "Do kereszt" for the syllables). Tested.
- `tunerAction` — the song details screen's entry: in its overflow menu (let out into the bar where it has room), in
  read only mode too, since nothing about it writes a file.

The microphone permission is `ui/platform/MicrophonePermission.kt`, an `expect` read where a tuner shows: Android's
`checkSelfPermission` and the Activity's launcher, iOS's `recordPermission` and `requestRecordPermission`, each read
again on every resume, with the app's page of the system settings; the web's `navigator.permissions.query`, asked as the
app starts (`CampfireWebApp`) so the tab's first frame knows, and followed through its change events, so an answer given
in the browser's prompt is known by the next frame too; the desktop always unknown, with the macOS and Windows
microphone privacy pages. Nothing asks at launch or by opening a tab: only the notice's button. Where the platform cannot
say (the desktop), the first tap is remembered, and from then on opening a tuner opens the microphone without one, so
it is one tap per device rather than per run; a refusal given later in the system's settings is heard as silence and
said with Open settings, and a permission the system has forgotten is asked again by its own prompt as the tuner opens.

The tab (`ui/screens/tuner/`) pins the display above a `SettingsPage` whose first row is the notice while there is one
(the display giving way to it), keeps the screen on while it hears, and scrolls to the top on a second tap of its item.
The sheet scrolls all of it under its header, the display first.
