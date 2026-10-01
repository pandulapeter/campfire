# Print a chorus recall under its own label, as the viewer does

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`
**Challenged:** amended — the header goes on the first recalled piece that yields rows (as in the viewer), not always on the first section, which plan 05 can empty.

## Problem

```kotlin
is ChordProBlock.ChorusRecall -> block.blocks.flatMap(::rowsFor).ifEmpty { block.label?.let { wrapped(it, bold = true) }.orEmpty() }
```

The recall's label is used only when nothing is recalled. The demo song's `{chorus: Last chorus, slowing down}` prints
as plain "Chorus" — a performance instruction lost on paper — and a `{chorus}` with no chorus before it prints nothing.
The viewer (`SongLyrics.kt`) heads a recall with
`block.label ?: (block.blocks.firstOrNull() as? ChordProBlock.Section)?.label ?: defaultLabels.chorus`, and shows that
heading even with nothing under it.

## Fix

Mirror the viewer: compute `header = block.label ?: (block.blocks.firstOrNull() as? ChordProBlock.Section)?.label ?:
labels.chorus`; give `rowsFor` an optional `labelOverride` used for a `Section` in place of its own label, continuation
or not. Walk the recalled blocks in order, passing the header to each `Section` until one of them yields rows (with
plan 05 a first piece whose lines the options hide yields none, and the header must then head the continuation that is
shown, as the viewer's `if (addSection(…)) header = null` does); every later piece gets none. Comments inside the recall
are `rowsFor` as usual. When no rows come back at all, emit the header alone (bold, as a label).

## Tests

`PrintLayoutTest`: chorus + `{chorus: Last time}` → the second occurrence is headed "Last time" and "Chorus" appears
once; a `{chorus}` with no chorus defined → one bold "Chorus" row.

## Manual check

Export the demo "Home on the Range"; page 2's last chorus is headed "Last chorus, slowing down".
