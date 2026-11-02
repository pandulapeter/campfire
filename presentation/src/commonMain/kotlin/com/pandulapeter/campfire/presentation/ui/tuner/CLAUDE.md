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

What the Tuner tab and song details' tuner sheet share.

### Lifecycle

- Listens for the player's instrument and tuning (the keyboard and Other read every note) plus the reference pitch,
  following changes made on any screen. `hasTurnedOnMicrophone` is device-only, never exported or synced.
- **Nothing of the tuner outlives its screen**: the view model stops it whenever the top of the back stack changes,
  whenever the tuner sheet stops being the visible dialog, and on exit; `TunerListeningEffect` follows the screen's
  lifecycle (app hidden, screen locked, required update drawn over), so the recording indicator is lit exactly while a
  tuner shows.
- The scale sheet's play steps through its notes as tones, each replacing the last; it ends with its sheet and with
  `stopTuner`; a note the platform refuses ends the run.

### Microphone permission

- Android, iOS, and the web re-read it on every resume (the web also at start and on the browser's change events); the
  desktop is always unknown.
- **Nothing asks at launch.** A tap that opens a tuner marks the opening (only where it did open), and the page consumes
  the mark and, if the notice is the never-asked question, presses its button for the player.
- A tuner shown without a tap (restored, the web's address or Back) waits for the button: an unrequested system prompt
  is easily refused, and iOS and Android only ask so often.
- Where the platform cannot say (desktop), the first ask is remembered and later openings listen without a tap; a later
  refusal is heard as silence and shown with Open settings.
- Notice order (`tunerNoticeOf`, tested): a stop reason, then a refusal, then a never-asked question. Notices are polite
  live regions, since they arrive without a tap.
- On Windows the silent line names the system's audio enhancements, which remove a held note as noise and which Java
  Sound cannot switch off, and offers Open sound settings.

### Display

- The note is sized to the width but **no taller on the tab than in the sheet**, since what scrolls under the pinned
  display is what the tab has left.
- The cents meter is a scale of ticks, not a track with a knob (which reads as a slider and gets reached for). In tune
  is the notch closing into a check, never colour alone.
- The reading crossfades only when its kind changes; numbers change in place. The prompt is laid out over an invisible
  note so the label keeps one height.
- **Accessibility**: one node explored with the exact reading; a second announces only the note and a coarse step after
  it held 700 ms, since a reading every 33 ms would queue speech the microphone hears.

### Strings and settings

- Notes heard in tune since the microphone opened are ticked off, forgotten when it closes or the instrument changes.
- Strings: one segmented row (read as buttons, not radio buttons), names shrinking to fit eight strings on a phone.
  Paired-string instruments get a second row of partners; a unison pair is one note, so its segments act together. None
  for the keyboard and Other. Another instrument's strings crossfade in while their room resizes unclipped; a row
  leaving is drawn as it was but plays nothing.
- **The tab alone** offers the instrument pill and the reference pitch. Both offer the Strings / Chromatic toggle,
  stored as `mode` (an older document's `isChromatic` is a dropped switch's, ignored). Chromatic dims the pill without
  changing the instrument, which the diagrams and scales share. The toggle is hidden for the keyboard and Other (pill
  never dimmed); on the tab it only fades, keeping its line, so the display never moves. Without microphone permission
  it fades to disabled and the pill is not dimmed, since the mode only changes what is heard. The refusal notice points
  to the strings' tones only where there are strings.
- **A tuning's note follows the chord notation, not the language** (tested): names hold a MIDI note, so Drop B is Drop H
  in German and Drop Si in Latin; notes too, with a spoken form for screen readers.

### Layout

- The tab pins the tools' instrument line: the pill at the start; the reference pitch's stepper and a filled 48dp play
  circle exactly where the list screens' pill and New button stand. Where the pill does not fit whole beside them (any
  360 dp phone; large text) they slide under it, still at the end, so the mode toggle stays under the play circle; the
  line grows only on width, never on the keyboard. No label.
- The display follows, above a `SettingsPage` that is all header, as wide as the display: the notice, the issue line,
  and the strings, untitled but with their hint (the sheet keeps the title). It keeps the screen on while it hears.
- The sheet scrolls everything under its header: the same display at the same size (compact only in a short window).
