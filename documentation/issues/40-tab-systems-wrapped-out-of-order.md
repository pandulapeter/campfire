# 40 · On a phone, a tab whose systems are not a blank line apart is read out of order, and a crafted one runs out of memory

**Severity:** wrong behaviour (all platforms, in practice phones and narrow windows; likely — any `{start_of_tab}` that
stacks two or more systems without an empty line between them, with or without chord names above each, which is how
a lot of hand-written and pasted tabs look) + crash / hang (all platforms; crafted file of about 100 KB, repeats every
time the song is opened, and on the web freezes the page) · **Area:** `:chordpro` (`ChordProTabWrapper`)
**Verifier:** replaced the "a string name the system already has" rule, which splits every tab that labels both E
strings `E|` and every DADGAD tab (`D A G D A D`), with "the system's string names start over, all of them in the same
order", and made the output bound one budget for the whole run, proportional to its size, since a per-system cap of
10,000 lets a crafted run of many small systems through; both run green in a worktree against every existing test.

## Symptom
1. Import this song and open it on a phone in portrait (or any window narrow enough that the tab does not fit):

   ```
   {title: Two systems}
   {start_of_tab}
      C       G
   e|---0---|---3---|
   B|---1---|---0---|
      Am      F
   e|---0---|---1---|
   B|---1---|---1---|
   {end_of_tab}
   ```

   `ChordProTabWrapper.wrap(lines, maxColumns = 12)` returns (verified on a38dea2f):

   ```
   [[   C, e|---0---|, B|---1---|,    Am, e|---0---|, B|---1---|],
    [   G, e|---3---|, B|---0---|,    F, e|---1---|, B|---1---|]]
   ```

   so the screen reads **C, Am, G, F** instead of **C, G, Am, F**: the first bar of every system, then the second bar
   of every system. With systems stacked directly on each other (no chord line: `e|…`, `B|…`, `e|…`, `B|…`) it is the
   same: `[[e|---0---|, B|---1---|, e|---5---|, B|---5---|], [e|---3---|, B|---0---|, e|---7---|, B|---8---|]]`.
   A whole-song tab of 40 such systems on a phone becomes two or three "rows" of 240 lines each — every system's
   left half, then every system's right half. Nothing is lost, which is what makes it hard to notice: the notes are all
   there, in the wrong order.
2. Crafted: a tab run of many short staff lines and one long one — `{sot}`, 12,500 × `e|---`, one line of `e|`
   followed by 25,000 dashes, `{eot}` (≈ 97 KB, far under `ImportLimits.MAX_TEXT_FILE_SIZE`). At 40 columns `wrap`
   returns **8,225,658** row lines (every short staff line is repeated as `e|` in every one of ~660 rows); with 16,000
   short lines and a 100,000-dash line the desktop test JVM dies with `OutOfMemoryError: Java heap space` inside
   `wrap` alone. `SongTabBlock` then measures every one of those lines with the `TextMeasurer`. Prefix-less staff
   lines (`---` × 12,500 + one line of 50,000 dashes, 100 KB) cost 1.5 s in `barColumns` before any row is built.

## Cause
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabWrapper.kt:43-65` cuts **every line of
the run at the same columns**:

```kotlin
val end = if (length - contentStart <= capacity) length else cutColumn(lines, isStaffLine, barColumns, contentStart, contentStart + capacity)
rows += lines.mapIndexedNotNull { index, line ->
    val content = if (start < line.length) line.substring(start, minOf(end, line.length)) else ""
    ...
    row.trimEnd().takeIf { isStaffLine[index] || it.isNotBlank() }
}
```

That is right for one system (the strings of one staff share their columns) and wrong for a run holding several: the
viewer's run is "consecutive `ChordProLine.Tab` lines" (`presentation/…/songDetails/SongLyrics.kt:511`
`groupConsecutiveTabs`, where only a `Blank` ends a group), so systems written without a blank line between them
arrive as one run, and the columns of the second system are cut together with — and shown after — those of the first.
The bar search makes it worse: `barColumns` wants a bar on *every* staff line of the run, which systems with bars in
different places rarely share, so the cut falls on a quiet column or mid-bar instead.

The crash is the same design at scale: a staff line is "always kept, so that the strings are the same in every row"
(`wrap`'s KDoc, `:34-36`; the code is `:60`), so the output is `rows × staff lines` whatever the lines hold, and `barColumns` (`:78-84`) is
`O(columns × staff lines)` with a list allocated per column:

```kotlin
return BooleanArray(length) { column ->
    val reachingLines = staffLines.filter { column < it.length }
    reachingLines.isNotEmpty() && reachingLines.all { it[column] == BAR }
}
```

`wrap` runs inside `TabRows.at` (`SongLyrics.kt:594`), i.e. in the layout/draw pass on the main thread, and on the web
on the page's only thread.

## Fix
All in `ChordProTabWrapper.kt`; the viewer and its grouping stay as they are.

1. **Wrap system by system.** Keep the two early returns of `wrap` (no staff at all, or the whole run fits: the run
   comes back as one row exactly as today). Otherwise split the run into systems and wrap each one on its own with the
   current algorithm, moved unchanged into
   `private fun wrapSystem(lines: List<String>, isStaffLine: List<Boolean>, maxColumns: Int, budget: Int): List<List<String>>?`
   (which keeps its own "fits" early return), concatenating their rows in order.

   `private fun systems(lines, isStaffLine): List<IntRange>` walks the lines once, with the string name of every
   staff line precomputed as `staffPrefix(line).filterNot { it.isWhitespace() || it == BAR }` (`""` for a line with
   no name). A new system starts at a staff line that follows the current system's staff when either
   - a non-staff line came between them: the system then starts at the **first** of those non-staff lines, since
     lines between two staves are chord names written *above* the staff they belong to, which is what the class's KDoc
     already assumes ("the chord names travel with the notes they are written over"). Lyrics written *under* a staff
     therefore go with the next system, an acceptable approximation — say so in the KDoc; or
   - with nothing between them, the staff lines from here on repeat the current system's string names **all of them,
     in the same order** (the current system has `count` staff lines so far; the next `count` lines are all staff
     lines, named exactly — case-sensitively — like the system's first `count`), the name here is not empty, and
     `count <= MAX_STRINGS` (new constant, 12). This is what tells a second system from a string whose name recurs
     inside one: `E B G D A E` (both E strings written `E|`, which many tab sites do) and DADGAD's `D A G D A D` are one
     system each, while two of either stacked are two. A staff without string names never splits this way, and the
     `MAX_STRINGS` bound keeps the check linear in the run.
   Non-staff lines before the first staff join the first system, those after the last staff the last one.

   Sketch (verified):

   ```kotlin
   private fun systems(lines: List<String>, isStaffLine: List<Boolean>): List<IntRange> {
       val names = lines.mapIndexed { index, line -> if (isStaffLine[index]) staffPrefix(line).filterNot { it.isWhitespace() || it == BAR } else "" }
       val starts = mutableListOf(0)
       var systemStaffStart = -1
       var staffCount = 0
       var firstLineBetweenStaves = -1
       for (index in lines.indices) {
           if (!isStaffLine[index]) {
               if (staffCount > 0 && firstLineBetweenStaves < 0) firstLineBetweenStaves = index
               continue
           }
           val startsSystem = when {
               staffCount == 0 -> false
               firstLineBetweenStaves >= 0 -> true
               else -> staffCount <= MAX_STRINGS && names[index].isNotEmpty() && repeatsNames(names, isStaffLine, systemStaffStart, index, staffCount)
           }
           if (startsSystem) {
               starts += if (firstLineBetweenStaves >= 0) firstLineBetweenStaves else index
               systemStaffStart = index
               staffCount = 0
           }
           if (systemStaffStart < 0) systemStaffStart = index
           staffCount++
           firstLineBetweenStaves = -1
       }
       return starts.mapIndexed { i, start -> start until (starts.getOrNull(i + 1) ?: lines.size) }
   }
   ```

   `repeatsNames(names, isStaffLine, from, index, count)` is true when `index + count <= names.size` and for every
   `offset` in `0 until count` both `from + offset` and `index + offset` are staff lines with equal names.

2. **Make `barColumns` linear**: one pass over the staff lines counting, per column, how many lines reach it and how
   many have a `|` there (two `IntArray(length)`); a column is a bar column when both counts are equal and non-zero.

3. **Bound the output of the whole run.** `wrap` computes one budget,
   `val budget = lines.size + lines.sumOf { it.length } / 2`, and hands each `wrapSystem` what is left of it;
   `wrapSystem` counts the lines of every row it builds (and the lines of a system that fits) and returns `null` as
   soon as they pass the budget, upon which `wrap` returns `listOf(lines)`: the run unwrapped, as one wide row, which
   the viewer draws past its edge — only a crafted file gets there. A real system cannot reach the budget: its row
   lines are about `strings × length / capacity` with a capacity of at least `MIN_CAPACITY` (8) columns, at most an
   eighth of its characters, against half. The budget is proportional rather than a fixed number because a fixed one
   per run or per system still lets a crafted file of many runs or many small systems multiply its size by the row
   count (at 10,000 row lines per 2.8 KB system, an 8 MiB file would still wrap into 30 million lines).

Do **not**:
- split in `SongLyrics.groupConsecutiveTabs` instead: the "run" is also what the transposer moves as one fingerboard
  (`ChordProTransposer.rewriteLines`), and the system is a layout notion that belongs next to the rest of the cut rules;
- start a system at any string name the system already has: `E|` twice and DADGAD are single systems;
- drop ended staff lines from later rows to save memory: "the strings are the same in every row" is deliberate
  (`a string written shorter than the others does not hide the bars of the rest`);
- treat a blank-less run as preformatted text: it is tablature and should wrap.

## Tests
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabWrapperTest.kt`:
- `systems stacked without a blank line are wrapped one after the other`:
  `listOf("e|---0---|---3---|", "B|---1---|---0---|", "e|---5---|---7---|", "B|---5---|---8---|")` at
  `maxColumns = 12` gives `[[e|---0---|, B|---1---|], [e|---3---|, B|---0---|], [e|---5---|, B|---5---|], [e|---7---|, B|---8---|]]`.
- `chord names above a system travel with that system`: the two-system input of the Symptom (chord lines
  `"   C       G"` and `"   Am      F"`) at `maxColumns = 12` gives
  `[[   C, e|---0---|, B|---1---|], [   G, e|---3---|, B|---0---|], [   Am, e|---0---|, B|---1---|], [   F, e|---1---|, B|---1---|]]`.
- `a run of several systems that fits is still one row`: the same input at `maxColumns = 40` is `listOf(lines)`.
- `a string name that recurs inside a system does not start a new one`: a six-string system named
  `E B G D A E` (each line `"X|---0---|---3---|"`) at `maxColumns = 12` wraps into 2 rows of 6 lines, and two of it
  stacked into 4 rows; the same for `D A G D A D`.
- `staff lines without string names are one system until a non-staff line separates them`: three identical
  `"--0--2--3--5--7--8--10--12--"` lines at `maxColumns = 19` give 2 rows of 3 lines.
- `a run that would wrap into more lines than can be drawn is returned whole`: `List(12_500) { "---" } + "-".repeat(50_000)`
  at `maxColumns = 40` returns `listOf(lines)` (measured: 35 ms); and 283 staff lines with distinct two-letter names
  (`"${'A' + i % 26}${'a' + i / 26}|---"`) plus `"e|" + "-".repeat(1414)` returns `listOf(lines)` as well.
- `every copy of a repeated string name starts a system`: `List(16_000) { "e|---" } + ("e|" + "-".repeat(100_000))`
  at `maxColumns = 40` returns in well under a second with `rows.sumOf { it.size } < 20_000` (measured: 18 ms,
  18,632 lines; before the fix `OutOfMemoryError`).
- Keep every existing test green (single-system behaviour is unchanged; verified).

## Verify
1. `./gradlew :chordpro:desktopTest`.
2. `./gradlew :app:android:assembleDebug`, import the two-system song above, open it in portrait on a phone: rows read
   C, G, then Am, F. Rotate to landscape: the tab fits and is one block as before.
3. Desktop (`./gradlew :app:desktop:run`): narrow the window across the width at which the tab starts wrapping, and
   back — no jump in the order; open the crafted 97 KB file: it opens, no `OutOfMemoryError`.
4. Web (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`): the crafted file opens without freezing the tab.

## Docs
`chordpro/CLAUDE.md`, the `ChordProTabWrapper` bullet: after its first sentence ("… every line of the run is cut at the
same columns instead — … at the edge.") add "A run is whatever lines of a tab environment no blank line separates, and
it may stack several systems, so it is cut system by system: a new one starts at the lines above a staff that follows
another, or where the string names start over, all of them in order (a name that recurs inside one system, `E|` for
both E strings or DADGAD's three `D|`, does not); every system is cut at columns of its own and its rows come before
the next one's." and at the end: "A run that would wrap into more row lines than half its characters, which only a
crafted file does, is returned whole." Update the class KDoc of `ChordProTabWrapper` (and `wrap`'s) the same way,
including that lyrics written under a staff travel with the next system.
`presentation/CLAUDE.md` needs no change (it defers the cut rules to `ChordProTabWrapper`).

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabWrapper.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabWrapperTest.kt`
- `chordpro/CLAUDE.md`

## Depends on
Nothing.
