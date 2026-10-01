# Leave a gap between the systems of a wrapped tab

**Kind:** output quality  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`
**Challenged:** amended — the gap only for tablature (`ChordProTabWrapper.isTablature(run)`); a preformatted run (chord names over lyrics) is read as wrapped text with no gap, as in the viewer's `TabRows.rowGap`.

## Problem

```kotlin
systems.forEach { system ->
    val rows = system.flatMap { wrapped(it) }
    …
    add(Row(rows.flatMap { … }, systemHeight))
```

Systems follow each other with no space, so the high `e|` of the second system sits directly under the low `E|` of the
first and six-string tab reads as a twelve-line staff (live run, `demo-rising-p1`, `hard-default-p5`). The viewer
leaves a blank line between systems. `tablatureWrapsAllStringsAtTheSameColumns` checks string order only.

## Fix

When the run is tablature (`ChordProTabWrapper.isTablature(run)`, already computed for the choice of wrapper), add
`Row(emptyList(), options.fontSize * 0.7f)` between systems (not after the last). A run that went through
`wrapPreformatted` gets no gap: its systems are a line of chord names and the lyrics under it, read like wrapped text,
which is what the viewer does (`rowGap` is 0 there). Because `place()` may break between rows, the gap row can end a
column harmlessly.

## Tests

Extend `tablatureWrapsAllStringsAtTheSameColumns`: the y of the second system's first string is at least
`0.7 * fontSize` more than one row below the first system's last string; a wrapped preformatted run (no staff lines)
has no gap.

## Manual check

Export the demo song with the tab at two columns; systems are visibly apart.
