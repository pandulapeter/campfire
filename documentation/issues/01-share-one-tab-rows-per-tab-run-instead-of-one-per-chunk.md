# Share one `TabRows` (and one set of aligned grid bars) per run across all of the run's chunks

**Challenged:** sound

**Kind:** performance  ·  **Severity:** high  ·  **Platforms:** all
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongSectionContent.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongTextMeasurements.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongTabBlock.kt` (KDoc only),
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongTextMeasurementsTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`

## Problem

On the song details screen (`canCutSections = true`) every item of a section that may be cut is its own unit
(`SongUnits.of`: `List(itemCount) { item -> item..item }`), and `SongLyrics` composes each unit as a separate
`SongSectionContent` under `key(unitKeys[unit])`. A run of tablature is several items: `runSlotCount` gives it "a slot for
every line and for every [MIN_TAB_SLOT_COLUMNS] characters of the longest", capped at `MAX_TAB_SLOTS = 24`. A six-string
staff of 80 characters is therefore 6 + 80 / 12 = 12 units.

Each of those units runs this in its own composition (`SongSectionContent.kt`, in the `FoldableKind.TAB` branch):

```kotlin
val lines = remember(group) { group.map { (it as? ChordProLine.Tab)?.text.orEmpty() } }
val rows = remember(lines, monospaceLyricsStyle, textMeasurements.textMeasurer) {
    TabRows(lines, monospaceLyricsStyle, textMeasurements.textMeasurer)
}
```

So a run cut into K slot units builds K `TabRows` instances for the same lines. Each instance:

- measures `rowGap` and `characterWidth` on construction, and `naturalWidth` (every line of the run, unwrapped) lazily;
- in `at(width)`, wraps the **whole** run with `ChordProTabWrapper` and measures every row line, for every width the
  column search asks about (`SongTabBlock`'s measure block calls `rows.at(width)` and keeps only its own slot).

The page's measurer is `rememberTextMeasurer(cacheSize = 0)` (`SongLyrics.kt`), so nothing below `TabRows` deduplicates
the work. The column search asks every unit for its height at several widths (and at more widths when the text is
fitted, see plan 04), so a tab run costs K times the wraps and text layouts it needs, on the main thread, on the first
frame of the page and on every whole-percent step of a pinch (a pinch changes `lyricsStyle`, which re-keys both the
`remember` above and `SongTextMeasurements`). With 12 to 24 slots per staff, a song with two or three tab runs spends
most of its layout time re-wrapping the same tablature.

The aligned grid bars have the same shape, but cheaper: in the `FoldableKind.GRID` branch every unit of a grid run runs

```kotlin
val alignedLines = remember(group) {
    group.filterIsInstance<ChordProLine.Grid>().map { it.tokens }.alignedGridBars { it.displayText() }
}
```

for the whole run, so a grid of n lines aligns n lines n times. This is only paid when a unit is first composed (the
key `group` survives a pinch), so it is a small part of this fix.

## Fix

Keep one `TabRows` per run (and one aligned grid per run) for the page, in `SongTextMeasurements`, which is already the
page's per-measurer, per-style cache ("It lives for as long as the measurer and the three styles do"; `SongLyrics`
remembers it by `textMeasurer, lyricsStyle, chordStyle, annotationStyle`).

1. In `SongTextMeasurements`, add

   ```kotlin
   private val tabRows = HashMap<TabRunKey, TabRows>()
   private val gridBars = HashMap<List<ChordProLine>, List<List<List<GridCell>>>>()

   /** The rows of the tab run of [lines] in [style], one for every chunk the run is drawn as. */
   fun tabRows(lines: List<String>, style: TextStyle) =
       tabRows.boundedTo(MAX_TAB_RUNS).getOrPut(TabRunKey(lines, style)) { TabRows(lines, style, textMeasurer) }

   /** The bars of the grid run [run], aligned once for every chunk the run is drawn as. */
   fun gridBars(run: List<ChordProLine>) = gridBars.boundedTo(MAX_TAB_RUNS).getOrPut(run) {
       run.filterIsInstance<ChordProLine.Grid>().map { it.tokens }.alignedGridBars { it.displayText() }
   }
   ```

   with `private data class TabRunKey(val lines: List<String>, val style: TextStyle)` and a bound of its own
   (`MAX_TAB_RUNS`, 64 is plenty: a song has a handful of runs, and the bound exists for the editor's preview, which makes
   a new run on every keystroke typed into a tab). Generalize the existing `bounded()` into
   `boundedTo(max: Int)` (or add a second helper) so both bounds use the same "start over when full" rule; keep
   `MAX_MEASURED_TEXTS` for the three existing maps. The style must stay part of the key: the monospace style is
   `lyricsStyle.copy(fontFamily = LocalMonospaceFontFamily.current)`, not one of the styles the class is keyed by.
   The grid bars depend only on the tokens (`GridToken.displayText()` in `SongGridLine.kt` is pure), so the run alone
   keys them; they could live elsewhere, but putting them next to the tab rows keeps "what a page computed once for all
   its chunks" in one place. Update the class KDoc to say that the runs of tablature and grids are kept here too, so
   that every chunk of a run shares them.

2. In `SongSectionContent.kt`, replace the two `remember` blocks with lookups through the shared cache:

   ```kotlin
   val rows = remember(lines, monospaceLyricsStyle, textMeasurements) { textMeasurements.tabRows(lines, monospaceLyricsStyle) }
   ...
   val alignedLines = remember(group, textMeasurements) { textMeasurements.gridBars(group) }
   ```

   The `remember` stays so that a composition does not hash the run's lines again; a `TabRows` a unit still remembers
   after the map was cleared stays valid (it only stops being shared), so clearing is safe.

3. In `SongTabBlock.kt`, adjust the `TabRows` KDoc: one instance is shared by every slot of the run, which is why
   asking about the same few widths from K slots costs one wrap per width. Nothing else in `TabRows` changes;
   `MAX_TAB_WIDTHS = 8` still fits, since every slot asks about the same widths.

Behavior is unchanged: the same rows at the same widths, the same drawing, the same intrinsic answers.

## Tests

Add `presentation/src/desktopTest/.../screens/songDetails/SongTextMeasurementsTest.kt` (desktop, since it needs a real
`TextMeasurer`; build it the way `PrintRendererTest` does:
`TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)`):

- `tabRows` returns the same instance for equal lines and an equal style, asked twice (what two chunks of one run do).
- It returns a different instance for different lines, and for the same lines in a different style.
- After more than `MAX_TAB_RUNS` distinct runs, the map starts over (the first run's instance is no longer returned),
  so the editor's preview cannot grow it without bound. Expose the bound as `internal` for the test if needed.
- `gridBars` returns the same list instance for an equal run.

## Manual check

On a phone held upright (one column, paged) open a song with a long tab run (the demo song's picking pattern, or a
song with two 6-string staffs of 80+ characters). Pinch the text size slowly up and down: the pinch should feel as
smooth as on a song without tablature, and the tab must still wrap into the same systems at every size, cut between
systems across pages exactly as before. Repeat on the desktop with the window resized by dragging its edge, and in the
editor's preview while typing into a `{start_of_tab}` block (the preview must keep wrapping correctly as the tab grows).

**CLAUDE.md:** in `screens/songDetails/CLAUDE.md`, extend the sentence "Every text the page measures by hand goes
through one `TextMeasurer` per page, and what the chorded lines ask of it (…) is kept in `SongTextMeasurements` for the
page rather than per line" to say that the rows of each tab run and the aligned bars of each grid run are kept there
too, once for all the chunks the run is drawn as.
