# Settle the neighbour pages' font scale only after the current page has finished settling

**Challenged:** amended — a swipe started inside the (now 500 ms) window would hand the page being left the old
settled scale as soon as `currentPage` moves past it mid-swipe, so it re-lays itself out at the pre-pinch size while
still half on screen (and again 500 ms later); the plan now also settles the scale the moment the pager starts
scrolling (`SongPages`, through a `CampfireViewModel.settleFontScale()` forward).

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/fontScale/FontScaleController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongPages.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`

## Problem

The pager composes the page on either side of the one being read (`beyondViewportPageCount = 1`), and those pages read
the settled scale (`SongPages.kt`):

```kotlin
fontScale = if (isFollowingGesture) ({ viewModel.fontScale }) else ({ viewModel.settledFontScale }),
```

`settledFontScale` follows the live value 200 ms after the last change (`FontScaleController.kt`):

```kotlin
fun startSettle() = scope.launch {
    snapshotFlow { fontScale }.debounce(FONT_SCALE_SETTLE_MILLIS).collect { settledFontScaleState.floatValue = it }
}
...
const val FONT_SCALE_SETTLE_MILLIS = 200L
```

and the page being read leaves `GLIDE` 200 ms after the last change too (`SongDetailsPage.kt`,
`rememberContinuousChange`, `CONTINUOUS_CHANGE_MILLIS = 200L`). So about 200 ms after the fingers stop, in the same
frame or the next one:

- both neighbour pages get a new `fontScale`, which in `SongLyrics` builds a new `SectionSizesPool` (empty), a new
  `SongTextMeasurements`, and new `TabRows`, and searches their grids from nothing: two full layouts of two songs,
  including the fitted searches;
- the current page flips `GLIDE` → `SPRING`, which recomposes every unit of it to put `animateBounds` back on;
- 50 ms later `RowSnapFlingBehavior.keepReaderInPlace` (`LAYOUT_SETTLE_MILLIS`, 250 ms after the layout held still)
  starts animating the scroll back to the line the reader was on, while the glides of the last grid change are still
  running.

The two neighbour layouts land on the main thread exactly when the page in front of the reader is animating, which is
where a dropped frame is seen. On a slow phone that is a visible hitch at the end of every pinch.

`settledFontScale` has no other reader: `grep` finds it only in `SongPages.kt` (above) and in
`CampfireViewModel.settledFontScale`, which forwards it. The stepper and the zoom shortcuts call `adjustFontScale` /
`zoomSongText`, which call `settleFontScale()` directly, and the stored value arriving (`startEcho`) does the same, so
none of those go through the debounce.

## Fix

Raise `FONT_SCALE_SETTLE_MILLIS` from `200L` to about `500L`, so the neighbours re-lay themselves out after the
current page has flipped back to `SPRING` (200 ms), started re-anchoring (250 ms), and let its glides mostly run down.
Since the debounce only drives `settledFontScale`, whose only reader is the neighbour pages, nothing else changes:

- the stepper and the shortcuts still reach every page at once (`settleFontScale()`);
- a swipe started within the 500 ms makes the next page the `targetPage`, which reads the live `fontScale` at once
  (`isFollowingGesture`), exactly as it does within 200 ms today;
- the preferences are still written after `writeDelayMillis`, which is unrelated.

Update the KDoc of `settledFontScale` if it names a duration (it says "once it has held still for a moment", which can
stay), give the constant a one-line comment saying why it is longer than `CONTINUOUS_CHANGE_MILLIS` and
`LAYOUT_SETTLE_MILLIS` (the pages beside the one being read lay themselves out after it has settled, not in the same
frames), and keep the comment above `isFollowingGesture` in `SongPages.kt` accurate.

**A swipe settles the scale at once.** `isFollowingGesture` is `currentPage || targetPage`, and `currentPage` moves to
the incoming song halfway through a swipe; from then on the outgoing song, still half on screen, reads
`settledFontScale` - the size from before the pinch, if the debounce has not fired yet - and is laid out again at it
while it slides away, and once more when the debounce fires. That already happens within 200 ms today; at 500 ms a
pinch followed by a swipe hits it routinely. So in `SongPages`, settle when the pager starts moving:

```kotlin
LaunchedEffect(pagerState) {
    // A swipe right after a pinch must not hand the song it leaves the size from before the pinch once it stops being
    // the current page, halfway across: the pages beside the one being read take the new size before any of them moves.
    snapshotFlow { pagerState.isScrollInProgress }.collect { if (it) viewModel.settleFontScale() }
}
```

with `fun settleFontScale() = fontScaleController.settleFontScale()` on `CampfireViewModel`, next to
`settledFontScale` (KDoc: "See [FontScaleController.settleFontScale]"). It costs one layout of the song on the far
side, off screen, at the start of a swipe that comes within 500 ms of a pinch, and nothing otherwise (an equal value
is ignored by the state). The step buttons' paging (`animateScrollToPage`) counts as a scroll and is covered too.

Otherwise do not move the neighbours to a separate state or delay inside the page: one constant and the settle on a
swipe are the whole mechanism.

## Tests

No unit test: the change is the duration of a `debounce` in a coroutine on the view model's scope, and there is no pure
logic to extract. (A `runTest` test of `FontScaleController.startSettle` would need `Snapshot.sendApplyNotifications()`
after each write for `snapshotFlow` to see it, plus fakes of `DialogHost` and `UpdateUserPreferencesUseCase`, to check a
constant; not worth it.)

## Manual check

In a setlist of several long songs on a low-end Android phone (or the smallest supported emulator, 360 × 640 dp),
pinch the text size on one song and lift the fingers: the end of the pinch (the sections coming to rest and the scroll
returning to the line being read) should be smooth, without the stutter about a fifth of a second after lifting that
master shows. Then swipe to the next song about one second later: it must already be at the new size, with no
re-layout visible as it slides in. Swipe immediately after a pinch (within half a second): the incoming song follows the
new size as it comes in, as before, and the song being left keeps the new size as it slides away (no jump back to the
old size halfway through the swipe). Pinch in a setlist without letting the pages move sideways: the song beside it
must not change size until half a second after the fingers lift. Tap the stepper's plus, then swipe: the next song is at the new size.

**CLAUDE.md:** in `screens/songDetails/CLAUDE.md`, change "the pages composed beside it read `settledFontScale`, the
live value once it has held still for 200 ms, which a step of the stepper or a shortcut sets at once" to say 500 ms, and
add that this is after the page being read has stopped gliding and re-anchored, so the pages beside it are laid out
again in frames of their own, and that a swipe settles it at once, so the song being left never goes back to the old
size.
