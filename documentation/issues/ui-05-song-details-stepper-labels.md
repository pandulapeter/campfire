<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Deduplicate song details stepper values before rendering keys

Priority: medium for large setlists. Affects initial song-details composition on every platform.

## Evidence

`SongDetailsScreen.kt` (around lines 205-216) calculates every transposition label for every chorded song in the pager: 23 values per song (`-11..11`). Each value calls `CampfireViewModel.renderKey`, which constructs a key-only `ChordProSong`, transposes it, and converts its notation (around line 1516). Only after all of those calls does `.distinct()` discard repeated labels. A setlist of 100 chorded songs can therefore do 2,300 key renderings before its first page appears, although the app bar only needs the width of the widest label.

## Implementation plan

1. Extract a pure helper for the candidate labels used by `rememberCompactSteppersWidth`. Preserve the rule that the app bar reserves enough width for **any song in the pager** and any transposition value, so paging never moves the controls.
2. Deduplicate inputs before calling `renderKey`: use the source key and file-level transpose (and the selected chord spelling) as the cache key. Songs that share those values need one set of 23 renderings, not one set per song. Keep songs without chords out, as today.
3. Cache the rendered key for each distinct input and transposition while building labels. Make the returned labels distinct without changing their text. Do not reduce the candidate range or reserve space based only on the current page.
4. Add a test for mixed keys, duplicate keys, songs with no chords, and German notation. Compare the new label set with the old algorithm for a representative list before removing the old code. If the helper lives in `:presentation`, add a `commonTest` source set with `kotlin("test")` to `presentation/build.gradle.kts`.

## Done when

- `renderKey` is called at most 23 times per distinct source-key/transpose pair, regardless of setlist length.
- Every label the old algorithm could produce is still considered for width measurement.
- Controls stay in the same place while paging between songs and transposing on web.
