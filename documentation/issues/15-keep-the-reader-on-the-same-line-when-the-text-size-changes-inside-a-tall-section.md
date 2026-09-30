# Keep the reader on the same line when the text size changes inside a section taller than the screen

**Challenged:** amended — the plan's line index was counted among the line tops of the reader's *stop*, which names
nothing stable in a song stepped by rows (a stop is named by the first section of its row, and a new layout flows other
sections into that row, so line 12 of it becomes a line of another section), fell back to an offset overloaded with two
meanings, anchored the viewport's top (under the fade) instead of the line being read, and clamped the remainder to the
new line's height, which moves the reader forward inside a wrapped line or a run of tablature as the text shrinks. The
fix now anchors the line the reader is at by its section and its place in that section (both independent of the layout,
since a section in a single column is always composed one chunk per line), keeps the reader's place inside it as a
proportion, resolves the stop from that line's section, and keeps a reader resting exactly on a stop on it. Probed in
the scratchpad (`challenge/a15`): 20 ↔ 36 ↔ 60 px lines keep the line at the reading top in both directions and never
land later inside it; a row whose sections move to another row resolves to the right one.

**Kind:** bug  ·  **Severity:** high (a line skipped for good under the pedal)  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RowSnapping.kt`
(`SongRows`, `ReadingAnchor`, `readingAnchorOf`, `anchoredScrollOffset`, the two calls in `keepReaderInPlace`),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt` (where
`SongSectionsLayout` builds the `SongRows`, the `lineTops =` line),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RowSnappingTest.kt`,
`presentation/CLAUDE.md` where it describes the reader staying in place across a resize or a text size change

## Problem

`0e0c85bdf` keeps the reader on the same row when the window is resized or the text size changes. The live run at
9ab7ca54e (a 400-line single section, 1000 × 450 window, six Downs to lines 30–36 at text size 1.0, then the size
changed to 1.8, to 0.6 and back) showed the scroll staying at 2597 px every time — and the *lines* under it changing:
at 1.8 the screen showed lines 17–19 (12 lines back), at 0.6 lines 48–59 (about 30 lines past what 1.8 had shown).
The lines in between were never on screen at 0.6, so the next Down skipped them for good, which is the one thing the
song details screen promises never to do. A window resize kept the line, since a resize keeps the line heights.

The anchor is a stop plus a **pixel** offset into it:

```kotlin
internal data class ReadingAnchor(val section: Int?, val offset: Int)

internal fun readingAnchorOf(scroll: Int, rows: SongRows): ReadingAnchor? {
    …
    ReadingAnchor(section = rows.stepSections[stop], offset = (scroll - rows.stepOffsets[stop]).coerceAtLeast(0))
}

internal fun anchoredScrollOffset(anchor: ReadingAnchor, rows: SongRows, viewportHeight: Int, maxValue: Int): Int? {
    …
    return (stepOffset + anchor.offset).coerceAtMost(lastFree).coerceIn(0, maxValue)
}
```

A section that starts at the same place after the new layout — the only section of the song, or any section whose
stop did not move — gets the same pixel offset back, and a pixel offset means a different line at every text size.
The same holds inside any row or section taller than the screen, which is exactly where the offset is not zero.

## Fix

Anchor the *line* the reader is at, by what names it in every layout, and keep their place inside it as a proportion.

**What a line is.** `SongRows.lineTops` lists the top of every chunk placed in a single-column row, and every section
that may be cut — every section of the song details screen, which passes `canCutSections = true`, unless it is folded —
is composed one chunk per item (`SongUnits.of`: a lyrics line, a comment, a run of tablature or grid lines). The items of
a section depend on its content, never on the width or the text size, and a section in a single-column row is never cut
across rows, so *(section, index of the chunk within the section)* names the same line in every layout. A line that
wraps into several visual lines at a large text size is still one chunk; that is what the proportion below is for.

1. **`SongRows` (RowSnapping.kt)** gains two lists parallel to `lineTops`, both defaulting to `emptyList()` so the
   existing tests and probes still build: `lineBottoms` (where each piece's content ends) and `lineSections` (the section
   each belongs to). `offsetBy` shifts `lineBottoms` as it shifts `lineTops`. Say so in the `SongRows` KDoc.
2. **`SongLyrics.kt`**, where the rows are handed out (the `lineTops = (0 until unitCount).filter { … }` line in the
   lookahead block of `SongSectionsLayout`): take the filtered unit list once and map it three ways —
   `lineTops = singleColumnUnits.map { songTop + arrangement.tops[it] }`,
   `lineBottoms = singleColumnUnits.map { songTop + arrangement.tops[it] + unitHeights[it] }`,
   `lineSections = singleColumnUnits.map { units.unitSections[it] }`.
3. **`ReadingAnchor`** keeps `section` and `offset` exactly as they are (the fallback, and every existing test), and gains
   `val line: LineAnchor? = null`, with
   `internal data class LineAnchor(val section: Int, val index: Int, val offset: Int, val height: Int)`: the section of
   the line being read, its index among that section's entries in `lineSections`, how far below its top the reading
   position is, and the line's height (`lineBottoms - lineTops`) when the anchor was taken.
4. **`readingAnchorOf(scroll, rows, readingTop: Int = 0)`** — `readingTop` is where reading starts below the top of the
   viewport, `ReadingWindow.top` (the fade), which is where every step puts a line (`top - window.top` in
   `nextStepTarget` / `previousStepTarget`). After the stop is found as now:
   - in the header (`stop < 0`), or resting on the stop itself (`scroll - stepOffsets[stop] <= POSITION_TOLERANCE`), or
     where the three line lists are not the same size: no `line`, exactly as now — so a reader on a stop is put back on
     the stop, pixel for pixel, whatever the text size;
   - otherwise, with `at = scroll + readingTop`, the line is the last index `i` with
     `lineSections[i] >= stepSections[stop] && lineTops[i] <= at` (a multi-column row lists no lines, and the rows before
     the stop hold only earlier sections, so a stop with no line of its own finds none and keeps the fallback);
     `LineAnchor(section = lineSections[i], index = <entries of that section before i>, offset = at - lineTops[i], height = lineBottoms[i] - lineTops[i])`.
5. **`anchoredScrollOffset(anchor, rows, viewportHeight, maxValue, readingTop: Int = 0)`**: where `anchor.line` is set,
   the three lists are the same size and the new rows list more than `line.index` entries for `line.section`, take that
   entry (`top`, `height = bottom - top`) and
   - map the offset by proportion: `if (line.offset >= line.height) height + (line.offset - line.height) else line.offset * height / line.height`
     (in `Long`, rounded down — down is towards what has been read; the first branch is a reader in the gap after the line,
     kept in pixels since a gap does not scale);
   - `target = top + mapped - readingTop`;
   - resolve the stop (for `lastFree`) from `line.section` rather than from `anchor.section`, since the line may now be in
     a row that starts with another section; then the existing `coerceAtMost(lastFree).coerceIn(0, maxValue)`.
   Otherwise (no line; a section folded since, which lists one entry where it listed many; a section now in a
   multi-column row, which is never taller than the screen) resolve exactly as now from `anchor.section` and `anchor.offset`.
6. **`keepReaderInPlace`** passes `readingWindow.top` as `readingTop` to both calls (the two `readingAnchorOf` calls and
   the `anchoredScrollOffset` one); nothing else in it changes.

Note the line anchor in the `ReadingAnchor` KDoc and in `presentation/CLAUDE.md`'s sentence about the reader staying in
place ("the line they were reading, and how far into it").

## Tests

`RowSnappingTest`, next to `sectionsOutOfVerticalOrderAreAnchoredByPosition` (which, like every existing anchor test,
builds rows without `lineSections` and must pass unchanged):

- `aTextSizeChangeInsideATallSectionKeepsTheLine`: one section, stop at 59, 400 lines of 20 px from 100
  (`lineTops`, `lineBottoms`, `lineSections` all set), `readingTop = 41`, scroll = line 30's top − 41 + 5; resolved
  against the same section with 36 px lines, the target puts line 30 at the reading top with 9 px of it above
  (`5 * 36 / 20`); against 60 px lines, 15 px.
- the same shrinking (36 → 20): line 30 again, with `5 * 20 / 36 = 2` px above — never a later line.
- `aReaderOnAStopStaysOnIt`: scroll exactly on a stop resolves to the new stop, whatever the line heights.
- `aLineIsFoundInTheRowItsSectionMovedTo`: rows stepped by row, row 0 = sections 0–2 in one column, the reader on the
  second line of section 2; in the new rows section 2 starts a row of its own: the target is that line's new top plus
  the mapped offset minus `readingTop`, not a place in row 0.
- a folded section (the new rows list one entry for it) and a multi-column row (none) resolve as before.

Run the fuzz probe's coverage walk (`scratchpad/probe/Fuzz.kt`) unchanged: the stepping functions are not touched.

## Manual check

Desktop build, the 400-line song at 1000 × 450: step to line 30, pinch or Ctrl + wheel to a much larger size and
back. The same line stays at the top each time.
