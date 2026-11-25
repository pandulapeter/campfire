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

What left the song on song details, which now starts at its first line (`SongLyrics.showsPlayingSections`; the editor's
preview and the PDF keep their sections): the first section's playing controls, the Chords and Scales sections, and the
app bar's metronome panel.

## Where it is

- **Under the pages, outside the pager**, so the pages are shorter rather than covered: no line is ever under it and a
  rested page's last line is unfaded. Over the setlist's pager bar, else over the bottom inset (`baseBottom`). One
  `surfaceContainer` surface rounded at its top, never collapsed, no divider or shadow.
- **Its height is the screen's, never the song's** (`SongDockState`): one that followed the song would lay the viewport
  out anew on every setlist page turn. A new height animates, the grid waiting for it as for the app bar.
- **A wide window** (`isDockSidePanel`: `WindowSize.EXPANDED`, 840 dp and wider, whatever its height) **gets a 176 dp side
  panel** at the song's end (`DockSidePanel`), the step buttons between them; the grid is searched without its width.
  Narrower windows keep the rows: beside a panel a phone on its side wraps a long-lined song's lines (`PageCountTest`,
  640 × 360: 12 pages under the row, 17 beside a panel), and 176 dp is as wide as costs a 1024 × 720 window no page.
  The switch travels: the rows shrink away as the panel slides in, the end inset passing between them
  (`PaddingBesidePanel`) so the song's start never moves.
- **A setlist's pager bar is its last row** (`lastRow`), stacked at the panel's foot (`isStacked`). **An import
  preview's card stands on the surface above its one row** (measured with the pages, `PagesAbovePreviewBar`), at the
  panel's head.
- Read by screen readers after the app bar and before the song (`readAfterDock`).

## Its rows

- **Decided by the switches, never the song** (`DockRows`): the playing controls with the chords or the metronome on,
  diagrams on an instrument with shapes, scale suggestions. No row, no dock. With the metronome alone the row is the
  metronome panel (`DockMetronomePanel`): stepper with Tap, a bar as wide as the rest leaves with its accents tapped in
  place, play / stop. A song with nothing for a row leaves it empty, or says so.
- **Its tier is the window's** (`dockLayoutOf`). A short window (`SHORT_WINDOW_HEIGHT`) narrower than the panel's gets
  one row with a chip per chord. Any other gets two, diagrams in the second at the Chords section's size where they fit
  `dockBudgetOf` (the share of the window that costs no page turn as `PageCountTest` measured it: 80 dp by page, 60 by
  column, 48 and 40 over a pager bar), else smaller. The owner chose diagrams on an upright phone at the cost of up to
  two pages on a long song, which `PageCountTest` holds.
- **In one row** (a short window, an import preview, no diagrams): the start, chord and scale chips scrolling, the tempo
  chip. **In two**: the start and the scale chips, then the diagrams and the tempo chip; without scale suggestions only
  the second. The start and the tempo chip stand still (`DockScrollingRow`) and what scrolls fades only where it meets
  them; at the screen's edge a row runs to it, padded instead.
- **The panel's rows** (`DockPanelRows`) wrap rather than scroll; the panel scrolls as one where the height runs out.

## Its contents

- Each page hands up what it drew the song with (`DockContent`, by page index); the dock shows the page the pager is on
  or heading for, crossfading as it turns, as the bar's subtitle does.
- **The key chip and the tempo chip are tonal pills** (`DockControlPill`), set apart from the outlined chord and scale
  chips (the song's) by their fill rather than by chevrons; every part of one opens its sheet but play and stop.
- **The key chip** (`DockKeyChip`, `dockKeyOf`) names what the transposition stepper names (the shapes' under `SHAPES`,
  the inferred key for a file naming none); **a capo other than zero is always on screen**, in orange after a stepper's
  divider. It opens the Playing sheet (`DialogType.Playing`): instrument and tuning, transposition and capo, then a
  full-width Tuner button, their pending writes unchanged. Read only mode's opens nothing (it would write overrides) and
  is left out with neither key nor capo; the instrument pill and tuner then start the dock (`DockInstrumentTools`), as
  with the chords off or an instrument without shapes, whose pill is what brings the diagrams back. The tuner sheet has
  the instrument pill too, so the instrument is changed wherever a tuner is shown.
- **The tempo chip** (`DockTempoChip`; never in an import preview, which has no metronome): the tempo of the page's
  stretch (`SongTiming.bpm`), as wide as the widest tempo so play / stop (M) never moves, and a beat row lit while the
  click plays. In the diagram row it is as tall as the diagrams, the number over the beats and a round play / stop
  (`DockRoundPlayStop`); elsewhere chip-high, play / stop after a divider (`DockPlayStop`). The click is the screen's
  (`DockMetronome`), not a page's. It opens the Tempo sheet (`DialogType.Tempo`): stepper with Tap, and the song's
  accents with a round play / stop; not the time signature, which is the file's (Song defaults). Read only mode's opens
  nothing; the click still plays.
- **The scale chips** are the Scales section's groups, the home one untitled, the rest under their titles ("Over E7").
  None (a songbook, a song over `MAX_PLAYED_CHORDS` included) says so: the row is the screen's.
- **The diagram strip** (`DockDiagramStrip`) is the Chords section's cells (`rememberChordCells`), sized by what the
  row's height leaves (`dockDiagramSize`). A chord chip or diagram opens the chord sheet, not in performance mode (it
  writes the player's pick), yes in an import preview; a scale chip its sheet, read only mode included.
- **Rows scroll themselves only on a page turn**, never under a finger (`ScrollToOnPageTurn`): the strip to the headed
  page's first chord (`firstChordFrom`), the scale row to its group (`scaleGroupAt`), from each page's stepper
  (`SongDockState.steppers`). A row swiped by hand stays put until the next turn; the panel only brings that chord into
  view (`BringIntoViewOnPageTurn`).
- A chord or scale sheet opened from the dock pauses a self-scrolling column and resumes it on closing
  (`autoScroll/CLAUDE.md`).
- Breadcrumbs (kinds only): `Dock: …` for every chip, the click, its accents, and the panel's stepper; `Tempo sheet: …`
  and `Playing sheet: …` for every step, reset, and accent.
