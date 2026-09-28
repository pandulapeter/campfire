# Treat a song search that normalizes to nothing as no search at all

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/SearchIndex.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt,
presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/SearchIndexTest.kt,
presentation/CLAUDE.md

## Problem
The songs screen decides between "sections" and "ranked search results" on the **raw** query, but ranks by the
**normalized** one (`CampfireViewModel.kt:693-701`):

```kotlin
val songGroups = combine(indexedSongs, songsSearch.activeQuery) { indexed, query ->
    if (query.isBlank()) {
        indexed.sections.map { SongGroup(header = it.header, songs = it.songs) }
    } else {
        rankSongs(indexed.search.filtered, normalizeSearchText(query)).takeIf { it.isNotEmpty() }?.let { listOf(SongGroup(header = null, songs = it)) }.orEmpty()
    }
}
```

`NormalizeSearchTextUseCaseImpl` drops everything that is not a letter, a digit or a mark, so a query made only of
punctuation, symbols or emoji (`!`, `&`, `-`, `'`, `#`, `♥`, `🎸`) normalizes to `""`. `rankSongs(songs, "")` then
matches every song, all in bucket 7 (`SearchIndex.kt:84-96`; `SearchIndexTest.kt:30` pins that `rankSongs` itself returns
everything for `""`).

Steps: open the songs search and type `&` (the first character of "& Friends", say) or `♥`.
Result: the whole (filtered) library is shown as one **headerless** group — every sticky section header disappears,
the fast scroller loses its bubble labels (`SongSectionIndex` gets one group with a null header), and the list is
"ranked" in plain list order. A query of spaces (`"   "`) is `isBlank()` and keeps the sections, so two queries that
both mean "nothing to search for" look different. The next letter typed brings the ranked results back, so the list
flips sections → flat list → ranked results with each keystroke.

The setlists screen does not show it visibly (`matchesSearch` with `""` matches every setlist, which is the same list
as no query), and the pickers' fields (`SongPicker`, `SetlistPicker`) also end up showing everything, which is what a
blank query shows there too — so only the song list is wrong.

A smaller relative: `SongsScreen.kt:287/311` put the raw field text into `ScrollToTopWhenChanged`'s key, so typing a
space or a punctuation mark that changes nothing in the results still throws a scrolled result list back to the top.

## Fix
1. In `SearchIndex.kt`, add a pure helper next to `rankSongs`, e.g.

   ```kotlin
   internal fun songGroupsFor(sections: List<SongSection>, filtered: List<SearchableSong>, normalizedQuery: String): List<CampfireViewModel.SongGroup>
   ```

   returning the sections as groups when `normalizedQuery.isEmpty()`, and otherwise the single headerless ranked group
   (or no group when nothing matches), exactly as the combine does today. (If `SongGroup` being nested in the view model
   is awkward for a pure file, move the helper's decision only: `fun isSearching(normalizedQuery) = normalizedQuery.isNotEmpty()`.)
2. In `CampfireViewModel.songGroups`, normalize first and branch on the normalized text:
   `val normalized = normalizeSearchText(query)` → `songGroupsFor(indexed.sections, indexed.search.filtered, normalized)`.
   Keep it inside the existing `flowOn(Dispatchers.Default)`.
3. Do the same in `setlistsWithSongs` (`CampfireViewModel.kt:782-789`): branch on `normalizeSearchText(query).isEmpty()`
   instead of `query.isBlank()`, so the two screens share one rule (no visible change there today, but it skips a pointless
   filter pass and keeps the screens consistent if `matchesSearch` ever changes).
4. Optional, same change: in `SongsScreen.SongList` build the `ScrollToTopWhenChanged` key from
   `viewModel.normalizeForSearch(query)` rather than the raw text (and the same in `SetlistsScreen.kt:264`), so an edit
   that leaves the normalized query unchanged does not scroll the results to the top. Reading the field text there stays
   as it is (it is cheap), only the key changes.
5. Tests (`SearchIndexTest.kt`, run on the desktop target): an empty normalized query returns the sections with their
   headers; a non-empty one returns one headerless group; one that matches nothing returns no group.
6. `presentation/CLAUDE.md`, the `CampfireViewModel.kt` bullet where `songGroups` is described ("one headerless group of
   ranked results while a search is on"): say that a query that normalizes to nothing - punctuation, symbols, emoji -
   counts as no search.

## Verification
- `./gradlew :presentation:desktopTest`
- Manual (any platform, a library with several artists): open the songs search, type `&`, `-` or an emoji → the list
  keeps its section headers and the fast scroller its letters; type a letter after it → ranked results as before.
  Type `love`, scroll the results, add a trailing space → the list does not jump to the top (step 4).

## Conflicts
`CampfireViewModel.kt` is touched by most lanes, but only the `songGroups` / `setlistsWithSongs` blocks change here.
`SongsScreen.kt` / `SetlistsScreen.kt`: one line each (step 4).
