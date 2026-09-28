# Precompute the song picker's filters and rows instead of folding the library in its first frame

**Kind:** ui-performance  ·  **Severity:** low  ·  **Platforms:** all (worst on web and low-end Android)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/SearchIndex.kt (or a new
`ui/SongPickerIndex.kt`), presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/ (new test),
presentation/CLAUDE.md

## Problem
`CampfireViewModel.alphabeticalSongs` (`CampfireViewModel.kt:663-671`) exists so that "the first frame of the sheet"
does not "wait for a whole library to be sorted" — yet `SongPicker` (`Dialogs.kt:1251-1340`) still walks, allocates and
sorts the whole library inside its first composition, on the main thread:

- `Dialogs.kt:1275` `val filters = remember(songs) { songs.toPickerFilters() }`, and `toPickerFilters`
  (`Dialogs.kt:1379-1402`) starts with `sortedBy { it.fileName }` over **every** song, then per song builds
  `song.languages.ifEmpty { … }.toSet()` and `song.tags.associateBy { it.lowercase() }` (a set and a map per song), and
  finally sorts the tags and languages. The full sort exists only to pick each tag's spelling "as the first song by file
  name spells it".
- `Dialogs.kt:1269-1274` `pickableSongs`: `alphabeticalSongs.associateBy { … }` (a 2000-entry map), a `filterNot`, and
  `.map { it.toPickableSong() }`, where `toPickableSong` (`Dialogs.kt:1356-1363`) allocates a lower-cased tag set and a
  language set per song.

With a 2000-song library that is a 2000-string sort plus ~8000 small collections and a 2000-entry map built in the
composition that the `ModalBottomSheet` opens with — every time "Add songs" / "Song assignments" is tapped, and again
whenever `allSongs` or `alphabeticalSongs` changes while the sheet is open (a sync run's live refresh, which since
cc4d6f55 follows every edit by ten seconds). On the web build `Dispatchers.Default` is the same single thread, so
nothing here can be hidden behind it; the cost lands on the first frames of the sheet's slide-up, which is exactly the
jank the view model's precomputed list was meant to prevent.

## Fix
1. Move `PickableSong` and `PickerFilterOptions` out of `Dialogs.kt` into a pure, internal file
   (`ui/SongPickerIndex.kt`, or next to `SearchableSong` in `SearchIndex.kt`).
2. Replace `toPickerFilters` with a pure `pickerFilterOptions(songs: List<Song>)` that needs no sort of the library:
   count per key and keep the spelling of the song with the smallest file name seen so far, the way
   `GetScreenDataUseCaseImpl.toTags` does (`if (song.fileName < spelled.fileName) …`). Same output as today.
3. In `CampfireViewModel`, turn the two into eager states built on `Dispatchers.Default`, like `alphabeticalSongs`:
   - `songPickerFilters = allSongs.map(::pickerFilterOptions).flowOn(Dispatchers.Default).asState(PickerFilterOptions.Empty)`
   - make `alphabeticalSongs` hand out `PickableSong`s (map `SearchableSong.toPickableSong()` there, once per library),
     and carry a `byFileName` map alongside the ordered list (e.g. a small `AlphabeticalSongs(list, byFileName)`
     value) so the sheet does not rebuild it.
4. In `SongPicker`, collect those two states; `pickableSongs` becomes
   `remember(alphabetical, initialSongFileNames) { initialSongFileNames.mapNotNull { alphabetical.byFileName[it] } + alphabetical.list.filterNot { it.song.fileName in initial } }`
   — two linear passes over already-built objects, no per-song allocation.
5. Test (`commonTest`, desktop target): `pickerFilterOptions` gives the tag spelling of the song first by file name, counts
   each tag once per song whatever its case, orders most-used first, leaves languages empty for a one-language library
   and puts `SongLanguage.UNKNOWN` last.
6. `presentation/CLAUDE.md`: where `alphabeticalSongs` / the song picker is described, say that the picker's filter chips
   and its rows are built by the view model as well, so the sheet's first frame only orders what is already there.

## Verification
- `./gradlew :presentation:desktopTest`
- Manual (Android debug build or the web build, library of ~2000 songs — e.g. import an archive of generated `.cho`
  files): open a setlist's "Add songs" repeatedly; the sheet slides up without the hitch at its start (compare with a
  frame-timing overlay / the browser's performance panel before and after). Filter chips, their counts and spellings,
  the initial "already in the setlist first" order and ticking behave exactly as before.

## Conflicts
`Dialogs.kt` may be touched by the dialogs / cover-art lanes (other sheets in the same file); this only changes
`SongPicker`, `PickableSong`, `PickerFilterOptions`, `toPickerFilters` and `toPickableSong`. `CampfireViewModel.kt`:
only `alphabeticalSongs` and one new state.
