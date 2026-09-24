<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Remove avoidable sorting and normalization from each search keystroke

Priority: medium for large libraries. On web, these Kotlin transforms share the UI thread.

## Evidence

`CampfireViewModel.filterAndRank` (around lines 2257-2280) scans every song and sorts all matches on each active query change. Its comparator uses only three booleans, so the sort does more work than the ranking needs. `Setlist.matchesSearch` (around lines 2288-2295) normalizes every setlist title and description again for every query; song search fields are already pre-normalized.

## Implementation plan

1. Replace the `MatchingSong.sortedWith(...)` step with eight ordered buckets, indexed by the three comparator booleans. Append matches to their bucket while scanning, then concatenate buckets from highest to lowest rank. Keep the original song order within a rank: the current stable sort does that. Preserve the existing `normalizeSearchText` behavior, including a punctuation-only query that normalizes to an empty string.
2. Add a `SearchableSetlist` or equivalent indexed value containing the normalized title and description, rebuilt only when that setlist changes. Search should still check the setlist's **unfiltered** song entries and show the whole setlist when any entry matches.
3. Keep the existing visible/archived setlist separation used by `setlistsPlaceholder`. Search and archive changes must still produce the correct empty-state message.
4. Add pure helper tests for rank order (title prefix, artist prefix, other title/artist hit, tag-only hit, ties), punctuation/accent normalization, and setlist matches through a song. If placed in `:presentation`, configure its new `commonTest` source set with `kotlin("test")`.

## Done when

- A query scans the song list once and does no comparison sort of the matches.
- Setlist title/description normalization runs when those fields change, not on every keystroke.
- Search results, ranking, order within ties, and placeholder states match the current behavior.
- Compare input latency on web with a large library before and after; use the same library and query sequence.
