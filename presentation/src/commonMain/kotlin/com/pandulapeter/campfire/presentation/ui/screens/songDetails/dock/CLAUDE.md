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
preview and the PDF keep their sections): the Chords and Scales sections, the instrument and tuning they are drawn for
with the tuner, and the click. The first section's key and capo are the app bar's key chip (`../CLAUDE.md`).

## Where it is

- **Under the pages, outside the pager**, so the pages are shorter rather than covered: no line is ever under it and a
  rested page's last line is unfaded. Over the setlist's pager bar (`../CLAUDE.md`: as wide as the screen, under the
  rows and the side panel alike), else over the bottom inset (`baseBottom`). One
  `surfaceContainer` surface rounded at its top, never collapsed, no divider or shadow.
- **Its height is the song's, never a screen's of it** (`SongDockState`): stepping through a song never changes it, and
  a setlist's next song may. A new height animates (a switch, a window resized, another song), the grid waiting for it
  as for the app bar, so the pages never jump; a change while neither the song nor the switches changed (its page
  saying what it has) is data arriving and snaps (`animateDockDp`). What a song has is kept by file name
  (`SongTextStore.dockFacts`), so a song opened again has its dock on its first frame; until its page has said, the last
  song's rows stand, or the switches' alone.
- **A side panel** at the song's end (`DockSidePanel`, `isDockSidePanel`), the step buttons between them; the grid is
  searched without its width. **Only with the diagrams**, which fill it: without them it held only the tempo chip, and with
  the metronome alone it lost the metronome panel's stepper and accents, so those keep their row under the song. **In pages**, a wide window's (`WindowSize.EXPANDED`, 840 dp and wider, whatever its
  height); narrower ones keep the rows: beside a panel a phone on its side wraps a long-lined song's lines
  (`PageCountTest`, 640 × 360: 13 pages under the row, 17 beside a panel). **In one column scrolling by itself**, any
  window holding the narrowest column (`MIN_COLUMN_WIDTH`) and the step buttons beside the panel, its side insets left
  out: that song is read by its height and never wider than a column, so the rows would cost every screen lines for
  nothing. **As wide as the tall tempo chip** (`dockTallTempoChipWidth`, measured at the interface's text size, about
  132 dp) or two full-size guitar diagrams with 8 dp to spare, whichever is wider, within 8 dp each side
  (`dockPanelWidthOf`); past 176 dp a 1024 × 720 window's songs take a page more (`PageCountTest`).
  The switch travels: the panel slides in as the rows under the song are staged into the scale row alone (below), whose
  scale chips keep their scroll, the end inset passing between them (`PaddingBesidePanel`) so the song's start never
  moves. The panel's tempo chip grows in and out at its foot.
- **A change of its rows is staged** (`rememberDockStage`, `DockPart`), as a change of the song's look is
  (`StagedSongFrame`), since it happens in view behind the Preferences sheet: what leaves (a row, the card or the tempo
  chip at a row's end, the diagrams at their old size, the chip row's old chips) fades out with the old layout held,
  then the height and the rows' places glide on one spring, then what arrives fades in. The rows stand on the dock's
  bottom, so its top edge uncovers or covers a row it gains or loses while the rest keep their places, and the chip row
  glides down onto the bottom or up over the diagram row (`DockChipRow`). The card and the tempo chip fade and grow in
  from their end of the row (`DockScrollingRow`). A change of the layout alone (a window resized) glides at once, and
  data arriving snaps. What the page stops handing up as a switch takes it is drawn until it has faded
  (`rememberLastOf`).
- **An import preview's card floats over the dock** (measured with the pages, which end above it,
  `PagesAbovePreviewBar`): on its own fill, no surface of the dock's behind it, the dock's rounded top under it, as far
  from the dock as from the window's edges. Always under the song: beside a side panel too, over the scale row there,
  never at the panel's head, which keeps its column of instrument card, diagrams, and tempo chip whole. A resize across
  the panel's breakpoint leaves the card where it is while the panel slides in or out.
- Read by screen readers after the app bar and before the song (`readAfterDock`).

## Its rows

- **Decided by the switches, then by the song** (`DockRows`, `DockRows.forSong`): diagrams on an instrument with
  shapes, scale suggestions, the tempo chip with the metronome on (never in an import preview); a song with no diagram
  to draw (no chords, a songbook of too many, `DockContent.hasChordCells`) or no scale to suggest lays the dock out as
  that switch off would, never a row saying it has none: the scale strip with the tempo chip, then the metronome panel,
  then no dock. No row, no dock. With the metronome alone (neither diagrams nor scale suggestions) the row is the
  metronome panel (`DockMetronomePanel`): stepper with Tap, a bar as wide as the rest leaves with its accents tapped in
  place, play / stop. **An instrument without shapes** (Other, the violin) has no diagrams but keeps the instrument card,
  one line, the way back to an instrument with them (`DockRows.hasInstrumentChip`, with the diagrams on): it starts the one row (the scale strip and the tempo chip, else beside the metronome panel, else a row of its
  own), never a side panel. **The diagrams switched off** keep that card the same way while it holds the tuner (the
  Dock's Tuner switch on, `rememberChordDiagrams`): the switch takes the diagrams, not the tuner and its instrument. **A song with no diagram to
  draw** keeps that card too (`DockRows.forSong`): the instrument, its tuning, and the tuner are the player's, not the
  song's.
- **Its tier is the window's** (`dockLayoutOf`). A short window (`SHORT_WINDOW_HEIGHT`) narrower than the panel's gets
  one row with a chip per chord. Any other gets two, diagrams in the second at the Chords section's size where they fit
  `dockBudgetOf` (the share of the window that costs no page turn as `PageCountTest` measured it: 80 dp by page, 60 by
  column, 48 and 40 over a pager bar), else smaller. The owner chose diagrams on an upright phone at the cost of up to
  two pages on a long song, and its 8 dp padding at one more in some cases, which `PageCountTest` holds.
- **Where the controls stand** (`DockControlsPlace`): without diagrams the instrument card and the tempo chip end the one
  row, unless the song's scale chips are wider than the room between them (`scalesFitBesideControls`: the controls'
  widths measured as text, `dockLineCardWidth`, `dockTempoChipWidth`; the chips' one-line width measured by the chip
  row by song, kept in `dockFacts`, drawn unseen until known where it decides, and data arriving only when first known,
  a later change being the reader's, which glides): the controls then take a row of their own under the scale
  row, nothing between them (`DockControlsRow`; never in a short window). Over a setlist's pager bar, controls with
  nothing between them move onto it around its buttons (`DockPagerBarCard`, `DockPagerBarTempo`, staged as a row is):
  the card alone always, cut short past `SETLIST_CONTROLS_MIN_WIDTH`, the card and the tempo chip in place of the
  metronome panel where all fit (`controlsFitPagerBar`); never the tempo chip alone, which would leave the bar lopsided.
- **The tempo chip always stands in the dock's bottom end corner**, and the instrument pill starts the chords' row. **In
  one row** (a short window, an import preview, no diagrams): the chip-high instrument card where there are chord chips
  or an instrument without shapes, the chord chips (with diagrams on; each keeps its diagram's degree or letters, in a
  smaller line's style before the name, as a chip holds one line) and the scale chips scrolling, the chip-high tempo
  chip at its end; the row measures its end first and leaves what scrolls at least `DOCK_MIN_SCROLLING_ROOM` (about two
  chips), so a card of long names ("12-string guitar", "Full step down") is cut short rather than taking the row
  (`DockScrollingRow`). **In two**: the
  scale chips alone, then the tall instrument pill, the diagrams, and the tall tempo chip, symmetric; without scale
  suggestions only the second. The pill and the tempo chip stand still (`DockScrollingRow`) and what scrolls fades only
  where it meets them; at the screen's edge a row runs to it, padded instead, as far in as the step buttons are
  (`PAGE_HORIZONTAL_PADDING`, 8 dp past the insets) and the import preview's card; the rows are as far from the
  surface's top and, at a window's edge with no bottom inset, its bottom (`DOCK_VERTICAL_PADDING`); over an inset (a
  navigation bar) half that, the inset parting them from the edge (`dockBottomPaddingOf`).
- **In the side panel the diagrams scroll in a grid** (`DockPanelRows`) between the tall instrument pill at its head and
  the tall tempo chip at its foot, both as wide as the panel's content (never widening it, the chip's content centred)
  and standing still, so the panel's controls make one column; the diagrams fade where they meet the pill and the chip
  (without a chip, at the bottom only over a bottom inset, padded by it). The grid is centred in the panel, its lines
  of several starting together so they read as columns; a diagram alone on its line (a long name that shares none) is
  centred, but an odd last one no wider than the one above stays in that column (`DockPanelGrid`).
- **Beside the panel the scale suggestions are one row under the song** (`SongDockState.rowsUnder`), the rows' own
  scale row, over the bottom inset, its corner square where it meets the panel and fading out under it while there
  is more to scroll that way: in the panel, wrapped they broke into ragged stacks, a chip a line pushed the diagrams
  down, and a row scrolling sideways showed too little.

## Its contents

- Each page hands up what it drew the song with (`DockContent`, by page index); the dock shows the page the pager is on
  or heading for, crossfading as it turns, as the bar's subtitle does.
- **The tempo chip and the instrument pill are outlined tonal pills** (the tempo chip a `DockControlPill`,
  `ControlPillFrame.TONAL`; the app bar's key chip is outlined only), set apart from the chord and scale chips (the
  song's) by their fill rather than by a chevron, the outline giving them contrast on the dock's surface. A screen
  reader reads the tempo chip as one description.
- **The instrument card is the app's pill** (`ui/instrument`'s `InstrumentPill`, which `DockInstrumentPill` only frames,
  never a pill of the dock's own): the instrument the diagrams are drawn for and its tuning ("Standard" too; an
  instrument with one tuning, its name alone), only where the diagrams or the chord chips are, or the one-line card of
  an instrument without shapes or of the diagrams off with the tuner in it (`DockRows.hasInstrumentCard`, so not
  with the diagrams and the Dock's Tuner both off; a song with no chords keeps it, one line). The tempo chip's height, fill, outline, and corners: beside the diagrams stacked (`isStacked`, the tuning under
  the instrument), elsewhere one line. Its halves open the instrument's and the tuning's sheets (`DialogType.Instrument`,
  `DialogType.Tuning`), read only mode included, the instrument being the device's, each half's press and ripple filling
  its part of the card edge to edge, under the tuner too (`InstrumentPillOverControl`). One line, its names keep 8 dp
  from the dividers and 12 dp from its start, the tempo chip's own, so its room goes to them; they shrink to 11 sp
  before they are cut short (a long tuning in the side panel), the card's size unchanged. The one place in song details
  the instrument is changed: the Playing and tuner sheets have no pill. `Dock: instrument pill` and `Dock: instrument
  pill tuning` breadcrumbs.
- **It holds the tuner**, built as the tempo chip is (its `control`), so the row reads tune, the diagrams, play: in a row
  the tuner starts the card, at the row's edge, mirroring play and stop at the tempo chip's (`isTunerAtStart`); in the
  side panel's column it ends the card, as play and stop end the chip under it. A round button (`DockTunerButton` over
  `DockRoundButton`, the play / stop's too) beside the stacked names, the tuner's mark parted by a divider on one line
  (`DockEndButton`, the chip-high play / stop's too). Only with the Dock's Tuner switch
  on (`SongDetailsTools.isTunerInCard`, the Tuner feature on, never an import preview); it fades and grows in and out
  with the switch. The song's menu holds the tuner wherever the card does not (`isTunerInMenu`). `Dock: tuner`.
- **On a narrow window** (under 600 dp, `DockLayout.areControlsCompact`) the tall instrument card and tempo chip are
  smaller, about 100 dp each where they took 60 % of a 360 dp row: text a size down, a 32 dp round button, 8 dp padding
  (`DockControlMetrics.kt`), eased across the width (`dockCompactness`) rather than snapped, the card at most 116 dp
  (`dockTallCardMaxWidth`), so a long tuning shrinks and is cut short rather than taking the diagrams' room. The side
  panel keeps the full size.
- **The tempo chip** (`DockTempoChip`, every part of it opening its sheet but play and stop; never in an import preview,
  which has no metronome): the metronome mark swinging with the click (where the visual beat is on), then the tempo of
  the page's stretch (`SongTiming.bpm`), as wide as the widest tempo so play / stop (M) never moves, and a beat row lit
  while the click plays. Beside diagrams (their row, the side panel's foot) it is tall, one size in both, as tall as a
  row of the smaller diagrams (`DockLayout.tallTempoChipHeight`, centred in a row of full-size ones), the number over
  the beats and a round play / stop (`DockRoundPlayStop`), smaller on a narrow window; elsewhere chip-high, play / stop
  after a divider (`DockPlayStop`). The click is the screen's (`DockMetronome`), not a page's. It opens the Tempo sheet
  (`DialogType.Tempo`): stepper with Tap, and the song's accents with a round play / stop; not the time signature, which
  is the file's (Song defaults). Read only mode's opens nothing; the click still plays.
- **The scale chips** are the Scales section's groups, the home one untitled, the rest under their titles ("Over E7").
  A song with none (a songbook, a song over `MAX_PLAYED_CHORDS` included) has no scale row. A group none of whose
  sections (`SongScaleGroup.sections`) has a line on the screen is faded back as the diagrams are, title and chips, still opening
  their sheets (`rememberScaleGroupsOnScreen`). The home group is one like the others: its sections are the ones played
  over the home scale, so a screen whose every section another group holds (a modulation, an "Over E7" part) fades it
  too. Nothing fades while no range is known, nor on a screen no group holds a section of (a comment, a picture).
- **The diagram strip** (`DockDiagramStrip`) is the Chords section's cells (`rememberChordCells`), sized by what the
  row's height leaves (`dockDiagramSize`). The chords played on the screen are drawn as they are and the rest faded
  back to 60 %, well above a disabled control's 38 %, so no colour is spent on the mark and nothing looks unplayable;
  nothing fades before the song is laid out, nor in a song the screen holds whole. **What is on the screen is its lines,
  never whole sections**: the layout reports every chunk it placed (`SongRows.pieces`, a line each where a section may be
  cut, in pages, a strip, and one column alike), and a chunk counts once half a line of it is in view, less what the bars,
  a strip's sides (inside its scroll, `ReadingWindow.sides`), and, scrolled into a row, the top fade cover
  (`piecesOnScreen`), so a section zoomed taller than the screen marks on each page only its chords there. The screen is the one a step or a fling comes to rest on, else the one shown now, so
  the marks follow a hand drag and a self-scrolling column too (`rememberChordsOnScreen`: `SongStepper.shownPieces`,
  derived, so only new lines recompose; `chordsOnScreen`, a screen of none marking the chord it is headed by,
  `firstChordFrom`, counted from its first line). A tab's slot stands for its whole run, whose line of chord names
  may be in any of them. A chord chip or diagram opens the chord sheet, not in
  performance mode (it writes the player's pick), yes in an import preview; a scale chip its sheet, read only mode
  included.
- **What is on the screen comes first** (`rememberDockRowsOrder`, `DockOrder.kt`, tested): the diagrams (the row's, the
  panel's grid, a short window's chord chips) and the scale groups are a stable partition, the ones on the screen first
  and the faded ones after, each part in the song's order; a song in several keys keeps each key's chords under its
  label. The home scale group always stays first, as nothing but its place names it, faded or not. Items glide to their
  new places (`animatePlacement`), and the row or panel then scrolls back to the first item on the screen
  (`ScrollWithDockOrder`): its start, or past a faded home group to the first group on the screen. So the chords a page
  needs are in sight without a reach, which a fixed order left past the row's edge in a long song.
- **It moves only when the reader is not**: the order follows what is on the screen once it has held for 400 ms (a
  step's or a fling's resting place, never every range a drag passes), at most every 1.5 s (a self-scrolling column
  passes sections steadily, and the dock would never stand still), never while a finger, a button, or a mouse is on the
  dock or one of its rows is scrolling (`DockHold`), the pending order applied once they let go
  (`rememberSettledForDockOrder`). A song opened again starts in its order; nothing moves before the song is laid out.
- A chord or scale sheet opened from the dock pauses a self-scrolling column and resumes it on closing
  (`autoScroll/CLAUDE.md`).
- Breadcrumbs (kinds only): `Dock: …` for every chip, the click, its accents, and the panel's stepper; `Tempo sheet: …`
  and `Playing sheet: …` for every step, reset, and accent.
