<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 20 — Make the scroll-start and scroll-end recomposition of the song rows cheap

| | |
|---|---|
| Lane | C |
| Impact | medium–low (the first frame of every drag or fling, and the frame it settles on) |
| Confidence | medium–high |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItems.kt |
| Depends on / conflicts with | Conflicts with 15, 19 and 22 (`SongsScreen.kt`) and with 15 and 19 (`ListItems.kt`); land after them. |
| Commit message | `Keep the song rows from recomposing in full as a scroll starts and ends.` |

## Problem
`listItemAnimation` (`ListItemAnimation.kt:137-150`) reads `listState.isScrollInProgress` in the item scope. Its KDoc explains why the placement spec has to be decided in composition, so every visible row recomposes once as a scroll starts and once as it ends. That design stays.

On the songs screen, though, that recomposition goes much deeper than it needs to:

- The spec change produces a new `animateItem` element, which is passed as `SongListItem`'s `modifier` (`SongsScreen.kt:387`). So `SongListItem` can never skip.
- Inside, `ListItems.kt:189-190` allocates new lists on every recomposition:
  ```kotlin
  val languages = if (shouldShowLabels) song.languages.filterNot { it in labelsOnEverySong.languages } else emptyList()
  val tags = if (shouldShowLabels) song.tags.filterNot { it.lowercase() in labelsOnEverySong.tags } else emptyList()
  ```
  A `List` parameter is compared by identity under strong skipping. So the `supportingContent` lambda (`:210`) is recreated, which recomposes `CenteredSongCardContent` (its `combineAsVirtualLayouts` content never skips), the `Column`, `AnimatedContent(note)` and `SongLabels`.
- The item scope also calls `viewModel.renderKey(...)` again (`SongsScreen.kt:392`). That builds a `ChordProSong` and transposes and converts its key (`CampfireViewModel.kt:1535-1538`).

So 15–20 rows do most of their composition twice per gesture, on the frame whose latency the user feels most.

The setlist rows are already mostly safe. Their animation modifier goes to `ReorderableItem`, and the row's content lambda keeps its identity. Leave them alone.

## Fix
1. `SongsScreen.kt`: put the animation modifier on a wrapper so the row's own parameters stay equal. `animateItem` only has to be on the item's root.
   ```kotlin
   Box(modifier = listItemAnimation(listState, hasLoadedLibrary)) {
       SongListItem(modifier = Modifier.fadingUnderListTop(topFade), ...)
   }
   ```
2. In the same item scope, remember the key:
   ```kotlin
   val transposition = transpositions[song.fileName, null]
   val key = remember(song.key, song.transpose, transposition, chordSpelling) { viewModel.renderKey(song, transposition, chordSpelling) }
   ```
3. `ListItems.kt`, in `SongListItem`:
   ```kotlin
   val languages = remember(song.languages, labelsOnEverySong, shouldShowLabels) { if (shouldShowLabels) song.languages.filterNot { it in labelsOnEverySong.languages } else emptyList() }
   val tags = remember(song.tags, labelsOnEverySong, shouldShowLabels) { if (shouldShowLabels) song.tags.filterNot { it.lowercase() in labelsOnEverySong.tags } else emptyList() }
   ```
   This helps every other reason a row recomposes too, such as a filter change or plan 22's star.

What must not change:

- The placement animation, which must still be off while scrolling and on otherwise.
- The fades.
- The row's layout. The grid gives an item a fixed width and `Box` passes it on, so `fillMaxWidth` inside behaves the same.

## Verification
- `./gradlew :presentation:desktopTest`, and the Android debug build.
- Manual check:
  - Rename, delete or filter songs while the list is at rest. Rows must still slide into place.
  - A fling to the top must still show no row sliding in from off screen.
- Measurement: in Layout Inspector's recomposition counts, start and stop a fling. `SongListItem`, `SongLabels` and `TagPill` should show skips rather than recompositions. With a system trace, the first frame of a fling should get shorter.
