<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Lay out each distinct fret and word of a tab run once, not once per occurrence and per column count

**Challenged:** sound

**Kind:** performance  ·  **Severity:** medium-high  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/TabStaffLines.kt`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongTextMeasurementsTest.kt`
(or a new `TabStaffLinesTest.kt` beside it)

Lane T.

## Problem

`TabStaffLines` lays out every word of every staff and word line into its own `TextLayoutResult`:

```kotlin
val layout = measure(word.value, if (word.range.first < nameEnd) nameStyle else fretStyle)
…
private fun measure(text: String, style: TextStyle) = textMeasurer.measure(AnnotatedString(text), style, softWrap = false)
```

with the page's measurer made without a cache (`SongLyrics.kt`: `rememberTextMeasurer(cacheSize = 0)`). `TabRows`
builds the rows for every column count it is asked about (`rowsOf(maxColumns)`, the last 8 kept, keyed by column count
since `8605618f3`), each time calling `staffLines::line` on every line of every row. A 40-system tab of six strings
holds thousands of fret words, of which a few dozen are distinct (`0`–`24`, `h`, `p`, `x`, the string names), so each
new column count — the first open's candidates, a resize crossing a column, a text size step — lays out thousands of
texts. At the measured rate of the notation painter (4.2 ms for 257 texts, ~16 µs each), that is tens of ms per wrap.

## Fix

Keep, per `TabStaffLines` instance (one per run and styles, owned by its `TabRows`), a `HashMap` from (text, style) to
`TextLayoutResult` — the four styles are fixed per instance (`nameStyle`, `fretStyle`, `wordStyle`, `chordStyle`), so a
map per style or a key of the text and a style index both work — and have `measure` look it up first. A layout is
drawn at draw time with its position and colour (`drawText(textLayoutResult = text.layout, color = …, topLeft = …)` in
`SongTabBlock`), so one layout may be placed many times. The map lives as long as the run's `TabRows` (bounded by the
run's distinct words) and goes with it. The samples measured in the constructor (`figures`, the line-height samples)
may go through it or not.

Leave `TabRows.preformattedLine` as it is: its lines are cut differently at every width and rarely repeat.

Must not break: every fret centred on its column and string as today (same layouts, same widths); string names in the
regular weight, frets in bold tabular figures; chord-name lines in the chords' colour; nothing scrolls sideways.

## Tests

- In desktopTest (it has Skia): two staff lines holding the same fret give `assertSame` layouts; the rows of one run
  at two column counts reuse the layouts of the words they share; a string name and a fret of the same text (`"D"` /
  a hypothetical) keep their own styles (different layouts).
- `TabStaffGeometryTest` and the song details UI tests pass unchanged.
- Confirm the gain with the sweep's measurement method (untracked `:tools:screenshots` test with temporary log lines
  counting `textMeasurer.measure` calls in `TabStaffLines` on song C's 40-system tab, deleted afterwards). Lane report only.
