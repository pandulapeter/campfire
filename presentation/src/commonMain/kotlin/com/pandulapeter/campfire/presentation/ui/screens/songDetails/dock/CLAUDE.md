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
- **A side panel** at the song's end (`DockSidePanel`, `isDockSidePanel`), the step buttons between them; the grid is
  searched without its width. **Only with the diagrams**, which fill it: without them it held only two chips, and with
  the metronome alone it lost the metronome panel's stepper and accents, so those keep their row under the song. **In pages**, a wide window's (`WindowSize.EXPANDED`, 840 dp and wider, whatever its
  height); narrower ones keep the rows: beside a panel a phone on its side wraps a long-lined song's lines
  (`PageCountTest`, 640 × 360: 13 pages under the row, 17 beside a panel). **In one column scrolling by itself**, any
  window holding the narrowest column (`MIN_COLUMN_WIDTH`) and the step buttons beside the panel, its side insets left
  out: that song is read by its height and never wider than a column, so the rows would cost every screen lines for
  nothing. **As wide as the chip-high tempo chip** (`dockTempoChipWidth`, measured at the interface's text size) or two
  full-size guitar diagrams with 8 dp to spare, whichever is wider, within 8 dp each side (`dockPanelWidthOf`); past
  176 dp a 1024 × 720 window's songs take a page more (`PageCountTest`).
  The switch travels: the rows shrink away as the panel slides in (drawn as they were until the height settles, then
  swapped for the row under the song, whose scale chips keep their scroll), the end inset passing between them
  (`PaddingBesidePanel`) so the song's start never moves.
- **A setlist's pager bar is its last row** (`lastRow`), stacked at the panel's foot (`isStacked`). **An import
  preview's card stands on the surface above its one row** (measured with the pages, `PagesAbovePreviewBar`), at the
  panel's head.
- Read by screen readers after the app bar and before the song (`readAfterDock`).

## Its rows

- **Decided by the switches, never the song** (`DockRows`): the playing controls with the chords or the metronome on,
  diagrams on an instrument with shapes, scale suggestions. No row, no dock; nor where the playing controls would be the
  only row and hold only the key chip (no tempo, no diagrams, no scale suggestions, `DockRows.isKeyOnly`): the app bar
  holds that chip and the tuner instead (`../CLAUDE.md`), the dock shrinking away as for any lost row, and the instrument
  pill of an instrument without shapes is left to the Playing sheet's instrument row (and the tuner sheet's pill). With the metronome alone the row is the
  metronome panel (`DockMetronomePanel`): stepper with Tap, a bar as wide as the rest leaves with its accents tapped in
  place, play / stop. A song with nothing for a row leaves it empty, or says so.
- **Its tier is the window's** (`dockLayoutOf`). A short window (`SHORT_WINDOW_HEIGHT`) narrower than the panel's gets
  one row with a chip per chord. Any other gets two, diagrams in the second at the Chords section's size where they fit
  `dockBudgetOf` (the share of the window that costs no page turn as `PageCountTest` measured it: 80 dp by page, 60 by
  column, 48 and 40 over a pager bar), else smaller. The owner chose diagrams on an upright phone at the cost of up to
  two pages on a long song, and its 8 dp padding at one more in some cases, which `PageCountTest` holds.
- **In one row** (a short window, an import preview, no diagrams): the start, chord and scale chips scrolling, the tempo
  chip. **In two**: the scale chips alone, then the start, the diagrams, and the tempo chip, the key and tuner pills
  and the tempo chip as tall as the diagrams; without scale suggestions only the second. The start crossfades and
  resizes where what it holds changes (read only mode, a Dock switch), never for a new key or page (`DockStart`). The
  start and the tempo chip stand still (`DockScrollingRow`) and what scrolls fades only where it meets them; at the
  screen's edge a row runs to it, padded instead, as far in as the step buttons are
  (`PAGE_HORIZONTAL_PADDING`, 8 dp past the insets) and the import preview's card; the rows are as far from the
  surface's top and, at a window's edge with no bottom inset, its bottom (`DOCK_VERTICAL_PADDING`); over an inset (a
  navigation bar) half that, the inset parting them from the edge (`dockBottomPaddingOf`).
- **The panel's controls stay put** (`DockPanelControls`): the key chip over the tuner's pill naming the tuning, as in
  the diagram row, then the tempo chip, all three a row tall and stretched to the panel's width; in read only mode the
  key chip, then the tuner's pill (never the round button the row under the song has) and the instrument pill, the
  same column. **Under them scroll** (`DockPanelRows`) the diagrams in a grid,
  fading under the controls and, only over a bottom inset, at the bottom, padded by the inset.
- **Beside the panel the scale suggestions are one row under the song** (`SongDockState.rowsUnder`), the rows' own
  scale row, over the bottom inset, its corner square where it meets the panel and fading out under it while there
  is more to scroll that way: in the panel, wrapped they broke into
  ragged stacks, a chip a line pushed the diagrams down, and a row scrolling sideways showed too little. The setlist's
  pager bar stays the panel's last row.

## Its contents

- Each page hands up what it drew the song with (`DockContent`, by page index); the dock shows the page the pager is on
  or heading for, crossfading as it turns, as the bar's subtitle does.
- **The key chip and the tempo chip are outlined tonal pills** (`DockControlPill`), set apart from the chord and scale
  chips (the song's) by their fill rather than by chevrons, the outline giving them contrast on the dock's surface; every part of one opens its sheet but play and stop. A
  screen reader reads each pill as one description of all it shows (key, transposition, and capo; tuner and tuning).
- **The key chip** (`DockKeyChip`, `dockKeyOf`) names what the transposition stepper names (the shapes' under `SHAPES`,
  the inferred key for a file naming none), a moved song's semitones after a named key ("Bm (+2)", not in read only mode); **a capo other than zero is always on screen**, after a stepper's
  divider. In the diagram row it stands over a pill of the tuner naming the tuning (`DockKeyAndTuner`), the two filling
  the row's height as the tempo chip does, where the start has no tuner of its own; alone there it is as tall as the
  row, its text unscaled, the capo on a line under the key naming the shapes' key where the key names what sounds
  ("Capo 2 (Am shapes)", `SongCapoControl.shapesKey`); never a key set large in a card. It opens
  the Playing sheet (`DialogType.Playing`): instrument and tuning, transposition and capo, their pending writes
  unchanged, and no tuner: wherever the key chip stands over no tuner pill (one row: a short window, no diagrams, an
  instrument without shapes) the round tuner button stands before it (`DockInstrumentTools`, `keyHoldsTuner`), so the
  Tuner switch on always leaves the tuner in the dock. Read only mode's opens nothing (it would write overrides) and
  is left out with neither key nor capo; the instrument pill and tuner then start the dock, as
  with the chords off or an instrument without shapes, whose pill is what brings the diagrams back. The tuner sheet has
  the instrument pill too, so the instrument is changed wherever a tuner is shown.
- **The tempo chip** (`DockTempoChip`; never in an import preview, which has no metronome): the metronome mark swinging with the click (where the visual beat
  is on), as the tuner pill has the tuner's, then the tempo of the page's stretch (`SongTiming.bpm`), as wide as the widest tempo so play / stop (M) never moves, and a beat row lit while the
  click plays. In the diagram row it is as tall as the diagrams, the number over the beats and a round play / stop
  (`DockRoundPlayStop`); elsewhere chip-high, play / stop after a divider (`DockPlayStop`). The click is the screen's
  (`DockMetronome`), not a page's. It opens the Tempo sheet (`DialogType.Tempo`): stepper with Tap, and the song's
  accents with a round play / stop; not the time signature, which is the file's (Song defaults). Read only mode's opens
  nothing; the click still plays.
- **The scale chips** are the Scales section's groups, the home one untitled, the rest under their titles ("Over E7").
  None (a songbook, a song over `MAX_PLAYED_CHORDS` included) says so: the row is the screen's.
- **The diagram strip** (`DockDiagramStrip`) is the Chords section's cells (`rememberChordCells`), sized by what the
  row's height leaves (`dockDiagramSize`). The chords played on the screen are drawn as they are and the rest faded
  back to 60 %, well above a disabled control's 38 %, so no colour is spent on the mark and nothing looks unplayable;
  nothing fades before the song is laid out. The screen is the one a step or a fling comes to rest on, else the one
  shown now, so the marks follow a hand drag and a self-scrolling column too (`rememberChordsOnScreen`:
  `SongStepper.shownSections`, derived, so only a new range of sections recomposes; `chordsOnScreen`, a screen of none
  marking the chord it is headed by, `firstChordFrom`). A chord chip or diagram opens the chord sheet, not in
  performance mode (it writes the player's pick), yes in an import preview; a scale chip its sheet, read only mode
  included.
- **The rows never scroll themselves**, only under a finger: a row following the song would fight a hand scroll and
  a self-scrolling column, which never settles.
- A chord or scale sheet opened from the dock pauses a self-scrolling column and resumes it on closing
  (`autoScroll/CLAUDE.md`).
- Breadcrumbs (kinds only): `Dock: …` for every chip, the click, its accents, and the panel's stepper; `Tempo sheet: …`
  and `Playing sheet: …` for every step, reset, and accent.
