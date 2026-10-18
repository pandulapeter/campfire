<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Key the setlists screen's scroll to the top on the list the search produced, not the typed text

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistList.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/ListedSetlists.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/setlists/SetlistsController.kt`,
its test, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/CLAUDE.md`

## Problem

`SetlistList.kt`:

```kotlin
val query = if (isSearchOpen) viewModel.setlistsController.setlistsSearch.textFieldState.text.toString() else ""
val searchedQuery = remember(query) { viewModel.songRenderer.normalizeForSearch(query) }
ScrollToTopWhenChanged(key = "$searchedQuery|...", contents = setlistsWithSongs, ...)
```

Reading `TextFieldState.text` recomposes the whole `SetlistList` on every typed character, and again a few frames later when
the filtered `setlistsWithSongs` arrives; the scroll key also runs ahead of the list it describes. The Songs screen carries
`SongGroups.filterKey` / `orderKey` with its list for exactly this reason.

## Fix

Carry the normalized query (a `filterKey`) in `ListedSetlists`, built in `SetlistsController.setlistsWithSongs` from the query
the list was filtered by, and key `ScrollToTopWhenChanged` on it. Remove the composition read of the text.

## Tests

`SetlistsControllerTest`: `ListedSetlists.filterKey` changes with the applied query, not with a text change while the search
is closed; `isLibraryRead` survives the search filter (untested today): false while `indexedSongs.isLibraryRead` is false,
true after, and kept with a query that filters some setlists out.
