<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Leave the page's first and last line out of a gutter's fair share where they cross it, so a heading over both columns no longer hides a short second column

**Kind:** bug (regression from 22d0af745)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/document/PdfLineLayout.kt`,
`data/formats/src/commonTest/kotlin/com/pandulapeter/campfire/data/formats/document/PdfLineLayoutTest.kt`,
`data/formats/CLAUDE.md`

## Problem

Since 22d0af745, `PdfLineLayout.gutter` counts each side's characters from ink clear of the band only (segments), but
still compares them with the total of every segment, those crossing the band included:

```kotlin
val characters = charactersByEnd.last()
…
val leftCount = charactersByEnd[ends.countAtMost(x - band)]
val rightCount = characters - charactersByStart[starts.countBelow(x)]
if (leftCount > characters / 5 && rightCount > characters / 5 && crossing <= rows.size / 5) {
```

A title over both columns now counts against both sides without counting for either. A two-column page from any
producer whose right column holds just under a fifth of the text clear of the title gets no gutter, and every row of
the right column is read into the left column's line on its baseline. Reproduction (fails at HEAD, verified):

```kotlin
val title = Placed(40, 740, "T".repeat(43))                                   // 40..298, crosses the gap
val left = (1..25).map { Placed(40, 720 - it * 12, "l$it".padEnd(12, 'l')) }  // 300 characters, 40..112
val right = (1..5).map { Placed(200, 720 - it * 12, "r$it".padEnd(17, 'r')) } // 85 characters, 200..302
// lines(listOf(title) + left + right) gives the title, then "l1lll…   r1rrr…" … "l5lll…   r5rrr…", l6 … l25
```

85 is not more than (300 + 85 + 43) / 5 = 85. Before 22d0af745 the title's glyphs were shared out by position and the
page split.

The finding's proposed fix, comparing each side with the text clear of the band
(`leftCount > (leftCount + rightCount) / 5`), is wrong: it breaks 22d0af745's own test
`a chord row with a wide gap is one line when only the lines crossing that gap have text right of it`, where the
characters of the lines crossing the chord row's gap (the capo line, the lyric) are exactly what keeps the right-hand
chords (10 characters) from passing for a column (verified: that test fails with it).

## Fix

Leave out of the total only what crosses the band in the page's first or last row (a heading over the columns, a
footer under them), never a line crossing it among the columns' rows:

- In `gutter()`, after `rows` is built: `val edgeRows = setOf(rows.minBy { it.first().y }, rows.maxBy { it.first().y })`
  (identity of the row lists; `rows` is never empty here).
- Beside `blocked`, `val blockedEdgeCharacters = IntArray(count + 1)`; in the `for (row in rows)` loop note
  `val isEdgeRow = row in edgeRows`, and in `block()` add, where `from <= to`,
  `if (isEdgeRow) { blockedEdgeCharacters[from] += characters; blockedEdgeCharacters[to + 1] -= characters }`.
- In the position loop, `edgeCrossingCharacters += blockedEdgeCharacters[index]`, and compare with what is left:

  ```kotlin
  val share = characters - edgeCrossingCharacters
  if (leftCount > share / 5 && rightCount > share / 5 && crossing <= rows.size / 5) {
  ```

The chord-row test keeps its total (its crossing lines are mid-page), the title case splits. Plan 01 adds a crossing
characters count to the same loop; if it lands first, reuse its diff-array pattern.

Checked with a patched copy of `PdfLineLayout` and `PdfTextExtractor` against `PdfLineLayoutTest` and
`PdfTextExtractorTest`: all pass, and the reproduction above passes. After the change also run
`./gradlew :data:source:local:implementation:desktopTest` (document goldens) and
`:presentation:desktopTest --tests '*PdfRoundTripTest*'`.

Docs: `data/formats/CLAUDE.md`, Columns bullet: "with a fifth of the characters on each side, counting no line that
crosses it" becomes "… counting no line that crosses it, and leaving a crossing first or last line of the page (a
heading, a footer) out of the whole as well".

## Tests

`PdfLineLayoutTest`: `a title over a short second column does not hide that column` — the reproduction above,
asserting `(listOf(title) + left + right).map { it.text }`. Fails at HEAD, passes with the fix; the existing
`a chord row with a wide gap …` test must still pass.
