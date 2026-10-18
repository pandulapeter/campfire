<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Release the search card hold when the results change under it

**Challenged:** amended — do not stop the shared `ListAnchor` transition unconditionally (a tag tap's own narration, started by `ScrollToTopWhenChanged` earlier in the same frame, would be killed); a hold set by *closing* the search must follow its card into the arriving full list rather than release to the top, which is what `songs/CLAUDE.md` documents; ordering confirmed.

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

Store in `SearchCardHold` what it was set for: `songGroups.filterKey`, the held card's item key, and the anchor
transition it started (add a running number to `ListAnchor`, bumped by `animateFrom`, and keep the one `keep()`'s
`animateFrom` produced). In `KeepCardsInPlaceAcrossSearch`'s `SideEffect`, when `songGroups.filterKey` differs from the
held one:

- **If `ListAnchor`'s number is no longer the hold's**, a row anchor (a tag or language tap) has started its own narration
  in `ScrollToTopWhenChanged` this very frame: release the hold and leave the transition alone. `filterAnchor` is shared by
  both, so a blanket `stopTransition()` here would cancel the tag tap's narration right after it began.
- **Else, with the search open** (a query typed or pasted, results re-ranked): release the hold and stop the transition
  (it narrates offsets of a layout that is gone); `ScrollToTopWhenChanged`'s request for item 0 then stands.
- **Else, the hold was set by closing**: `activeQuery` turns empty at once but the full list arrives frames later, inside
  the narration, so the hold's index is one into the results list. Re-resolve the held card's key in the new `groups`
  (`itemIndexOf(key, hasPlaceholder)`) and hold that index with the same offset, which keeps the documented closing
  behaviour ("the top left card then is kept"); release if the card is not in the new list. (Today it holds an index into
  the wrong list; a plain release would send the list to the top instead.)

Ordering, checked: `ScrollToTopWhenChanged`, `KeepCardsInPlaceUnderTick` and `KeepCardsInPlaceAcrossSearch` all act in
`SideEffect`s, which run in composition order after the composition that brought the new `songGroups` is applied and
before the grid measures it, and `holdingSearchCard`'s `apply` runs in that measure. So the release (or re-key) above
lands after `ScrollToTopWhenChanged`'s request and before the hold could repeat its old one. `orderKey` need not be
stored: `filterKey` already holds the query and the sorting mode (`LibraryState`), so it changes whenever `orderKey` does.

## Tests

Pull the hold's decision into a pure function and test: same filterKey and progress → hold; different filterKey with the
search open → release and stop; different filterKey after a newer anchor transition → release, transition kept; different
filterKey on closing → re-hold at the card's new index, or release where it is gone; scroll in progress → release; progress
null → release. A UI test in
`tools/screenshots` (songs package) can open the search on a scrolled list and type at once, then assert that the first
result is the first visible item.
