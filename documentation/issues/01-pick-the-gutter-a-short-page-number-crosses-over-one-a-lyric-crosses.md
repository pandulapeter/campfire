<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Among gutter positions crossed by as many lines, pick the one the fewest characters cross, so a centred page number wins over a lyric ending at the column's edge

**Kind:** bug  ·  **Severity:** high  ·  **Platforms:** all
**Files:** `data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/document/PdfLineLayout.kt`,
`data/formats/src/commonTest/kotlin/com/pandulapeter/campfire/data/formats/document/PdfLineLayoutTest.kt`,
`data/formats/CLAUDE.md`

## Problem

The demo House of the Rising Sun (`presentation/src/commonMain/composeResources/files/demo/traditional_american-house_of_the_rising_sun.cho`),
exported in English in two columns and imported back, has its lyrics scrambled: verse 3's last two lines vanish from
it, "My sweetheart, he's a drunkard, Lord God" lands inside verse 7 before "One foot on the train", and verse 4 comes
after verse 7. Any two-column PDF (Campfire's or another producer's) with a page number centred under the gutter can do
the same.

Cause, traced with a debug copy of `PdfLineLayout` on that PDF's second page: the page number `2 / 3` is centred on the
page (`PrintLayout.finish`, `(width - measure(number)) / 2`), so it spans x 287.96–307.31 while the left column's ink
ends at 281.54 ("Lord God") and the right column starts at 306.64. Every position of the true gutter is crossed by the
footer, so no position has `crossing == 0`; among the positions crossed once, `gutter()` breaks the tie by the gap left
of the band only:

```kotlin
if (leftCount > characters / 5 && rightCount > characters / 5 && crossing <= rows.size / 5) {
    if (best == null || crossing < bestCrossing || crossing == bestCrossing && gap(x) > gap(best)) { best = x; bestCrossing = crossing }
}
```

`gap(x)` measures from the last glyph end at or left of `x`; inside the footer the glyph ends are a few points apart,
so x = 287.52, just left of the footer, wins (gap 6.0 against at most 4.75 inside it). Its band
(`[x - 0.8 em, x]` = 275.9–287.5) is crossed by the left column's "Lord God" line instead of the footer, and
`buildLines` makes that baseline a spanning row:

```kotlin
val spanning = glyphs.filter { it.span.end > gutter - band && it.span.start < gutter }
```

so `rows()` reads the left column's line and the right column's line on the same baseline as one line, and the columns
above and below it are read as two separate blocks. On the first page the footer `1 / 3` happens to start 0.4 points
further right, a step of the half-point grid, and the gutter is clean.

Minimal reproduction (fails at HEAD, as a `PdfLineLayoutTest` case):

```kotlin
val left = (1..8).map { Placed(40, 700 - it * 24, if (it == 4) "L".repeat(35) else "l$it".padEnd(30, 'l')) }
val right = (1..8).map { Placed(290, 700 - it * 24, "r$it".padEnd(30, 'r')) }
val footer = Placed(254, 40, "Page 2 / 3", size = 6)
// lines(left + right + footer) gives l1, l2, l3, r1, r2, r3, "LLL…L       r4rrr…", l5 … l8, r5 … r8
```

## Fix

In `PdfLineLayout.gutter`, count the characters of the segments crossing each position as well as the segments, and
break a tie in `crossing` by fewer crossing characters before the gap:

- Beside `blocked`, keep `val blockedCharacters = IntArray(count + 1)`; in `block()`, where the segment blocks
  `from..to`, also `blockedCharacters[from] += characters; blockedCharacters[to + 1] -= characters` (the segment's own
  `characters`, which `block()` already reads for `segments += Segment(start, end, characters)`).
- In the loop, `crossingCharacters += blockedCharacters[index]` next to `crossing += blocked[index]`, and keep
  `bestCrossingCharacters` with `best`:

  ```kotlin
  if (best == null || crossing < bestCrossing ||
      crossing == bestCrossing && (crossingCharacters < bestCrossingCharacters || crossingCharacters == bestCrossingCharacters && gap(x) > gap(best))
  ) { best = x; bestCrossing = crossing; bestCrossingCharacters = crossingCharacters }
  ```

A five-character page number then beats a forty-character lyric, and the band chosen sits inside the footer, which
alone becomes the spanning row (at the page's foot, where it is dropped as a running line). Nothing changes where a
position no line crosses exists, nor where one line (a title over both columns) crosses every candidate, since it then
adds the same characters everywhere.

Rejected: leaving the margins (`PdfRunningLines.isEdge`) out of the search, since the top margin also holds the first
lines of both columns, which must not be read as one row.

Checked with a patched copy of `PdfLineLayout` and `PdfTextExtractor` run against `PdfLineLayoutTest` and
`PdfTextExtractorTest` (all pass) and the House of the Rising Sun PDF (the second page's gutter moves to 306.52 and
only the footer spans it). After the change also run `./gradlew :data:source:local:implementation:desktopTest` (the
document goldens) and `:presentation:desktopTest --tests '*PdfRoundTripTest*'`.

Docs: in `data/formats/CLAUDE.md`, Columns bullet, after "A band no line crosses that opens a column beats one a
heading crosses, …" add: "Among bands crossed by as many lines, the one the fewest characters cross wins (a page number
centred under the gutter rather than a lyric filled to the column's edge), then the widest."

This plan and plan 13 both edit `gutter()`; land them one after the other in the same lane (either order).

## Tests

`PdfLineLayoutTest`: `a page number centred across the gutter does not join a lyric ending at the column's edge to the
line beside it` — the reproduction above, asserting
`(left + right).map { it.text }` equals `lines(left + right + footer).filter { it != "Page 2 / 3" }`. Fails at HEAD
(verified), passes with the fix.
