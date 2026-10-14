<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Make the PDF group a song's sections and its tab and grid runs with the screen's own functions

**Kind:** consistency (decided behaviour change in the PDF) · **Severity:** medium · **Platforms:** all
**Challenged:** amended — commit 2 names what must stay visible to `PrintLayouter` until commit 3 (it did not compile
as written); commit 3 now keeps empty segments for the break rule, puts the label on the first segment that prints
(a hidden first piece before a break stranded it), flattens a recall's segments, builds a chorus's comments and key
lines at the indented width, and spaces only after a group that placed rows (`hidden instrumental chord lines do not
reserve space` would fail otherwise); one test added for the break case.
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songLayout/LineRuns.kt` (new, moved from
`screens/songDetails/SectionFolding.kt`, which goes),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songLayout/SectionGroups.kt` (new, moved
out of `screens/songDetails/SongRenderSections.kt`),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionFolding.kt` (deleted),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongRenderSections.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RenderSection.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionChunks.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongSectionContent.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongTabBlock.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRows.kt` (new),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songLayout/LineRunsTest.kt` (new),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songLayout/SectionGroupsTest.kt` (new),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RenderSectionsTest.kt` (imports),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRendererTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`,
`.github/scripts/size_allowances.json`

User decision 3 (2026-10-14): **the PDF follows the screen's rules, through shared code.** This plan carries it out for
how a song's blocks are grouped into sections and how a section's lines are grouped into tab and grid runs. Land it
before plan 11, which moves the song details files (it already expects the two new `songLayout` files to be here).

## Problem

The song details screen and the PDF each decide, with code of their own, which blocks of a parsed song make one
section and which lines make one run of tablature or grid, and the two disagree.

**Sections.** The screen joins a section that a comment, a break, a `{transpose}` (or, with the metronome off, a
`{tempo}`/`{time}`) cut in two back into one, with the comments it opens and ends with, through `joinCutSections` in
`screens/songDetails/SongRenderSections.kt`:

```kotlin
private fun List<ChordProBlock>.joinCutSections(joinsTimings: Boolean = true): List<List<ChordProBlock>> {
    fun ChordProBlock.isJoinedCut() = isSectionCut() || (joinsTimings && this is ChordProBlock.Timing)
    ...
}

/** The section a group of [joinCutSections] is of, or its only block where it is not one. */
private fun List<ChordProBlock>.firstSection() = firstOrNull { it is ChordProBlock.Section } ?: first()

private fun ChordProBlock.isCommentPlaced(placement: CommentPlacement) = this is ChordProBlock.Comment && this.placement == placement
...
private fun ChordProBlock.isSectionCut() = this is ChordProBlock.Comment || this is ChordProBlock.Break || this is ChordProBlock.Transpose
```

The PDF (`print/PrintLayout.kt`, `PrintLayouter.song`) places every block on its own (`blocks.forEachIndexed { … place(timingRows + rows) … }`)
and patches the chorus back together with two heuristics of its own:

```kotlin
/** A chorus section, or a comment written inside one or at the start of one, which is printed as part of it. */
private fun List<ChordProBlock>.isInChorus(index: Int): Boolean = when (val block = this[index]) { … }

/** Whether the block after [index] is the same chorus going on: its continuation, or a comment inside or at the start of it. */
private fun List<ChordProBlock>.continuesChorusAfter(index: Int): Boolean { … }
```

which look at the neighbouring block only. So on paper, unlike on screen:

1. A section cut by a comment, a key change, a keyless transposition or an unprinted timing change is placed as
   separate pieces, each kept whole on its own, so a section that fits a column can still be split between two at the
   cut.
2. The chorus bar breaks at a key change (`continuesChorusAfter` is false for a `Transpose`, and `keyChangeRows` is not
   indented), at an unprinted timing change, and before a comment that follows a break.
3. Halves joined over a cut that prints nothing (a transposition of a song with no `{key}`, a key change with Key off,
   a timing change with Tempo off) are still separated by the 0.65-line gap `spaceAfter` adds, and a tab or grid run
   that the cut falls inside is wrapped as two runs.
4. A comment that opens a section (`CommentPlacement.START_OF_SECTION`) is printed above the section's heading; the
   screen draws it under the heading, inside the section.
5. With the chords hidden, a section whose first piece is only tablature loses its heading even where a later piece of
   it prints lyrics (`sectionRows` returns nothing for the first piece, and a continuation is never labelled).
6. A chorus recall whose chorus prints only a comment drops its heading (`recallRows` prints the heading only where
   *nothing* printed); the screen prints the heading after the comment.

**Runs.** The screen's rule, `groupIntoRuns` in `screens/songDetails/SectionFolding.kt`:

```kotlin
internal fun List<ChordProLine>.groupIntoRuns(): List<List<ChordProLine>> {
    ...
        if (first != null && (first.foldableKind() != line.foldableKind() || first.environmentLabel != line.environmentLabel)) {
```

starts a run where the kind of line or the environment's label changes. The PDF's `sectionRows` / `tabRows` start a
tab run wherever `ChordProLine.Tab.continuesEnvironment` is false and a grid run where the label changes:

```kotlin
is ChordProLine.Tab -> {
    val startsRun = lineIndex == 0 || section.lines[lineIndex - 1] !is ChordProLine.Tab || !line.continuesEnvironment
```

So two `{start_of_tab}` environments written back to back under one label (or both unlabelled) are one run on screen
and two in the PDF — two wraps, and the label row (`line.label?.takeUnless { it == sectionLabel }`) printed twice.

### The `continuesEnvironment` rule — decided

The screen's rule wins: **a run is a stretch of lines of one kind under one environment label** (`groupIntoRuns`), and
the layout no longer reads `continuesEnvironment`. Checked whether the screen is wrong instead:

- `continuesEnvironment` is `:chordpro`'s notion of a *fingerboard* (`ChordProLine.Tab` KDoc; `ChordProTabs.runsOf`,
  `ChordProChordRewriter`): it decides what is moved to the player's instrument as one. Where two such environments are
  moved differently, `ChordProTabs.sectionWithNotices` cuts the section with a notice comment in front of the run, and a
  comment always ends a run on screen too, so the screen never draws two differently moved environments as one block.
- Drawn as one run, two back-to-back staffs get the blank row between their systems that `ChordProTabWrapper` and
  `TabRows.rowGap` give a wrapped run; drawn as two runs (the PDF today) they get no gap at all when wrapped, the
  "last string read as the first string of the next staff" problem `SongTabBlock` exists to avoid.
- Fold keys are saved in the preferences (`RunFoldKeyTest`): changing the screen's rule would renumber the folds of
  every later run of the same name in a section that has two back-to-back environments.

So the screen is not wrong, and only the PDF changes.

## Fix

Three commits, each compiling and passing the tests.

### Commit 1 — move the shared functions into `ui/songLayout` (pure move)

1. `git mv` `screens/songDetails/SectionFolding.kt` to `songLayout/LineRuns.kt` and change its package to
   `com.pandulapeter.campfire.presentation.ui.songLayout`. It holds exactly `foldableKind`, `runSlotCount`,
   `wholeFoldableKind`, `environmentLabel`, `groupIntoRuns`, `MIN_TAB_SLOT_COLUMNS`, `MAX_TAB_SLOTS`; keep them
   `internal` (the constants stay `private`). Drop the now-pointless `import …songLayout.FoldableKind` /
   `…songLayout.areAll` (same package) and the stray `import androidx.compose.foundation.layout.size`. The KDoc link
   `[SongTabBlock]` in `runSlotCount` no longer resolves from `songLayout`; write it as `` `SongTabBlock` `` (the
   codebase's form for a name in another package, as `SongUnits` does for `` `SongSectionsLayout` ``).
2. Move `joinCutSections`, `firstSection`, `isCommentPlaced` and `isSectionCut`, with their KDoc, verbatim from the end
   of `SongRenderSections.kt` into a new `songLayout/SectionGroups.kt` (license header, package `…ui.songLayout`).
   `joinCutSections` and `firstSection` become `internal`; `isCommentPlaced` and `isSectionCut` stay `private` (only
   `joinCutSections` calls them).
3. Add the imports where these are now used: `RenderSection.kt` (`foldableKind`, `runSlotCount`, `wholeFoldableKind`,
   `environmentLabel`, `groupIntoRuns`), `SongSectionContent.kt` (`wholeFoldableKind` is a property of
   `RenderSection.Lines`, but `environmentLabel` is called directly), `SectionChunks.kt` and `SongTabBlock.kt` (KDoc
   `[runSlotCount]` only), `SongRenderSections.kt` (`joinCutSections`, `firstSection`), `RenderSectionsTest.kt`
   (`runSlotCount`). Let the compiler find any other.
4. Check the move mechanically (`git diff --color-moved=dimmed-zebra HEAD~1`), then grep the repository for
   `SectionFolding` and for each moved name: `screens/songDetails/CLAUDE.md` names `joinCutSections` and `runSlotCount`
   without a path, which stays right.

Message, e.g.: `Move the line runs and the section groups into songLayout, for the PDF to share.`

### Commit 2 — split the PDF's row building out of `PrintLayout.kt` (pure move)

`PrintLayout.kt` is 727 lines against a 500-line limit (allowance 727, which may only shrink), and commit 3 adds code
to it. Move the row builders into `print/PrintRows.kt` first, verbatim:

- `internal class PrintRows(options: PrintSettings, labels: PrintLabels, columnWidth: Float, capacity: Float,
  measureText: (String, PrintStyle) -> Float)` takes the styles (`titleStyle` … `gridStyle`, `labelSpace`), the
  `shortMeasurements` cache and `measure`, and the functions `blockRows`, `sectionRows`, `lyricsRows`, `tabRows`,
  `gridRows`, `gridLineRows`, `keyChangeRows`, `timingRows`, `tempoDetails`, `commentRows`, `recallRows`, `wrapped`,
  `labelRows`, `boxedRows`, `chorusBar`; with them the file-level `Part`, `Row` (now `internal`; nothing else in
  `ui/print` declares or imports another `Row`/`Part`, but check), `isInChorus`, `continuesChorusAfter`, `isChorus`,
  `printText`, `isPrintBlank`, `METADATA_SEPARATOR`, `CHORUS_INDENT`, `CHORUS_BAR_WIDTH`, `FRAME_WIDTH`.
- `PrintLayouter` builds one `PrintRows` and calls through it; it keeps the geometry, the cursor, `place`, `overview`,
  `song`, `chordDiagramRows`, `diagramNameLines`, `playingDetails`, `blockRowsAt`, `spaceAfter`, `finish`, the diagram
  types and constants, and `List<Row>.height()`.
- **What the layouter still reaches** must not be `private` in commit 2, or it does not compile: `PrintRows`'s
  `measure` (the layouter's `diagramNameLines`, `chordDiagramRows` and `finish` call it, and the cache belongs with
  it), `wrapped`, `tempoDetails` (`playingDetails`), `blockRows` (`blockRowsAt`), and the styles `titleStyle`,
  `chordStyle`, `detailStyle`, `overviewArtistStyle` (`overview`, `song`, the diagrams) are members without
  `private`; the file-level `Part`, `Row`, `METADATA_SEPARATOR`, `CHORUS_BAR_WIDTH`, `isInChorus` and
  `continuesChorusAfter` are `internal` (`chordDiagramRows` builds `Part`s and `Row`s, `playingDetails` joins with
  `METADATA_SEPARATOR`, and `blockRowsAt` and `spaceAfter` call the last three until commit 3 deletes them). Commit 3 makes private again whatever nothing outside `PrintRows.kt` uses
  any more.
- Run `python3 .github/scripts/check_sizes.py --update` in this commit (both files end up under 500 lines, so the
  `PrintLayout.kt` allowance goes).

Message, e.g.: `Move the rows of a printed song into a class of their own.`

### Commit 3 — print by the screen's groups and runs (the behaviour change)

In `PrintRows`:

1. **One group, one unit.** Replace `blockRows`/`sectionRows` for sections with
   `groupSegments(group: List<ChordProBlock>, width: Float, labelOverride: String? = null): List<List<Row>>` for a group
   of `joinCutSections` whose `firstSection()` is a `ChordProBlock.Section`:
   - `isChorus` is that first section's `type == SectionType.Chorus`; `lineWidth` is `width - CHORUS_INDENT` for a
     chorus; `sectionLabel = first.header(labels.sections).ifEmpty { null }` (as today); the label printed is
     `labelOverride ?: sectionLabel?.takeUnless { first.isContinuation }` (as today: only a section after a printed
     timing change or a recall cut starts with a continuation).
   - Walk the group's blocks in order, building *parts* the way the screen's `addSection` builds `SectionPart`s: a
     `Section`'s lines are appended to the previous part where that is lines too (so halves joined over a cut that
     printed nothing are one run of lines again), else start a new lines part; a `Comment` is a part where
     `commentRows` prints anything; a `Transpose` is a part only where `keyChangeRows` prints anything; a `Timing`
     inside a group (only there when it was joined, i.e. Tempo is off) adds nothing; **a `Break` ends the segment**: the
     parts after it start a new segment, and the lines on its two sides are not joined. This is the one rule the PDF
     keeps of its own — a page has columns to break and the screen has none (`ChordProBlock.Break -> Unit // The column
     layout makes its own breaks.`).
   - A lines part's rows: `lines.groupIntoRuns().flatMap { run -> when (run.first().foldableKind()) { TAB -> tabRows(run,
     sectionLabel, lineWidth); GRID -> gridRows(run, lineWidth); null -> run.flatMap { line rows as today } } }`, tab and
     grid runs only with `options.showChords`. `tabRows` and `gridRows` take the run itself instead of
     `(lines, lineIndex)`: delete their `takeWhile` scans (the `continuesEnvironment` one and the label one) — the run is
     what `groupIntoRuns` gave. `tabRows` keeps printing the run's own label where it differs from the section's.
   - Between two printed parts a gap row `Row(emptyList(), options.fontSize * 0.65f)` (the gap `spaceAfter` and the
     chorus's bar gap give today between pieces).
   - **Hidden lines**: where the group has lines and none of its lines parts prints a non-blank part
     (`row.parts.any { !it.text.isPrintBlank() }`, today's test), the group prints only its comment parts, unlabelled and
     without a bar, as the screen's `addSection` adds only `comments` there. A group whose sections have no lines at all
     keeps its label (`a section written without lines keeps its label`).
   - **Empty segments are kept** in the returned list (a segment whose parts print nothing is an empty list), so
     that the layouter still sees every break: today a break moves to the next column whatever follows it.
   - The label rows go at the top of the **first segment that prints anything** — not simply the first: "Intro" of tab
     lines, a `Break`, a lyrics continuation, with the chords hidden, would otherwise leave the label alone at the
     bottom of one column and the lyrics in the next. **A chorus's bar is drawn over every segment whole**
     (`chorusBar(segmentRows, startsWithLabel = segment carries the label)`), comments and key change lines included,
     since they are inside the indent now: build a chorus's comment and key change parts at `lineWidth`, not `width`
     (`commentRows` loses its `isInChorus` parameter and its recursive chorus branch, which did that indent itself).
2. **Other groups** are a single block: a `Comment` group → `commentRows(comment, width)`, a `Transpose` →
   `keyChangeRows`, a `Timing` → `timingRows`, a `ChorusRecall` → `recallRows`, a lone `Break` → a column break (below).
3. **Recalls** use the same groups: `recall.blocks.joinCutSections()` (timings joined, as the screen's
   `block.blocks.joinCutSections()`), the recall's heading as `labelOverride` on the first section group that prints
   anything, comment groups printed plainly, and where no section group printed, the heading after whatever did print,
   on the chorus bar (the screen's `RenderSection.Lines(header = it, parts = emptyList(), isOnCard = true)`). A
   recall stays one unit: its groups' segments are concatenated (`flatten()`), since a `Break` inside a recalled chorus
   (the parser copies the breaks between its halves, `ChordProChorusRecalls`) prints nothing today and the screen
   ignores it too.
4. Delete `isInChorus`, `continuesChorusAfter`, `isChorus`, and the `isInChorus` parameters.

In `PrintLayouter.song`:

5. `val groups = blocks.joinCutSections(joinsTimings = !options.showTempo)` (blocks already `withNumberedSections`, as
   today; the screen joins timings exactly when it does not show them). Iterate the groups where the blocks were
   iterated, keeping every rule of the loop: the first non-empty segment of the first group that prints anything (a
   lone `Break` group skipped, a `Timing` group counting, as today) travels with the heading, standing for
   `firstBlockRows`, and every break before it — a lone `Break` group or a segment boundary — is ignored, as today's
   `index < firstIndex -> Unit` ignores them; after it, a lone `Break` → `if (y > margin) nextColumn()`, and so does
   every segment boundary, **whether or not the segment after it prints anything** (today's rule: `block == Break ->
   if (y > margin) nextColumn()`); a non-empty segment is `place`d (kept whole where it fits a column, else its first
   two rows together); a `Timing` group's rows are held and placed with the next non-empty segment; after a group
   **that placed rows**, `space(options.fontSize * 0.65f)` — never after one that printed nothing or a held timing,
   which `hidden instrumental chord lines do not reserve space` checks to the point (no `continuesChorusAfter`
   exception any more: a chorus is one group). `yield()` every 50 groups instead of blocks.
6. Delete `blockRowsAt` and `spaceAfter`.

What changes in the PDF, then, and nothing else:

| Case | Today | After |
|---|---|---|
| Section cut by a comment / key change that fits a column | pieces placed separately, may split at the cut | kept whole in one column |
| Chorus cut by a key change, a hidden timing, or a break before an inside comment | bar breaks, key line unindented | one bar over the whole chorus, key line indented |
| Cut that prints nothing (keyless transposition, Key off, Tempo off) | 0.65-line gap, a run across it wrapped as two | no gap, one run |
| Comment opening a section | above the heading | under the heading |
| Chords hidden, first piece only tab, later piece lyrics | heading lost | heading kept |
| Back-to-back tab environments under one label | two runs, label printed twice | one run, label once |
| Recall whose chorus prints only a comment | heading lost | heading after the comment, on the bar |

Kept as they are, on purpose: a break starts a new column (and splits a run it falls inside); the PDF's own options
(comments, key, tempo, chords, diagrams); everything at the level of a line (which chords and annotations a line keeps
with the chords hidden, blank lines, wrapping), which is not a grouping rule and is outside this plan.

7. Docs: add one bullet to `print/CLAUDE.md`'s Layout list: "Sections and runs are grouped by the screen's own `joinCutSections` and `groupIntoRuns` (`ui/songLayout/`):
   a chorus is one unit under one bar, a break the one cut the PDF keeps." In `screens/songDetails/CLAUDE.md`, append
   "(shared with the PDF)" after `joinCutSections` in the Rendering bullet. Both files are over their budgets on
   allowances: cut as many words elsewhere in the same file as you add, and run `check_sizes.py --update`.

Message, e.g.: `Print a song's sections and tab runs as the song details screen groups them.`

Before the last commit, grep the repository for `continuesChorusAfter`, `isInChorus`, `blockRowsAt`, `SectionFolding`.

## Tests

Commit 1 — pure functions, newly reachable from `songLayout` tests (`commonTest/…/ui/songLayout/`):

- `LineRunsTest`: `groupIntoRuns` splits tab from grid from other lines and at a blank line, splits two environments
  with different labels, and keeps two back-to-back environments under one label (`continuesEnvironment = false` on
  the second's first line) as **one** run — the decided rule, pinned; `wholeFoldableKind` of a tab-only section with
  a blank line inside is `TAB`, of a mixed one null; `runSlotCount` of a grid is its grid line count, of a tab at
  least 1 and at most 24.
- `SectionGroupsTest`: `joinCutSections` puts opening comments, a continuation after a comment, a break and a
  transposition, and an ending comment into the section's group; a comment between two sections is a group of its
  own; a `{chorus}` recall is never joined; a `Timing` before a continuation is joined only with `joinsTimings`;
  `firstSection` of a comment-led group is the section.

Commit 2 — none (verbatim move; the existing `PrintLayoutTest` and `PrintRendererTest` are the check).

Commit 3 — `PrintLayoutTest`, one test per row of the table, built with the file's `song(…, blocks)` helper:

- `a chorus cut by a key change keeps one bar under its key line`: Chorus, `Transpose(2, key = "E")`, Chorus
  continuation; the "Key: E" text is at `x >= margin + 8`, and the bars are contiguous (as `a comment inside a chorus
  carries its bar` checks them).
- `a section cut by a comment stays in one column where it fits`: fill most of a column with a verse, then a section
  of 2 + comment + 2 lines that fits a column but not the room left; both halves have the same `placeOf(…)` column.
- `a cut that prints nothing leaves no gap`: Verse, `Transpose(0)` (no key), continuation; the first line of the second
  half is exactly one lyric row (`12 * 1.45f`) under the last of the first.
- `a comment opening a section is printed under its heading`.
- `a section keeps its heading where only its first piece is hidden`: section "Intro" of tab lines, `Transpose(0)`,
  continuation with lyrics, `PrintSettings(showChords = false)`: "Intro" is printed.
- `back to back tab environments under one label are one run`: a "Solo" section holding `Tab(…, label = "Riff")`,
  `Tab(…, continuesEnvironment = true, label = "Riff")`, `Tab(…, continuesEnvironment = false, label = "Riff")`: "Riff"
  is printed once.
- `a break inside a section still starts a new column`: Section, `Break`, continuation; the continuation's first line
  is in the next column.
- `a section's label goes with its first printed piece across a break`: "Intro" of tab lines, `Break`, a lyrics
  continuation, `PrintSettings(showChords = false, columns = 2)`, a verse before it so the cursor is below the margin:
  `placeOf("Intro") == placeOf(<the continuation's first lyric>)`.
- `a recall whose chorus prints only a comment keeps its heading`.

The existing tests must pass unchanged; `a comment inside a chorus carries its bar`, `a break at the start of a song does
not strand its heading`, `a timing change is printed where it stands and kept with what follows` and `a timing change is
not left at the end of a column` cover the rules the loop keeps.

`PrintRendererTest`: add `exports a song of cut sections for visual QA`, which lays out and renders one song holding a
chorus cut by a comment and by a key change, a section cut by a break, and two back-to-back tab environments, asserts
only that it renders (page count ≥ 1), and writes `cut-sections.pdf` under `CAMPFIRE_PRINT_QA_DIR` like its siblings,
for the eye. `campfire.pdf` (the importer's fixture, a single verse) is unaffected and is not regenerated.

Verification: the root `CLAUDE.md` test command, `:app:desktop:compileKotlin`, `:app:android:compileDebugKotlin`,
`:app:web:compileKotlinWasmJs`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`, `python3 .github/scripts/check_sizes.py`,
and `CAMPFIRE_PRINT_QA_DIR=/tmp/campfire-print-qa ./gradlew :presentation:desktopTest --tests '*PrintRendererTest*'`,
then open `setlist.pdf` and `cut-sections.pdf` and compare them with the song details screen of the same text.
