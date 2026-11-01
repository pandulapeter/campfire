<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Drop a page's lowest line where it counts this page among the document's, wherever above the margin band it stands

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/document/PdfTextExtractor.kt`,
`data/formats/src/commonTest/kotlin/com/pandulapeter/campfire/data/formats/document/PdfLineLayoutTest.kt` (or
`PdfTextExtractorTest.kt`), `data/formats/CLAUDE.md`

## Problem

A page number is dropped only inside the top or bottom 8% of the page (`PdfRunningLines.isEdge`):

```kotlin
fun isEdge(y: Double, height: Double) = y < height * 0.08 || y > height * 0.92
```

```kotlin
val lines = page.lines.filterNot { line ->
    isEdge(line.y, page.height) && (isRunning(line) || running.any { … })
}
```

Campfire's export prints the page number at `height - margin - 11f` (`PrintLayout.finish`), whose baseline lands at
about `height - margin`. With a wide margin it is above the band: A4 portrait from about 24 mm, A4 and Letter
landscape from about 17 mm, Letter portrait from about 22 mm (the export allows 10–25). Probed with
`PdfRoundTripTest`'s pipeline over `chordpro/src/desktopTest/resources/corpus/01-basic-verse-chorus.cho`: at 15, 20,
and 22 mm (A4 portrait) and at 15 mm landscape the footer is dropped; at 25 mm portrait (baseline 771.0 of 841.9,
0.916) and 25 mm landscape (524.4 of 595.3, 0.881) `1 / 1` comes back as a lyric line inside the song's last section,
in every language and column count. Other producers' songbooks with generous bottom margins do the same.

## Fix

In `PdfTextExtractor.extract`, also drop the page's lowest line (greatest `y`) where it lies in the lower half of the
page and reads as this page's count among the document's pages, by the same test the edge uses:

```kotlin
val lowest = page.lines.maxByOrNull { it.y }
val lines = page.lines.filterNot { line ->
    isEdge(line.y, page.height) && (isRunning(line) || running.any { … }) ||
        line === lowest && line.y > page.height / 2 && isPageCount(line.text.trim(), index + 1, pages.size)
}
```

`isPageCount` already requires both numbers to be this page's own (`1 / 1`, `2 of 3`, `Page 2/3`), so a song's time
signature (`3/4`) is never taken, and only the page's last line is considered, so nothing between lyrics is. The
columns do not matter: the lowest line is found by position, not by its place in reading order.

Rejected: widening `isEdge` (a top band of 12% would put a title's or first verse's lines among the running-line
candidates); moving the printed page number into the 8% band (a visible change to every export with a wide margin, and
no help for other producers).

Checked with a patched copy of `PdfTextExtractor` against `PdfTextExtractorTest` and `PdfLineLayoutTest` (all pass) and
the synthetic case below. After the change also run `./gradlew :data:source:local:implementation:desktopTest`.

Docs: `data/formats/CLAUDE.md`, Running lines bullet, add: "A page's lowest line that counts this page among the
document's (`2 / 3`) is dropped wherever in the lower half it stands, since a wide margin lifts the page number out of
the band."

This plan is in the same lane as 01 and 13 (`data/formats` only) and touches a different file.

## Tests

`PdfLineLayoutTest` (its `lines` helper runs the whole extractor on one page): `a page count above the bottom margin
band is still left out` —

```kotlin
val placed = (1..6).map { Placed(50, 700 - it * 14, "Lyric line $it") } + Placed(290, 80, "1 / 1", size = 9)
assertEquals((1..6).map { "Lyric line $it" }, lines(placed))
```

(y 80 of 792 is 0.9 of the page from the top, outside the 8% band). Fails at HEAD (verified), passes with the fix. Add
the negative: `Placed(290, 80, "3/4")` as the lowest line stays.

Optionally, `PdfRoundTripTest` can print one corpus song at `PrintSettings(marginMm = 25)` and
`PrintSettings(isLandscape = true, marginMm = 25)` and compare `summary`s as the corpus test does; it fails at HEAD.
