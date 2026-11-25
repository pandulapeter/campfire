<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens/songDetails/dock

What left the song on song details: its first section (instrument pill, tuner, transposition, capo, tempo, Tap, time
signature, metronome button), its Chords section, and its Scales section, which the song no longer starts with
(`SongLyrics.showsPlayingSections`; the editor's preview and the PDF keep them). The plan it follows, and what is
still to come (the key and tempo chips, the chord strip, the scale row, the sheets), is
`documentation/plans/song-details-dock.md`.

## Where it is

- **Under the pages, outside the pager**, in the screen's column, so the pages are shorter rather than covered: no line
  is ever under it, and a rested page's last line is unfaded. Above the setlist's pager bar, else over the bottom inset,
  which the pages then leave to it (`baseBottom`). One `surfaceContainer` surface, rounded at its top corners, never
  collapsed, with no divider or shadow.
- **Its height is the screen's, never the song's** (`SongDockState`): a height that followed the song would lay the
  viewport out anew on every setlist page turn. Animated to a new one (a switch, a window resized), the pages' grid
  waiting for it as for the app bar (`isViewportSettled`).
- Read by screen readers after the app bar and before the song (`readAfterDock` on the pages).

## Its rows

- **Decided by the switches** (`DockRows`): the playing controls with the chords or the metronome on, the chords with
  diagrams drawn on an instrument with shapes, the scale suggestions. No row, no dock. A song with nothing for a row
  leaves it empty, or for the diagrams says so.
- **The budget scales to the room** (`dockBudgetOf`, tested): the share of the window that costs no page turn, as
  `PageCountTest` measured it on 360 × 640 dp (80 dp turned by page, 60 by column, 48 and 40 over a pager bar), the same
  share of a taller window. Within it, the richest tier that fits (`dockLayoutOf`): diagrams at the Chords section's
  size, smaller ones, or one row with a chip per chord. A phone always gets the one row; its padding is as thin as a
  chip allows, since 48 dp already cost a column-turned setlist a step.
- The row height follows the system text size (`songControlHeight`), never the song's: the controls are drawn at the
  interface's size (`fontScale` 1).
- Every row scrolls sideways by hand and runs to the screen's edges, padded in from them, so nothing fades yet: a fade
  belongs only where a scrolling row meets something that does not.

## Its contents

- Each page hands up what it drew the song with (`DockContent`, by page index); the dock shows the page the pager is
  on or heading for, crossfading as it turns, as the bar's subtitle does.
- In one row: the instrument pill, the playing controls or read only mode's line (`DockPlayingControls`), the chord
  chips (`DockChordChips`), the scale chips (`DockScaleChips`). With diagrams, those go in a row of their own
  (`DockDiagramStrip`). A chord chip or diagram opens the chord's sheet where shapes may be picked (as the section's
  diagrams did), a scale chip its scale's sheet, read only mode included.
