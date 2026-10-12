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
  sentence a polite live region, as is `TunerInputIssueMessage`, since both arrive without a tap.
- `TunerDisplay` / `TunerMeter` — the note with its octave, sized to the display's width (`TextAutoSize`, 57 to 96sp), and
  no taller on the tab than in the sheet, however wide the tab's page, since what scrolls under the pinned display is
  the room the tab has left; the cents meter: a scale of ticks every five
  cents between the words for its ends, rather than a track with a knob, which reads as a slider and is reached for; the
  in-tune window around the centre tinted with the second accent color; a needle on a spring, gone while nothing is
  heard, and giving way to the notch closing into a check in tune, so it is never said by color alone (the note and the
  reading take the second accent color with it); under the meter the reading in words, the signed cents (`CentsText.kt`,
  `signedCents`: a typographic minus, tested) or "In tune", crossfading only when what it says changes kind
  (`tunerReadingKindOf`, tested) while the number changes in place; and last the frequency heard and the target's. A tone being
  played is named instead and the meter rests. Compact (note beside the rest, in a label of one width so the meter never
  moves) in a short window, on the tab and in the sheet alike. The prompt is laid out over an invisible note, so the label is one height
  either way and nothing under it moves as a note comes and goes. Explored as one node with the exact reading; a second
  node announces only the note and a coarse step (far flat, flat, in tune, sharp, far sharp, `TunerAnnouncement.kt`, tested) once it has
  held for 700 ms, since a reading every 33 ms would be a queue of speech the microphone hears too. With nothing heard it
  is read as the prompt, and while a tone plays as that tone. It reads the tuner's state itself (a `State`), so the
  readings, thirty a second, recompose the display and nothing around it; the screens pass the rest of the page only
  what changes with a note or a setting.
- `TunedStrings.kt` — pure, tested: the notes heard in tune since the microphone was opened (`tunedNotesAfter`), which
  `TunerController.tunedNotes` keeps and the strings row ticks off, so the player sees which strings are still to do;
  forgotten as the microphone closes, since a string in tune when the instrument was put down is not known to be when it
  is picked up again, and as the instrument changes.
- `TunerInputIssueMessage` — the input issue line (`SILENT`, with Open settings, and `WAITING_FOR_GESTURE`), on both.
  On Windows the silent line also names the system's audio enhancements, which remove a held note as noise and which
  Java Sound cannot switch off, and offers Open sound settings (`MicrophonePermission.openSoundSettings`).
- `TunerStringsSetting` / `TunerStrings` — on both: one segmented row, a segment per string, so the strings read as one
  instrument: the tone's or the heard one selected, a tap toggling its tone, the sounding one carrying a speaker (what it is
  doing, where a stop square read as a media control) and a tuned one a check; read as buttons rather than the radio buttons Material makes segments; the names shrinking to fit
  eight strings on a phone. Gone in chromatic mode.
- `InstrumentChoice` — **the tab's alone**: two settings of chips, like the Metronome tab's time signatures. Instrument:
  Chromatic first, then every instrument, each string count one of its own (`TunerInstrument`: guitar, seven- and
  eight-string guitar, bass, five-string bass, ukulele, banjo, violin, mandolin); a chip picks its standard tuning. Every
  guitar and bass, whatever its strings, offers a half and a full step down.
  Tuning: that instrument's tunings, named without the instrument picked right above them (`TuningLabels.kt`), there only
  for an instrument with more than one, the chips crossfading as one such instrument follows another. **A tuning's note
  follows the chord notation, not the language** (`TuningName.kt`, pure, tested): the name holds a MIDI note rather than
  a letter, so Drop B is Drop H in German and Drop Si in Latin, and DADGAD and the fiddle's cross tunings are spelled
  from their strings (`Re La Re Sol La Re`). The reference tone button says its note the same way.
  `TunerInstrument.kt` (`instrumentOf`; every preset under exactly one instrument, its standard tuning first, tested).
- `ReferencePitchSetting` — **the tab's alone**: a `Stepper` reset by a tap on its value, and a play / stop toggle
  button sounding A4.
- **The tab's layout** follows the Metronome tab's: the display pinned and as wide as a page of two sections, and on a
  window with room for two columns the notice, the issue line and the strings as the page's `header`, spanning both,
  with the instrument and its tuning in the first column and the reference pitch on a card in the second. A narrower
  window keeps all of it in one column, in that order.
- **The sheet offers neither the instrument nor the reference pitch**, as the song details' metronome panel plays with
  the Metronome tab's presets without offering them: it tunes to what the tab was set to, and holds the display, the
  issue line and the strings.
- `NoteNames.kt` — a MIDI note in the reader's chord notation with sharps (`H` in German, `La` in Latin, letters for a
  numbering) and its octave in scientific numbering, and, for screen readers, with the sharp spoken (`spokenNoteName`: "C
  sharp", Hungarian "Cisz", and "Do kereszt" for the syllables). Tested.
- `TunerButton` — the song details screen's way in: a button of the song's first section after the transposition and
  the capo (see `screens/songDetails/CLAUDE.md`), in read only mode too, since nothing about it writes a file;
  `tunerAction`, an entry of the overflow menu instead, only where that section is not there (the chords and the
  metronome both switched off).

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
(the display giving way to it), then the issue line, the strings, the instrument, its tuning, and the reference pitch;
it keeps the screen on while it hears, and scrolls to the top on a second tap of its item.
The sheet is laid out like the tab, scrolling all it has under its header: the same display at the same size (compact
in a short window only), collapsing for the notice row under it, then the issue line and the strings.
