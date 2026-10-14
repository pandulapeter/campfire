<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens/songDetails

The song details screen. What the first section holds and read only mode are in `ui/CLAUDE.md`; chord diagrams in
`ui/chords/`; the click in `ui/metronome/`.

The guarantee most of this file serves: **every line of a song can be reached with Up and Down alone** (a page turner
pedal), at any text size on any screen. `LayoutBudget` is its only exception.

### The songs it pages through

- `songDetailsSongsOf` resolves the destination against `songLookup` (the library and whether it has been read). Before
  the read it resolves only a destination whose songs are all present, so a restored setlist pager never restores its
  page index onto the wrong song over a partial batch.
- Once every song it names is gone from a read library, the screen closes itself.

### Rendering (`SongLyrics.kt` and its siblings)

- Tabs and grids are monospace: columns of characters that must line up.
- A comment's bracketed chords are transposed and colored (`ChordProHighlighter.chordsOfShownText`, shared with the
  editor). A chorus recall draws everything `ChordProBlock.ChorusRecall` carries, headed once.
- **A section cut by a comment, break, or `{transpose}` is drawn whole again** (`joinCutSections` (shared with the PDF), over
  `Section.isContinuation`), so the comment stays between its lines rather than becoming a unit that could land
  elsewhere. Comments opening or ending a section join it (`Comment.placement`), so every comment inside a section
  folds with it; one between sections is a unit of its own that never folds. A chorus recall is the one cut not joined.
- A later `{transpose}` leaves a "Key: A" line (`RenderSection.KeyChange`) in the reader's transposition and notation,
  the capo left out as in the Transposition control; omitted where the song has no `{key}` or the chords are off.
- Lyrics-only mode drops tab and grid lines (and comments inside them, `isInTabOrGrid`), a section left empty whole, and
  the info section's key, capo, tempo, and time line (the section itself where nothing else is, `withMetadataSection`).
- Lyrics take their direction from content (`TextDirection.Content`), so chords of an RTL line hang leftwards.
- Chords are drawn over the lyrics, not composed. Lyrics under a wider chord are padded with non-breaking spaces
  (`padLyricsToFitChords`); a padded line may break only between two chords at a word boundary (padding whitespace is
  non-breaking, a zero-width space is the break), so no chord is pulled back over the one before. Overflow is clipped by
  the line and each pager page, never wrapped onto the lyrics.
- Because chords are drawn, each chorded line's content description is its ChordPro form (`[Am]There is a…`), and a tab
  block's its lines, so screen readers read the chords.
- One `TextMeasurer` per page; `SongTextMeasurements` caches chord names, lyric widths, line heights, tab rows, and
  aligned grid bars per page, keyed on the styles and the measurer (not the song), so a transposition only measures chord
  names it has not drawn yet.

#### Tabs and grids

- Each run of `ChordProLine.Tab` is one `SongTabBlock`: its columns only line up measured together. Row cuts are
  `ChordProTabWrapper`'s rule. The block draws text rather than composing it because a section is measured at several
  widths: a childless `Layout` answers an intrinsic measurement by running its measure block, so the height for a
  candidate width already counts the rows the tab wraps into.
- A run with no staff line (chord names over lyrics in `{start_of_tab}`) is preformatted text, cut between words
  (`wrapPreformatted`), a chord line kept with its lyrics. **Nothing on the page scrolls sideways.**
- Grids: `alignedGridBars` (`GridColumns.kt`, shared with the PDF) pads every bar so bar lines align across lines; text
  after a line's last bar line is aligned with nothing so it never widens a bar; each bar is one text, since only one
  monospaced text keeps its columns. A too-wide grid line breaks between bars (`SongGridLine`), a too-wide bar inside
  itself.
- A paragraph holding nothing but one tab or grid run is headed "Tab" / "Grid" (screen and PDF); one with lyrics around
  the run is not.

#### Tempo and time changes

Product side in `ui/metronome/CLAUDE.md`.

- **A later `{tempo}` / `{time}` starts a page of its own** (`RenderSection.Timing`, `showsTiming`): never joined into
  its section; a read-only `SongTimingLine` stands in its place (an override scaled in by `withTempo` / `sectionBpm`); the
  rest of the section is headed by its fold toggle alone and folded under the section's key plus `~n`, so switching the
  Metronome feature does not renumber later folds. With the feature off it is joined over like a `{transpose}`.
- A timing line with no song line after it is dropped (`withoutEmptyTimings`, after `LayoutBudget`).
- Each stretch between changes is searched as a song of its own (`SongUnits.timingStarts`, `searchSegment`,
  `SectionGrid.concatenated`), each starting a page, never half of a side-by-side pair; such a song is always stepped.
- A change before the first line starts no page (`timingStretchesOf`); `SongRows.timingSections` reports it at section 0.
- Unpaged layouts (the editor's preview, a songbook) show changes as lines; a songbook with changes is one column
  whatever the width (`widthColumnCountFor`).

### Folding

- Every section with a header folds, and so does every tab or grid run inside one (`FoldedRuns`). Saved per song in
  `UserPreferences.foldedSections` (`toggleSectionFold`), the same wherever the song is opened from: it is the reader's,
  so it never reaches a setlist, an export, or a sync run; a rename carries it, a deletion drops it.
- **A fold is keyed by what the file calls the section** (label as written, else its kind) plus which occurrence
  (`chorus#2`), and a run by its section's key plus its own label or kind (`Intro#1/Picking pattern#1`). Never by
  position (a verse added above would shift every fold) and never by the shown heading (translated).
- The page remembers only keys toggled while it was open, so only those fade in.
- Every section has a header: unnamed kinds labelled in the app's language (`DefaultSectionLabels.labelOf`, shared with
  the PDF), numbered where a song has several (`withNumberedSections` in `SectionNumbering.kt`,
  `UserPreferences.shouldNumberSections`). Only unlabelled sections and those labelled with the kind's own name are
  numbered, and "Verse 2" keeps its number, so no two share one. The number is `Section.number`, not part of the label,
  so the saved folds do not move with the setting. An unnamed `{chorus}` recall takes the repeated chorus's number.
- A plain paragraph, and the rest of a section a chorus recall cut, get a chevron-only pill (`UNNAMED_SECTION_HEADER`),
  named for screen readers by `firstLyric`. The editor's preview folds nothing but draws that pill empty, keeping the
  chevron's room, so it lays out like the details screen.
- A run's own toggle is labelled by its environment's label, else "Tab" / "Grid"; two back-to-back environments with
  different labels fold apart; a section that is only one run folds as the section, no second toggle.
- A chorus has no pill and folds from its whole title row; its card's paddings are inside `SongSectionContent` so the
  press reaches the card's edges. The chevron is `components/ExpandChevron.kt`, shared by every fold in the app.
- Folds are part of `UnitContent.foldedKeys`, so a fold re-measures only its own section (the grid is still
  re-searched). An unfolding run animates only opacity: a height still animating would be measured halfway.

### The first section (`SongMetadataSection.kt`)

- Flows through the same rows and columns, never cut. Controls are `SongPlayingControls` (`SongPlayingControlsRow.kt`)
  in `BalancedRows`: one line, two and two, or one per line, never three over one; a moved control springs on the
  sections' spring via its `layoutId`.
- **Controls scale with the lyrics like the header pills**: `fontScale` grows their content, not their padding, so each
  is exactly `songControlHeight` (the pill's `titleStyle` plus `HEADER_VERTICAL_PADDING`). Scaling a pill whole towered
  over the page. `UserPreferences.MIN_FONT_SCALE` is what keeps them big enough to hit.
- Tool buttons (`SongPlayingTools`, `SongToolButton`): the tuner after the capo (or the transposition), the metronome
  after the time signature; a button whose group is absent goes after the other (`playingToolPlaces`, tested). With
  chords and metronome both off there is no section and the tuner is an overflow menu entry (`tunerAction`).
- Whether the section holds either button is part of `RenderSection.Metadata`, since measured sizes are keyed by it.
- Read only line (performance mode, archived setlist): always names capo, tempo, and time, since an absent value would
  read as unknown (`readsDefaults`: "Capo 0", 120, 4/4); the editor's preview names a capo only where the text writes
  one. It reads values as the click and the steppers do (tempo clamped to the click's range, `C` as 4/4, capo within
  the neck); split around the tools.
- In the editor's preview (`isSongInfoShown`) the section is the About the song tiles (`SongInfoCard`), emitted under one
  key whatever it says, so its cover waits for typing to pause rather than fetching half-typed addresses.

### The About the song sheet and its editors

- **The one place a song's description is edited**: each group is a tile opening its own sheet; the cover opens the
  cover search. Key, capo, tempo, and time stay on the page; the sheet only reads what the file declares and opens Song
  defaults. No menu lists the sheet's editors one by one; Edit song file is the details menu's first own entry.
- `DialogType.SongInfo`, read live from the song's text and closed with the song; opened only by the title (below). The
  sheet and the preview's card share `SongInfoBody`; `songPlayingOverrides` is shared with the Song defaults sheet.
- Group titles are always plural, so a title does not change wording under the finger adding the first item.
- Chips in a tile are labels; the tile takes the tap. An empty group's call to action (`SongInfoAction`) is exactly
  `tagPillHeight` tall, so the tile keeps its height as its first chip arrives.
- Links are outlined buttons (they open a page, inside a tile that opens Manage links). **No tile is shorter than the
  links tile with one link** (`songInfoTileMinHeight`). Link buttons report their height without the touch target slack
  (`withoutTouchTargetSlack`), or their tile's centering would be off. The editor's compact card (`isCompact`) shows
  links as chips.
- Over the editor (`SongEditTarget.EditorDraft`) it reads `CampfireViewModel.editorDraft`, the key as typed, no
  overrides; its tiles edit the typed text.
- `SongMetadataDialog.kt`: the duration's digits fill from the right like a timer (`DurationDigits.kt`, `428` is `4:28`,
  since a number pad has no colon), written as `m:ss`, at most `99:59:59`; a duration it cannot read is left alone on
  Save. Opens with no field focused. **Writes only the fields changed** (`SetChordProMetadataUseCase`), so a field
  synced in meanwhile survives. A blank field removes the directive. A new title or artist is not a rename.
- `SongLinksDialog.kt`: a draft written once on Save. Rows are keyed by an id of the dialog's own so a moved row slides
  rather than two rows trading contents. Links keep the file's order (tags and languages are alphabetical,
  `sortedAlphabeticallyBy`). Duplicate or unusable addresses and a named row without one block Save; empty rows are
  dropped (`songLinksToSave`); links synced in while open but never offered are kept.

### Building the model, and the budget

- `rememberSongLyricsModel` parses, transposes, and splits off the main thread, except the first rendering of the page
  on screen (and pages before it), built in place (`buildsModelInPlace`) so the page never opens on an empty frame and a
  step back from a song's top has the previous song's end to land on. Search-found chord shapes follow from
  `Dispatchers.Default`. Later builds keep the previous rendering until the next is ready.
- **`LayoutBudget` caps what is laid out** (3,000 lines, 200,000 characters): every section is fitted and every chorded
  line measured on the main thread, and a concatenated songbook or a log file froze the app for seconds (on a phone,
  killed it). The model stops after the last section that fits (or inside an oversized first one) and says so; the file
  is untouched and the header is built from all of it.

### Layout (`SongSectionsLayout`, `SectionGrid.kt`, `SectionCutting.kt`, `LayoutScore.kt`, `TextFitting.kt`)

- **Fewest (widest) columns that fit the song in one row**, else the fewest that fit it in several (`searchColumnCount`),
  from intrinsic heights; a failing candidate jumps to the smallest count that could fit. A single-column window skips
  the search (it is paged).
- Column width 384–560dp scaled by `fontScale` (288dp narrow, below). Once decided, each column narrows to its widest
  content and each row's leftover width is shared evenly before, between, and after its columns, clear of the step
  buttons where needed.
- **Several counts fit one row → the most even fill** (`balancedAmong`): width filled against height filled; more columns
  only by a margin and wrapping no more lines.
- **A page with room spreads out** (`SectionGrid.arrange`'s `maxExtraSectionGap`): each column of a single-row page is
  centered vertically, the room widening section gaps first (by at most a chorded line), the rest 45% above, since exact
  centering reads low. A multi-row page moves down whole. The room is that the grid was decided for, so it never
  scrolls.
- **A fold holds the columns in place for 900 ms** (`ColumnCenteringHold`) before re-centering, or the header just
  tapped would move and a second tap miss. Armed in a `SideEffect` ahead of the frame's measure, released only by the
  delay; `SongSectionsLayout` records spreads and page shifts on unheld lookahead passes, `arrange`'s `heldSpreads` /
  `heldPageShifts` place them. `SongLyrics` passing no hold turns it off.
- Rows are read across like systems of sheet music, never column by column, which would send the reader back up. **A
  section taller than the screen gets a row of its own**: a pedal reader cannot go back up to a column beside it.
- `flowIntoRows`: a dynamic program over row ends and column counts, fewest pages first (a tall row counts its pages,
  `pagesOf`), then the one reaching furthest (`isBetterRow`), leaving slack on the last page. Columns within a row are
  balanced by a DP over squared deviation from the ideal height; greedy fill dumps all slack on the last column.
- A section wider than a column (a tab staff) may take a wider row, up to the full width, where that saves a page or
  lowers the row; neighbours may stack in that row's single column (`forEachWideRow`), so a short section beside a tab
  gets no screen of its own. The editor's preview does this too.
- **A song not read whole in one row is set like a magazine** (`flowLikeAMagazine`): columns filled to the screen,
  sections running on into the next column or row, since every page ending early is an extra page turn. Fewest pages
  first, then `LayoutScoring` (`ColumnFlow.program`) prices cuts (more inside a section of four lines or fewer, almost
  nothing after an empty line or before a comment), pieces of one or two lyric lines, and the squared empty space at the
  end of non-last columns. Diagram rows, tab systems, comments, and the first section are never "stranded lines". Each
  page is then evened out (`ColumnFlow.evenOut`); the last takes the fewest columns that hold it. Compared against the
  whole-section layout by `SectionGrid.pageCount`, then `score`; a full tie keeps sections whole. A song read whole in
  one row is never cut.
- **Narrow columns** (`NARROW_COLUMN_WIDTH`, 288dp): counts where at most a quarter of lyric lines wrap
  (`fitsNarrowColumns`), taken only for strictly fewer pages (`withNarrowColumns`).
- **Text fitting** (`TextFitting.kt`): 97.5%, then 95%, never below `MIN_FONT_SCALE`, the first that saves a page
  (`chooseFitScale`), only searched where a lower bound says it could. Measured wider and drawn through a scaled layer
  (hit testing scales with it), so nothing recomposes and the first frame is fitted (`DecidedGrid.fitScale`). Deferred
  while the inputs keep changing (`isFitDeferred`: pinch, resize), since it costs two or three more layouts a frame.
- **A single-column width is paged** (`flowIntoPages`) with the same scoring. Two neighbours that fit half the column
  unwrapped may sit side by side (`SectionGrid.joinsPrevious`); such a pair is never cut. Something uncuttable may move
  to the next page only if this one is at least half full; otherwise the page runs past the screen. Rows share a start
  edge (`sharesKeyline`).
- The editor's preview and a songbook (over `MAX_CUT_SECTION_COUNT`, 200 sections) keep a plain, unmeasured single
  column, neither cut, narrowed, nor fitted: the search would rerun on every pinch frame, and typing would move pieces.
- **Chunks** (`SongUnits`): a cuttable section is composed per line (a line, comment, grid line, tab system, or diagram
  row), so `SongRows.lineTops` is known. Tab runs are drawn as slots (`runSlotCount`, `SongTabBlock`'s `slot`): slot *i*
  holds row *i* at the measured width; an empty slot has no height and no cut before it; **every slot's intrinsic width
  is the whole run's**, or narrowing the column to one slot would rewrap the run. Cuts only where `sectionChunkStarts`
  allows: a line each side, never after a comment or before an empty line. A folded section is one chunk; the preview
  never cuts (`canCutSections`).
- A continuation piece shows no header. Chorus cards are drawn by the layout behind the chunks, one per piece, padding
  inside the first and last chunk and added at a cut, so an uncut card is unchanged.
- **Motion** (`SectionMotion`): a single change springs with `animateBounds` in a `LookaheadScope`; continuous changes
  (pinch, resize, title row collapse, metronome panel; `GLIDE` from the second change within 200 ms) follow directly,
  only a new grid's jump glided (`SectionGlides`, an offset from the lookahead pass, so each section is measured once).
  The preview animates neither (`NONE`).
- `animateBounds` measures with `Constraints.fixed`, which cannot hold 262,143 px with any width: animated sections are
  measured no taller than half that (`maxAnimatedSectionHeight`), and one reaching it loses the modifier.
- Each section is a traversal group indexed in song order, or screen readers read across the columns.
- `SongDetailsScreen` passes `availableHeight` from a `BoxWithConstraints`: inside `verticalScroll` height is infinite.

### Scrolling, stepping, and snapping

- **A fling rests the top of the screen exactly below a row's divider** (`RowSnapping.kt`, rows from `SongRows`); a row
  taller than the screen is landed on at its top and then scrolls freely. Rows are measured by content, never trailing
  space. The fling finishes the sub-pixel remainder. The top fade is the scroll past the current row's top
  (`SongRows.scrolledIntoRow`), so a rested row is unfaded. Dividers come from the lookahead pass, not mid-animation.
- **Every row shorter than the screen is followed by space down to its bottom** (`minRowPitch`, where `rowViewportHeight`
  is given; never in the preview), the last too (`minLastRowHeight`), so a rest shows one row. Only where the song
  scrolls anyway.
- **Step buttons** (`StepButtons`, one pair over the whole pager, answering the current page's `SongStepper`): stops are
  rows or pages, or sections in an unpaged single column, each stop `EDGE_FADE_SIZE` above its section. `SongStepper`
  scans offsets without sorting or allocating per scroll update.
- Inside a stop taller than the screen a step pages (`nextStepTarget`, `previousStepTarget`, `ReadingWindow`): the screen
  less the top fade, the bars, and two lines (at most a third), adjusted to start at a line (`lineTops`); it moves on only
  once the stop's end is in view. Buttons read "Scroll down" / "Scroll up" while paging.
- A press moves at least a line where that skips nothing (near-equal clamped stops are chained); the last press hands off
  only once the whole song is on screen.
- **A press mid-step steps on from its target**, so two quick pedal presses are two steps.
- Previous leaves only at the very top; next only once the end has been on screen; a song that fits has neither. In a
  setlist they go on to the next song's top and the previous song's end (that page's scroll is set to its end
  beforehand, else a second press skips it).
- **They never cover a line**: a scrolling song is laid out narrower (`stepButtonInset`); the full width is kept where the
  song fits there as one row and the full-width grid does too (`isReadWithoutStepping`), never in a setlist
  (`keepsStepButtonInset`). An unpaged single column is assumed to scroll.
- The dots (`StepProgressIndicator`) count reachable stops (`reachableStops`), the top padding belonging to the first
  (`SongRows.belowPadding`). The stop is read through derived state so it recomposes per stop, not per pixel; drawn and
  faded by color, not a layer, for the web build (see `FastScroller`).
- **Tap to step** (`stepOnTap`): top half previous, bottom half next, only for taps nothing inside consumed; never a long
  press, a right or middle click, a fling-stopping press, or one the system cancelled (Android's home and back gestures
  arrive as a consumed release). A drag under 250 ms and a tenth of the height is still a tap (a strumming hand slides),
  stepping from where the song was at finger-down and replacing the fling.
- **A fling goes no further than one step** (`oneStepCappedTarget`); a drag goes anywhere.
- Focus: the buttons take none, and the screen retakes it when lost to nothing while uncovered (`isUncovered`), or
  Compose spends the next arrow on a focus search, which scrolls. While something is over it the step keys are
  swallowed: the export screen shares the window and takes focus itself (`focusTarget()` on its root).
- **A held step key is one step until released** (window focus loss counts as a release): key repeats would chain pages.
  A held arrow still scrolls where there are no buttons.

#### Keeping the reader in place

- The scroll keeps pixels across a resize, pinch, fold, or transposition, so `RowSnapFlingBehavior.keepReaderInPlace`
  records at rest a `ReadingAnchor` (the stop's first section and the offset past it) and, inside a tall stop, a
  `LineAnchor` (section, chunk, fraction). Once a new layout has held still for 250 ms it scrolls there, snapped by
  `settledScrollTarget`. The wait prevents flicker during a pinch; anchors are taken only at rest and never from its own
  moves, so a pinch through many layouts returns to the starting row.
- A height-only change (metronome panel, title row collapse) only changes row trailing padding, so it is followed in the
  same layout pass (`onRowsPlaced`), before the scroll places the song: the divider never slides into view. Only the
  current page follows frame by frame; pages beside it use `pageViewportHeight`.

### Text size and the steppers

- `fontScale` is snapshot state on the view model, not a flow, so a pinch recomposes only its readers. Saved to
  `UserPreferences.fontScale` debounced; a pending value is written on `onCleared` and before the desktop exits. The
  stored value wins whenever nothing local is pending, so Settings, a restore, or sync are followed.
- Pages beside the current one read `settledFontScale` (held still 500 ms; a stepper or shortcut sets it at once; a
  swipe settles it, so the song being left does not jump back).
- Kept in whole percent, so sub-percent pinch frames lay nothing out again; `FontScaleAccumulator` adds the wheel's
  unrounded deltas, since each fraction would otherwise round away.
- `fontScaleGestures`: pinch damped by an exponent; Ctrl / Cmd + wheel counted in notches (`verticalWheelNotches`, the
  web reporting pixel or line deltas); both consumed in the initial pass. A touchpad pinch the platform reports (macOS
  desktop, browsers' Ctrl-less wheel) goes to `magnifyByTouchpad` with the same `PINCH_SENSITIVITY`. Ctrl / Cmd + plus,
  minus, and zero are answered by the desktop window and the web page (`zoomSongText`), since the browser acts first.
- Steppers are shorter than Material buttons and lower `LocalMinimumInteractiveComponentSize` to match (touch still gets
  48dp). Height is `STEPPER_HEIGHT` in a bar or menu, `songControlHeight` in a song; `trailing` holds the Tap segment.
- **The transposition wraps around the octave** (`wrapTransposition`, −5 to +6), when stepped and when read, so its
  buttons are never disabled.
- The app bar: the text size stepper where it fits, else at the end of the menu (`MenuStepperRow` as `menuFooter`, the
  menu staying open while pressed). As the bar narrows the text size goes into the menu, then Choose setlists, then the
  cover, each while the title keeps 160dp (`appBarButtons`, from the settled width, for every page at once). Performance
  mode: the stepper in the bar where the title keeps 160dp (`showsFontScaleInPerformanceBar`), else alone in a menu; the
  cover only where the title keeps 160dp beside both (`showsCoverInPerformanceMode`).

### The app bar

- **The subtitle (`SongHeaderNote`) names the sounding key**, transposition and capo applied (unlike the Transposition
  control), and inside a setlist the duration, after 16dp dots (the cards' 24dp would grow the bar and shrink the
  lyrics' room). Worked out for the song its crossfaded content was composed for, since a page change draws two. Chords
  off drop the key. No tempo: the first section says it.
- **The title scrolls the song to its top, and at the top opens the About the song sheet** (its only way in). A chevron
  (`ic_expand`) shows while a tap would open it; its room is kept wherever the sheet is offered, so a long title is not
  cut afresh. The touch target (`titleTouchTarget`) is all the bar's free space but reports only the title's room. No
  ripple: a press dims the title, cover, and chevron. The scroll is read inside the title slot, so passing the top
  recomposes only it. Nothing is taken at the top where the sheet is not offered.

### Keeping the display on

`Modifier.keepScreenOn()` on the root, since a song is read with both hands on the instrument: the view flag on Android,
`idleTimerDisabled` on iOS, a Screen Wake Lock on the web (retaken when the tab is shown, secure contexts only), nothing
on the desktop.

### Keyboard (`SongKeyboardShortcuts.kt`)

- Up and Down scroll a tenth of the viewport, linearly over the key repeat interval; Left and Right change the setlist's
  song. Where the step buttons are, Up, Down, Page Up, and Page Down press them instead (a Bluetooth page turner sends
  exactly these; a nudge leaves the player mid-verse); elsewhere the page keys are unconsumed. Counted from the page the
  pager is heading to, so two quick presses go two songs.
- The screen takes focus as it opens (nothing on it would otherwise), and the handler is in the preview pass, so it sees
  keys wherever focus has moved and the scroll container never handles an arrow. The trade is 2D focus traversal; Tab
  still works.
- Left and Right are unconsumed at the setlist's ends and for a single song; all four with Alt, Meta, or Ctrl (Alt + Left
  is the web's Back, Cmd + Left / Right a Mac's Back and Forward).
- The pager bar numbers by place in the setlist, missing entries included; pages are only the songs that exist.

### The metronome button

- `MetronomeButton` shows and hides the panel (`toggleMetronomePanel`): opening starts nothing, closing stops a playing
  click. M toggles the click from anywhere on the song, opening the panel with it.
- The pendulum (`MetronomeIcon`, `ic_metronome_body` + `ic_metronome_pendulum`, `rememberMetronomeIconBeat`; the
  navigation item too) is at an arc end on every heard beat and swings over one beat at the tempo read from `playback`,
  each beat restarting the swing from where it is, so it never drifts from the ear. The Animate switch stills it.
- `TempoStepper` is highlighted while overridden and reset by a tap on its value; Tap sits in its `trailing` slot, since
  it sets the number the stepper steps.
