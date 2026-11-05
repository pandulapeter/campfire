<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Preferences sheet

How a song is read, played, and clicked is set in one place, a Preferences sheet. It opens from the screens the
preferences affect, and its pages are also Settings' own tabs. This plan replaces the current split between Settings →
Preferences, the song details menu, and the Metronome tab's Playback section, and makes Performance mode hard to turn
on by accident without noticing.

## Decided

### One component per setting

- **Every page is one component, drawn identically wherever it appears**: in the sheet, as a Settings tab, and (for the
  metronome) on its own screen. Never a second UI for the same setting, so a player who learned it in one place knows
  it everywhere.

### The sheet

- **Performance mode (read only) sits above the pages**, in every context, since it affects everything.
- **A tab row under it** picks the page. Pages are grouped by concept, not by screen, so one page serves several
  screens:

  | Page          | Holds                                                                          |
  |---------------|--------------------------------------------------------------------------------|
  | **Chords**    | instrument and tuning, notation, chord names with a capo, diagrams, degrees    |
  | **Song**      | lyrics-only mode, layout, scale suggestions                                    |
  | **Metronome** | the Metronome tab's Playback section (sound, volume, tempo change, visual beat, vibrate) |

- **The context decides which pages exist and which one is open.** A page that changes nothing on the screen the sheet
  is opened from is hidden, never disabled (the full set is always in Settings):

  | Opened from        | Pages                      | Opens on |
  |--------------------|----------------------------|----------|
  | Song details       | Song, Chords, Metronome    | Song     |
  | Chords, Scales     | Chords                     | Chords   |
  | Metronome tab      | no sheet                   | -        |

- **A context with one page has no tab row**: the sheet is that page under Performance mode.
- A page whose feature is off in Settings → Features is left out (no Metronome page with the metronome off).
- Pages slide between each other (staged motion, not a crossfade), and the sheet's height animates between pages of
  different lengths.

### Settings

- **The Preferences tab stays, showing the sheet's own content** (Performance mode, then its page row), so the sheet
  and the tab share one name and one look, and a player learns one place. The nesting is made legible by Material 3's
  primary / secondary tabs: Settings' row stays primary, the pages' row is secondary (lighter), the same in the sheet.
- **Performance mode leaves General**: it lives only in the Preferences content.
- No long scrolling page combining the sections, and no tab per page: one name everywhere beats a flat tab row.

### Lists preferences are removed

- The Lists section (what the song cards show) goes, with no replacement. Everyone gets the defaults: on Songs the
  cards show key and tempo, on Setlists key, tempo, and duration.
- **The code behind it is removed in the same change**: the preference fields' reads and writes, the section and its
  `CardDetailsChoice`, and anything only they use (strings in every language included). The stored keys are simply
  no longer read, so no new field and no version bump; older versions still find what they wrote.

### Entry points

- **A "Preferences" entry in the overflow menu** of song details, Chords, and Scales; it replaces song details'
  current preference entries. No new button on any bar.
- **The Metronome tab has no entry**: its page is already on screen.
- **The screens Performance mode locks show that it is on**, with a small indicator (on song details, on its bar) that
  opens the sheet, so an accidental switch-on is noticed and undone where it happened. Drawn as an icon alone, it is a
  `CampfireIconButton` whose tooltip says what it opens (`ui/components/CLAUDE.md`).

### Metronome tab

- **The screen keeps everything it has now.** Its Playback section becomes the shared Metronome page component, which
  the screen keeps showing in place, so it does not go empty.
- Song details' sheet shows that same component as its Metronome page.

### Tuner

- **No Tuner page.** The mode toggle is already in both tuners (the Tuner tab and song details' tuner sheet).
- **The reference pitch is added to the tuner sheet**, with the same `ReferencePitchControls` as the Tuner tab, so both
  tuners offer the same controls.
- The existing instrument pickers (song details, Tuner, Chords, Scales, onboarding) all stay.

### Defaults taken without a ruling

- The instrument is a row of the Chords page, not beside Performance mode (the pinned height: header, Performance
  mode, and tab row are already ~180 dp on a 360 × 640 dp phone).
- Song details' sheet always opens on Song.

## Open

- **Room on a phone**: two tab rows plus Performance mode in Settings → Preferences on a 360 × 640 dp phone, and how
  the wide layout's category pane shows the inner pages.
- **What the indicator looks like** and which screens besides song details get one (the list screens, where Performance
  mode hides New and the edit actions).
- The exact split of the current Settings → Preferences rows between Chords and Song, row by row.

## Order

1. Reference pitch in the tuner sheet (independent, can land first).
2. Remove the Lists preferences and their code (independent).
3. Extract the pages as components (Chords, Song, Metronome) from today's Settings → Preferences and the Metronome
   tab, no visible change.
4. Settings: the Preferences tab shows Performance mode and the pages under secondary tabs; Performance mode leaves
   General.
5. The sheet, with its contexts, from song details' overflow menu first, then Chords and Scales.
6. The Performance mode indicator.
