# Sort the whole library once per change, for both the song list and the song picker

**Kind:** performance (CPU on every library change)  ·  **Severity:** low  ·  **Platforms:** all (most on 2-core devices and the web)
**Lane:** S  ·  **Files:**
`domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/models/ScreenData.kt`,
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImpl.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImplTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (only
`indexedSongs` ~line 803-816, `pickerSongs` ~line 908-929, and the private `IndexedSongInput` / `IndexedSongs`
classes ~line 4339-4350),
`domain/implementation/CLAUDE.md` (if it lists `ScreenData`'s fields)

**Challenged:** amended — the memo is one `@Volatile` reference to an immutable (input, sorting mode, result) holder rather than a "field pair": the view model collects the same use case instance twice at once (`screenData`, and `getScreenData(_songFilter).first { … }` in the import path), on `Dispatchers.Default` threads, and two separately written volatile fields can be read torn — the new input with the previous result, i.e. another library's order. Also states that the picker adopting the songs screen's order is what `presentation/CLAUDE.md` already promises ("a sheet lists the setlists or the songs in the order the screen does"), so no documentation changes for it, and that `SongPickerIndexTest` never builds the picker's order, so it is unaffected.

## Problem

Every change to one song (a save, a tag, a sync refresh, a rescan's result) re-normalizes and re-sorts the whole
library **twice**, on `Dispatchers.Default`:

1. `GetScreenDataUseCaseImpl.sortIntoSections` (`GetScreenDataUseCaseImpl.kt:335-350` at 491c4254a) maps every song to
   `SortableSong(…, artist = normalizeText(it.artist), title = normalizeText(it.title))` and sorts by
   `SortableSong.ORDER = compareBy({ it.initial != null }, { it.sectionKey }, { it.primaryText }, { it.secondaryText },
   { it.song.fileName })` — for the filtered songs only, and again on every filter toggle.
2. `CampfireViewModel.pickerSongs` (`CampfireViewModel.kt:908-929`), on every `indexedSongs` emission, normalizes the
   title and artist of every song again and sorts by `compareBy({ it.second }, { it.third }, { it.first.song.fileName })`
   (primary text, secondary text, file name) — whether or not the picker sheet is ever opened.

Neither costs a main-thread frame, but on a 2-core low-end device (and on the web, where `Dispatchers.Default` is the
one thread) the duplicate pass competes with everything else: an estimated 30–60 ms of CPU per change for 2,000 songs.

The two orders are **not identical**: the list's puts every song whose primary text does not start with a letter
first, and by title groups songs by their upper-case initial before comparing the full text (so `ı…` and `I…` share a
run), while the picker sorts by the plain normalized text. The picker's KDoc says it lists the library "in the order
the songs screen is sorted by", so adopting the list's order is the intended behaviour, and the difference only shows
for titles/artists starting with a symbol, a digit or a letter whose case pairs are unusual.

## Fix

Sort the whole library once in the use case, memoized across filter changes, and hand the result to the picker.

1. **`ScreenData`**: add
   ```kotlin
   /**
    * The whole library in the order [songs] is sorted in, the filters left out: what the song picker lists, which
    * would otherwise sort the library a second time on every change.
    */
   val sortedSongs: List<Song> = emptyList(),
   ```
   (with a default, so `partialScreenData` and the tests' constructions keep compiling; set it everywhere `ScreenData`
   is built in `GetScreenDataUseCaseImpl`, `EMPTY_SONG_PART` included).
2. **`GetScreenDataUseCaseImpl.toSongPart`**:
   - Build `sortable = map { SortableSong(…) }.sortedWith(SortableSong.ORDER)` for **all** songs (not the filtered
     ones), memoized in a one-entry cache keyed by the identity of the input list and the sorting mode. The cache
     is **one** `@Volatile private var sortMemo: SortMemo? = null` holding an immutable
     `private class SortMemo(val input: List<Song>, val sortingMode: UserPreferences.SortingMode, val sorted: List<SortableSong>)`,
     read once into a local and replaced whole (`sortMemo?.takeIf { it.input === this && it.sortingMode == mode }?.sorted
     ?: sort().also { sortMemo = SortMemo(this, mode, it) }`). Not a field pair: one transform runs one emission at a
     time, but the view model collects this same instance twice concurrently (`screenData`, and the
     `getScreenData(_songFilter).first { … }` calls of the import path), and two fields written one after the other can
     be read torn from another thread — the new input paired with the previous library's order. A filter toggle then
     sorts nothing.
   - `sortedSongs = sortable.map { it.song }`.
   - Apply `filterTags` / `filterLanguages` to `sortedSongs` exactly as today (they are `filter { }` predicates, so the
     order is kept), and cut the sections from the filtered songs mapped back to their `SortableSong`
     (`sortable.associateBy { it.song.fileName }` — file names are unique), replacing `sortIntoSections`' own map and
     sort. The section-cutting code (`linkedMapOf`, `sectionKey`, headers) is unchanged.
   - `songs = songSections.flatMap { it.songs }` as today; `SongPart` carries `sortedSongs` into `ScreenData`.
3. **`CampfireViewModel`**:
   - `IndexedSongInput` and `IndexedSongs` gain `val sorted: List<Song>`; `indexedSongs` fills it from
     `data?.sortedSongs.orEmpty()`, so the picker's order and its search index always come from the same emission.
   - `pickerSongs` becomes
     ```kotlin
     internal val pickerSongs = indexedSongs.map { indexed ->
         val list = indexed.sorted.mapNotNull { indexed.search.byFileName[it.fileName] }.map { it.toPickableSong() }
         PickerSongs(list = list, byFileName = list.associateBy { it.song.fileName })
     }.flowOn(Dispatchers.Default).asState(PickerSongs.Empty)
     ```
     dropping its own `combine` with the sorting mode (the use case already sorted by it) and its `normalizeText` calls.
     Keep its KDoc's point (built here rather than as the sheet opens) and say the order is the use case's.
   - Do not touch `songMetadataOf` or anything else (lane U edits that).

Optional, not part of this plan: caching the normalized keys per `Song` instance in the use case (as `SongSearchIndex`
does for search keys) would also take the remaining one normalization pass off a change; do it only if a profile
still shows `sortIntoSections`.

Visible change, checked: the picker adopts the songs screen's order (symbols and digits first, the title order
grouped by upper-case initial). `presentation/CLAUDE.md` already says the assignment sheets list "the songs in the order
the screen does", and the picker's KDoc says the same, so neither needs a change beyond the `pickerSongs` KDoc;
`SongPickerIndexTest` covers the chip counting (`pickerFilterOptions`) and `songPickerMatches` over a list it is
handed (an empty query keeps that list's order), never the order `pickerSongs` builds, so it passes unchanged.

## Tests

In `GetScreenDataUseCaseImplTest.kt` (pure, `:domain:implementation:desktopTest`):
- With no filter, `sortedSongs` equals `songs` for both sorting modes, including a song whose title starts with a
  digit and one starting with `¿` (both first).
- With a tag filter, `songs` is the subsequence of `sortedSongs` holding the tagged songs, in the same order.
- Toggling the filter with the same song list emits a `ScreenData` whose `sortedSongs` is the same instance as before
  (`assertSame`), proving the memo.

Run `./gradlew :domain:implementation:desktopTest :presentation:desktopTest`.

## Manual check

Open a setlist's Choose songs sheet: songs are listed in the same order as the Songs screen in both sorting modes
(compare a song titled `#1 Crush` or `¡Ay!`, which now lead both lists). Switch the sorting mode with the sheet closed
and reopen it: it follows. Ticking songs, searching in the sheet and its filter chips behave as before.
