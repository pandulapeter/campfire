<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Give a tab run of several systems a slot for every row each of its systems can wrap into

**Challenged:** amended — the defect is proven at HEAD (numbers below); the test now wraps to 20 columns, since at 12 even
the fixed count falls short (a pre-existing breach of the 12-column assumption, out of this plan's scope).

**Kind:** bug  ·  **Severity:** medium (needs the test)  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songLayout/LineRuns.kt`,
`libs/chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/tabs/ChordProTabWrapper.kt` (a public accessor
for the systems), `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songLayout/LineRunsTest.kt`,
`libs/chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/tabs/ChordProTabWrapperTest.kt`

Lane T, after plan 04, before plan 10 (both edit `ChordProTabWrapper.kt`).

## Problem

A run is drawn as a number of slots the section may be cut between; the last slot holds every row left, which a column
cannot cut (what `d39cab343` set out to fix). For tablature the count assumes one staff:

```kotlin
FoldableKind.TAB -> (size + maxOf { it.monospaceText?.length ?: 0 } / MIN_TAB_SLOT_COLUMNS).coerceIn(1, MAX_RUN_SLOTS)
```

but a run may stack several systems (`ChordProTabWrapper` cuts it system by system), and each system wraps into its own
rows. **Proven at HEAD** (a throwaway probe, deleted): 20 copies of one system, built as

```kotlin
val chord = ("Am" + " ".repeat(10) + "C           G           ".repeat(6).take(136)).padEnd(150)
val staff = "-0---2---3-|".repeat(13).take(148) // each staff line is "$name|" + staff, 150 characters
val system = listOf(chord) + listOf("e", "B", "G", "D", "A", "E").map { "$it|$staff" }
val lines = List(20) { system }.flatten() // 140 lines, as ChordProLine.Tab(text, continuesEnvironment = index > 0, label = null)
```
 `runSlotCount(FoldableKind.TAB)` is 140 + 150 / 12 = **152**, while
`ChordProTabWrapper.wrapLines(lines, maxColumns)` gives:

| maxColumns | rows of the run | rows of one system | slots today | slots after the fix (20 × (7 + 12)) |
|-----------:|----------------:|-------------------:|------------:|------------------------------------:|
| 40 | 100 | 5 | 152 | 380 |
| 30 | 120 | 6 | 152 | 380 |
| 20 | **240** | 12 | 152 | 380 |
| 16 | **260** | 13 | 152 | 380 |
| 12 | **480** | 24 | 152 | 380 (still short) |

So from about 20 columns (a phone at a larger text size: a column is ~0.5 em of the lyrics' size) the last slot holds
roughly 90–110 rows in one block no column can cut. At 12 columns a single system already wraps into 24 rows against
its 19 slots, because the repeated string names take 2 of the 12 columns and the quiet-column cut may fall in the
second half of the remaining 10: the 12-column assumption itself, which this plan leaves as it is (and which no screen
is believed to reach: at the largest text size a phone fits about 16).

## Fix

**Write this test first; drop this plan if it passes without the fix** (it fails at HEAD: 240 rows against 152 slots).
In `LineRunsTest`: build the run above as `ChordProLine.Tab` lines and assert `runSlotCount(FoldableKind.TAB)` is at
least `ChordProTabWrapper.wrapLines(lines, maxColumns = 20).size` (and at 16). Not 12: there even the fixed count
(380) is under the 480 rows, for the reason above.

Then count tab slots per system:

1. In `ChordProTabWrapper` (explicit API), a public function giving a run's systems as line ranges, wrapping the
   internal `systems(lines, isStaffLine)` (e.g. `public fun systemRanges(lines: List<String>): List<IntRange>`), KDoc'd.
2. `runSlotCount` for `TAB`: the sum over systems of (the system's line count + its longest line / `MIN_TAB_SLOT_COLUMNS`),
   still `coerceIn(1, MAX_RUN_SLOTS)`. A run of one system counts exactly as today. Update the KDoc above
   `runSlotCount` ("A staff of tablature wraps as one, so it takes a slot for every line and for every
   `MIN_TAB_SLOT_COLUMNS` characters of the longest") to say per system.

The `MIN_TAB_SLOT_COLUMNS` assumption (no row narrower than 12 characters) is unchanged; a slot costs about a lyric
line, and `MAX_RUN_SLOTS = 500` still bounds a run.

Plan 10 (after this one) may cut a row at an earlier bar, but only in the second half of the row, which the quiet-column
cut already allows today: it makes no row narrower than the wrapper already can, so it does not weaken this count.

Must not break: a single-system run's slots and layout (no change); slot *n* draws row *n*, the last slot the rest;
pedal reachability of every line (`PedalCoverageTest`); the editor's preview.

## Tests

- The test above, and one asserting a single-system run's count is unchanged.
- `ChordProTabWrapperTest`: `systemRanges` of a two-system run gives the two ranges `wrapLines` cuts by.
