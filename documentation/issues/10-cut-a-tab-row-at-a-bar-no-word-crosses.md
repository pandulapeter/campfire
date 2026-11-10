<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Cut a tab row at a bar line no chord name or lyric word crosses, where one fits

**Challenged:** amended — the defect is proven at HEAD (input and output below); the preferred bar must lie in the
second half of the row, as the quiet cut's does, so a word crossing every later bar cannot shrink rows to one bar (which
would also undercut plan 09's slot count); the test inputs are given.

**Kind:** bug  ·  **Severity:** low-medium  ·  **Platforms:** all
**Files:** `libs/chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/tabs/ChordProTabWrapper.kt`,
`libs/chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/tabs/CLAUDE.md`,
`libs/chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/tabs/ChordProTabWrapperTest.kt`

Lane T, after plan 09.

## Problem

`cutColumn` takes the last bar line that fits without looking at the lines that are not staff lines:

```kotlin
for (end in maxEnd downTo start + MIN_BAR_WIDTH) {
    if (barColumns[end - 1] && !barColumns[end]) return end
}
```

A system's chord names above its staff and lyrics under it are cut at the same column, so a chord name written over the
bar or a lyric word that runs across it is split between two rows. The quiet-column fallback already refuses to cut
through a chord name; the bar cut, which wins first, does not.

**Proven at HEAD** (a throwaway probe, deleted), `ChordProTabWrapper.wrapLines(lines, maxColumns = 20)`:

```
input                              rows at HEAD
e|----|----|----|----|----|        [e|----|----|----|] [B|-1--|-3--|-0--|] [  hello my old da]
B|-1--|-3--|-0--|-2--|-0--|        [e|----|----|]      [B|-2--|-0--|]      [  rkness]
  hello my old darkness

  Am   Em   C  Dsus4               [  Am   Em   C  Ds] [e|----|----|----|] [B|-1--|-3--|-0--|]
e|----|----|----|----|----|        [  us4]             [e|----|----|]      [B|-2--|-0--|]
B|-1--|-3--|-0--|-2--|-0--|
```

The last bar that fits (cut at column 17) splits `darkness` and `Dsus4`; the bar before it (column 12) is in the second
half of the row (content from column 2, at most 18 columns) and, with the texts below, free.

## Fix

**Write this test first; drop this plan if it passes without the fix** (it fails at HEAD, as above). In
`ChordProTabWrapperTest`, at `maxColumns = 20`: the staff above with the lyric line `"  hello my  old darkness"` (two
spaces before `old`, so the bar cut at column 12 crosses no word) must give the rows
`["e|----|----|", "B|-1--|-3--|", "  hello my"]` and then the rest with `darkness` whole; and the chord line
`"  Am   Em   C  Dsus4"` over the same staff must keep `Dsus4` whole, moving `C` and `Dsus4` to the second row. (With
`"  hello my old darkness"` the column-12 cut crosses `old`, and the only free bar, column 7, is in the first half, so
that input keeps today's cut: use it for the fallback test below.)

Then in `cutColumn`, first look for the last bar cut (same condition) **in the second half of the row**
(`end >= start + (maxEnd - start) / 2`, the bound the quiet cut already uses) at which no non-staff line has a word
across the cut — a line crosses `end` where `0 < end < line.length` and neither `line[end - 1]` nor `line[end]` is a
space (use `cutBoundary` so a multi-`Char` character counts as one); failing that, keep today's last bar cut; then the
quiet column and the edge as today. Update `cutColumn`'s KDoc and the wrapper's class KDoc ("A cut is made after a bar
line wherever one fits…"), and add to `tabs/CLAUDE.md`'s `ChordProTabWrapper` bullet that a bar cut prefers one no chord
name or word crosses.

Why the second half: lyrics under a staff often run across many bars; without the bound a word crossing every bar
but the first would cut a row after one bar, multiplying the rows (and outgrowing plan 09's slots, which assume no
row much narrower than the quiet cut makes).

Must not break: termination and linearity (the extra check is O(lines) per candidate column, as the quiet-column
loop already is); a row never wider than `maxColumns` (unless under `MIN_CAPACITY`); the repeated string names; cuts
never inside a surrogate pair or combining mark; every existing wrapper test.

## Tests

- The tests above; one where every bar in the second half of the row is crossed by a word (`"  hello my old darkness"`),
  which still cuts at the last bar as today; every existing `ChordProTabWrapperTest` case unchanged (none of them puts a
  word across a bar cut: check `the rows are cut after a bar line and repeat the string names`, whose `D` at column 32
  sits after the bar).
