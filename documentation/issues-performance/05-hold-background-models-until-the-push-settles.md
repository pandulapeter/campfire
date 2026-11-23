<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
# Hold every model built in the background until the screen's push and the pager have settled, so no song is laid out in the middle of a slide

**Challenged:** amended — quiet also requires `currentState == targetState` (`isRunning` is false until the animation's first tick, and a plan 03 cache hit applies without suspending, so it would land on frame 2); a result held while its page turns into one that builds in place is taken by that in-place branch instead of building the song again on the main thread; the signal is read in `SongPages` (no new parameters on the pager or screen) and rides on plan 03's one `SongDetailsPage` parameter, both functions being past 120 lines.

**Kind:** performance  ·  **Severity:** high (setlists), medium (every song with chord diagrams)  ·  **Platforms:** all
**Files:** (`ui/` = `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`)
`ui/songView/RememberSongLyricsModel.kt`,
`ui/screens/songDetails/SongDetailsPage.kt`,
`ui/screens/songDetails/SongPages.kt`,
`ui/screens/songDetails/RememberIsSongScreenQuiet.kt` (new: the signal, read in `SongPages`),
`ui/songView/model/SongLyricsModel.kt` (only if the quiet check is a pure function placed beside `buildsModelInPlace`),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongModelBuildTest.kt`,
`ui/songView/model/CLAUDE.md` ("Building the model"), `ui/screens/songDetails/CLAUDE.md`

## Problem

Two models land on the frames of the push slide, each laying a whole song out again on the main thread.

**The setlist's next page.** `SongPages` composes `beyondViewportPageCount = 1` pages on each side. The page after the
current one does not build in place (`buildsModelInPlace` in `ui/songView/model/SongLyricsModel.kt` is
`page == currentPage || page == targetPage || page == targetPage - 1 || isVisible`), so `rememberSongLyricsModel`
(`ui/songView/RememberSongLyricsModel.kt`) builds it on Default and writes it whenever that finishes:

```kotlin
LaunchedEffect(inputs) {
    val built = state.value
    state.value = when {
        built == null || built.first != inputs -> inputs to withContext(Dispatchers.Default) { latestPrepare(inputs, true) }
        built.second.hasPendingShapes -> inputs to withContext(Dispatchers.Default) { built.second.withSearchedShapes() }
        else -> return@LaunchedEffect
    }
}
```

A build of ≈10–40 ms started on the push frame finishes during the ≈300 ms slide, and that page then composes and lays
out its whole song (units, measurements, the column search) on a frame of the slide. The hold `SongPages` already has
only covers a pager swipe, not the navigation transition:

```kotlin
var hasShownLyrics by remember { mutableStateOf(text != null) }
val shownText = if (!hasShownLyrics && !isFollowingGesture && pagerState.isScrollInProgress) null else text
```

**The open page's searched shapes.** The page being opened builds in place without the shapes only the search finds,
and the same effect immediately runs `built.second.withSearchedShapes()` on Default and writes the result mid-slide. The
new model is a new instance, so `rememberShownSections` (`ui/songView/ShownSections.kt`) rebuilds the Chords cells
(`remember(model, chordDiagrams?.tuning, ...)`) and the section list, and `SongLyrics` recomputes `units`,
`unitContentsOf` (hashing every unit's section), `sectionMeasurements` (a new instance, so its `lastGridKey` is null and
the column search runs again), and the section and unit keys. Only the Chords section is measured afresh (the pool
reuses the rest by content), but the search and the rebuilds land on a slide frame; if the Chords section's height
changes, the sections spring as well.

## Fix

Let both builds run on Default as now, but **apply** their result only once the screen is quiet: its navigation
transition has ended and the pager is not moving. Building early keeps the work off the main thread; applying late keeps
the layout off the slide.

1. `rememberSongLyricsModel` gets `isQuiet: () -> Boolean` (a snapshot read) through plan 03's `SongModelBuild` holder
   (a second field beside `cache`; not a parameter of its own, see the size note below). In the effect, compute the new
   pair on Default as today (or take it from plan 03's cache), put it in a remembered, non-state `held` slot (and into
   plan 03's cache, where there is one), then `snapshotFlow { isQuiet() }.first { it }` before `state.value = ...`.

   **A held result is taken by the in-place branch.** A page whose result is held can turn into one that builds in
   place before the screen is quiet (a swipe started during or right after the push makes it the target or visible).
   Today that page's background result has usually landed by then; with the hold it has not, and the composition-time
   branch (`buildsInPlace && state.value == null`) would build the whole song again on the main thread, on a swipe frame,
   which is worse than what this plan removes. So the in-place branch takes `held` when its inputs are the current
   ones (`held?.takeIf { it.first == inputs } ?: inputs to prepare(inputs, false)`), and so does the first frame's
   `remember`. Clear `held` once it is applied. The wait is cancelled with
   the effect when the inputs change, like the build. The first in-place model (`remember { ... prepare(inputs, false) }`
   and the `inPlace.built` branch) is otherwise untouched (it only prefers `held`): lyrics on the first frame of a song
   opened before stay exactly as they are, and a page that becomes the target mid-swipe still has a model at once. Update the function's KDoc: "the
   background result is applied once the screen is quiet".

2. **The quiet signal.** In `SongPages`, which is composed inside the NavDisplay entry and already has `pagerState`,
   `isQuiet = { !transition.isRunning && transition.currentState == transition.targetState && !pagerState.isScrollInProgress }`,
   with
   `transition = LocalNavAnimatedContentScope.current.transition` (`androidx.navigation3.ui`, which
   `ReportNavigationTransition` in `ui/CampfireScreens.kt` already reads per entry and which recomposes on its change, so it
   is snapshot-observable: `isRunning` reads `startTimeNanos`, and `currentState`/`targetState` are state too). The
   second condition matters: `startTimeNanos` is set only on the animation's first frame, so on the push frame itself
   and in the effects it launches `isRunning` can still be false, and a plan 03 cache hit, which the effect has without
   suspending, would otherwise apply at once and lay the neighbour out on frame 2. Read the local once in `SongPages`
   (not its `.transition.isRunning`, which would recompose the pager) and hand the lambda, remembered, to the holder,
   so pages do not recompose for it.

   Options considered:
   - **Entry lifecycle RESUMED** (as `ui/chrome/ScreenSurface.kt` reads it). Rejected as the gate on its own: Nav3 holds
     an entry below RESUMED while transitioning, but the *host* is not always RESUMED in use (a desktop window without the
     focus is only STARTED, as `ScreenSurface`'s KDoc says), so a song opened in an unfocused window would never apply its
     background models. If used, it must be "host RESUMED and entry below RESUMED means moving", exactly as
     `ScreenSurface` compares them; the transition flag needs no such care.
   - **`Navigator`'s `isNavigationTransitionRunning`**: a plain `var`, not state, and app-wide; not observable. No.

   Recommended: the entry's `transition.isRunning`. Verify `LocalNavAnimatedContentScope` is provided wherever
   `SongDetailsScreen` is composed (every NavDisplay entry is; check `tools/screenshots`' offscreen driver composes it
   through `CampfireScreens` too). If some host lacks it, pass `{ true }` there rather than reading the local.

3. Optionally make the condition a pure function next to `buildsModelInPlace`
   (`appliesBackgroundModel(isTransitionRunning, isPagerScrolling) = !isTransitionRunning && !isPagerScrolling`) only if a
   test wants it; otherwise keep it as the lambda.

4. `SongPages`' first-lyrics hold stays as is: during a push the next page's text swap only shows a loading indicator
   (cheap; its model is now held by 1), and the hold still protects in-place builds of pages a settling swipe makes
   visible.

5. The editor's preview does not use `rememberSongLyricsModel` (`ui/screens/songEditor/SongPreview.kt` has its own
   debounced flow), so nothing there can wait. The import preview's pages are `SongPages` too and get the same signal; a
   preview's transition ends like any push.

**Size limits.** `SongDetailsScreen` (281 lines), `SongPages` (213), and `SongDetailsPage` (272) are past the 120-line
function limit and may not grow: nothing changes in the screen or the pager, `SongPages` builds the lambda in a small
`rememberIsSongScreenQuiet(pagerState)` in a file of its own under `ui/screens/songDetails/` (one line in `SongPages`,
paid for by a line moved out), and `SongDetailsPage` gains nothing beyond plan 03's holder. Update `ui/songView/model/CLAUDE.md` ("Built off the main thread
except …": add "and applied once the push and the pager have settled") and the read-ahead line of
`ui/screens/songDetails/CLAUDE.md`.

With plan 03 landed, a reopened song's in-place model is usually the searched one and the second problem disappears for
it; this plan still covers first opens and every neighbour page.

## Tests

The gating is a `LaunchedEffect` waiting on composition locals; no commonTest can drive a NavDisplay transition, and the
UI tests are the place for it. If the condition is extracted (step 3), add to `SongModelBuildTest`: a background model is
applied neither while the transition runs nor while the pager scrolls, and is applied once both are false. Otherwise
none; say so in the commit.

**Measure** (after plan 01): `./gradlew :tools:screenshots:benchmark --args="--suite transitions"`, `push:setlist` (a
song in the middle of a setlist, so the next page exists) and `push:fresh` (a song with chord diagrams on, never opened
before, so its shapes are searched): the long frames after frame 1 during the
slide (today the neighbour's and the shapes' layouts) should disappear; frame 1 is unchanged, and one longer frame may
appear right after the transition ends, which is allowed (nothing is moving).
