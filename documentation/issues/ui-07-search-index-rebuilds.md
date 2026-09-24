<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Reuse normalized song search fields across library updates

Priority: medium for large libraries and repeated edits/imports. Affects web most because normalization executes on its UI thread.

## Evidence

`CampfireViewModel.kt` builds `searchableSongs` by normalizing every song in the filtered list and separately builds `searchableSongsByFileName` by normalizing every song in the full library (around lines 590-602). The latter is rebuilt whenever `allSongs` changes. Saving one song, renaming it, or importing a batch publishes a new library list, so unchanged titles, artists, and tags are normalized again. The two indexes also duplicate most of the same work.

## Implementation plan

1. Introduce one immutable search-index state built from a single `screenData` emission. It should contain the file-name lookup for all songs and the filtered songs in their domain-supplied order. Derive both consumers from that one state so a new library emission cannot briefly pair a new filtered list with an old lookup.
2. Reuse normalized title, artist, and tag strings for a song if those fields are unchanged. A change only to `size`, `lastModified`, key, or transposition must still replace the stored `Song` reference with the latest model, but need not normalize text again. Remove cache entries for deleted or renamed files.
3. Keep the unfiltered lookup for setlists. Song filters must not hide a song from setlist search or its rows. Preserve `distinctUntilChanged` behavior where it prevents unrelated setlist writes from rebuilding the song side.
4. Add tests using an injected/counting normalizer: first load normalizes each song once; a filter/sort change reuses normalized fields; editing one title normalizes one song; deleting and renaming remove old lookup keys. Configure `:presentation` common tests if the helper lives there.

## Done when

- One metadata-only song edit does not normalize every unchanged song twice.
- Song search ranking and setlist search still use current `Song` values and the correct filtered/unfiltered collections.
- A rename does not leave a stale search result under the old file name.
- Check a large-library edit on web for fewer main-thread allocations and no search-result flash.
