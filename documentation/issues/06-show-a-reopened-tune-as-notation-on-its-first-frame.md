<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Keep the app's renderings in a bounded cache, hand the page its width at rest up front, and place a tune's first rendering at once, so a tune seen before is notation on its first frame and a new one never sets the song moving

**Challenged again:** amended — the extension holds (the token, the width at rest, Navigation 3 composing the entering
page in the transition's first frame, the summary flag); four corrections: the width at rest is
`maxOf(settledWidth, maxWidth)` less the padding, not `maxWidth + extraWidth` (a difference added back is not
guaranteed to give the same `Dp` on every frame, nor the one a reopen at rest asks the cache for); a rendering that
also re-renders a tune (the reader's change) is no arrival, so the token never silences a change the reader made in
the same rendering; the effect computes `rerenderedTunes` once into a `val` both fields read, so plan 07's widening
reaches the arrival; the warm-up waits a fixed 2 s in common code on every platform instead of an `awaitIdleMoment`
expect/actual (on Android the engine's start takes a core during the cold start the plan said it avoided, and on the
web an idle callback can fire between the frames of an animation and the pause is the engine's whole start, not "one
dropped frame").

**Amended:** extended after the challenge — the cold first open too: a tune's first rendering places every section at
once (no spring, glide, row travel, or fade-in), the details page engraves for its width at rest so a push that hides
the rail renders once, and the engine is warmed in the background once the library holds a tune (a decision).

**Challenged:** amended — the cache is bounded by the words it holds laid out as well as by tunes (a painted tune's
size is its text layouts, and one long tune can hold thousands); each caller computes its horizontal padding once for
both the modifier and `availableWidth`; the UI test first proves the tune was engraved at all, so a renderer that fails
in the test environment cannot pass it vacuously.

**Kind:** bug (wrong first frame, motion nobody caused) · **Severity:** medium · **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/SongNotations.kt`, new
`.../ui/notation/NotationCache.kt`, new `.../ui/notation/NotationEngineWarmUp.kt`, `.../ui/CampfireApp.kt`,
`.../ui/songView/SongLyrics.kt`, `.../ui/songView/SongSectionsLayout.kt`, `.../ui/songView/SectionMotion.kt`
(`SectionGlides`), `.../ui/songView/SongSectionChildren.kt`, `.../ui/screens/songDetails/SongDetailsPage.kt`,
`.../ui/screens/songEditor/SongPreview.kt`, `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/model/ChordProSummary.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`,
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/song/Song.kt`,
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/SongMappers.kt`,
`.../ui/notation/CLAUDE.md`, `.../ui/songView/CLAUDE.md`, `abc/implementation/CLAUDE.md`,
new `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationCacheTest.kt`,
new `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationArrivalTest.kt`,
new `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SectionGlidesTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`,
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/SongMappersTest.kt`,
new `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SheetMusicReopenTest.kt`,
new `tools/screenshots/src/test/resources/songs/campfire_tests-a_tune.cho`

(`...` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation`.) Written against the code as plan
05 leaves it: `NotationRendering` holds its painted `systems`, and `NotationRequest` carries a `painting`. The warm-up's
data pieces (Fix 7) are only for the option of the decision that needs them.

## Problem

Every time a song with an `abc` block is opened (the demo's "Home on the Range" is one), its first frames show the
tune as monospace ABC source, and then the page reflows into staff notation: the source is usually several times
taller or shorter than the staff, so every section below it moves, and on the details screen it **springs** there
during the enter transition, which no other song does. That is a wrong first frame, which the UI rules say to fix at
the source, and data arriving narrated as if the reader had changed something.

Four things make it so, all in `ui/songView/SongLyrics.kt`, `ui/notation/SongNotations.kt`, and what the song details
page hands them:

1. The width the tunes are engraved for is known only after the first layout:

   ```kotlin
   var songWidth by remember { mutableIntStateOf(0) }
   ...
   width = if (songWidth == 0) null else with(density) { ((minOf(songWidth.toDp(), MAX_COLUMN_WIDTH * fontScale) - CARD_PADDING * 2) / notationUnit).toInt() },
   ...
   Box(modifier = modifier.onSizeChanged { songWidth = it.width }) {
   ```

   and `rememberSongNotations` makes no request without one
   (`val request = if (width == null || width <= 0 || tunes.isEmpty()) null else NotationRequest(...)`). So even a
   rendering that could be had at once cannot be on frame one.

2. Renderings live only in the composition of one page:

   ```kotlin
   var rendered by remember(renderer) { mutableStateOf(RenderedNotations.NONE) }
   ```

   Leaving the song (back to the list, the pager moving two songs on, the editor replacing the screen) forgets them, and
   reopening engraves every tune again. The engine is fast once warm (~5 ms a tune on the desktop), but the result is
   still at least two frames late (width, then the effect), and the first tune of a process also starts the engine
   (~150 ms on the desktop, more on a phone: `abc/implementation/CLAUDE.md`).

3. **The first rendering's relayout is narrated.** The details page lays the song out with
   `sectionMotion = if (isChangingContinuously) SectionMotion.GLIDE else SectionMotion.SPRING`, and nothing tells the
   layout that the notation arriving is not a change the reader made. So in the composition the rendering arrives in,
   `SongLyrics` passes `glidesEveryMove = sectionMotion == SectionMotion.SPRING && !isResizing`, and
   `SectionGlides.follow` springs every section, card, and divider that the new heights moved:

   ```kotlin
   if ((glidesEveryMove || isNewGrid) && previous != null && previous != positions[index]) {
       glide.start(jump = previous - positions[index], scope = scope, spec = spec)
   }
   ```

   (`isNewGrid` glides even while `isResizing`, which is the case during a transition that hides a navigation rail);
   `SongSectionChildren` hands `animatesRows = !isResizing && (...)` to the first, Chords, and Scales sections, whose
   rows then travel if the new column width rearranges them. `SongNotationBlock` itself already replaces the text
   without a cross-fade (`isRerendered` is false for a first rendering); only the song around it moves.

4. **A push that hides the rail engraves twice.** While the navigation transition runs, the details screen is as narrow
   as the rail leaves it (`SongDetailsScreen`'s `settledWidth` KDoc), and `SongLyrics` already decides its columns
   for the width at rest (`extraWidth = (settledWidth - maxWidth).coerceAtLeast(0.dp)`). The notation does not: it is
   engraved for the narrow width, and again 300 ms after the transition ends (`isOnlyResized`), and that second
   rendering is a re-rendering (`isRerendering` is true for a width change), so it cross-fades and springs the song
   around it again, a change nobody made.

The details screen and the editor's preview are the two callers; both already sit in a `BoxWithConstraints`
(`SongDetailsPage`'s, `SongPreview`'s) and know the width before composing `SongLyrics`.

## Fix

1. **Width from the constraints, at rest.** Add a required `availableWidth: Dp` parameter to `SongLyrics` (after
   `model`), the width its content gets inside the modifier's padding once any transition resizing it has ended, and
   drop `songWidth` and the `onSizeChanged` (the `Box` keeps `modifier`). Extract the arithmetic into a pure, tested
   function beside `rememberSongNotations`:

   ```kotlin
   /** The SVG units a tune is engraved across on a page [availableWidth] wide: its widest column less a card's padding. */
   internal fun notationPageWidth(availableWidth: Dp, maxColumnWidth: Dp, cardPadding: Dp, notationUnit: Dp): Int? =
       if (!availableWidth.isSpecified || availableWidth <= 0.dp) null
       else ((minOf(availableWidth, maxColumnWidth) - cardPadding * 2) / notationUnit).toInt().takeIf { it > 0 }
   ```

   Callers, each computing its start and end padding once into two `val`s that both its `Modifier.padding(...)` and
   `availableWidth` read, so the two can never drift apart:
   - `SongDetailsPage`: `start = contentPadding.calculateStartPadding(layoutDirection) + PAGE_HORIZONTAL_PADDING`,
     `end` likewise, and the page's width at rest computed once into a `val`
     (`val settledPageWidth = maxOf(settledWidth, maxWidth)`), from which both
     `extraWidth = settledPageWidth - maxWidth` (what it hands `SongLyrics` today, the same value) and
     `availableWidth = settledPageWidth - start - end` are taken: the width at rest, the one the columns are already
     decided for. Not `maxWidth + extraWidth`: a difference added back to a width that changes on every frame of the
     transition is not guaranteed to be the same `Dp` on each, nor the one the page has at rest, and a tune whose width in
     SVG units sits at a whole number would then be engraved again, or miss the cache, for one unit. `settledWidth` is
     the window's width (`CampfireScreens`: the details screen covers the chrome), so at rest, after a resize, on a
     foldable or a phone on its side, and on the web, `maxWidth` is it and nothing changes; only while a push hides the
     rail (or a pop shows it, on the page that leaves) is the column narrower than the tune's page, and the scaling
     ends with the transition: nothing is drawn scaled down at rest. During a transition that hides the rail the tune is then engraved once, for the page it settles into, and
     scaled down to the narrower column meanwhile (`SongNotationBlock` scales a page wider than its column down, as a
     chorus card's narrower inside already does); no second rendering, cross-fade, or spring follows the transition,
     and the key is the one a reopen at rest asks the cache for.
   - `SongPreview`: `start = 16.dp + contentPadding.calculateStartPadding(layoutDirection)`, `end` likewise (its two
     `.padding` calls today, `start = 16.dp, end = 16.dp` and `contentPadding.only(start = true, end = true, ...)`,
     become one horizontal padding of those plus the existing top and bottom ones), `availableWidth = maxWidth - start - end`
     (no transition resizes the editor).

   Away from a transition this is the width the `Box` measured to before (the modifier is `fillMaxSize()` then those
   paddings, in both callers), so the notation is engraved for the same width as today, give or take the pixel the
   padding's rounding moves; the column cap (`MAX_COLUMN_WIDTH * fontScale`), the single-column auto-scroll layout,
   and a chorus card's narrower inside are unchanged. Only the width changes `availableWidth`, not the height, so the
   details page's height-only changes (the metronome panel, the app bar) still skip composing the song. A window
   resize still changes `availableWidth` every frame, as `songWidth` did, so `rememberSongNotations`'s 300 ms settle
   stays as it is.

2. **An app-scoped cache** in new `ui/notation/NotationCache.kt`, keyed per tune by everything its rendering depends
   on, so one edited or added tune does not miss the others:

   ```kotlin
   internal data class NotationTuneKey(
       val tune: String,
       val chords: NotationChordSpelling, // request.chords.movedBy(offset)
       val width: Int,
       val unitSize: Float,
       val songTitle: String?,
       val painting: NotationPainting,
   )

   /** The renderings made last, by tune and everything they were made for, at most [MAX_CACHED_TUNES]; main thread only. */
   internal class NotationCache {
       private val renderings = LinkedHashMap<NotationTuneKey, NotationRendering>()
       operator fun get(key: NotationTuneKey): NotationRendering? = renderings.remove(key)?.also { renderings[key] = it }
       operator fun set(key: NotationTuneKey, rendering: NotationRendering) { renderings.remove(key); renderings[key] = rendering; trim() }
       /** Every tune of [request] rendered, or null where any one is not cached. */
       fun renderedFor(request: NotationRequest): RenderedNotations? = ...
   }
   ```

   Least recently used out first (common Kotlin's `LinkedHashMap` has no access order, hence `remove` and put again).
   **Bounded twice, whichever is reached first: 16 tunes, and 4,000 words laid out** (the sum of every cached
   rendering's `systems.sumOf { it.texts.size }`, kept as a running total beside the map). 16 tunes covers the three
   pages the details pager composes, a few tunes each, plus the renderings a pinch, a resize, or the editor's typing
   leaves behind. But a painted tune's size is mostly its `TextLayoutResult`s (one per chord symbol, syllable, and
   annotation; a few kilobytes each, a `StaticLayout` on Android), and a long tune with words holds thousands of them,
   so a count of tunes alone would let 16 long ones keep tens of megabytes alive after their pages are gone. 4,000 is
   a few megabytes, and about eight ordinary tunes of a dozen systems with words. A rendering heavier than the whole
   bound is not cached (its page still shows it). The page holding a rendering keeps it alive anyway; the bound is
   only on what is kept after. A tune that failed is not cached (it stays text and is tried again on
   the next open, as now). Only touched from composition and from the effect after its `withContext` returns, so on
   the main thread; no lock.

   Provide it beside the renderer in `CampfireApp`:
   `LocalNotationCache provides remember { NotationCache() }` next to `LocalAbcRenderer provides koinInject<AbcRenderer>()`,
   with `internal val LocalNotationCache = staticCompositionLocalOf<NotationCache?> { null }` in `NotationCache.kt`
   (null where nothing provides one: the cache is then skipped, as the renderer's null leaves ABC text).

3. **`rememberSongNotations` reads it.**
   - Initial state from the cache, so a hit is drawn on the first frame:
     `var rendered by remember(renderer) { mutableStateOf(request?.let { cache?.renderedFor(it) } ?: RenderedNotations.NONE) }`
     (the hit's `rerenderedTunes` empty: it is the page arriving, not a change; its `request` is the request, so the
     effect returns at once). `request` is then computed before `rendered`, which today is declared first.
   - In the effect, take each tune from the cache before rendering it, and store each one rendered. Where every tune of
     the request hits, skip the `isOnlyResized` delay too: nothing has to be engraved, and stepping back to a text size or
     width seen a moment ago (a pinch reversed) is then instant. The `isRerendering` / `rerenderedTunes` rule is
     unchanged, so a cached rendering replacing one the reader changed still cross-fades.

4. **A tune's first rendering is never narrated.** When a tune goes from its text to its first rendering, the relayout
   it causes places every section, card, divider, and row at once; what the reader causes (a transposition's
   re-rendering and its cross-fade, a text size, a resize, a fold) keeps its motion.
   - **Which tunes arrive** — a pure function in `SongNotations.kt`, generic so plan 08's `AbcTune` keys need no change
     to it:

     ```kotlin
     /**
      * The tunes of a new rendering that arrive as data: rendered now and not before, and not replacing a staff the reader
      * changed or a stand-in (plan 07), which are [rerendered]. Their relayout places the song at once rather than moving it.
      */
     internal fun <T> arrivingTunesOf(previous: Set<T>, rendered: Set<T>, rerendered: Set<T>): Set<T> = rendered - previous - rerendered
     ```

     `RenderedNotations` gains `val arrivingTunes: Set<String>` (`Set<AbcTune>` once plan 08 lands; empty in `NONE`); the
     effect computes the re-rendered tunes once into a `val rerenderedTunes` (today's expression, inline in the
     constructor call) and hands it to both fields, `rerenderedTunes = rerenderedTunes` and
     `arrivingTunes = arrivingTunesOf(previous = rendered.renderings.keys, rendered = renderings.keys, rerendered = rerenderedTunes)`,
     so plan 07, which widens that expression, widens what is not arriving with it. A cache hit taken as the initial state has every tune arriving (harmless: nothing has a place to move from on
     the page's first frame). After plan 07 an edited tune drawn as its stand-in is in `rerenderedTunes`, so it
     cross-fades and the song glides as for any edit; a tune with no stand-in (a block just added) arrives.
   - **The token.** `SongNotations` exposes
     `val arrival: Any? get() = rendered.takeIf { it.arrivingTunes.isNotEmpty() && it.rerenderedTunes.isEmpty() }`:
     the `RenderedNotations` instance itself, so a `SongNotations` rebuilt for the same rendering (a pinch changes only
     `unitSize`) is the same arrival, never a second one. A rendering that re-renders any tune is the reader's change
     (a transposition, a text size, a width, after plan 07 an edit drawn over its stand-in) and is narrated whole, a
     tune arriving in it included (one that failed before and renders now, a block added beside an edited one): data
     alone is placed, never a change the reader made in the same rendering.
   - **`SectionGlides.follow` places at once for one pass.** It takes `arrival: Any?` and remembers the last it saw:

     ```kotlin
     private var lastArrival: Any? = null

     fun follow(keys: List<Any>, grid: DecidedGrid, positions: Array<IntOffset>, glidesEveryMove: Boolean, arrival: Any?) {
         // A tune's first rendering is data arriving: the pass that lays it out sends every section straight to its new
         // place, the grid's jump included, and only that pass.
         val isArrival = arrival != null && arrival !== lastArrival
         lastArrival = arrival
         ...
             if (!isArrival && (glidesEveryMove || isNewGrid) && previous != null && previous != positions[index]) {
     ```

     It still records every `target` and `lastGrid`, so the next change glides from the arrived places. A glide already
     running for another reason keeps running relative to its new target. Consumed in the lookahead pass that first sees
     it, which is the one that measures the new heights: `SongLyrics` renews `sectionSizesPool` on `notations`, and a
     notation slot answers its height for any width at once, so the relayout is that one pass. `SongSectionsLayout`
     gains an `arrival: Any?` parameter passed through to `follow` (`SongLyrics` passes `notations.arrival`); a token
     rather than a flag, because a flag would stay set until `SongLyrics` next recomposes and silence a later
     measure-only move.

     What else the arrival pass could carry, checked: a fold, a transposition, or a text size step composes and
     lays out in the frame it is made in, and the rendering it may ask for arrives in a later pass, so its glides are
     already running and keep running relative to their new targets. Only a change whose composition lands in the very
     frame the effect writes `rendered` shares the pass, and is placed with it: one frame's chance, accepted. During a
     transition that hides the rail the grid is decided at the width at rest, so `isNewGrid` there is the arrival's own
     regrid; while a window edge is dragged, the first rendering of a tune opened a moment before can share a pass with
     one grid change of the drag, which then lands in place once. The editor's preview (`SectionMotion.NONE`) has no
     `SectionGlides` and already neither animates rows nor fades chunks in; a setlist neighbour arriving off screen
     while the reader swipes moves only its own page's sections, which the pager carries as a whole.
   - **Rows and fade-ins, for the composition the arrival lands in.** In `SongLyrics`:

     ```kotlin
     // The composition a tune's first rendering lands in lays the song out anew without narrating it (see SectionGlides).
     val notationArrival = notations.arrival
     val seenArrival = remember { SeenArrival() }
     val isNotationArriving = notationArrival != null && notationArrival !== seenArrival.value
     SideEffect { seenArrival.value = notationArrival }
     ```

     (`private class SeenArrival(var value: Any? = null)`, not a state, like `LastShownTuning`) and hand
     `SongSectionChildren` `placesAtOnce = isResizing || isNotationArriving`, renaming its `isResizing` parameter to
     `placesAtOnce` with a KDoc line (what is placed rather than moved: a window being resized, a tune's first rendering
     arriving). It already turns `animatesRows` off on it (the controls, diagrams, and chips are placed, their
     `PlacementMotion` cleared) and `isFadingIn` (no chunk fades in with it; the run keeps the text's slots, so none
     should be new, but nothing narrates the arrival if one is). The next composition of `SongLyrics`, which every
     change the reader makes causes (folds, transposition, text size, width), turns it back off. `glidesEveryMove`
     stays as it is: the token covers the layout.

5. **Rendering starts when the song is opened: no separate prefetch path.** The page's own `rememberSongNotations`
   already starts as early as anything could:
   - Every way a song screen is pushed goes through `Navigator.addOnBackStackChanged` → `SongTextStore.readAhead` in
     the click, and the details screen composes its first page (in place, `buildsModelInPlace`, where the text was read
     before) in the next frame, which is the transition's first: Navigation 3 composes the entering entry before it
     moves it. With step 1 the request exists in that very composition (the width comes from the constraints, the unit
     from the text size, the chords and title from the model), so the effect launches at the end of the transition's
     first frame.
   - A prefetch from the push would start at most that one frame earlier, and only by building the request a second
     time outside the page: the transposition and spelling (setlist and device overrides, preferences), the title, the
     unit from the text size, the page's width at rest from the window less the chrome, insets, and the page's padding,
     and the `NotationPainting` (density, font resolver, the theme's text style). Any drift between the two derivations
     misses the cache silently, so it would cost a key kept twice to save one frame. Not done.
   - The pager's neighbours (`beyondViewportPageCount = 1`) are composed with the same width and key as when they are
     swiped to, so a setlist's next song is already rendered at the key it is read at.

   **How often a cold open still misses its first frame: always, once per tune and key.** The first open in a process
   of a tune at a given width, text size, transposition, and title (and any open after the cache let it go) draws the
   text on its first frame; nothing launched at the click can beat a frame. With the engine warm (step 7), the staff
   arrives after ~5 ms of engraving plus the parse and plan 05's painting, off the main thread: typically two or three
   frames into the transition on the desktop, a few more on a phone. Without the warm-up the first tune of the process
   adds the engine's start (~150 ms on the desktop, more on a phone), which can land anywhere in the transition. A
   song whose text was never read shows its loading state first, and the tune arrives after its text the same way.
   With step 4 that late arrival is one step, early, while the page is still coming in: data arriving, as the rules
   allow, never a spring.

6. **Docs.** `ui/notation/CLAUDE.md`: renderings are kept app-wide (`NotationCache`, 16 tunes or 4,000 laid-out
   words, least recently used out), keyed by everything they depend on, so a tune seen before at the same width, size,
   and transposition is notation on its first frame; a cold one is text until it arrives, and its arrival
   (`arrivingTunesOf`, `SongNotations.arrival`) moves nothing gradually; the engine is warmed as step 7 decides
   (`NotationEngineWarmUp.kt`). `ui/songView/CLAUDE.md`: `SongLyrics` is handed its width at rest
   (`availableWidth`), which the tunes are engraved for; in **Motion**, a tune's first rendering is data arriving: the
   pass that lays it out places every section, card, divider, and row at once (`SectionGlides.follow`'s `arrival`).
   `abc/implementation/CLAUDE.md`: the engine is "started by the first render" or by the app's warm-up, whichever
   comes first.

7. **Warm the engine in the background (a decision, below).** Under option A: new `ui/notation/NotationEngineWarmUp.kt`:

   ```kotlin
   /**
    * Starts the notation engine once the library first holds a song with an `abc` block, away from any song being opened,
    * so that a tune's first open pays its own rendering (~5 ms on the desktop) rather than the engine's start as well.
    */
   @Composable
   internal fun WarmNotationEngine(songs: StateFlow<List<Song>>) {
       val renderer = LocalAbcRenderer.current ?: return
       LaunchedEffect(renderer, songs) {
           songs.first { library -> library.any { it.hasSheetMusic } }
           // Past the launch's first frames and the list appearing, which the engine's start would otherwise compete with.
           delay(WARM_UP_DELAY_MILLIS)
           renderer.render(tune = WARM_UP_TUNE, width = WARM_UP_WIDTH, transposition = 0)
       }
   }
   ```

   with `WARM_UP_TUNE` a two-bar tune with a chord symbol and a line of words (`"X:1\nK:C\n\"C\"C2 E2 | G4 |]\nw:la la la"`),
   `WARM_UP_WIDTH` 300, and `WARM_UP_DELAY_MILLIS` 2,000, all private, the result dropped: rendering a real tune is the warm-up, so `AbcRenderer`
   (an `explicitApi()` interface) gains nothing. Called once in `CampfireApp`, inside the `CompositionLocalProvider` that
   provides `LocalAbcRenderer`, as `WarmNotationEngine(songs = viewModel.libraryState.allSongs)`: the flow is handed
   over, not collected there, so a library change recomposes nothing. `AbcRendererImpl` is a Koin `@Single`, so this
   starts the very engine the pages and the PDF use.
   - **Threads, and why the wait is the same everywhere.** On Android and the desktop `QuickJsAbcScriptEngine.render`
     moves to its own thread (`withContext(dispatcher)`) before `QuickJs.create` loads the native library and abc2svg
     is read; on iOS `JavaScriptCoreAbcScriptEngine` does the same on its own thread. So the main thread never waits,
     but the start still takes a core for ~150 ms (more on a phone), and the library is read during the cold start,
     its first content frame composed right after: started then, the engine would compete with the startup the
     Baseline Profile is tuned for. The fixed wait keeps it out of that on every platform with no expect/actual; a
     tune opened within it starts the engine from its page, as today, and the warm-up then finds it running.
   - **The web** runs the engine on the page's one thread (`WebAbcScriptEngine` evaluates abc2svg, ~330 KB of script,
     in one synchronous step that cannot be split). Untouched, that pause lands inside the first tune's enter
     transition, the worst moment for it. Recommended: warm there too, after the same wait. The pause still happens
     once, for as long as the engine's start takes (not measured on the web; on the order of the desktop's ~150 ms or
     more, several frames), wherever the reader is then, most likely reading the list; a scroll or a tap at that moment
     stutters once. An idle callback (`requestIdleCallback`) would not keep it out of motion either: idle periods fall
     between the frames of an animation, and a synchronous start overruns its deadline.
   - **Option A's data: `Song.hasSheetMusic`.** The library scan already walks every line of every file in
     `ChordProParser.summarize`, where each line carries its `environment`. Add `val hasSheetMusic: Boolean = false` to
     `ChordProSummary` (KDoc: whether a line stands in an `abc` environment, the block the app draws as staff notation),
     set in `scan` by `if (environment == ABC) hasSheetMusic = true` before the early `return@forEach` that skips lines
     once chords are found (`ChordProEnvironments.delegateEnvironments` already names `"abc"`; reuse its spelling).
     `ChordProSummaryCache` needs nothing: an edit it keeps the old summary for stays inside a lyric line, which cannot
     open an environment. Add `val hasSheetMusic: Boolean = false` to `Song` (`data/model`, KDoc as for `hasChords`),
     set by `SongMappers.toSong` from `summary.hasSheetMusic`. The default keeps every other construction (the import
     preview's, the test fakes) as it is; nothing but the warm-up reads it. Under option B, none of this: the effect
     waits for the library to be read (`songs.first { it.isNotEmpty() }` is not enough for an empty library; wait on
     `libraryState.isLoading` turning false instead) and warms unconditionally.

## Decision: warming the engine

Measured on the desktop: ~150 ms to start the engine (more on a phone, plus loading a native library on Android and the
desktop), ~5 ms a render once warm, ~2.4 MB for the engine and abc2svg.

- **A. Warm when the library holds a tune (recommended).** The first open of a tune pays only its rendering. Cost:
  one Boolean from the scan to `Song` (~15 lines over `ChordProSummary`, `ChordProParser`, `Song`, and `SongMappers`,
  with tests) and the warm-up composable with its fixed wait. Every launch of a library with a tune starts the engine,
  which includes every library still holding the demo's "Home on the Range"; a library without one never does.
- **B. Warm at every launch, once the library is read.** No data change; every launch pays the engine's start and
  memory, tune or none.
- **C. No warm-up** (this plan's first recommendation). Nothing at launch; the first tune of each process adds the
  engine's start to its first open, inside the enter transition (on the web, a frozen transition).

Recommended: **A**, every platform after the same 2 s wait, the web included (sub-decision: or the web left cold, by
an `internal expect val warmsNotationEngine: Boolean` beside `Platform.kt`'s others, false only in `wasmJsMain`,
which the composable returns on; nothing else changes). A is the smallest way to know the library holds a tune: the
song texts are not held (they are read on demand), so the scan that already walks every line is the one place that
sees an `abc` environment, and `Song` is not serialized anywhere (no cache, sync index, or export holds it; the
mapper builds it on every scan), so the field touches no stored format. Step 4 already keeps a late
arrival from moving the song gradually, so the warm-up decides only how late the staff replaces the text on a first
open, not whether the page springs.

## Tests

- `NotationCacheTest` (commonTest): `` `a request is rendered from the cache only when every tune of it is cached` ``,
  `` `the tune used least recently leaves first` `` (fill past the bound, read one early entry, add one more, assert
  the other early entry is gone and the read one stays), `` `a tune under another transposition is another entry` ``,
  `` `renderings holding more words than the bound push the oldest out` `` (renderings of systems with many
  `NotationTextPaint`s; build them by painting one small parsed fixture, or make the weight a pure function of the
  text counts and test that, so no fake paint is needed).
  `notationPageWidth`: `` `a page wider than a column engraves for the column` ``, `` `no width engraves nothing` ``.
- `NotationArrivalTest` (commonTest, `arrivingTunesOf`): `` `a song's first rendering arrives whole` `` (previous
  empty), `` `a transposition re-renders and nothing arrives` `` (every tune in `rerendered`),
  `` `a tune added beside rendered ones is the only one arriving` ``, `` `a tune replacing its stand-in does not arrive` ``;
  and on `SongNotations.arrival`, `` `a rendering that re-renders a tune is no arrival` `` (one tune arriving beside
  one re-rendered: `arrival` null).
- `SectionGlidesTest` (commonTest, `runTest`, the glides on its `backgroundScope`, so no animation runs and
  `offsetOf` reads the jump `start` adds at once): one key followed at y 0, then at y 100 with a new `arrival` token,
  `` `a tune's first rendering places the sections at once` `` (offset zero); followed again at y 200 with the same
  token, `` `the move after an arrival glides again` `` (offset −100); `` `a move with no arrival glides` ``. Build the
  `DecidedGrid` as `SectionMeasurements` does (`DecidedGrid(emptyGrid(), isInset = false)`), the same one each time.
- `ChordProParserTest`: `` `a song with an abc block is summarized as holding sheet music` ``, and a song with a
  `{start_of_tab}` block and chords is not. `SongMappersTest`: the song carries it. (Option A only.)
- UI test `SheetMusicReopenTest` in `tools/screenshots` (package `...presentation.ui.screens.songDetails`,
  `TestWindow.PHONE`, so one column and no rail), with a new test song `campfire_tests-a_tune.cho` holding a `{key}`,
  a verse of lyrics, one `{start_of_abc}` tune of two or three systems, and a second verse after it. Find the tune's
  node by its content description (the tune's text at this point; plan 09 changes that description, so find it the
  way that plan leaves it if it lands first), and the lines above and under it by their text.
  - `` `a tune seen before is drawn as notation on the first frame its song is reopened` ``: with
    `mainClock.autoAdvance = false`, open the song, advance frame by frame to the first frame the node exists, and
    read its height (the cold open: its text); then let it go idle and read it again (the staff), and **assert the
    two differ**: that proves the tune was engraved at all, so a renderer that fails in the test environment (the
    desktop's QuickJS library, plan 02) fails this test rather than passing it with text on both sides. Go back to the
    list, open the song again, advance to the first frame the node exists, and assert its height is the staff's. The
    engine is the real one on the desktop, as the app's Koin graph gives it.
  - `` `a tune's first rendering moves the song under it in one step` ``: in a fresh app start (so the cache is
    empty), with `mainClock.autoAdvance = false`, open the song and from the first frame the tune's node exists
    record, every frame, the second verse's top less the first verse's top (which cancels the transition's own
    movement of the page). Advance one frame at a time (`mainClock.advanceTimeByFrame()`) until the tune's height has
    changed, bounded by a real-time deadline of a few seconds (the engine works on its own thread meanwhile; no
    sleep), then 60 frames more, past any spring. Assert the tune's height changed (as above), the first and last
    recorded values differ, and **every recorded value is one of those two**: a glide would leave values between them.
    With the engine warmed by step 7 the first frame is still the text (the effect starts after it), so the test does
    not depend on the decision.
- Run `./gradlew :presentation:desktopTest :chordpro:desktopTest :data:source:local:implementation:desktopTest :tools:screenshots:test`.
