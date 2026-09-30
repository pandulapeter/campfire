# Land on the previous song's end when Up hands off backwards

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (a setlist read with the pedal)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`
(`stepBack`, `PageStepper`, the pager page block), `presentation/CLAUDE.md` where it describes the hand-off between the songs of a setlist

## Problem

Down at the end of a song hands off to the next song's top, which is right. Up at the top of a song hands off to the
previous song — at wherever that song's scroll happens to be, which for a song not read yet is its top:

```kotlin
fun stepBack() {
    val stepper = currentPageStepper
    if (stepper?.canStepBack == true) coroutineScope.launch { stepper.step(-1) } else if (pagerState.targetPage > 0) pageStepper.step(-1)
}
…
private class PageStepper(private val pagerState: PagerState, private val coroutineScope: CoroutineScope) {
    fun step(delta: Int) {
        val from = request?.page ?: pagerState.targetPage
        val page = (from + delta).coerceIn(0, pagerState.pageCount - 1)
        …
        pagerState.animateScrollToPage(page)
```

and every page's `rememberScrollState()` starts at 0. The live run opened the setlist at its last song and pressed
Up twice, 80 ms apart: the first Up landed on the previous song's *top*, so the second Up, with nothing above it to
step to, handed off again, and the song in between was skipped whole. A musician who wants the last lines of the song
before — the usual reason to go back at a song's top — gets its first lines instead and has to page all the way down.
Going down, two quick presses at a song's end never skip a song, since the next song's top always has a step below it.

## Fix

When `stepBack` hands off, put the previous page at its end first, so that the pager arrives on the last lines and a
second Up steps back inside that song. The neighbouring pages are composed (`beyondViewportPageCount = 1`), so their
scroll states exist: register each page's `ScrollState` in a `remember { mutableStateMapOf<Int, ScrollState>() }`
from the page block (`DisposableEffect(page) { pageScrollStates[page] = scrollState; onDispose { pageScrollStates.remove(page) } }`),
and in `stepBack`'s hand-off branch:

```kotlin
val previousPage = pagerState.targetPage - 1
pageScrollStates[previousPage]?.let { state -> coroutineScope.launch { state.scrollTo(state.maxValue) } }
pageStepper.step(-1)
```

`maxValue` is known once the page has been laid out; a page that has not been (its text still loading) has
`maxValue == 0` and lands on its top as today — acceptable, and rarer than the case fixed. Landing exactly at
`maxValue` is where a Down at the song's end would have rested, so `isStepStop` and the progress indicator agree with
it. `stepForward` keeps landing on the next song's top. Say in `presentation/CLAUDE.md`'s hand-off sentence that Up
hands off to the previous song's end.

## Tests

None practical: two scroll states and a pager. `RowSnappingTest` already pins `previousStepTarget` from `maxValue`.

## Manual check

Desktop build, a setlist of three songs, open the third: Up lands on the second song's last lines; Up again pages back
inside it rather than jumping to the first song.
