<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
# Keep a few built song models across opens, so a song opened before takes its model from memory on its push frame instead of building it in composition

**Challenged:** amended — `SongPages` reads the cache from the `songTextStore` it already has (no new `SongPages` parameter), and `SongDetailsPage` gains exactly one parameter, a small holder plan 05 extends instead of adding its own; both functions are past 120 lines and may not grow, so each added line is paid for; `tabReadingOf` is also called by `ExportController` (on Main), which the memo must allow; measured with plan 01's one-app run and `push:fresh`.

**Kind:** performance  ·  **Severity:** high  ·  **Platforms:** all
**Files:** (`ui/` = `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`)
`ui/songView/model/SongLyricsModelCache.kt` (new),
`ui/songView/RememberSongLyricsModel.kt`,
`ui/screens/songDetails/SongDetailsPage.kt`,
`ui/screens/songDetails/SongPages.kt` (only to pass the cache through),
`ui/state/songText/SongTextStore.kt` (holds the cache as a member, not a constructor parameter),
`ui/tabs/TabReading.kt`, `ui/tabs/TabPlayers.kt` (value equality for `SongLyricsInputs.tabs`),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongLyricsModelCacheTest.kt` (new),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/tabs/TabPlayersTest.kt`,
`ui/songView/model/CLAUDE.md` ("Building the model, and the budget"), `ui/screens/songDetails/CLAUDE.md` (the read-ahead line), `ui/state/songText/CLAUDE.md`

## Problem

Every push to song details builds the current page's model in composition, which runs inside the pager's measure on the
push frame. `ui/songView/RememberSongLyricsModel.kt`:

```kotlin
val state = remember { mutableStateOf(if (buildsInPlace) inputs to prepare(inputs, false) else null) }
```

`prepare` is `SongDetailsPage`'s lambda, which parses the text (`songRenderer.songReadingOf`, `shownSongOf`) and calls
`prepareSongLyrics` (`ui/songView/model/SongLyricsModel.kt`), whose scale walk runs in the in-place build too:

```kotlin
val analysis = if (shouldShowChords && !isSongbook && (suggestsScales || collected.isNotEmpty())) songScaleAnalysisOf(standardSong, labels) else null
```

The measurement (desktop, warm JIT) put `rememberSongLyricsModel` at 11.6 ms self time on Proud Mary's push frame, and
the scale walk (`songScaleAnalysisOf` / `SongScaleSuggestions.of` / `SongScaleGroups.fitOf` / `looseFitOf`) at about 20%
of that frame's layout samples. Nothing of it is kept: the page and its `remember` leave with the screen, so reopening the
same song a moment later, with the same text and settings, builds the same model again on the frame the user waits for.

`SongLyricsInputs` (`SongLyricsModel.kt`) is a `data class` naming everything the model is built from, and `prepare`
reads nothing else (`SongRenderer` is stateless; `notationChordsOf` in `SongDetailsPage.kt` reads only the inputs). It is
almost a usable cache key, except for one field: `val tabs: TabReading? = null`, where
`ui/tabs/TabReading.kt` declares

```kotlin
internal class TabReading(val player: TabPlayer, val labels: TabNoticeLabels, val selects: Boolean)
```

a plain class compared by identity, and `rememberTabReading` makes a new one in every new composition of `SongPages`
(`remember(instrument, tuning, notation, labels, isMoved) { tabReadingOf(...) }`). `TabPlayer`
(`:libs:chordpro`, `TabPlayer.kt`) holds lambdas (`assumedTuning`, `stringName`), so it cannot be a data class. As things
stand, inputs built on two opens are never equal.

## Fix

**1. An LRU of built models keyed by the inputs.** New `ui/songView/model/SongLyricsModelCache.kt`:

```kotlin
/** The models song pages built lately, by what they were built of, so that a song opened again takes its model ... */
internal class SongLyricsModelCache(private val capacity: Int = MAX_KEPT_MODELS) {
    private val models = LinkedHashMap<SongLyricsInputs, SongLyricsModel>(capacity, 0.75f, true) // access order
    operator fun get(inputs: SongLyricsInputs): SongLyricsModel?
    fun keep(inputs: SongLyricsInputs, model: SongLyricsModel)  // evicts the eldest past capacity
}
```

- Read and written only on the main thread (composition and the `LaunchedEffect` after `withContext` returns), so it
  needs no lock; say so in its KDoc. The web is single-threaded anyway.
- **Memory bound:** `MAX_KEPT_MODELS = 8` (a setlist's current page and two neighbours, plus a few reopened songs). Never
  keep a model whose `isCut` is true (a songbook past `LayoutBudget`), which is the only kind large enough to matter. A
  model holds the parsed song and its sections, roughly a few times the text; eight ordinary songs are well under a
  few MB.
- `keep` replaces a model kept for the same inputs, so the model with searched shapes (see 2) replaces the in-place one.
- **Invalidation is the key itself:** text (an edit, a sync run), transposition and capo/tempo overrides, spelling and
  notation, lyrics-only mode, section labels (language, numbering), shape names, inferred keys, the instrument and tuning
  (`chordTuning`, `tabs`), scale suggestions and degrees, picture sizes. A changed preference makes a different key; the
  old entry simply ages out. Nothing is ever served for inputs it was not built of. Write this list in the KDoc so a
  future field added to `SongLyricsInputs` is known to join the key automatically, and a value read by `prepare` from
  anywhere but the inputs is known to break the cache.

**2. Use it in `rememberSongLyricsModel`.** Add a `cache: SongLyricsModelCache?` parameter (null keeps today's
behaviour, for any caller that should not share):

- First frame: `remember { mutableStateOf(if (buildsInPlace) inputs to (cache?.get(inputs) ?: prepare(inputs, false)) else null) }`,
  and the same `cache?.get(inputs) ?:` before the `inPlace.built = inputs to prepare(inputs, false)` branch.
  **Only where `buildsInPlace`.** A page that builds in the background (the setlist's next page) must keep getting its
  model through the `LaunchedEffect`, even on a hit: handing it a model on the first frame would lay out a second whole
  song on the push frame, the opposite of the goal.
- In the `LaunchedEffect(inputs)`: `cache?.get(inputs)?.takeUnless { it.hasPendingShapes } ?: withContext(Dispatchers.Default) { latestPrepare(inputs, true) }`
  for the first branch, and after each branch writes `state.value`, `cache?.keep(inputs, model)`. Also keep the in-place
  model as soon as it is built, so a reopen within the shape search still hits.
- A hit whose model already has its shapes skips the `withSearchedShapes` pass entirely (its `hasPendingShapes` is
  false), which also removes the mid-slide model swap plan 05 is about, for every song opened before.

**3. Hold the cache app-wide.** `CampfireViewModel.kt` is past the 500-line limit and may not grow, and `SongTextStore`'s
constructor is at 12 parameters, so add it as a member of `SongTextStore`:
`val lyricsModels = SongLyricsModelCache()` — the store already owns "what the back stack's songs read as" and is handed to
`SongPages`, which passes `songTextStore.lyricsModels` to `SongDetailsPage` (new parameter `lyricsModels`), which passes
it to `rememberSongLyricsModel`. `SongPages` already takes `songTextStore`, so it reads
`songTextStore.lyricsModels.takeIf { importPreview == null }` itself and gains no parameter: an import preview's pages
pass `null` (their texts are not the library's and would only push library songs out).

**Size limits.** `SongDetailsPage` (272 lines) and `SongPages` (213) are past the 120-line function limit and may not
grow (`check_sizes.py` judges against the base). `SongDetailsPage` gains exactly **one** parameter, and plan 05 must
not add a second: make it a small holder in `ui/songView/` beside `rememberSongLyricsModel`, e.g.
`internal class SongModelBuild(val cache: SongLyricsModelCache?)` remembered per `SongPages`, which plan 05 extends with
its quiet signal. Pay for the added lines in both functions by lines moved out (a helper, or a call folded onto one
line). `pruneSongTexts` does not touch it: dropping a text is about not re-reading files, while the
cache is what makes a reopen cheap, and a stale entry can never be served (the text is part of the key).

**4. Give `TabReading` value equality**, or no key ever matches across opens:

- Option A (recommended): memoize `tabPlayerOf` (`ui/tabs/TabPlayers.kt`) in a small map keyed by
  `(instrument, tuning, notation, isMoved)` (it is called from composition only; bound it to a handful of entries), so the
  same `TabPlayer` instance comes back for the same reader, and make `TabReading` a `data class` (its `labels` are a data
  class of strings). Equality is then identity of the player plus value of the rest. Its callers are `rememberTabReading`
  (`SongPages`, the editor's `SongPreview`) and `ExportController.preparePrintSource` through `tabReadingOf`, which runs
  on the main dispatcher too (only the work after it moves to `Dispatchers.Default`), so a main-thread memo holds; say
  in its KDoc that it is main-thread only. The domain is finite (instruments × tunings × notations × `isMoved`), so a
  bound of a handful of entries only drops readers no longer in use.
- Option B: keep `TabReading` a class and override `equals`/`hashCode` from a data key it is built from; the tests that
  construct it directly (`TabPlayersTest`, `DemoSongTabsTest`) then need that key too. More churn for the same result.

**5. Building before the push frame (deferred, not part of this change).** `SongTextStore.readAhead` runs inside the
click, but the read it starts is asynchronous, and the inputs need everything `SongPages` works out per page (playing
overrides, shape names, inferred keys, labels from string resources, picture sizes). A prebuild would mean extracting
that into a pure `pageLyricsInputsOf(...)` shared by `SongPages` and the store, and the build (≈12 ms+ on Default) would
still mostly lose the race to a frame 8–16 ms after the click. Leave it until plan 01's numbers show the first open of a
song, rather than a reopen, is what still drops frames.

**6. The scale walk stays in the in-place model.** `ui/songView/model/CLAUDE.md` keeps it there so degrees are right on
the first frame; with the cache a reopen skips it anyway, so do not move it.

Update the docs: `ui/songView/model/CLAUDE.md` "Building the model" gains one bullet (models kept by their inputs, eight,
never a cut one; a page building in the background still gets its model after the first frame);
`ui/screens/songDetails/CLAUDE.md`'s read-ahead line, and `ui/state/songText/CLAUDE.md` one line naming `lyricsModels`.

## Tests

`SongLyricsModelCacheTest` (commonTest, beside `SongModelBuildTest`), building models with `prepareSongLyrics` as
`RenderSectionsTest` does:

- a model kept for inputs is returned for equal inputs (a copy of the data class, a new `String` of the same text), and
  null for inputs differing only in `transposition`, `spelling`, `shouldShowChords`, `labels`, or `chordTuning`;
- the ninth distinct inputs evict the least recently read entry, not the least recently kept one;
- a model with `isCut` is not kept;
- keeping again for the same inputs replaces the model.

`TabPlayersTest`: two `tabReadingOf(preferences, labels, isMoved)` calls for equal preferences are equal (and hash
equally); a different tuning or `isMoved` is not.

**Measure** (after plan 01): `./gradlew :tools:screenshots:benchmark --args="--suite transitions"`, `push:long` and
`push:abc`, whose warm passes are reopens in plan 01's one-app run: the push frame should lose the in-composition
build (≈12 ms self plus the scale walk's share on Proud Mary), so frame 1 drops by roughly a third to a half; `push:fresh` (a first open) is unchanged. Allocation on the reopen should fall well below today's 37 MB / 65 MB.
