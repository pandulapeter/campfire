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

How the app looks and a song is read, played, and clicked: four pages, each drawn identically as a Settings tab of its
own name and as a page of the Preferences sheet (`dialogs/songDisplay/PreferencesSheet.kt`, `PreferencesContent`). **Never a second UI for the
same setting**: a page is one component wherever it appears (the Metronome page is `ui/metronome`'s
`MetronomePlaybackSection`, also the Metronome tab's second section).

## Layout

- The pages are picked by a connected button group (`PreferencesPageButtons`), as the tools are switched: an icon each,
  the picked one named and wider. Never a second tab row (tabs nested in Settings') nor chips (read as a setting).
- **The sheet's buttons are pinned at its bottom** (`PreferencesFrame`): the page scrolls above them and fades out at
  its bottom edge, so another page is picked from anywhere down this one, within the thumb's reach. The sheet takes each
  page's height, its top edge moving; the buttons stay on its bottom edge meanwhile (`stayOnSheetBottom`), and the page
  snaps to its size so the sheet's own height animation is the only one. Settings has no buttons: its own tab row
  picks the page, as nested navigation (tabs over buttons) read as two.
- Pages: **General** (read only mode, `PerformanceSection`, only while the songs are on; Appearance, ending with the
  Tooltips switch; Language), **Song** (layout, the Chords switch, i.e. lyrics only, scale suggestions, section
  numbers), **Chords** (Diagrams: the instrument and tuning, diagrams, degrees; Names: notation, accidentals, chord
  names with a capo, capo keeps the key), **Metronome** (sound, volume, tempo change, visual beat, vibrate). Same order
  everywhere.
- Pages slide in their buttons' direction.
- Nothing is typed into the Preferences, so the pinned buttons never meet the keyboard.

## Contexts (`PreferencesContext`, `preferencesPages`, tested)

- A page that changes nothing on the screen the sheet is opened from is hidden, never disabled: song details has
  every page, the Chords and Scales tabs General and Chords. A page whose feature is off goes (Song
  with the songs, Metronome with the metronome); General and Chords always stay.
- The sheet opens on the page its caller names (`DialogType.Preferences.page`): Song from song details' menu, General
  from the read only mark, Chords from the Chords and Scales tabs.
- Choices the rows open (notation, instrument, tuning) stack over the sheet (`DialogStack`), so a pick comes back.

## Rows

- The chord-writing rows are disabled only while nothing names a chord (songs with chords, the Chords or Scales tabs).
  Accidentals stay enabled under a numbering (they still spell its key). Examples are written in the picked notation,
  never in the language. Diagrams and degrees are disabled with the songs or their chords off.
