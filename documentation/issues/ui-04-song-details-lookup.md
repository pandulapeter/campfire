<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Avoid whole-library and quadratic lookups when opening song details

Priority: medium. The cost lands in composition before a song details page appears.

## Evidence

`SongDetailsScreen` builds `allSongs.associateBy { it.fileName }` to resolve a destination even when the destination names only one song (around lines 140-146). For a setlist, `setlistSlots` then calls `setlist.entries.indexOfFirst` for **each** pager song (around lines 190-194), which is quadratic in the setlist length. Both happen when the details screen composes and when their `remember` keys change.

## Implementation plan

1. Expose a file-name-to-`Song` lookup derived once from `allSongs` in `CampfireViewModel`, or reuse an existing indexed value without coupling the details screen to search normalization. Resolve `destination.songFileNames` from that map. Preserve the `songsBeingRenamed` fallback exactly; rename still has to keep a page alive while disk references catch up.
2. In `SongDetailsScreen.kt`, build `entryIndexByFileName` once from `setlist.entries.withIndex()`, then map pager songs through it. The map must store the **first** index of a duplicate name to match `indexOfFirst`, although ordinary setlists normally name a song once. Keep the current `null` result when any pager song is absent from the setlist.
3. Add tests around the extracted slot builder: ordinary setlist, a missing song before a present one, duplicate entry names, a song removed during a sync, and a rename in progress. Check that pager numbering still includes missing entries.
4. Manually open a single song in a large library and a song near the end of a large setlist on web. Confirm the correct initial page, title, and `current / total` indicator.

## Done when

- Opening one song does not allocate an index for every song in the library in the screen composition.
- Building setlist slots scans the entries once, then performs constant-time lookups per pager song.
- Numbering and rename behavior are unchanged.
