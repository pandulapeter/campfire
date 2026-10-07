# Build the first rendering of a song page off the main thread unless the page is on screen or headed for

**Kind:** performance (opening a song, paging)  ·  **Severity:** low  ·  **Platforms:** all
**Lane:** U  ·  **Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongModelBuildTest.kt` (new),
`presentation/CLAUDE.md`

**Challenged:** amended — the page before the target page now always builds in place (the pedal's step back from a song's top scrolls that page to its end, which needs it laid out); the background result for the same inputs replaces an in-place fallback model instead of being dropped (it carries the searched chord shapes, which nothing else would search); the `isVisible` fallback is stated as one frame late, like the existing held pages; the claims about swipes are narrowed to the model build, since the neighbour's composition and layout stay on the main thread.

## Problem

`rememberSongLyricsModel` (`SongLyrics.kt:2437-2452` at 491c4254a) builds the first model of every page on the main
thread, during composition:

```kotlin
val state = remember { mutableStateOf(inputs to prepare(inputs, false)) }
```

That build parses, transposes, notates and cuts the song into sections. It is done in place so that a page never
opens on an empty frame (earlier performance plan 07). The pager composes the current page and one on each side
(`beyondViewportPageCount = 1`, `SongDetailsScreen.kt:789`), and every one of them builds this way. That includes the
two the user cannot see.

- **Opening a song from a setlist.** When the texts are already in `songTexts`, the first composition of the
  screen builds three models in one frame: the song and both neighbours. When a neighbour's text arrives a little
  later, from `LaunchedEffect(pagerState.currentPage, songs)` (`SongDetailsScreen.kt:395-399`), its build lands on a
  frame of the navigation push animation.
- **Paging.** The swap from loading to lyrics of a page that is not followed is held until the pager has come to
  rest (`hasShownLyrics`, `SongDetailsScreen.kt:812-819`). But that gate starts as `text != null`. So a page that is
  composed with its text already loaded builds in place at once, in the middle of the swipe that composed it, because
  the page two steps ahead is composed when `currentPage` flips. That is every page of a setlist paged back through,
  or paged through a second time. Otherwise the build runs on the first frame after the pager rests, which delays the
  next input.

Measured on the desktop JVM (warm), full first builds take 109 µs, 236 µs and 715 µs (a 724-line song). That is
1–10 ms on a low-end device, more while the JIT is still cold right after launch, for a page nobody is looking at.


## Fix

Build in place only the page that is on screen, headed for, or the one a step back would land on. Build every other
page's first model on `Dispatchers.Default`, as every later model already is. What moves off the main thread is the
model build alone (parse, transpose, notate, sections): the page's composition and layout of its lines still happen on
the main thread, in the frame its model lands.

1. Add a pure helper in `SongLyrics.kt`, next to `rememberSongLyricsModel`:

   ```kotlin
   /**
    * Whether a page's first model is built in place, so that it never shows an empty frame: the page is on screen or
    * headed for, or it is the one before the target page, which a step back from the top of a song scrolls to its end
    * at the moment of the press - a page with no model yet has no end to scroll to.
    */
   internal fun buildsModelInPlace(page: Int, currentPage: Int, targetPage: Int, isVisible: Boolean) =
       page == currentPage || page == targetPage || page == targetPage - 1 || isVisible
   ```

   Why the page before the target: `stepBack` in `SongDetailsScreen.kt` (~line 375-385 at 491c4254a) runs
   `pageScrollStates[pagerState.targetPage - 1]?.let { state -> coroutineScope.launch { state.scrollTo(state.maxValue) } }`
   at the press. A page still waiting for its background model is a loading box with a `maxValue` of 0, so the pedal
   would land on the top of the previous song instead of its last lines, and the next press would skip that song
   whole — breaking pedal-only reading. Building that page in place keeps it laid out whenever the press can come.
   The cost: paging backward through a setlist keeps today's in-place build of the page two behind (it is
   `targetPage - 1`), so the saving on paging is for paging forward, and on opening a song from a setlist one
   neighbour (the next song) instead of two.

2. Change `rememberSongLyricsModel` to take `buildsInPlace: Boolean` and return `SongLyricsModel?`:
   - If `buildsInPlace` is true on the first composition, it builds in place exactly as now (without the searched
     shapes, which follow from `Dispatchers.Default` as today).
   - Otherwise it starts with no model, and its `LaunchedEffect(inputs)` builds the first one with
     `withContext(Dispatchers.Default) { latestPrepare(inputs, true) }` (whole, the searched shapes included).
   - If the page becomes one that builds in place before that background build has finished, it builds in place in
     that composition (`prepare(inputs, false)`), as today. Keep that model in a remembered holder (a plain field, not
     snapshot state, written during composition) and return it while the state is still empty.
   - When the background build for the same inputs then finishes, its result **replaces** the in-place model rather
     than being dropped: it is the same model with the searched shapes, which nothing else would search for the
     in-place one (the effect that searches pending shapes is the very `LaunchedEffect(inputs)` that is busy with the
     background build). The swap is the one every first build already makes today when its shapes follow.
   - A result for inputs that are no longer the current ones is dropped, as today (the restart of
     `LaunchedEffect(inputs)` cancels the wait).

   Keep the KDoc in step: in place for the page being read, headed for, or stepped back to; from
   `Dispatchers.Default` for the page beside it on the other side.
3. In the pager's page lambda (`SongDetailsScreen.kt` ~line 789-890), compute the answer as a `derivedStateOf`, so
   that the page recomposes only when the answer flips and not on every frame of a swipe, and pass it down through a
   new `buildsModelInPlace: Boolean` parameter of `SongDetailsPage` (`SongDetailsScreen.kt:1044-1120`) to
   `rememberSongLyricsModel`:

   ```kotlin
   val buildsInPlace by remember(pagerState, page) {
       derivedStateOf {
           buildsModelInPlace(page, pagerState.currentPage, pagerState.targetPage,
               isVisible = pagerState.layoutInfo.visiblePagesInfo.any { it.index == page })
       }
   }
   ```

   `isVisible` covers the first frames of a drag, before `targetPage` turns to the page sliding in at the edge (Compose
   only moves `targetPage` past its positional threshold). Note that it is **one frame late**: `layoutInfo` is written
   by the measure pass that first shows the page, so the composition that reacts to it is the next frame's. A page
   whose background build has not finished by then (it normally has: it started when the page was composed, at least
   a settle animation earlier) shows its sliver of empty loading box for that one frame. That is no worse than today's
   `hasShownLyrics` gate, which holds a page with no text back in exactly those frames.
4. While the model is null, show the page's existing loading content: the `Box` with `DelayedLoadingIndicator`, which
   shows nothing for its delay. Show it inside the `songText != null` branch, so the `AnimatedContent` keyed on
   `text != null` does not crossfade a second time. When the model arrives on an offscreen page it appears in one
   frame, unanimated. That is data arriving, which the no-incidental-animations rule says must not be animated, and
   nobody sees it. The `hasShownLyrics` gate stays as it is. It only decides when the text is handed down.

**Drop this plan if** the background build visibly races the user: on the slowest device available, a page that
comes into view showing the empty loading box for more than the one frame described in step 3, or a pedal press at
the top of a song landing anywhere but on the previous song's last lines. If it cannot be made reliable, the ≤10 ms
build of a neighbour is not worth a flash.

Update the `rememberSongLyricsModel` sentence in `presentation/CLAUDE.md` ("builds only the first rendering of a page
in place, so that it never opens on an empty frame"). It should say that only the page on screen, headed for or before
the target builds in place, and the next page builds its first rendering on `Dispatchers.Default` too.

## Tests

`SongModelBuildTest` (`presentation/src/commonTest/.../songDetails/`) covers `buildsModelInPlace`:

- the current page builds in place;
- the target page builds in place mid-swipe, while it differs from the current one;
- a visible neighbour builds in place;
- the page before the target builds in place, at rest and mid-swipe in either direction;
- the composed but invisible page after the current one (current + 1, not the target) does not;
- after a forward settle, the page two ahead (target + 1) does not.

Run `./gradlew :presentation:desktopTest`.

## Manual check

1. On a low-end Android phone (or the emulator with CPU throttled), open a long song from a setlist of long songs.
   The push animation is smoother than before, and the song is there on the first frame, as before.
2. Swipe quickly forward through the whole setlist, then back. No page ever shows a blank or loading frame as it
   slides in beyond the single edge frame described in step 3. (The swipe that composes the page two ahead still lays
   that page out on the main thread when its model lands; only the model build has left the frame.)
3. With a pedal or the arrow keys, page backward through a setlist of short songs (each fits one screen) as fast as
   the keys allow: every press lands on the previous song's last lines, never its top.
4. Turn on Read only and repeat. Change the transposition on one page and swipe away and back. The transposed model
   is still there. A song with chords whose shapes only the search finds shows its Chords section complete on every
   page, the next song's included.
