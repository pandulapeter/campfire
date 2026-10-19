# Test the Scales tab's search keys and that every chord a seven-note scale stacks has a table shape

**Kind:** bug (test-only, no production code)  ·  **Severity:** low  ·  **Platforms:** all
**Files:**
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/NavigatorTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/ScaleChordsTest.kt`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/ToolsItemTest.kt` (only if plan 11 was not carried out)

**Challenged:** amended — the chord-shape test covered only the guitar and the ukulele, but the sheet draws chords in the player's own tuning (`chordTuning()`), and every fretted `ChordTuning` has a table of its own (`ChordVoicingSearch.table`: drop D, open G/D/C, DADGAD, mandolin, GDGD, five banjo tunings); it now walks every fretted tuning, asks the tables directly (`ChordVoicingSearch.hasTableShapes`) so no session cache can make it pass, and checks each distinct chord once.

## Problem

These paths are untested:

- **The Scales tab's search keys.** `ScreenSearches.toolSearch` answers both the Chords and the Scales tab:
  ```kotlin
  private val toolSearch: ToolSearch?
      get() = when (top()) {
          CampfireDestination.Chords -> chordLibrary()
          CampfireDestination.Scales -> scaleLibrary()
          else -> null
      }
  ```
  `NavigatorTest` builds a `scaleLibrary = ScaleLibraryState(keyIndex = mutableIntStateOf(0))` and passes it in, but only the Chords branch has tests (`` `ctrl f on the chords tab asks its search field for the caret` `` and `` `a step back empties the chords tab's search before leaving the tab` ``). Dropping the `Scales` branch would pass every test.
- **The scale sheet's chord shapes.** `ScaleSheetChords.kt`'s KDoc claims the tables answer every chord the sheet shows:
  > The shape is the tables' only: a chord they lack is drawn as the empty frame ([SongChord.isShapePending]) rather than searched for while the sheet is composed, which the qualities a stack of thirds makes never need

  Nothing checks it. A seven-note scale stacks into `sus2`, `sus4`, `aug`, `maj7#5`, `7#5`, `7b5`, `maj7b5`, `mmaj7`, and `dim7` too (`ScaleChords.triadSuffixes` / `seventhSuffixes`). A quality the guitar or ukulele table lacks would show an empty frame on the sheet.
- **`ToolsItemTest.tools()`** lists Metronome, Tuner, and Chords, not Scales. Plan 11 adds Scales. If plan 11 was not carried out, do that one-line change here, as plan 11's Tests section describes.

## Fix

No production code changes. Add the tests below.

The chord-shape test belongs in `:chordpro`'s `ScaleChordsTest`, not in `:presentation`'s `ScaleSheetChordsTest`, for two reasons:

- `scaleSheetChords` takes `isShapePending` straight from `ChordVoicings.needsSearch(chord, tuning)`. That returns false for any chord whose default was already worked out earlier in the same test JVM (`(chord to tuning) !in defaults`). In `:presentation` the cache cannot be reset (`ChordVoicings.resetCache()` is `internal` to `:chordpro`), so another test that searched the chord would make this one pass falsely.
- In `:chordpro` the test can ask the tables directly (`ChordVoicingSearch.hasTableShapes`, `internal`), which `needsSearch` wraps with the cache.

The claim is about the tables, which are `:chordpro`'s.

## Tests

In `NavigatorTest`, mirror the Chords ones. The `scaleLibrary` field already exists.

- `` `ctrl f on the scales tab asks its search field for the caret` ``:
  1. Collect `scaleLibrary.searchFocusRequests` in `backgroundScope` with `UnconfinedTestDispatcher(testScheduler)`, counting requests.
  2. Call `navigator.selectTopLevelDestination(CampfireDestination.Scales)`. Assert `navigator.searches.openCurrentSearch()` is true and the count is 1.
  3. Select `CampfireDestination.Metronome`. Assert `openCurrentSearch()` is false and the count is still 1.
- `` `a step back empties the scales tab's search before leaving the tab` ``:
  1. Select Scales and assert `isCurrentSearchOpen` is false.
  2. Set `scaleLibrary.query = "dorian"` and assert it is true.
  3. Call `closeCurrentSearch()`. Assert the query is `""` and `isCurrentSearchOpen` is false.
- `` `the scales tab's search is no other screen's to empty` ``:
  1. Set `scaleLibrary.query = "dorian"` while Songs is on top, and assert `isCurrentSearchOpen` is false.
  2. Call `songsSearch.open()`, then `closeCurrentSearch()`.
  3. Assert `songsSearch.isOpen.value` is false and the query is still `"dorian"`.

In `ScaleChordsTest`:

- `` `every chord a seven-note scale stacks has a table shape in every fretted tuning` ``:
  1. For every scale in `Scales.all` with `intervals.size == 7`, for every root in `listOf("C", "C#", "Db", "D", "Eb", "E", "F", "F#", "Gb", "G", "Ab", "A", "Bb", "B")`, and for each of `ScaleChords.triads(scale, root) + ScaleChords.sevenths(scale, root)`, parse the name with `ChordProChords.parse(it.name)!!`. Keep the first `"${scale.id} ${it.name}"` per distinct `Chord` (a `LinkedHashMap<Chord, String>`): the tables answer by pitch content, so a chord two scales share is checked once.
  2. For `tuning` in `ChordTuning.entries.filter { it.instrument != ChordInstrument.KEYBOARD }`, collect `"$label ${tuning.id}"` wherever `!ChordVoicingSearch.hasTableShapes(chord, tuning)`.
     - Every tuning, not the guitar and ukulele only: the sheet draws its chords in the player's tuning
       (`userPreferences.chordTuning()`), and each tuning reads its own table (`ChordVoicingSearch.table`, through
       `tableTuning`), so a chord the guitar table has can still be an empty frame in open G or on a banjo.
     - `hasTableShapes` rather than `ChordVoicings.needsSearch`: it is what `needsSearch` asks of the tables, without the
       session's `defaults` cache, which another test in the same JVM may have filled with a searched shape. It is
       `internal` to `:chordpro`, which this test is in (`ChordVoicingsTest` calls `ChordVoicingSearch` the same way).
       No `@BeforeTest` cache reset is needed.
  3. Assert the collected list is empty, with the list as the message, so a failure names every missing chord at once.
  4. Imports: `com.pandulapeter.campfire.chordpro.model.ChordInstrument`, `com.pandulapeter.campfire.chordpro.model.ChordTuning`, `com.pandulapeter.campfire.chordpro.model.Chord` (already there), and `com.pandulapeter.campfire.chordpro.voicings.ChordVoicingSearch`.

If the test fails when written:

- Keep it, annotated `@Ignore` (`kotlin.test.Ignore`), with a KDoc saying the tables lack these chords and listing the failing qualities and tunings.
- If only some tunings fail, split it instead: one test over the tunings that pass, kept running, and the `@Ignore`d one over the rest, so a regression in the passing tables is still caught.
- Report the list to the user as a finding. It means the `ScaleSheetChords.kt` KDoc is wrong and the sheet shows empty frames for those chords.
- Do not change the tables or the KDoc in this plan; that is a fix for the user to decide.
