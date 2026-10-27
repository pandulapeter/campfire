# Draw each key heading of the chord diagrams above its first diagram's travelling position, so it glides with its row on a resize

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the Tests section no longer relies on `GoldenRendersTest` (compared on Linux only, and its one chord-diagram golden has a single key, so no heading) and lists what was checked unchanged (hit-testing, RTL, retain/clear).
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongChordsSection.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/CLAUDE.md` (only if the Motion
bullet is touched; see Fix)

## Problem

A song's Chords section groups its diagrams by key, each group's first row headed "Key: X" (`ChordCellLayouts.headings`,
keyed by the group's first cell, which always starts a row). In `ChordRowsBlock`'s `drawBehind`, the cells travel to a
new arrangement through `PlacementMotion` while the headings are drawn straight at their row's new y:

```kotlin
for (row in rows) {
    layouts.headings[starts[row]]?.let { heading ->
        val headingWidth = minOf(heading.size.width, width)
        clipRect(right = width.toFloat(), bottom = y + heading.size.height.toFloat()) {
            drawText(textLayoutResult = heading, color = paint.headingColor, topLeft = Offset(if (isRightToLeft) (width - headingWidth).toFloat() else 0f, y.toFloat()))
        }
    }
    y += layouts.headingHeight(starts, row)
    …
    for (cell in layouts.cellsOfRow(starts, row)) {
        …
        val target = IntOffset(if (isRightToLeft) width - x - cellWidth else x, y)
        val (cellLeft, cellTop) = if (isAnimated) motion.positionOf(cell, target) else target
```

When a narrower or wider window rewraps an earlier group onto more or fewer rows, a later group's diagrams glide to
their new rows while its heading jumps there in one frame, leaving it over the wrong diagrams for the length of the
spring. That breaks the root rule "A resized window moves nothing abruptly", and `songView/CLAUDE.md`'s Motion bullet
("the chord diagrams … travel to a new arrangement of their rows").

The PDF is unaffected: `ui/print` lays the diagrams out itself and never uses `ChordRowsBlock` or `PlacementMotion`.

## Fix

Draw the heading from its group's first cell's animated position rather than from the row's target y. Since that cell
always starts its row, its target x is the row's start, so only y travels; the heading keeps its x (left edge, or the
right edge less its width in a right-to-left layout).

Draw the heading inside the cell loop, at the row's first cell, so `motion.positionOf` is called once per cell per
frame (calling it a second time for the same cell in the same frame, before the launched `animateTo` has set the new
`targetValue`, would launch a second animation that cancels the first):

```kotlin
val heading = layouts.headings[starts[row]]
y += layouts.headingHeight(starts, row)
…
for (cell in layouts.cellsOfRow(starts, row)) {
    …
    val (cellLeft, cellTop) = if (isAnimated) motion.positionOf(cell, target) else target
    if (heading != null && cell == starts[row]) {
        // Over the row's first diagram wherever it is on its way, so a heading travels with the chords it names.
        val headingTop = cellTop - layouts.headingHeight(starts, row)
        val headingWidth = minOf(heading.size.width, width)
        clipRect(top = headingTop.toFloat(), right = width.toFloat(), bottom = headingTop + heading.size.height.toFloat()) {
            drawText(textLayoutResult = heading, color = paint.headingColor, topLeft = Offset(if (isRightToLeft) (width - headingWidth).toFloat() else 0f, headingTop.toFloat()))
        }
    }
    …
}
```

`headingHeight` already counts the gap under the heading, so with no travel `headingTop` is the old `y` and every
still frame draws exactly as today. Keep the comment's voice to the code-style skill's ("why", present tense).

Update `ChordRowsBlock`'s KDoc sentence about `isAnimated` to say a key's heading travels with its row's first diagram.
`songView/CLAUDE.md`'s Motion bullet already promises the diagrams travel; no change needed there unless the reviewer
wants the heading named.

## Tests

None can be written usefully: the change is where a heading is drawn on the frames of a glide, and the UI tests cannot
resize their window mid-test (`runAppTest` fixes `TestWindow` for the run), while a pure helper would only test one
subtraction. Run `./gradlew :presentation:desktopTest --tests '*ChordRowsTest*'` to confirm nothing in the row
arithmetic moved. `GoldenRendersTest` proves nothing here: goldens are compared on Linux only (on a Mac it just draws
them), and the one golden with chord diagrams (`song-details`, House of the Rising Sun) has a single key, so no heading;
leave the still frame to CI's run and to reading the code (with no travel `headingTop` equals the old `y`).

Unchanged by the fix, checked: hit-testing (`ChordCellLayouts.cellAt`) and the screen reader's description still use
the targets, as for the travelling cells; the heading draws no selectable text; a right-to-left heading keeps its
right-edge x, the first cell's target x being the row's start in both directions; `PlacementMotion.retain` and `clear`
need nothing new, since the heading reads its cell's entry rather than keeping one.
