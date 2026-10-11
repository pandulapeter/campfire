# Defer the fitted-text search while the layout follows a continuous change, and fit once it holds still

**Challenged:** amended — fitting is no longer deferred on `sectionMotion == GLIDE` (that deferred the one search an
app-bar, short-window title row or metronome panel burst makes when the viewport comes to rest, showing the song
unfitted for 200 ms and then fitting it again: two movements and an extra search on every such burst); it is deferred
only while what the grid key depends on keeps changing (a second `rememberContinuousChange` over `maxWidth`,
`settled.height` and the font scale in `SongDetailsPage`, handed to `SongLyrics` as `canFitText`), plus notes on the
two trackers flipping in different frames and on rapid stepper taps.

**Kind:** performance  ·  **Severity:** high (tablets and desktop), medium (phones)  ·  **Platforms:** all
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongSectionsLayout.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionMeasurements.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionMotion.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsPage.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionMeasurementsTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`

**DECISION for the user** (see Fix): recommended is to defer; the alternative is to leave it as it is.

## Problem

Every whole-percent step of a pinch changes `fontScale`, and with it `lyricsStyle`, so `SongLyrics.kt` builds a new pool
(`remember(fontScale, …) { SectionSizesPool<UnitContent>() }`), a new `SectionMeasurements`, and with it an empty grid
cache: `SectionMeasurements.grid(gridKey) { … }` searches again. It also rebuilds the fit scales:

```kotlin
val fitScales = remember(fontScale, canCutSections) { if (canCutSections) fitScalesFor(fontScale) else emptyList() }
```

`SongSectionsLayout.kt`'s `searchFitted` then searches the plain grid, and for a song stepped through on more than one
page

```kotlin
val fitScale = chooseFitScale(plain.pageCount, fitScales) { scale ->
    val isPairable = { section: Int -> isNarrowSection(section, halfColumnWidth, scale) }
    if (!couldSaveAPage(unwrappedSectionHeights, isPairable, unitCount, scale, plain.pageCount, pageCapacity)) {
        null
    } else {
        searchGrid(totalWidth, scale).also { fitted[scale] = it }.pageCount
    }
}
```

runs the whole `searchGrid` again at 0.975 and, where that does not save a page, at 0.95. Each fitted search reads
every unit's height at a width `1 / fitScale` wider (`measuredWidthOf`), which are new widths, so every unit is measured
again there. `couldSaveAPage` only rules out songs a whole page over, so most multi-page songs pass it, especially
on phones where pages are short. On a pinch frame that is two or three full layouts of the song instead of one, on top
of the inset and full-width plain searches the grid lambda may already run. The same happens on every frame of a window edge being dragged on
the desktop, where `settledWidth` changes each frame.

While those changes keep coming the layout is already in `SectionMotion.GLIDE` (`SongDetailsPage.rememberContinuousChange`
over `maxWidth, maxHeight, currentFontScale`), following them rather than narrating each.

## Fix

**DECISION.** This is a visible trade-off. Recommended: **defer**. While the layout follows a continuous change
(`GLIDE`) the grid is decided at the reader's own size (fit scale 1), and once the change holds still the grid is
searched again with fitting allowed; where a fit saves a page it lands then, as one animated change of the grid. During
a pinch the text is shown at exactly the size the stepper names, which is what the reader is adjusting anyway.
Alternative: **leave as is** (drop this plan): fitting keeps being decided on every pinch frame, at two to three times
the layout work.

If deferring:

1. `DecidedGrid` (`SectionMeasurements.kt`) gets `val isFitDeferred: Boolean = false`: whether this grid was decided
   with fitting left out though the song could have been fitted. `hasSameCellsAs` ignores it (it says nothing about
   where the cells are).

2. `SectionMeasurements.grid` takes whether fitting is allowed, and searches again under the **same key** once it is,
   if the last grid was decided without it:

   ```kotlin
   fun grid(key: SectionGridKey, canFit: Boolean, search: (canFit: Boolean) -> DecidedGrid): DecidedGrid {
       if (key != lastGridKey || (canFit && lastGrid.isFitDeferred)) {
           sizes.forEach { if (it.heightsByWidth.size > MAX_SECTION_WIDTHS) it.heightsByWidth.clear() }
           lastGrid = search(canFit)
           lastGridKey = key
       }
       return lastGrid
   }
   ```

   A grid decided with fitting allowed is never searched again because fitting became disallowed; do **not** simply add
   the fit scales or the motion to `SectionGridKey`, since `GLIDE` also covers the app bar collapsing and a window edge
   being dragged, and the key would then flip (and search) at the start and the end of every such burst.

3. `SongSectionsLayout` gets a parameter `canFitText: Boolean`. `searchFitted(totalWidth)` becomes
   `searchFitted(totalWidth, canFit)`: after its existing early return (no fit scales, read without stepping, one page,
   unbounded rows) it returns `FittedLayout(plain, 1f, isFitDeferred = true)` when `!canFit`, and otherwise does what it
   does today. `FittedLayout.decided` carries `isFitDeferred` into `DecidedGrid`. The `sectionMeasurements.grid(gridKey)`
   call passes `canFitText` and hands `canFit` on to each `searchFitted`. A song the fit scales cannot apply to (one page,
   the editor's preview) is never marked deferred, so nothing is searched twice for it.

4. **Do not derive `canFitText` from `sectionMotion`.** `GLIDE` also lasts 200 ms past the end of a burst of the
   *live* height (the app bar or the short window's title row collapsing, the metronome panel opening), and the grid
   key changes exactly once in such a burst: when the viewport comes to rest, `settled.height` takes the new height and
   `availableHeight` changes, while the page is still `GLIDE`. Deferring there would decide the plain grid, show the
   song unfitted for 200 ms and then fit it again, a second movement and a second search on every scroll that collapses
   the title row in a short window, where today there is one fitted search. Instead, defer only while what the grid key
   is made of keeps changing:

   - `SongLyrics` gets a parameter `canFitText: Boolean = true` (the editor's preview passes nothing; it has no fit
     scales anyway) and hands it to `SongSectionsLayout`.
   - `SongDetailsPage`, next to `isChangingContinuously`, takes
     `val isGridChangingContinuously = rememberContinuousChange(maxWidth, settled.height, currentFontScale)` (after
     `settled.height` has been updated) and passes `canFitText = !isGridChangingContinuously`. A pinch, a side edge
     or a bottom edge of the window being dragged (the viewport is settled then, so `settled.height` follows every
     frame) are bursts of it and defer; an app-bar or panel burst changes `settled.height` once, which is a single,
     discrete change, so its one search fits as it does today. Comment why the two trackers differ.

   The two trackers each wait their own 200 ms, so at the end of a pinch `canFitText` and `SPRING` may arrive in
   different frames. Either order is safe: `isPending` (step 5) keeps `animateBounds` off until the re-search has run,
   and a re-search under `GLIDE` glides like any other grid change.

5. **The release must not jump.** At the end of a burst the page recomposes with `SPRING`, which puts
   `animateBounds` back on every unit in the same composition as the measure pass that now finds the fitted grid; a
   freshly attached `animateBounds` has no previous bounds to animate from, so the fitted grid would land in one frame,
   and `SectionGlides.follow` skips units `isCarriedByBounds`. Keep the units on the glide path for that one pass: add a
   small state holder, e.g.

   ```kotlin
   /** Whether the grid on screen still waits to be fitted, which only the layout finds out (see SectionMeasurements.grid). */
   internal class FitDeferral { var isPending by mutableStateOf(false) }
   ```

   remembered in `SongLyrics` and handed to `SongSectionsLayout`, which writes `fitDeferral.isPending = decidedGrid.isFitDeferred`
   after deciding the grid (write only when it changes; this is the same kind of layout-to-composition state as
   `SectionAnimation.isTooTallToAnimate`). In `SongLyrics`'s `movedBy`, treat `fitDeferral.isPending` like
   `sectionMotion != SectionMotion.SPRING` (no `animateBounds`). Then the flip composes without `animateBounds`, the
   measure pass re-searches with fitting, `SectionGlides.follow` sees the grid change and glides the jump, `isPending`
   turns false, and the next composition puts `animateBounds` back on the units that are not gliding (the existing
   `isGliding` check already holds off those that are).

6. KDoc: `SongLyrics`' and `SongSectionsLayout`'s `canFitText` say the text is not fitted while what the grid is decided for keeps changing (not the same as `GLIDE`, see step 4); `DecidedGrid`'s KDoc names
   `isFitDeferred`; `SongSectionsLayout`'s KDoc paragraph on fitted text says it is decided once the change holds still.

Everything else (the fit scales, `couldSaveAPage`, `chooseFitScale`, the drawing through a scaled layer, a stepper tap
or a shortcut, which are `SPRING` from the start) is unchanged.

## Tests

New `SectionMeasurementsTest.kt` (pure, `commonTest`), with a `SectionMeasurements(emptyList())`, a fixed
`SectionGridKey`, and a search lambda that counts its calls and returns a `DecidedGrid` (use `emptyGrid()` and
`isFitDeferred = !canFit` to model a song that could be fitted):

- `a grid decided with fitting deferred is searched again once fitting is allowed`: `grid(key, canFit = false)` then
  `grid(key, canFit = true)`: two searches, the second one handed `canFit = true`.
- `a grid decided with fitting is not searched again`: `grid(key, true)` then `grid(key, false)` then `grid(key, true)`:
  one search.
- `a deferred grid is not searched again while fitting stays deferred`: `grid(key, false)` twice: one search.
- `a grid that could not have been fitted is not searched again`: the lambda returns `isFitDeferred = false` with
  `canFit = false`, then `grid(key, true)`: one search.

## Manual check

On a phone held upright open a song that is a few lines over two pages (so that it is fitted to two at the default
size; check the page count with the stepper). Pinch slowly: the text follows the fingers at the exact size, and a
moment after the fingers lift the text may shrink by up to 5% and lose a page, with the sections gliding or springing
there rather than jumping. Pinching should feel smoother than on master on a slow phone. On a tablet or a desktop
window, drag the window edge continuously: the same, with the fit landing once the drag stops. Tap the stepper's plus
and minus: the fit is applied in the same animated step as today, with no second movement after it (two taps within
200 ms are a burst, and their fit lands once they stop, which is expected). In a short window (a phone on its side),
open a song that is fitted, scroll so the title row collapses and expands: the song must stay fitted throughout, with
no moment at the unfitted size and no second movement after the bar comes to rest; the same for the metronome panel
opening and closing.

**CLAUDE.md:** in `screens/songDetails/CLAUDE.md`, in the paragraph beginning "**A song a few lines too long for its
pages is set a little smaller**", after "a change of it is a grid change that glides like any other", add that while
what the grid is decided for keeps changing (a pinch, a window edge being dragged; not the app bar or the metronome
panel, which change the grid once, when they come to rest) the song is laid out at the reader's own size and fitted
once the change holds still (`DecidedGrid.isFitDeferred`), since fitting costs two
or three more layouts of the song on every frame.
