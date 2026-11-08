<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/preferences

How a song is read, played, and clicked, set in one place: `PreferencesContent`, drawn identically as Settings'
Preferences tab and as the Preferences sheet (`dialogs/songDisplay/PreferencesSheet.kt`). **Never a second UI for the
same setting**: a page is one component wherever it appears (the Metronome page is `ui/metronome`'s
`MetronomePlaybackSection`, also the Metronome tab's second section).

## Layout

- Read only mode (`PerformanceSection`, only while the songs are on) above everything, in every context; then a
  Material secondary tab row of pages (lighter than Settings' primary row, flat, no divider); no row for one page.
- Pages: **Song** (layout, the Chords switch, i.e. lyrics only, scale suggestions, section numbers), **Chords**
  (Diagrams: the instrument and tuning, diagrams, degrees; Names: notation, accidentals, chord names with a capo, capo
  keeps the key), **Metronome** (sound, volume, tempo change, visual beat, vibrate). Same order everywhere.
- Pages slide in their tabs' direction and the height follows (`SizeTransform`), so the sheet grows and shrinks.
- The whole content scrolls, in the sheet too: nothing is typed, so nothing needs pinning.

## Contexts (`PreferencesContext`, `preferencesPages`, tested)

- A page that changes nothing on the screen the sheet is opened from is hidden, never disabled: song details has
  every page, the Chords and Scales tabs only Chords; Settings has all. A page whose feature is off goes (Song with the
  songs, Metronome with the metronome); Chords always stays.
- The sheet opens on its first page (Song over a song); Settings keeps its page for the session (`SettingsSession`).
- Choices the rows open (notation, instrument, tuning) stack over the sheet (`DialogStack`), so a pick comes back.

## Rows

- The chord-writing rows are disabled only while nothing names a chord (songs with chords, the Chords or Scales tabs).
  Accidentals stay enabled under a numbering (they still spell its key). Examples are written in the picked notation,
  never in the language. Diagrams and degrees are disabled with the songs or their chords off.
