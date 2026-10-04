# Keep the room the search anchor added at the end of the Songs list until it has scrolled out of sight, so a drag near the end does not jump

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongSearchScrollAnchor.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt, presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongSearchScrollAnchorTest.kt

**Challenged:** amended — the two `SongsScreen` collectors (scroll start, spacer visibility) are merged into one over `isScrollInProgress to isSpaceVisible`: as written, a spacer already out of sight when the scroll let the anchor go never re-emitted `false`, so its room was kept until it had been scrolled into view and out again, and collector order within a frame could make the release run before the let-go. The test changes are justified: the room outliving the anchor is the fix itself, and the changed test still asserts the anchoring is released.

## Problem

When the search opens or closes near the end of the Songs list, the section headers below the anchored card collapse, which would shorten the scroll range below the position the anchor holds. A spacer at the end of the grid keeps exactly that lost room (SongsScreen.kt:553–561):

```kotlin
item(key = "search_anchor_space", span = { GridItemSpan(maxLineSpan) }, contentType = "search_anchor_space") {
    Spacer(Modifier.fillMaxWidth().layout { measurable, constraints ->
        val removedHeight = (LIST_APP_BAR_HEIGHT.toPx() * (1f - appBarOverlap().coverage)).roundToInt()
        val height = searchScroll.trailingHeaderCount * removedHeight
        …
```

The room only exists because the anchor needed it, so it is on screen whenever it matters. But the first frame of any drag drops it to nothing (SongsScreen.kt:331–333):

```kotlin
LaunchedEffect(listState, searchScroll) {
    snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { searchScroll.cancel() }
}
```

`SongSearchScrollAnchor.cancel()` (SongSearchScrollAnchor.kt:78–84) resets `trailingHeaderCount = 0`, and so do the scrolling branches of `update` (:42–46, which runs on every composition while a scroll is in progress) and `positionFor` (:64–67). The grid then has less content below the viewport than the viewport shows, clamps its offset in the same frame, and every card jumps down by the visible part of the spacer under the finger. (Traced in code; consistent with the live run's "partly confirmed".)

## Fix

Let a scroll release the *anchoring* (as now) but keep the tail room until it is out of sight, or until what it was kept for changes.

In `SongSearchScrollAnchor`:
1. Add `private var trailingSpaceContents: Any? = null`, set to `contents` where the snapshot is captured (next to `trailingHeaderCount = card.trailingHeaderCount`).
2. Split `cancel()`:
   - `private fun letGo()` — clears `contents`, `index`, `originalPosition`, `lastInset`; leaves `trailingHeaderCount` and `trailingSpaceContents`.
   - `fun cancel()` — `letGo()` plus `releaseTrailingSpace()`.
   - `private fun releaseTrailingSpace()` — `trailingHeaderCount = 0; trailingSpaceContents = null`.
3. In `update`, first compute `val isChange = open != isOpen || (trailingSpaceContents != null && trailingSpaceContents != contents)` — new results or the search toggled bring the headers back or change the list, so the room has nothing left to keep. The scrolling branch becomes `letGo(); if (isChange) releaseTrailingSpace(); isOpen = open; return` (the mode change is consumed there, so the release must happen in the same call). On the idle path, `if (index == null && isChange) releaseTrailingSpace()` before the existing logic (a capture that follows sets the count afresh).
4. In `positionFor`, the scrolling branch calls `letGo()` instead of `cancel()`.
5. Add `fun releaseTrailingSpaceIfOutOfSight(isSpaceVisible: Boolean) { if (index == null && !isSpaceVisible) releaseTrailingSpace() }` — only once the anchor has let go: while it still holds a position, the spacer may not have been laid out yet, and releasing it then would take away the room the position needs.
6. Update the class KDoc / the `trailingHeaderCount` KDoc: the room is kept after a scroll takes the position over, until it has left the screen, since dropping it while it is in sight pulls the end of the list up under the finger.

In `SongsScreen.kt`:
- Replace the scroll-start collector with **one** collector that both lets go and releases, so the release cannot miss its moment:
  ```kotlin
  LaunchedEffect(listState, searchScroll) {
      snapshotFlow { listState.isScrollInProgress to listState.layoutInfo.visibleItemsInfo.any { it.key == SEARCH_ANCHOR_SPACE_KEY } }
          .collect { (isScrolling, isSpaceVisible) ->
              if (isScrolling) searchScroll.letGoForScroll()
              searchScroll.releaseTrailingSpaceIfOutOfSight(isSpaceVisible)
          }
  }
  ```
  `letGoForScroll()` is a new public name for `letGo()` (idempotent, so calling it again on a visibility change during the same scroll is harmless). Two separate collectors — a visibility flow beside the scroll-start one — would not work: `snapshotFlow` emits only when its value changes, so a spacer that was already out of sight when a scroll let the anchor go never emits `false` again and its room would wait until it had come into view and left once more; and the two collectors' order within a frame is unspecified, so the visibility one could run while the anchor still holds its position and be ignored. Pairing it with `isScrollInProgress` re-evaluates at the scroll's start and end. (Reading `layoutInfo` makes the flow recompute on every scrolled frame; it is an `any` over the visible items, cheap.)
  Extract `"search_anchor_space"` into `private const val SEARCH_ANCHOR_SPACE_KEY`, used by both the item and the collector. Update the comment above the spacer item ("Keep just that lost tail space until the user scrolls…" → "…until it has scrolled out of sight, or the search changes, or the headers return").

Releasing room that is not visible changes nothing on screen (the first visible item and its offset stay put), so there is no jump in either direction.

## Tests

`SongSearchScrollAnchorTest` (pure class, runs on desktop):
- Change `scrollStartingBetweenCompositionAndMeasurementPermanentlyReleasesTheAnchor` to expect `assertEquals(3, anchor.trailingHeaderCount)` after the scroll, then call `anchor.releaseTrailingSpaceIfOutOfSight(isSpaceVisible = true)` → still 3, then `(false)` → 0.
- Add `aScrollKeepsTheTailRoomUntilTheResultsChange`: capture, `positionFor(…, isScrolling = true)`, `update(open = true, contents = contents, isScrolling = true, …)` → still 3; `update(open = true, contents = "filtered results", isScrolling = false, …)` → 0.
- Add `closingTheSearchAfterAScrollReleasesTheTailRoom`: capture on open, scroll, then `update(open = false, …)` → 0.
- Add `theTailRoomIsNotReleasedWhileTheAnchorStillHoldsAPosition`: capture, `releaseTrailingSpaceIfOutOfSight(false)` → still 3.
- `openingDuringAnExistingScroll…` and `changedSearchResultsReleaseTheOriginalAnchor` keep expecting 0.

Run `./gradlew :presentation:desktopTest --offline`.

## Manual check

On a phone with a library long enough to scroll and several sections (sorted by artist), scroll to the very end of Songs, open the search with the button (headers collapse; the last card stays in place). Without typing, start dragging the list upwards slowly: the cards follow the finger from the first frame with no downward jump. Drag back to the end: the extra room is still there until you scroll far enough that the end leaves the screen; after that, returning to the end shows the list ending at its last card.
