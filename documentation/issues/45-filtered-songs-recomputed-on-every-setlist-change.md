# 45 · Every setlist change re-normalizes and regroups the whole song list on Main

**Severity:** medium (perf) · **Area:** `:presentation` (`CampfireViewModel`)

`filteredSongs = screenData.map { it.data?.songs.orEmpty() }` (`CampfireViewModel.kt:346`) has no
`distinctUntilChanged`, and `searchableSongs` (:376–378) is a raw `map`. `screenData` emits on any setlist write
with the *same* `songs` list, so `searchableSongs` re-runs `normalizeText` on every title and artist and `songGroups`
regroups the library — once per tick in the song picker, per transposition step inside a setlist, per reorder.

## Fix

- `private val filteredSongs = screenData.map { it.data?.songs.orEmpty() }.distinctUntilChanged()`
- Make `searchableSongs` a state like its sibling: `.asState(emptyList())` — `asState` already applies
  `distinctUntilChanged`, and `songGroups` is its only reader, so the normalization then runs once per library change.
- Check `visibleSetlists` and `setlistGroups` (the setlists side) for the same pattern; `searchableSongsByFileName` is
  already a state.
