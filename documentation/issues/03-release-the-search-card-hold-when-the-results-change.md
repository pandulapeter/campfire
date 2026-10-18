<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Release the search card hold when the results change under it

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (mostly desktop and web, Ctrl / Cmd + F)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/KeepCardsInPlaceAcrossSearch.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongList.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/CLAUDE.md`,
a test in `presentation/src/commonTest/.../screens/songs/`

## Problem

Opening the search holds the top left card where it is (`SearchCardHold`, applied before the grid measures through
`holdingSearchCard`), and the hold is released only when the narration ends or the user scrolls:

```kotlin
if (progress == null || listState.isScrollInProgress) { index = -1 } else { listState.requestScrollToItem(index = index, scrollOffset = offset) }
```

The narration runs on `searchTravelSpec` (~0.3–0.5 s). A query typed or pasted inside that window changes `orderKey`, so
`ScrollToTopWhenChanged` asks for item 0, and on the next measure the hold asks for the old index again, overriding it.
`requestScrollToItem` does not set `isScrollInProgress`, so nothing releases the hold: the ranked results open scrolled to
around the old position with the best matches above the viewport. A tag tap inside the window is overridden the same way.

## Fix

Release the hold when the groups it was set for are no longer the ones shown: store the `filterKey` (and `orderKey`) it was
set for in `SearchCardHold`, and in `KeepCardsInPlaceAcrossSearch`'s `SideEffect` call a new `SearchCardHold.release()`
(and stop the anchor's transition if `ListAnchor` offers it) when `songGroups.filterKey` / `orderKey` differ. Recommended over
having `apply` ignore itself silently, since releasing also stops narrating a jump that no longer applies.

Check the ordering against `ScrollToTopWhenChanged` (called before `KeepCardsInPlaceUnderTick` and
`KeepCardsInPlaceAcrossSearch` in `SongList`): the release must happen in the composition the new key arrives in, before
the grid measures, or the hold wins once more.

## Tests

Pull the hold's decision into a pure function (`shouldHold(heldFor, current, isScrollInProgress, progress)`) and test: same
keys and progress → hold; different filterKey → release; scroll in progress → release; progress null → release. A UI test in
`tools/screenshots` (songs package) can open the search on a scrolled list and type at once, then assert that the first
result is the first visible item.
