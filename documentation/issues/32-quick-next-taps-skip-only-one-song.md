# 32 · Tapping Next (or pressing the pedal / arrow key) twice quickly in a setlist moves on only one song

**Severity:** wrong behaviour (all platforms; likely for anyone skipping a song while playing — two presses made
before the page animation is halfway count as one; with a page turner pedal the second press is simply
lost) · **Area:** `:presentation` (`SongDetailsScreen.kt`: `SongDetailsScreen`, `SongPagerControls`)

**Verifier:** Line numbers corrected (`:201-210`, `:373-380`, `:396-409`), and the fix no longer relies on `targetPage` alone: it is only updated once the launched coroutine has entered `scroll { }` (and drops back to `currentPage` while a cancelled animation hands the mutex to the next one), so presses queued before that were still lost; the screen now remembers the page its own last press asked for until that request ends.

## Symptom
1. Open a setlist of five songs on its first song.
2. Tap the pager bar's **Next** button twice in quick succession (or press → twice on a keyboard, or double-press a
   Bluetooth page turner pedal).

The screen ends up on song 2, not song 3. Tapping three times quickly still usually lands on song 2 (or song 3,
depending on where the third tap falls). Previous behaves the same way. A single press per song works, which is why
this only shows up under quick presses — the way a musician skips a song they decided not to play.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`

Every way of paging computes its target from `currentPage`:

```kotlin
onPreviousSong = if (canPage && pagerState.currentPage > 0) {
    { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }
} else { null },
onNextSong = if (canPage && pagerState.currentPage < songs.lastIndex) {
    { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }
} else { null },
```
(`:201-210`), and the bar (the call at `:373-380`, the buttons in `SongPagerControls` at `:396-409`):

```kotlin
onPageSelected = { page -> coroutineScope.launch { pagerState.animateScrollToPage(page) } },
...
IconButton(enabled = currentPage > 0, onClick = { onPageSelected(currentPage - 1) }) { … }
IconButton(enabled = currentPage < pageCount - 1, onClick = { onPageSelected(currentPage + 1) }) { … }
```

`PagerState.currentPage` only changes once the animation has carried the pager past the halfway point of the page.
A second press before that asks `animateScrollToPage(currentPage + 1)` for the **same** page again, which cancels the
running animation and starts it over towards the same target. Compose already knows where the pager is going:
`PagerState.targetPage` returns `programmaticScrollTargetPage` while an `animateScrollToPage` is running (foundation
1.12.0, `PagerState.kt:424-444`), `currentPage` when nothing scrolls, and the settling page during a fling.

## Fix
Step from the page the last press asked for, as long as that request is still being carried out, and otherwise from
where the pager is going.

`PagerState.targetPage` alone is not enough: it only becomes the requested page once `animateScrollToPage` is
running inside `scroll { }` (`updateTargetPage` is its first statement, foundation 1.12.0 `PagerState.kt:1041`), and
the call is launched rather than run, so a second press handled before the coroutine starts (two queued key events,
a pedal's double press on a busy frame) still reads the old page. And when a second animation cancels the first,
`isScrollInProgress` is false for the moment between the one leaving the mutex and the other taking it, during
which `targetPage` is `currentPage` again. So the screen keeps the request itself:

1. `SongDetailsScreen.kt`, a small private class at the end of the file:

   ```kotlin
   /**
    * Previous and Next as steps from the page the last of them asked for, rather than from the page on screen:
    * PagerState.currentPage only moves once an animation is past halfway, and even PagerState.targetPage only once
    * the launched animation has started, so presses that come quicker than that would each ask for the same page.
    * A request is forgotten when its animation ends, however it ends (a swipe cancels it), and whatever the pager
    * then settles on is where the next press starts from.
    */
   private class PageStepper(
       private val pagerState: PagerState,
       private val coroutineScope: CoroutineScope,
   ) {
       private var request: Request? = null

       private class Request(val page: Int)

       fun step(delta: Int) {
           val from = request?.page ?: pagerState.targetPage
           val page = (from + delta).coerceIn(0, pagerState.pageCount - 1)
           if (page == from) return
           val request = Request(page).also { request = it }
           coroutineScope.launch {
               try {
                   pagerState.animateScrollToPage(page)
               } finally {
                   // Only the latest request is cleared: an earlier one ends when the next press cancels it.
                   if (this@PageStepper.request === request) this@PageStepper.request = null
               }
           }
       }
   }
   ```

   Held with `val pageStepper = remember(pagerState, coroutineScope) { PageStepper(pagerState, coroutineScope) }`
   next to `pagerState`. It is only touched from the UI thread (key and click handlers, and the coroutine on the
   composition's dispatcher), so a plain field is enough. A request object rather than the page number, so that
   two requests for the same page are still told apart.

2. `SongDetailsScreen`, `:201-210`:

   ```kotlin
   onPreviousSong = if (canPage && pagerState.targetPage > 0) { { pageStepper.step(-1) } } else null,
   onNextSong = if (canPage && pagerState.targetPage < songs.lastIndex) { { pageStepper.step(1) } } else null,
   ```

   (formatted like the surrounding code). `targetPage` in composition only decides whether the key does anything;
   the step itself is decided at the time of the press.

3. `SongPagerControls` gets `targetPage: Int` next to `currentPage` (the label keeps showing `currentPage`, the song
   on screen) and `onStep: (Int) -> Unit` in place of `onPageSelected`, which has no other caller:
   `enabled = targetPage > 0, onClick = { onStep(-1) }` / `enabled = targetPage < pageCount - 1, onClick = { onStep(1) }`.
   The call at `:373-380` passes `targetPage = pagerState.targetPage` and `onStep = pageStepper::step`. A press
   that races the button being disabled is ignored by `step` (`page == from`).

This is idempotent in the useful sense — N presses go N songs, whatever their timing — and adds no delay or
debounce. Do not debounce the buttons, and do not switch to `scrollToPage` (the animation is what tells the reader
the song changed).

## Tests
None (UI is untested).

## Verify
1. `./gradlew :app:desktop:run`, open a setlist of five songs on the first one: click Next twice quickly → song 3;
   three times → song 4; press → twice quickly → two songs on. Previous likewise backwards.
2. At the second to last song, click Next three times quickly: the pager stops at the last song, the button disables.
3. Swipe with a finger (Android) and tap Next while the swipe settles: the pager goes one past the page the swipe was
   settling on. Tap Next, and while it animates swipe back: the swipe wins, and the next Next starts from where the
   pager then is.
3a. Hold → on a keyboard: every auto-repeat is a press, so the pager runs towards the end of the setlist and stops
    there without error (the same as a held key already does to the scroll position; not filtered here).
4. The position label ("3 / 5") still changes when the new song is on screen, not at the tap.
5. Compile checks: `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.

## Docs
`presentation/CLAUDE.md`, the `screens/songDetails/SongKeyboardShortcuts.kt` bullet: after "Left and Right step to
the previous and the next song of the setlist" add "— counted from the song the pager is on its way to, so that two
quick presses go two songs, as do two quick taps on the pager bar's buttons".

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing.
