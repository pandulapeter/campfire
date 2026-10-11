# Let each chunk of a section compose its own items without walking the whole section

**Challenged:** sound

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RenderSection.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongSectionContent.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionFolding.kt` (only if a helper moves there),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RenderSectionsTest.kt`

**Depends on plan 01** (the tab rows and grid bars come from `SongTextMeasurements`, so a chunk no longer needs the
whole run in hand to remember them per run).

## Problem

On the song details screen a section that may be cut is composed as one unit per item (`SongUnits.of`), and every unit
runs `SongSectionContent`, which finds its item by walking the whole section (`SongSectionContent.kt`):

```kotlin
val runNameCounts = mutableMapOf<String, Int>()
var item = 0
section.parts.forEach { part ->
    when (part) {
        is RenderSection.Comment -> if (item++ in items) {
        ...
        is SectionPart.Lines -> part.runs.forEach { group ->
            val kind = group.first().foldableKind()
            if (kind == null) {
                group.forEach { line ->
                    if (item++ in items) when (line) {
```

A section of n items is n units, so composing it costs O(n²) iterations. Every unit recomposes on every whole-percent
step of a pinch (`fontScale` and the styles are parameters) and on every fold (`foldedRuns` is a new instance), so the
walk is paid again each time. Real songs have sections of 4 to 30 lines, where it does not matter. `LayoutBudget`,
though, deliberately lets a single section of up to `MAX_LINES = 3_000` lines through ("or inside a first section too
long on its own (a file with no blank line in it)"): a log or a lyrics dump with no blank line. A throwaway JVM probe
of exactly this loop shape (3000 units, each walking 3000 lines with an `in IntRange` check) took about 16 ms per
full pass on an Apple-silicon desktop JIT once warmed up (64 ms cold), so on a low-end phone or the wasm build it is a
frame budget or several by itself, on every pinch step, on top of composing 3000 units.

## Fix

Precompute, once per section and off the main thread (sections are built by `rememberSongLyricsModel` on
`Dispatchers.Default`), what each item of the section is, so that a chunk iterates only `items`.

1. In `RenderSection.kt`, next to `itemKinds`, add to `RenderSection.Lines`:

   ```kotlin
   /** What each of the section's items is, in the order [itemKinds] counts them, see [SectionItem]. */
   val sectionItems: List<SectionItem> = buildSectionItems(parts, foldKey)
   ```

   with

   ```kotlin
   internal sealed interface SectionItem {
       /** A comment or a key change standing between two lines. */
       data class Inline(val part: SectionPart) : SectionItem
       /** A line that is not part of a run of tablature or a grid. */
       data class Line(val line: ChordProLine) : SectionItem
       /** The [slot]-th of the [slotCount] pieces of a run of tablature or grid lines, folded under [runFoldKey]. */
       data class RunSlot(val run: List<ChordProLine>, val kind: FoldableKind, val slot: Int, val slotCount: Int, val runFoldKey: String) : SectionItem
   }
   ```

   `buildSectionItems` walks `parts` exactly the way `SongSectionContent` does today: one `Inline` for every
   `RenderSection.Comment` and `RenderSection.KeyChange` part, one `Line` per line of a run with no foldable kind, and
   `runSlotCount(kind)` `RunSlot`s per foldable run, the run key computed with the same
   `runNameCounts.nextRunFoldKey(sectionFold = foldKey, label = run.first().environmentLabel, kind = kind)` counter,
   called once per foldable run in order, whether or not the run is ever shown (as today: "Every item is counted, drawn
   or not, and so is every run's fold"). The list's size must equal `itemKinds.size`; `itemKinds` may be derived from it,
   but leave that refactor out unless it is trivially the same.

2. In `SongSectionContent.kt`, replace the walk with a loop over `items` (clamped to `sectionItems.indices`), keeping
   every behavior:
   - `Inline` draws `SongComment` / `SongKeyChange` with the same modifiers.
   - `Line` draws the same `Text` / `SongLineWithChords` / blank `Text` as today.
   - Consecutive `RunSlot`s of the same run (same `runFoldKey`) form one group, the equivalent of today's
     `shownSlots`: if `wholeSectionKind == null && foldedRuns != null`, the `FoldToggleRow` is drawn when slot 0 is in
     the group, a collapsed run draws nothing more, and the group's `runModifier` is
     `Modifier.fadingIn(isFadingIn = foldedRuns.hasBeenToggled(runFoldKey))`, composed once per group as today; otherwise
     `runModifier` is `Modifier`. Then the group's slots are drawn as today (`SongTabBlock` per slot with
     `isLastSlot = slot == slotCount - 1`, or the grid `Column` with one `SongGridLine` per slot), with `rows` /
     `alignedLines` taken from `textMeasurements` as plan 01 does.
   - Remove `runNameCounts` and the `item` counter.

   The composables emitted, their order, and their modifiers must be the same as today for every `items` range, both
   the one-item ranges of the details screen and the whole-section range of an uncut or folded section and of the
   editor's preview.

## Tests

In `RenderSectionsTest.kt` (pure, `commonTest`), build a `RenderSection.Lines` with `foldKey = "intro#1"` whose parts are
a lyrics line, a comment, an unlabelled tab run of two lines, a blank line, a grid run of three lines labelled
"Rhythm", and a second unlabelled tab run, then assert:

- `sectionItems.size == itemKinds.size`, and the kinds line up (`Line` of a blank where `itemKinds` says `BLANK`, `Inline`
  where it says `COMMENT`, `RunSlot` where it says `RUN`).
- The run slots carry `runFoldKey`s `"intro#1/tab#1"`, `"intro#1/Rhythm#1"` (the label as written, see
  `nextRunFoldKey`: `label ?: kind.name.lowercase()`), and `"intro#1/tab#2"`, matching what `nextRunFoldKey` produces
  in today's walk.
- The grid run has three `RunSlot`s with `slot` 0, 1, 2 and `slotCount` 3; the tab runs have `runSlotCount(TAB)` slots each.

## Manual check

Open a song with a verse, a chorus card, a tab run inside a verse, and a grid; fold and unfold the section and each
run, pinch the size, and step through the pages: everything must look and behave exactly as on master (toggle rows,
fade-in, cut points, the chorus card's padding). Then open a file of about 3000 lines with no blank line in it (paste a
long log into a new song) on an Android phone and pinch: the pinch should respond noticeably better than on master,
and the cut notice under the last line must still be there.
