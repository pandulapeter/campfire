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

What the Tuner tab (`ui/screens/tuner/TunerScreen`) and the song details screen's tuner sheet
(`dialogs/instrument/TunerSheet`) share.

## Lifecycle

- `TunerController` holds the settings (`UserPreferences.tunerSettings`, a `DebouncedPreference`), listening, tones, and
  `hasTurnedOnMicrophone` (device-only, never exported or synced). It listens for `tunerConfig`: the player's instrument
  and tuning (`ui/instrument/`; the keyboard and Other read every note) plus the reference pitch, and follows changes made on any screen.
- **Nothing of the tuner outlives its screen**: the view model stops it whenever the top of the back stack changes
  (closing the sheet, which belongs to the song under it), whenever the tuner sheet stops being the visible dialog, and
  on exit (`AppExitController`). `TunerListeningEffect` stops and restarts it with the screen's lifecycle (app hidden,
  screen locked, required update drawn over), so the system's recording indicator is lit exactly while a tuner shows.
- The scale sheet's play toggle (`toggleScale`) steps through its notes as tones, each replacing the last; it ends with
  its sheet (`stopScale` on any dialog change away from it) and with `stopTuner`; a note the platform refuses ends the
  run (`refusedTones`).

## Microphone permission

`ui/platform/MicrophonePermission.kt`: Android, iOS, and the web re-read on every resume (the web also at app start,
`CampfireWebApp`, and on the browser's change events); the desktop is always unknown.

- **Nothing asks at launch.** A tap that opens a tuner (the Tuner item, the song's tuner button or menu entry) marks the
  opening (`TunerController.markOpenedByTap`, only where the tuner did open), and the page consumes the mark and, if the
  notice is the never-asked question, presses its button for the player (`TunerAskOnOpenEffect`).
- A tuner shown without a tap (restored, the web's address or Back) waits for the button: an unrequested system prompt
  is easily refused, and iOS and Android only ask so often.
- Where the platform cannot say (desktop), the first ask is remembered and later openings listen without a tap; a later
  refusal is heard as silence and shown with Open settings.
- `TunerNotice.kt` (pure, tested): which notice replaces the display (`tunerNoticeOf`: a stop reason, then a refusal,
  then a never-asked question) and `canListenWithoutTap`. `TunerNoticeCard` and `TunerInputIssueMessage` are polite live
  regions, since both arrive without a tap.
- On Windows the silent line names the system's audio enhancements, which remove a held note as noise and which Java
  Sound cannot switch off, and offers Open sound settings.

## Display

- The note is sized to the display's width but **no taller on the tab than in the sheet**, since what scrolls under the
  pinned display is what the tab has left.
- The cents meter is a scale of ticks, not a track with a knob (which reads as a slider and gets reached for). In tune
  is shown by the notch closing into a check, never by colour alone.
- The reading crossfades only when its kind changes (`tunerReadingKindOf`, tested); numbers change in place.
- The prompt is laid out over an invisible note so the label keeps one height.
- **Accessibility**: one node explored with the exact reading; a second announces only the note and a coarse step
  (`TunerAnnouncement.kt`, tested) after it held 700 ms, since a reading every 33 ms would queue speech the microphone
  hears.

## Strings and settings

- `TunedStrings.kt` (pure, tested): notes heard in tune since the microphone opened, ticked off in the strings row;
  forgotten when the microphone closes or the instrument changes.
- `TunerStrings`: one segmented row (read as buttons, not radio buttons), names shrinking to fit eight strings on a
  phone. Paired-string instruments (`Instrument.courses`: mandolin, twelve-string) get a second row of partners; a
  unison pair is one note to the tuner, so its segments act together. None for the keyboard and Other. Another instrument's
  strings crossfade in while their room resizes unclipped; a row leaving is drawn as it was but plays nothing
  (`isStringOf`).
- **The tab alone** offers the instrument and tuning pill and the reference pitch. Both offer the Strings / Chromatic
  toggle (`TunerModeToggle`, `TunerSettings.mode`, stored as `mode`: an older document's `isChromatic` is a dropped
  switch's, and ignored), extra small: in the sheet's header, and on the tab under the play circle, its end at the
  circle's. Chromatic reads every note and dims the pill without changing the instrument, which the chord diagrams and
  scales share. The toggle is hidden for the keyboard and Other, whose pill is never dimmed; on the tab it only fades,
  keeping its line, so the display never moves. While the microphone is not allowed (`TunerNotice.isMissingPermission`)
  it fades to disabled, and the pill is not dimmed, since the mode only changes what is heard. The refusal notice
  points to the strings' tones only where there are strings.
- **A tuning's note follows the chord notation, not the language** (`TuningName.kt`, pure, tested): names hold a MIDI
  note, so Drop B is Drop H in German and Drop Si in Latin. `NoteNames.kt` (tested) does the same for notes, with a
  spoken form for screen readers (`spokenNoteName`).

## Layout

The tab pins the tools' instrument row (`ui/instrument/ToolInstrumentSetting`, as the Chords and Scales tabs start)
and the mode toggle under it: the instrument pill at the start of a line of the list bar's height whose end holds the
reference pitch, its stepper pill and a filled 48dp play circle exactly where the list screens' pill and New button
stand (`NEW_ITEM_BUTTON_*`); where the pill does not fit whole beside them (any 360 dp phone, the controls taking
214 dp; large text), the pill keeps the line and they slide under it, still at the end, so the mode toggle stays under
the play circle; the line grows with them and only on width, never on the keyboard (`ui/instrument/ToolInstrumentLines.kt`).
No label.
The display follows, above a `SettingsPage` that is all header, as wide as the display (`headerColumns`): the notice,
the issue line, and the strings, untitled but with their hint, centered under the display (the sheet keeps their
title). It keeps the screen on while it hears. The gaps are the tools' `TOOL_GROUP_GAP`. The sheet scrolls everything
under its header: the same display at the same size (compact only in a short window), then the issue line and strings.
