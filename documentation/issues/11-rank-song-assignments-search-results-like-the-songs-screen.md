# Rank the Song assignments sheet's search results the way the songs screen does, title and artist matches before tag-only ones

**Kind:** bug (consistency)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/SearchIndex.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/SongPickerIndex.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt,
presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/SearchIndexTest.kt,
presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/SongPickerIndexTest.kt,
presentation/CLAUDE.md

**Challenged:** sound

## Problem
The Song assignments sheet (`SongPicker`, `Dialogs.kt` ~1717-1725 at 800ebde0b) already folds the query like the songs
screen (`viewModel.normalizeForSearch`, against `PickableSong`'s pre-folded title/artist/tags, so accents, spaces and
punctuation are ignored alike), but it only **filters**, keeping library order:

```kotlin
val matches = remember(pickableSongs, query, activeTags, activeLanguages) {
    val normalizedQuery = viewModel.normalizeForSearch(query)
    pickableSongs.filter { pickableSong ->
        (normalizedQuery in pickableSong.title || normalizedQuery in pickableSong.artist ||
                pickableSong.searchableTags.any { normalizedQuery in it }) &&
                (activeTags.isEmpty() || activeTags.any { it in pickableSong.tags }) &&
                (activeLanguages.isEmpty() || activeLanguages.any { it in pickableSong.languages })
    }
}
```

The songs screen ranks the same matches with `rankSongs` (`SearchIndex.kt` ~85): title prefix, then artist prefix, then
any title/artist match, and a song found by a tag alone last — "a tag is shared by a whole shelf of songs, so a query
that names one song and also happens to be part of a tag would otherwise have that song buried". Live run: typing a
song's title in Song assignments listed a song tagged with that word above the song itself. Root `CLAUDE.md` promises
the ranking for "both list screens"; the picker is the same search over the same library and should answer the same.

## Fix
1. `SearchIndex.kt`: extract the rank of one song from `rankSongs` into a shared internal function, so the formula
   exists once:
   ```kotlin
   /** [rankSongs]' bucket for a song with these folded fields, or null where [query] does not find it. */
   internal fun searchRank(title: String, artist: String, tags: List<String>, query: String): Int? {
       val titleHit = title.contains(query)
       val artistHit = artist.contains(query)
       if (!titleHit && !artistHit && tags.none { it.contains(query) }) return null
       return (if (title.startsWith(query)) 4 else 0) + (if (artist.startsWith(query)) 2 else 0) + (if (titleHit || artistHit) 1 else 0)
   }
   ```
   and have `rankSongs` use it (behaviour unchanged; `SearchIndexTest` keeps passing).
2. `SongPickerIndex.kt`: a pure function the sheet calls, with a KDoc saying it answers like the songs screen's search
   and narrows by the chips like its filters:
   ```kotlin
   internal fun songPickerMatches(
       songs: List<PickableSong>, normalizedQuery: String, activeTags: Set<String>, activeLanguages: Set<String>,
   ): List<PickableSong>
   ```
   — drop the songs the chips exclude (same conditions as today), then, for a non-empty query, bucket the rest by
   `searchRank(song.title, song.artist, song.searchableTags, normalizedQuery)` (eight buckets, highest first, ties in
   list order, as `rankSongs` does); for an empty query return the filtered list in its order.
3. `SongPicker`: `val matches = remember(...) { songPickerMatches(pickableSongs, viewModel.normalizeForSearch(query), activeTags, activeLanguages) }`.
   The checked songs still lead through `songOrder.ordered(matches)`, which keeps the order of the rest as given.
4. `presentation/CLAUDE.md`: where the song picker's search is described (search for "PickerFilters" / "Song
   assignments"), add that its results are ranked as the songs screen's are (`songPickerMatches`, `searchRank`).

## Tests
- `SongPickerIndexTest`: with songs "Love Me Do" (title), "Other" by "Lovers" (artist), "Ballad" tagged "love songs"
  (tag only) in library order tag-only first, `songPickerMatches(…, "love", emptySet(), emptySet())` gives title,
  artist, tag; an empty query gives the library order; an active tag or language chip still excludes songs; a query
  that matches nothing gives an empty list.
- `SearchIndexTest`: add `searchRank` cases (null for no match, 7 for title+artist prefixes, 1 for a contained match,
  0 for a tag-only match); the existing `rankSongs` test stays green.
Run `./gradlew :presentation:desktopTest`.

## Manual check
Library with a song titled "Wonderwall" and another tagged "wonder" listed before it alphabetically: open a setlist's
Song assignments, type "wonder" — "Wonderwall" comes first, the tag-only song after it; ticked songs still stay at the
top; clearing the search restores the library order.
