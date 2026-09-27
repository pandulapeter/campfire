<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 06 — A neighbouring song's lyrics are not swapped in while the pager is still moving

| | |
|---|---|
| Lane | A |
| Impact | medium (first pass through a setlist; `songTexts` keeps texts afterwards) |
| Confidence | medium |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt |
| Depends on / conflicts with | Edits the pager content lambda, as 02 and 01 do. Land after them. Order: … 07 → **06** → 09 → 10. |
| Commit message | `Keep swiping between the songs of a setlist smooth while the next one loads.` |

## Problem
`SongDetailsScreen.kt:241-245` requests text only for the pages next to the current one, and only once `currentPage` has changed:

```kotlin
LaunchedEffect(pagerState.currentPage, songs) {
    listOfNotNull(songs.getOrNull(pagerState.currentPage - 1), songs.getOrNull(pagerState.currentPage + 1))
        .filter { it.fileName !in songTexts && it.fileName !in failedSongFileNames }
        .forEach { viewModel.loadSongContent(it.fileName) }
}
```

`currentPage` changes at the halfway point of a swipe. The page that just became the new neighbour (c + 2 when swiping from c to c + 1) is already composed as a beyond-viewport page (`beyondViewportPageCount = 1`), but in its loading state. The library pager also prefetches `visible ± 2` (`PagerState.calculatePrefetchIndex`), and that happens before the text exists, so it is cheap and useless.

`loadSongContent` (`CampfireViewModel.kt:1500-1509`) is a quick local read, so the text usually lands during the settle animation. `SongDetailsPage`'s `AnimatedContent` (`:574-579`) then switches in a single frame to:
- `renderSong`: parse, transpose, notation (`:599`);
- `toRenderSections`;
- composition of every line;
- full measurement, including intrinsics on multi-column windows.

The result is one long frame in the middle of the pager's settle, which is a visible hitch at the end of the swipe. After the first pass the texts are cached in `songTexts`, so the problem only appears the first time through a setlist.

## Fix
Hold back the swap from "loading" to lyrics for a page that is neither on screen nor being swiped to, until the pager has stopped. This is recommended over loading ±2 ahead. Loading ahead would move the heavy composition to the first frame of the next swipe, because the pager composes the new beyond-viewport page synchronously in the measure pass that reveals its neighbour. Waiting for the pager to be idle guarantees that no animation is running when that frame happens.

In the pager content:
```kotlin
val text = songTexts[song.fileName]
// A page shows its lyrics once and keeps them; only the first swap from loading is held back.
var hasShownLyrics by remember { mutableStateOf(text != null) }
val isOutOfSight = page != pagerState.currentPage && page != pagerState.targetPage
val shownText = if (!hasShownLyrics && isOutOfSight && pagerState.isScrollInProgress) null else text
if (shownText != null && !hasShownLyrics) SideEffect { hasShownLyrics = true }
SongDetailsPage(text = shownText, …)
```

- `isScrollInProgress`, `currentPage` and `targetPage` change only at the start, middle and end of a swipe, so reading them here adds no per-frame recomposition.
- `hasFailed` is left as it is: an error state is cheap to show.
- **What must not change:**
  - A page whose lyrics are already shown keeps showing them during a swipe, including when `songTexts` delivers a newer version from a sync.
  - A page being swiped to (`targetPage`) is never held back.
  - The ±1 preloading stays as it is.

## Verification
- Run `./gradlew :presentation:desktopTest :app:android:assembleDebug`.
- Android `.debug` build, a fresh process, a setlist of six long songs. Swipe through it once while `adb shell dumpsys gfxinfo com.pandulapeter.campfire.debug framestats` runs. The long frame at the end of each settle should be gone: the frame that composes the next-but-one song now lands after the settle.
- Swipe quickly twice in a row. The second song appears with its lyrics (it was the `targetPage`), never with a loading indicator that was not there before.
