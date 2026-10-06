# Cut the song details screen's Chords section between its rows of diagrams, the way a run of tablature is cut between its systems

**Challenged:** amended — the slot cap is `MAX_SONG_CHORDS` (48), not 24: on a phone at the largest text size, or with long numbered names, a row holds one cell, and 24 slots left a last slot of 25 rows that could not be cut (the very bug); a cell wider than the block is clamped and clipped to it as the composed `FlowRow` clamped it, instead of drawn past the column; `measure` guards an unbounded width; the semantics keep the measured width rather than the cell range, so a new `ChordCellLayouts` is re-described at once; the diagrams get a text measurer with a cache; the paging/cutting tests are said to pin existing behaviour (re-traced and probed).

**Kind:** bug / reading  ·  **Severity:** low  ·  **Platforms:** all
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ChordRows.kt` (new, pure),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongChordsSection.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ChordRowsTest.kt` (new),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionPagingTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionCuttingTest.kt`,
`presentation/CLAUDE.md`, `CLAUDE.md`

Lands after plans 30–38 (30 rewrites `rememberSongLyricsModel` / `prepareSongLyrics` in `SongLyrics.kt` and adds
`SongChord.isShapePending`; 36 and 38 change `components/ChordDiagram.kt`, which this plan only calls). Nothing here
conflicts with them: a pending shape is drawn as the empty frame at the cell's full size, which is all this plan needs.

## Problem

The Chords section is the one section of a song that can never be cut. `SongUnits.of` (`SongLyrics.kt`) cuts only
`RenderSection.Lines`; everything else is one chunk:

```kotlin
val ranges = if (section is RenderSection.Lines && isCuttable(section) && section.itemCount > 1) {
    List(section.itemCount) { item -> item..item }
} else {
    listOf(0 until ((section as? RenderSection.Lines)?.itemCount ?: 1))
}
```

and the section draws its cells as one `FlowRow` (`SongChordsSection.kt`):

```kotlin
if (section.isFolded) return@Column
FlowRow(
    modifier = Modifier
        .fillMaxWidth()
        .fadingIn(isFadingIn = hasBeenToggled)
        .padding(top = HEADER_GAP),
    horizontalArrangement = Arrangement.spacedBy(CELL_GAP * fontScale),
    verticalArrangement = Arrangement.spacedBy(CELL_GAP * fontScale),
) {
    section.cells.forEach { cell -> ChordCellContent(cell = cell, chordStyle = chordStyle, fontScale = fontScale) }
}
```

`presentation/CLAUDE.md` documents it ("puts `RenderSection.Chords` after the metadata section, whole and uncuttable
like it"), and so does the KDoc of `RenderSection.Chords` ("kept whole like it"). Since 2026-10-06 no rule keeps any
other section whole (the user's pedal-reading rule: fewest pages beats intact sections, a mostly empty page is never
acceptable), and the Chords section is now the one exception. That costs pages and steps wherever the section is
taller than what is left of a page:

- **Phone held sideways** (360×640dp, a page of about 163dp; `review18/android/63_details_land.png`,
  `64_details_land_p2.png`, `65_details_land_p3.png`): `flowIntoPages` cannot cut the section, so page 1 holds only
  the song's first section (the controls) and the Chords section starts page 2 and runs past it. A section taller
  than the page is then paged through by the screen, and a page only starts at a line where `SongRows.lineTops` has
  one — but `lineTops` are the tops of the single-column *units* (`lineTops = singleColumnUnits.map { arrangement.tops[it] }`),
  and the Chords section is one unit, so it has no line inside it. `nextStepTargetOnce` (`RowSnapping.kt`) finds no
  `lineStart` and takes the page as it is (`page = lineStart ?: page`), less the two lines kept from the page before:
  the next step shows the Chords header and row 1 again with row 2 cut at the bottom, and only the step after that
  brings row 2 whole.
- **Desktop window of 800×600** (`review18/l2-desktop.md`, finding 4; the Hostile 48 song on the guitar): in two
  columns `flowLikeAMagazine`'s `columnEnd` cannot put an uncuttable unit taller than the page into any column, so the
  first row holds the first section alone and the Chords section becomes a single-column row of its own, paged
  through — page 1 is the controls and nothing else.

The section is a `FlowRow` because nobody knew its rows before it was measured; a `SubcomposeLayout` that composes
the cells once the width is known cannot be used, because `SongSectionsLayout` decides the grid from every chunk's
**intrinsic** heights at candidate widths (`measurables[unit]::maxIntrinsicHeight`), which a `SubcomposeLayout`
refuses to answer. Tablature had the same problem and solved it with slots (`runSlotCount`, `SongTabBlock`): a fixed
number of chunks composed up front, each a `Layout` with no children that, at whatever width it is measured or
intrinsically asked at, takes the row its index names (the last one every row left) and draws it. The Chords section
can be cut the same way.

## Fix

### 1. A pure row-breaking rule — `ChordRows.kt` (new, `songDetails` package, no Compose)

```kotlin
/**
 * Where each row of a Chords section's diagrams starts when the section is [width] wide, as the index of its first
 * cell: the cells in their order, [gap] apart, a row ending before the cell that would take it past [width], and a
 * cell wider than [width] a row of its own. What the `FlowRow` the section used to be did, worked out without
 * composing anything, so that the section can be cut between its rows (see [chordSlotCount]).
 */
internal fun chordRowStarts(cellWidths: IntArray, gap: Int, width: Int): IntArray {
    if (cellWidths.isEmpty()) return IntArray(0)
    val starts = mutableListOf(0)
    var used = cellWidths[0].toLong()
    for (cell in 1 until cellWidths.size) {
        val next = used + gap + cellWidths[cell]
        if (next > width) {
            starts += cell
            used = cellWidths[cell].toLong()
        } else {
            used = next
        }
    }
    return starts.toIntArray()
}

/**
 * How many slots a Chords section of [cellCount] cells is drawn as, the most rows it can have: one per cell, up to
 * [MAX_CHORD_SLOTS], the last one holding whatever rows are left. Never fewer than one, which holds the header.
 */
internal fun chordSlotCount(cellCount: Int) = cellCount.coerceIn(1, MAX_CHORD_SLOTS)

/**
 * The rows a chunk holding the slots from [firstSlot] to [lastSlot] draws when the section has [rowCount] rows at its
 * width: the rows those slots name, and every row after them where the chunk ends the section ([isLastSlot]). Empty
 * where the width has no row for its first slot, which then has no height, and nothing is cut in front of it.
 */
internal fun chordSlotRows(rowCount: Int, firstSlot: Int, lastSlot: Int, isLastSlot: Boolean): IntRange =
    if (firstSlot >= rowCount) IntRange.EMPTY else firstSlot..(if (isLastSlot) rowCount - 1 else minOf(lastSlot, rowCount - 1))

/**
 * The most slots a Chords section is cut into: one per chord the song details screen ever lists ([MAX_SONG_CHORDS]),
 * so that every row it can wrap into is a slot of its own and the last one never holds more than one row there. Only
 * the editor's preview, whose definitions are not bounded, has more cells, and it is never cut.
 */
internal const val MAX_CHORD_SLOTS = MAX_SONG_CHORDS
```

(Not tablature's 24. A row can hold a single cell: a phone is one column of the window's width whatever the text size,
so at `MAX_FONT_SCALE` (2.5) a keyboard cell is 190dp and a guitar one 140dp in a column of about 330dp, and a
numbered chord with its letters (`b7(7sus4)/4 Bb7sus4/F`, plan 57) is wider than half a phone at the default size.
Forty-eight such chords are 48 rows; with 24 slots the last would hold 25 of them as one unit taller than many pages,
which `flowIntoPages` cannot cut and the screen pages through without a line inside it — the bug this plan fixes. The
cost of 48 is 48 empty `Layout`s and as many dividers, most of them unmeasured: tablature already composes 24 per
run.)

### 2. The item model — `RenderSection.Chords` (`SongLyrics.kt`)

Give it the same two members `RenderSection.Lines` has, so `SongUnits.of` can treat both alike:

```kotlin
/** One slot per row the diagrams can wrap into ([chordSlotCount]), the first headed by the pill; only the pill while folded. */
val itemCount get() = if (isFolded) 1 else chordSlotCount(cells.size)

/** Every row may start a piece: a row of diagrams is a line, with the header kept with the first one. */
val chunkStarts get() = sectionChunkStarts(List(itemCount) { SectionItemKind.CONTENT })
```

(`sectionChunkStarts` of all-CONTENT items is `0..n-1`, so a cut may fall before any slot but the first; the header is
drawn by slot 0's chunk, which is the "header kept with its first row" rule `Lines` already follows — a header is never
an item of its own.) Rewrite its KDoc: drop "kept whole like it"; say it is cut between its rows of diagrams like any
section, its rows drawn as slots (see `SongChordsSection`).

In `SongUnits.of` change the parameter to `isCuttable: (RenderSection) -> Boolean` and read both kinds:

```kotlin
val itemCount = when (section) {
    is RenderSection.Lines -> section.itemCount
    is RenderSection.Chords -> section.itemCount
    else -> 1
}
val ranges = if (isCuttable(section) && itemCount > 1) List(itemCount) { item -> item..item } else listOf(0 until itemCount)
val cutStarts = when (section) {
    is RenderSection.Lines -> section.chunkStarts
    is RenderSection.Chords -> section.chunkStarts
    else -> null
}
```

and in `SongLyrics`:

```kotlin
val units = remember(sections, foldedSections, canCutSections) {
    SongUnits.of(sections) { section ->
        when (section) {
            is RenderSection.Lines -> canCutSections && section.foldKey !in foldedSections
            // Folded, it is one item - its pill - whatever this says.
            is RenderSection.Chords -> canCutSections
            else -> false
        }
    }
}
```

Nothing else about the units changes: the section is not on a card (`cardStarts` stays -1, no piece padding), its
key stays `RenderSection.Chords::class`, so every chunk is `UnitKey(section = <that key>, firstItem = slot)` — a shape
chosen keeps every chunk's composition, the fold keeps slot 0's (the one with the pill and the pencil) and adds or
removes the others. `LayoutBudget` is untouched: it runs on `model.sections` before `withChordsSection` and counts the
section as nothing. The dividers (`repeat(sections.size - 1 + units.isCuttableBefore.count { it })`) grow by up to 47
unmeasured composables, as tablature's already do. Make `chunkStarts` a `val` in the class body rather than a getter, as
`Lines` has it, since `SongUnits.of` reads it once per slot.

### 3. The cells measured once per section, drawn rather than composed — `SongChordsSection.kt`

Add a class the section's chunks share, built lazily (nothing is measured until the layout asks):

```kotlin
/**
 * The cells of a Chords section as its slots measure and draw them: every name laid out once, every cell's width and
 * height, and the rows they wrap into at the last few widths the layout asked about ([MAX_CHORD_ROW_WIDTHS], as
 * `TabRows` keeps them), shared by every slot of the section so that a width is broken into rows once.
 */
internal class ChordCellLayouts(
    val cells: List<ChordCell>,
    chordStyle: TextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
    fontScale: Float,
)
```

holding, with `density` and `fontScale` exactly as the composed cell had them:

- `names[i]` = `textMeasurer.measure(AnnotatedString(cell.name), chordStyle, maxLines = 1, softWrap = false)`, and
  `secondNames[i]` the same for `cell.soundingName ?: cell.letterName` (null where neither), `NAME_GAP = 4.dp`
  (unscaled, as `Modifier.padding(start = 4.dp)` was) between them;
- `diagramSize(i)` = `(if fretted FRETTED_WIDTH else KEYBOARD_WIDTH) * fontScale` by the matching height, `roundToPx()`;
- `cellWidths[i]` = `max(diagram width, name width + (NAME_GAP + second width where there is one))` — the composed
  `Column(horizontalAlignment = CenterHorizontally)` was exactly that wide wherever the row had room for it. Where it
  did not (a long numbered name and its letters at a large text size on a phone), the `FlowRow` measured the cell at
  most as wide as itself: the name `Text` was held to that width (cut at its last character that fits, one line), the
  second name was left no room, and the diagram was centred in what was left. So a cell is drawn at
  `min(cellWidths[i], width)` — `chordRowStarts` already gives it a row of its own — with its names clipped to that
  width (`clipRect`) and its diagram centred in it, never past the block into the next column or off the screen;
- `cellHeights[i]` = name row height (the taller of the two names, which share a style) + diagram height;
- `gap` = `(CELL_GAP * fontScale).roundToPx()`;
- `geometries[i]` lazily, as `ChordCellContent` remembered it:
  `shape?.let { chordDiagramGeometryOf(it, instrument, root) } ?: emptyChordDiagramGeometryOf(instrument)`;
- `lineWidth` = `cellWidths.sum() + gap * (n - 1)` (all cells on one line) and `widestCell` = `cellWidths.max()`;
- `rowStartsAt(width)` = `chordRowStarts(cellWidths, gap, width)`, cached per width, `width == Constraints.Infinity`
  being one row;
- `height(width, firstSlot, lastSlot, isLastSlot)`: for the rows `chordSlotRows(...)` names, the sum of each row's
  height (its tallest cell, since the editor preview's definitions can mix a fretted and a keyboard one) plus `gap`
  between two rows, plus one more `gap` after its last row **where a row follows** — the trailing gap a tab slot keeps
  (`if (goesOn && rowsAtWidth.isNotEmpty()) rows.rowGap`), so the slots stacked uncut are exactly the `FlowRow`'s
  height and a cut leaves the gap at the bottom of a page rather than the top of the next; 0 for no rows;
- `cellsAt(width, firstSlot, lastSlot, isLastSlot): IntRange` — the cells of those rows, for semantics.

In `SongLyrics`, next to `textMeasurements`, build it once for the page's section:

```kotlin
val chordsSection = sections.firstNotNullOfOrNull { it as? RenderSection.Chords }
val chordLayouts = remember(chordsSection?.cells, chordStyle, textMeasurements.textMeasurer, density, fontScale) {
    chordsSection?.let { ChordCellLayouts(it.cells, chordStyle, textMeasurements.textMeasurer, density, fontScale) }
}
```

The measurements depend on nothing the `SectionSizesPool` is not already keyed by (`fontScale`, `density`, the
styles) or the section's content (`cells`), so the pool's reuse stays right.

### 4. The slot block

Replace the `FlowRow` and the private `ChordCellContent` with a block drawn the way `SongTabBlock` is, but with its
intrinsic sizes answered by its own `MeasurePolicy`. Rewrite `SongChordsSection` to draw one chunk:

```kotlin
@Composable
internal fun SongChordsSection(
    modifier: Modifier = Modifier,
    section: RenderSection.Chords,
    items: IntRange,                // the slots this chunk holds, counted as [RenderSection.Chords.itemCount]
    layouts: ChordCellLayouts,
    chordDiagrams: ChordDiagrams?,
    onFoldToggled: (() -> Unit)?,   // chordDiagrams.onFoldToggled, wrapped by SongLyrics (below)
    isFadingIn: Boolean,            // the fold has been toggled while the page is open
    headerStyle: TextStyle,
    fontScale: Float,
) = Column(modifier = modifier) {
    if (items.first == 0) { /* the header Row exactly as today: pill + FoldToggle, then the pencil's AnimatedVisibility */ }
    if (section.isFolded) return@Column
    ChordRowsBlock(
        modifier = Modifier
            .fillMaxWidth()
            .fadingIn(isFadingIn = isFadingIn)
            .padding(top = if (items.first == 0) HEADER_GAP else 0.dp),
        layouts = layouts,
        firstSlot = items.first,
        lastSlot = items.last,
        isLastSlot = items.last == section.itemCount - 1,
    )
}
```

The header — pill, fold chevron and the Chord shapes pencil fading, scaling and expanding with the diagrams — is
unchanged and stays on its own row in slot 0's chunk; folded, the section is that one chunk.

`ChordRowsBlock` is a `Layout(modifier, measurePolicy)` with **no children**:

- `measure`: `layout(width = constraints.maxWidth, height = layouts.height(constraints.maxWidth, …).coerceIn(min, max)) {}`,
  with `layouts.lineWidth` for the width where `constraints.maxWidth` is `Constraints.Infinity` (as `SongTabBlock` answers
  its natural width there), since `layout` refuses an infinite size.
- It **overrides all four intrinsics** — unlike `SongTabBlock`, which leaves them to its measure block on purpose, since
  a staff's unwrapped width *is* its minimum and that is what gives it a wide row — rather than leaving them to the default (which would run `measure` at an
  infinite width and answer the whole line as the *minimum* width too, making `wideWidthFor` take the section for a
  staff of tablature that needs a row of its own):
  `minIntrinsicWidth` = `layouts.widestCell`; `maxIntrinsicWidth` = `layouts.lineWidth` — **every slot answers for the
  whole section**, as every tab slot answers for the whole run, since the layout narrows a column to the widest thing
  in it and a column narrowed to one row's share would break the other slots into other rows than the page was
  planned with; `min/maxIntrinsicHeight(width)` = `layouts.height(width, …)`.
- Draws in `drawBehind` at `size.width.roundToInt()`: for each row of `chordSlotRows`, cells from the start edge `gap`
  apart, each top-aligned in its row (the `FlowRow`'s default), the name row centred over the diagram or the diagram
  under a wider name row (the `Column`'s `CenterHorizontally`), the second name after the name and bottom-aligned with
  it (`Alignment.Bottom`), then `translate(cellLeft, diagramTop) { drawChordDiagram(geometry, diagramSize, lineColor,
  mutedColor, rootColor, backgroundColor, diagramTextMeasurer) }` with the colours `ChordDiagram` reads (`onSurface`,
  `onSurfaceVariant`, `LocalSecondAccentColor`, `surface`) and plan 36's default `minimumStroke`, the name in
  `LocalSecondAccentColor` and the second name in `onSurfaceVariant` via `drawText`. `diagramTextMeasurer` is a
  `rememberTextMeasurer()` of the block's own with the default cache, as `ChordDiagram` had: `drawFretted` measures a
  base fret's number on every draw, and the page's measurer (`textMeasurements.textMeasurer`, used for the names, which
  `ChordCellLayouts` keeps) has `cacheSize = 0`. In a right-to-left layout (`DrawScope.layoutDirection`) mirror every
  x — cells start at the right edge, and the second name stands to the left of the name — as the `FlowRow` and `Row`
  did; the diagram itself is drawn as it is, not mirrored, as `ChordDiagram`'s `drawBehind` never was (a guitar's low
  string stays on the left).
- Takes no press, as the cells took none (a tap turns the page).
- **Semantics per row.** The cells it shows depend on the width, which composition does not know, so the block keeps
  them as state set from its placed size, and reads them out as one description:

  ```kotlin
  // The width the block was last placed at, which is all its cells depend on that composition does not know.
  var shownWidth by remember { mutableIntStateOf(-1) }
  val shownCells = if (shownWidth < 0) IntRange.EMPTY else layouts.cellsAt(shownWidth, firstSlot, lastSlot, isLastSlot)
  val descriptions = mutableListOf<String>()
  for (cell in shownCells) descriptions += chordCellDescription(layouts.cells[cell])
  // modifier chain, after the padding so that it is the width the rows are drawn at:
  .onSizeChanged { shownWidth = it.width }
  .then(if (descriptions.isEmpty()) Modifier else Modifier.semantics { contentDescription = descriptions.joinToString("\n") })
  ```

  The width, not the cell range, is what is kept: a new `ChordCellLayouts` (a shape chosen, plan 30's searched shapes
  arriving, another instrument, notation or capo) is described from the width already known in the same composition,
  where a range remembered by `layouts` would start empty again and wait for an `onSizeChanged` that a block of the same
  size only gets because its lambda changed. A width that moves (a pinch or resize frame) recomposes this one small
  block, which the page's styles changing on the same frame recompose anyway. The description arrives a frame after
  the first layout, which nothing visible depends on. Each chunk is already a traversal group with its unit index (`unitModifier`), so a
  screen reader reads the header, then row by row in order, wherever the rows landed. This replaces one focus stop per
  diagram with one per row; `chordCellDescription` is unchanged and still names each cell's chord and shape.
  **Recommended: one stop per row (the user's decision D-cut).** **Fallback if a reviewer objects to the state write:** tablature's way — slot 0's block carries every cell's
  description and the others none (`if (slot == 0) Modifier.semantics { … } else Modifier`).

### 5. Wiring in `SongLyrics`

- In the units loop the `is RenderSection.Chords ->` branch passes `items`, `layouts = chordLayouts!!`,
  `onFoldToggled`, `isFadingIn` and drops `chordStyle` (it is in the layouts).
- The fold's fade can no longer be a `remember` inside the section: unfolding composes new chunks, which must fade in
  too. Hoist it beside `toggledFolds`:

  ```kotlin
  // Whether the Chords section was folded or unfolded while the page is open, so that its rows fade in as they come back
  // in every chunk they come back in, rather than only in the one the pill is in.
  var hasChordFoldBeenToggled by remember { mutableStateOf(false) }
  val onChordFoldToggled = chordDiagrams?.onFoldToggled?.let { toggle ->
      remember(toggle) { { hasChordFoldBeenToggled = true; toggle() } }
  }
  ```

  Unfolding is then what unfolding any cut section is: slot 0 keeps its node, the other slots are composed fresh and
  fade in, and the sections under them spring down on `animateBounds`; folding drops them and the pencil leaves as
  today. Nothing animates on a page's first frame.
- Update the comment above `units` ("Every section that may be cut is composed as the chunks it may be cut into…") to
  name the Chords section's slots, and `SongChordsSection`'s KDoc ("The cells flow and wrap rather than scroll
  sideways") to: the cells are drawn in rows that wrap rather than scroll sideways, one slot per row it may be cut
  between, as a run of tablature is drawn as systems.

### What this gives, with nothing else in the layout changed

- `flowIntoPages` and `flowLikeAMagazine` cut it like any section (a piece holds at least one row, the first piece
  the header and the first row); page 1 on a phone held sideways takes the rows that fit after the controls, or, where
  not one fits, the section starts the next page with as many rows as that holds — never re-showing a row.
- Every row is a single-column unit, so it is a `lineTop`: a step through a stretch taller than the screen lands on a
  row's top, never halfway through a diagram, and `LineAnchor` keeps the reader on the row they were at across a pinch.
- In two or more columns (800×600 desktop) the section runs on from the first column into the next and into the next
  row, as lyrics do, instead of being a single-column row of its own with page 1 left holding the controls.
- A song read whole in one row is still never cut; the editor preview (`canCutSections = false`) is one chunk whose
  block draws every row; a section whose single row is taller than the screen (a huge text size) is paged through as an
  uncuttable staff is.

Drop this plan if, once implemented, the drawn cells cannot be made to match the composed ones pixel for pixel on the
four platforms (name position, diagram size, colours in both themes) — the composed `FlowRow` is then kept and only the
documentation stays as it is. Do **not** fall back to "report each row as a `lineTop`" without cutting: the rows of a
composed `FlowRow` are unknown to the grid, and it would fix only the repeated row, not page 1.

### Documentation

- `presentation/CLAUDE.md`, Chord diagrams paragraph: replace "puts `RenderSection.Chords` after the metadata section,
  whole and uncuttable like it and keyed as one section whatever it holds, so a shape chosen or the fold lays nothing
  out again" with: puts `RenderSection.Chords` after the metadata section, keyed as one section whatever it holds (each
  of its chunks by its slot, so a shape chosen or the fold composes nothing afresh but the rows that come back), and
  **cut between its rows of diagrams like any section**: it is drawn as slots (`chordSlotCount`, up to `MAX_SONG_CHORDS`, 48), the first
  headed by the pill, each holding the row of cells its index names at the width it is measured at
  (`chordRowStarts`, `chordSlotRows`, `ChordRows.kt`, tested) and the last every row left, the cells drawn rather than
  composed (`ChordCellLayouts`: names measured by the page's measurer, diagrams by `drawChordDiagram`), so that an
  intrinsic measurement at a candidate width already counts the rows they wrap into, every slot answering for the
  whole section's width; a screen reader reads it row by row. Replace "and a `FlowRow` of cells — the name in the
  chords' style and accent over its diagram, growing with the text size, wrapping rather than scrolling sideways,
  taking no press" with "and rows of cells — the name … over its diagram, growing with the text size, wrapping rather
  than scrolling sideways, taking no press".
- `presentation/CLAUDE.md`, `SongLyrics.kt` paragraph: in "Every section that may be cut is composed line by line
  (`SongUnits`, one chunk per line, a comment, a line of a grid or a system of tablature: …)" add "or a row of the
  Chords section's diagrams, drawn as slots the same way (see Chord diagrams)".
- Root `CLAUDE.md`, "How every chord of a song is fingered is shown at its top": replace "folded by one preference for
  every song and scrolling away with the song's first page" with "folded by one preference for every song and cut
  between its rows of diagrams wherever a page ends inside it, like any other section".

## Tests

New `ChordRowsTest` (commonTest, `songDetails`):

- `cellsFlowIntoRowsAtTheWidth` — `chordRowStarts(intArrayOf(56, 56, 56), gap = 6, width = 118)` is `[0, 2]`; at
  `117` it is `[0, 1, 2]`; at `Int.MAX_VALUE` it is `[0]`.
- `aCellWiderThanTheWidthIsARowOfItsOwn` — `chordRowStarts(intArrayOf(56, 200, 56), 6, 120)` is `[0, 1, 2]`;
  `chordRowStarts(IntArray(0), 6, 120)` is empty.
- `aSlotHoldsTheRowItsIndexNamesAndTheLastOneTheRest` — `chordSlotRows(rowCount = 5, 1, 1, false)` is `1..1`;
  `(5, 3, 3, true)` is `3..4`; `(2, 3, 3, false)` is empty; the uncut chunk `(5, 0, 47, true)` is `0..4`.
- `theSlotsAreAsManyAsTheCellsUpToTheCap` — `chordSlotCount(1) == 1`, `(5) == 5`, `(48) == 48`, `(60) == MAX_CHORD_SLOTS`
  and `MAX_CHORD_SLOTS == MAX_SONG_CHORDS` (every row the song details screen can have is a slot of its own).
- `aFoldedChordsSectionIsItsHeaderAlone` — `RenderSection.Chords(cells = List(5) { cell }, isFolded = true).itemCount
  == 1`; unfolded it is 5 and `chunkStarts` is `[0, 1, 2, 3, 4]` (a `ChordCell(name = "G", soundingName = null, instrument =
  ChordInstrument.GUITAR, root = 7, selection = SelectedShape(null, SelectedShape.Source.DEFAULT))`).

`SectionPagingTest`:

- `aChordsSectionIsCutBetweenItsRowsRatherThanRunPastThePage` — the landscape phone: `flowIntoPages(sectionStarts =
  intArrayOf(0, 1, 4), heightAt = { unit, _ -> intArrayOf(90, 130, 100, 100)[unit] }, isNarrow = { false },
  isCuttableBefore = { it >= 2 }, piecePadding = IntArray(2), sectionGap = 12, maxRowHeight = 163)`: rows
  `[0, 1, 2, 3]`, `columnCounts` `[1, 1, 1, 1]`, no row `joinsPrevious` — every page starts with a row of diagrams and
  none is shown twice.

`SectionCuttingTest`:

- `aChordsSectionTallerThanThePageRunsOnIntoTheNextColumn` — the 800×600 desktop: `flowLikeAMagazine(sectionStarts =
  intArrayOf(0, 1, 5), columnCount = 2, heightAt = { unit, _ -> intArrayOf(200, 150, 150, 150, 150)[unit] },
  isCuttableBefore = { it >= 2 }, piecePadding = IntArray(2), sectionGap = 20, maxRowHeight = 450)` is one row of two
  columns (`rows` all 0, `columns` `[0, 0, 1, 1, 1]`, `columnCounts` `[2]`), where the same section uncut
  (`isCuttableBefore = { false }`) leaves the first section alone in a row of its own (`columnCounts` `[1, 1]`) —
  assert both, so the test shows what the cut buys.

The two grid tests run the unchanged `flowIntoPages` and `flowLikeAMagazine` and pass before this plan as well
(re-traced by hand and probed at dac1d9d59): they pin the behaviour the cut relies on, and show what it buys. What fails
before the plan is `ChordRowsTest` and `aFoldedChordsSectionIsItsHeaderAlone` (`RenderSection.Chords` has no `itemCount`
or `chunkStarts`); `SongUnits` is private, so the wiring between them is checked by hand.

Run `./gradlew :presentation:desktopTest`. The drawing, the intrinsic answers and the semantics are UI and are checked
by hand.

## Manual check

1. **Phone held sideways** (the 360×640dp AVD, see `smallest-supported-screen` in memory): open the demo "House of the
   Rising Sun" and a song of 12+ chords on the guitar, turned sideways. Page 1 holds the controls and, where a row fits
   under them, the Chords header and its first rows; every Down press starts on a whole row of diagrams and no row is
   shown on two pages. Repeat at the smallest and the largest text size, and on the ukulele and the keyboard.
2. **Desktop at 800×600, default text size**: the same two songs and a song of 48 unusual chords on the guitar (the
   review's "Hostile 48"): the Chords section runs from the first column into the second and on into the next page;
   page 1 is never the controls alone. Resize the window and pinch: rows rewrap with the width, nothing overlaps.
3. **Phone upright** and a **tablet / wide desktop** where the song fits one row: the section looks exactly as before
   (cells, names, sounding/letter names under a capo or a numbering, gaps, centring of a long name over its diagram).
   At the largest text size on the upright phone, a numbered chord with letters wider than the screen is cut at the
   column's edge as before, nothing drawn past it; 48 keyboard chords at that size are 48 rows, every one starting a
   step, none paged through.
4. **Fold and unfold** the section: the rows fade in in every chunk they come back in, the sections under them spring;
   the pencil comes and goes on the header's row; read only mode has no pencil. Folded, the section is its pill alone.
5. **Chord shapes sheet**: choose another shape; the cell redraws, nothing jumps.
6. **TalkBack / VoiceOver**: the header, then each row read as its chords and shapes in order, in one and in two
   columns; the next row after a page turn.
7. **Editor preview**: the Chords section of `{define}`s draws every row as before and is never cut.
8. **Light and dark theme** and every palette: the names in the second accent, the diagrams as the Chord shapes sheet
   draws them.
