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
(`SongLyrics.showsPlayingSections`; the editor's preview and the PDF keep them), and the metronome panel the app bar
had. The plan it follows, and what is still to come, is `documentation/plans/song-details-dock.md`.

## Where it is

- **Under the pages, outside the pager**, so the pages are shorter rather than covered: no line is ever under it, and
  a rested page's last line is unfaded. Over the setlist's pager bar, else over the bottom inset, which the pages then
  leave to it (`baseBottom`). One `surfaceContainer` surface, rounded at its top corners, never collapsed, with no
  divider or shadow.
- **A wide window** (`isDockSidePanel`: `WindowSize.EXPANDED`, 840 dp and wider, whatever its height) **gets a side
  panel instead** (`DockSidePanel`), 176 dp at the song's end plus the end inset it covers, rounded at its top start,
  the step buttons between the song and it. The grid is searched with its width taken off, and waits for it to come to
  rest (`settledSize`). Any narrower window keeps the rows, a phone on its side its one row: the owner's decision, since
  a panel there wraps a long-lined song's lines (`PageCountTest`, 640 × 360: The Lighthouse Keeper 12 pages under the
  row, 17 beside a panel). 176 dp is as wide as costs a laptop's window no page (1024 × 720: four at 176 dp, five at 200).
- **The switch travels**: the rows (the pager bar with them) shrink away while the panel slides in from the end edge,
  laid out at its full width, and the reverse; the end inset passes from the song to the panel as the panel grows
  (`PaddingBesidePanel`), so the song's start never moves.
- **A setlist's pager bar is its last row** (`SongDock`'s `lastRow`), on the same surface, its "Up next … Step again"
  state kept; read with the dock. In the panel it ends the panel under what scrolls, stacked (`isStacked`): the
  setlist's name, or the next song's, on a line above the buttons and the place or the hint. **An import preview's card
  stands on the surface above its rows** (a room of it, measured with the pages in `PagesAbovePreviewBar`), which
  squares the dock's own corners off while it is there; a preview's dock is one row (its chords as chips, no tempo chip,
  a preview having no metronome), the card taking the second's room. In the panel the card heads it, scrolling with it,
  and the pages keep their whole height.
- **Its height is the screen's, never the song's** (`SongDockState`): a height that followed the song would lay the
  viewport out anew on every setlist page turn. Animated to a new one (a switch, a window resized), the pages' grid
  waiting for it as for the app bar (`isViewportSettled`).
- Read by screen readers after the app bar and before the song (`readAfterDock` on the pages).

## Its rows

- **Decided by the switches** (`DockRows`): the playing controls with the chords or the metronome on, the chords with
  diagrams drawn on an instrument with shapes, the scale suggestions. No row, no dock. With the metronome alone (the
  chords off) the one row is the metronome panel (`DockMetronomePanel`): what starts the dock, the tempo stepper with
  Tap, the bar as wide as the rest leaves with its accents tapped in place, and play / stop (read only mode: the number
  alone, and the accents untappable, both being writes). A song with nothing for a row
  leaves it empty, or for the diagrams says so.
- **Its tier is the window's** (`dockLayoutOf`, tested). A short window (`SHORT_WINDOW_HEIGHT`, a phone on its side)
  narrower than the panel's gets one row with a chip per chord. Any other window gets two rows,
  the chords' diagrams in the second: at the Chords section's size where they fit the budget (`dockBudgetOf`: the
  share of the window that costs no page turn, as `PageCountTest` measured it on 360 × 640 dp, 80 dp turned by page, 60
  by column, 48 and 40 over a pager bar, the same share of a taller window), else smaller. The owner chose diagrams on
  an upright phone at the cost of up to two pages or steps on a long song, which `PageCountTest` holds (the smaller
  diagrams are as large as keep that: a 112 dp dock). Its padding is as thin as a chip allows.
- The row height follows the system text size (`songControlHeight`), never the song's: the controls are drawn at the
  interface's size (`fontScale` 1).
- Every row scrolls sideways by hand and runs to the screen's edges, padded in from them.
- **The panel's rows** (`DockPanelRows`) wrap rather than scroll: the start and the tempo chip, the scale chips (each
  group's title on a line of its own, names a size smaller so the common ones keep to a line), then every diagram at
  the section's size, two to a line. The panel scrolls as one where the window is too short for them.

## Its contents

- Each page hands up what it drew the song with (`DockContent`, by page index); the dock shows the page the pager is
  on or heading for, crossfading as it turns, as the bar's subtitle does.
- **The dock's start** is the key chip (`DockKeyChip`, `dockKeyOf`, tested): the key of the page's chords as the
  transposition stepper names it (the shapes' under `SHAPES`, the inferred key for a file naming none, the semitones for
  a song naming none), and a capo other than zero as an orange half, sliding in and out. It opens the Playing sheet
  (`DialogType.Playing`, its chevron saying so), which holds the instrument and tuning, the tuner, and the transposition
  and capo steppers (their pending writes unchanged); the instrument, tuning, and tuner sheets stack over it. Read only
  mode's chip opens nothing and has no chevron, and is left out where there is neither key nor capo; the instrument pill
  and the tuner then start the dock (`DockInstrumentTools`), as they do with the chords off, which takes the chip. An
  instrument without shapes keeps its pill there, since it is what brings the diagrams back.
- **In one row** (a short window, an import preview, or no diagrams): the start, then the chord chips (`DockChordChips`) and the scale
  chips (`DockScaleChips`) scrolling, then the tempo chip. **In two**: the start and the scale chips, then the diagrams
  (`DockDiagramStrip`) and the tempo chip; without scale suggestions only the second, the start before the diagrams.
  The start and the tempo chip stand still (`DockScrollingRow`), and what scrolls between them fades where it meets
  them, as far as it can still scroll. A chord chip or diagram opens the chord's sheet where shapes may be picked, a
  scale chip its scale's sheet, read only mode included.
- **The tempo chip** (`DockTempoChip`, with the metronome on, never in an import preview, which has no metronome): the
  tempo of the stretch the page is in (`SongTiming.bpm`, a later `{tempo}`'s with an override scaled in), as wide as
  the widest tempo, a small beat row that only shows the bar (lit while the click plays, a playing stretch's own accents
  until its bar ends), and play / stop (`DockPlayStop`, M), which stay in place whatever the number. The click is the
  screen's (`DockMetronome`), not a page's. The number opens the **Tempo sheet** (`DialogType.Tempo`,
  `dialogs/playing/TempoSheet`): the tempo stepper with Tap, writing the override as the first section's did, and the
  song's accents, tapped there; not the time signature, which is the file's (Song defaults). Read only mode's opens
  nothing and has no chevron; the click still plays.
- **The scale chips** are the Scales section's rows: the home group's untitled, then each group and each chord worth a
  scale of its own under its title ("Over E7"). A song with none says so ("No scale suggestions for this song"), a
  songbook and a song over `MAX_PLAYED_CHORDS` included, whose scales are never worked out: the row is the screen's.
- **The diagram strip** (`DockDiagramStrip`) is the Chords section's cells (`rememberChordCells`): every chord once,
  first-played order, each key's group after a "Key: A♭" label where there are several, the degree or letters line
  over the names where they are on. The diagrams take what the row's height leaves the lines of text, at most the
  section's size (`dockDiagramSize`, tested), so the row stays the screen's; the keyboard's are wider. A changed shape
  crossfades. A song of no chords says so, a songbook (`hasTooManySongChords`) that it has too many. A cell opens the
  chord sheet as the section's did (not in performance mode, yes in an import preview).
- **The strip and the scale row scroll themselves only on a page turn**, never under a finger (`ScrollToOnPageTurn`):
  the strip to the first chord of the page the reader is headed for (`SongStepper.headedSection`, `sectionAt`,
  `firstChordFrom`, tested; a key's first chord with its label), the scale row to that page's group (`scaleGroupAt`,
  tested: the group of its last section at or before it, from `SongScaleGroup.sections`, which the model fills from the
  blocks each section was made of), each page's stepper handed to the dock by index (`SongDockState.steppers`). A row
  swiped by hand stays put until the next turn changes its target. A short window's row, whose chord chips come first,
  does not scroll to a group. The panel scrolls only as far as brings the page's first chord into view
  (`BringIntoViewOnPageTurn`).
- Breadcrumbs (kinds only): `Dock: …` for every chip, the click, its accents, and the panel's stepper; `Tempo sheet: …`
  and `Playing sheet: …` for every step, reset, and accent.
