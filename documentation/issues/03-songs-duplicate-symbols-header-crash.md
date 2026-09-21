# 03 · Sorting the songs by title crashes the start screen, on every launch, once one title starts with a digit and another with `¿`, `…`, a curly quote or an emoji

**Severity:** crash (all platforms; a launch loop, since Songs is the start screen and the sorting mode is persisted — likely in any library with a Spanish song, a title typed on a phone with smart punctuation, or an emoji) · **Area:** `:domain:api` (`ScreenData`), `:domain:implementation` (`GetScreenDataUseCaseImpl`), `:presentation` (`CampfireViewModel`, `SongsScreen`)

## Symptom
1. Set the Songs screen's sorting to **By title**.
2. Have one song whose title starts with a digit or ASCII punctuation (`7 Years`, `99 Luftballons`,
   `(I Can't Get No) Satisfaction`, `...Baby One More Time`) and one whose title starts with a character that is
   not a letter and sorts after `z`: `¿Dónde estás?`, `¡Ay, Carmela!`, `…And Justice for All` (phone keyboards turn
   `...` into `…`), `’Round Midnight`, `“Heroes”`, `„Idézet”`, an emoji.
3. Have few enough songs, or a wide enough window (3–4 grid columns on a tablet or the desktop), for the first and the
   last section to be on screen together — a new user with a dozen songs is enough.

The app dies with `IllegalArgumentException: Key "header_Symbols" was already used. If you are using LazyColumn/Row
please make sure you provide a unique key for each item.` The sorting mode is a saved preference and Songs is the
bottom of every back stack, so every launch crashes again; on Android the only way out is clearing the app's data,
which is the library. With a longer list the two headers are never composed together, but both items carry one key,
so after a library change the grid can "follow" its first visible key to the other end of the list.

Two lesser ways to the same exception come from the same code and go away with the same fix (neither was run, both
are read off the code):
- Two lowercase initials with one uppercase form: a title starting with `ı` (dotless i, not in the accent table)
  sorts after `z` but is filed under `I` like the `i` titles → two `header_Letter(letter=I)` items. Likewise `ſ`/`s`.
- Switching the sorting mode on a large library. The list is sorted on `Dispatchers.Default` by the domain layer
  but cut into sections on the main thread by the ViewModel, each from its own copy of the preference. Between the
  preference reaching the ViewModel and the re-sorted list arriving, `songGroups` holds a list in one order cut into
  the sections of the other — by-artist order cut by title initial is a duplicate letter every few rows. A frame
  that lands in that window (the longer the sort, the likelier: thousands of songs on a slow phone) composes it.

## Cause
The order and the sections are decided in two places that do not agree on what a section is.

`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImpl.kt:208-216`
sorts by the plain `String` order of the normalized text:

```kotlin
val comparator = when (listPreferences.sortingMode) {
    UserPreferences.SortingMode.BY_ARTIST -> compareBy<SortableSong>({ it.artist }, { it.title })
    UserPreferences.SortingMode.BY_TITLE -> compareBy<SortableSong>({ it.title }, { it.artist })
}
```

`NormalizeTextUseCaseImpl` only trims, lowercases and folds accents, so punctuation, digits and emoji stay, and in
UTF-16 order they sit on **both** sides of the alphabet (`0x21–0x60` before `a`; `¡ ¿ « …  ‘ ’ “ ” „` and every
surrogate after `z`).

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:1586-1610` then
merges only **consecutive** songs with an equal key, and gives every non-letter run the same header object:

```kotlin
UserPreferences.SortingMode.BY_TITLE -> song.title.initialLetter()?.toString().orEmpty()      // "" for every non-letter
…
if (lastGroup != null && key == lastKey) { lastGroup.second += song.song } else {
    …
    UserPreferences.SortingMode.BY_TITLE -> key.firstOrNull()?.let { SongGroup.Header.Letter(it) } ?: SongGroup.Header.Symbols
```

and the mode it groups by is not the mode the list was sorted by, but the ViewModel's own reading of the preference
(`CampfireViewModel.kt:460`):

```kotlin
val songGroups = combine(searchableSongs, songsSearch.activeQuery, userPreferences.map { it?.sortingMode }.distinctUntilChanged()) { songs, query, sortingMode ->
```

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt:317` keys
the sticky header by the header's `toString()`:

```kotlin
stickyHeader(key = "header_$header", …)      // "header_Symbols" for each of those runs
```

What was checked and is sound, so that nobody goes looking again:
- **By artist** the grouping key *is* the primary sort key (the normalized artist), so its runs are contiguous, the
  empty artist ("Unknown artist", also an artist of blanks or of combining marks only) is one run at the top, and
  two different keys cannot share a display name because the key is a function of the name. The only way to a
  duplicate `header_Artist(…)` is the mode race above. What by-artist does get wrong is cosmetic: artists starting
  with a symbol land on both sides of the alphabet (`2Pac` first, `¡Forward, Russia!` last), so the fast scroller's
  bubble says `#` at both ends.
- **The Setlists screen** keys its headers, descriptions and "add songs" rows by the setlist's file name and its rows
  by setlist + song file name (entries are deduplicated in `SetlistMappers`); it has no letter headers and passes the
  fast scroller no labels. Nothing to fix there.
- **The fast scroller** has no index of its own: `labelForItem` maps an item index to the label of its section
  (`SongsScreen.kt:247-252`), so a repeated label is harmless to it.
- **Non-Latin initials**: `Char.isLetter()` is true for Cyrillic, Greek, Hebrew, CJK…, so such a title is filed under
  its own first character, after `Z`. That is right and stays.

## Fix
Decide the order and the sections in one function, from one key, in the domain layer; make a repeated header
impossible by construction; key the lazy item by that key. Steps 1–3 are one change (the build breaks in between).

1. **New file** `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/models/SongSection.kt` (MPL
   header copied from `ScreenData.kt`):

   ```kotlin
   package com.pandulapeter.campfire.domain.api.models

   import com.pandulapeter.campfire.data.model.domain.Song

   /**
    * One section of the sorted song list: the songs filed under one header, in the order the list shows them.
    *
    * The sections are cut by whoever sorts the list, see [ScreenData.songSections], because the two only work
    * together: a header comes up once in a list only for as long as the order keeps everything filed under it in one
    * run.
    */
   data class SongSection(
       val header: Header,
       val songs: List<Song>,
   ) {

       sealed interface Header {

           /**
            * What tells this header from every other one of the same list, and stays what it is while songs come and
            * go under it - which is what a lazy list asks of an item's key.
            */
           val key: String

           /**
            * @param name The artist as the first song of the section spells it, which may be blank.
            * @param initial The first letter of the name in upper case, null where the name starts with anything else.
            */
           data class Artist(val name: String, val initial: Char?, override val key: String) : Header

           data class Letter(val letter: Char) : Header {
               override val key get() = letter.toString()
           }

           /** Every title that starts with something other than a letter: a digit, punctuation, an emoji. */
           data object Symbols : Header {
               override val key = ""
           }
       }
   }
   ```

2. `domain/api/.../models/ScreenData.kt`: add the sections next to `songs` (keep `songs`: the placeholders and the
   search read the flat list).

   ```kotlin
       /** The library, narrowed by the [SongFilter] and the preferences and sorted the way the preferences ask for. */
       val songs: List<Song>,
       /**
        * The same songs in the same order, cut into the sections that order is listed under. It travels with [songs]
        * rather than being worked out from it by the caller, since a caller would have to know which order the list it
        * holds was sorted in, and the preference it would ask has usually moved on before the list has.
        */
       val songSections: List<SongSection>,
   ```

3. `GetScreenDataUseCaseImpl.kt`: replace `sortSongs` and `SortableSong` (lines 204–222) with the following, and in
   `createScreenData()` build both fields from one call:

   ```kotlin
   val songSections = songsByTag
       .filterLanguages(filter, availableLanguages)
       .sortIntoSections(listPreferences)
   ScreenData(
       setlists = setlists,
       songs = songSections.flatMap { it.songs },
       songSections = songSections,
       …
   ```

   ```kotlin
       /**
        * The songs in the order the preferences ask for, cut into the sections that order is listed under.
        *
        * Both come from [SortableSong.sectionKey]: it is what the songs are ordered by before anything else and the only
        * thing they are filed by, so a section is one run of the list, and collecting the runs in a map keyed by it means
        * that no header can come up twice whatever a title starts with. The keys are computed once per song, since the
        * selector of a comparator runs on every comparison.
        */
       private fun List<Song>.sortIntoSections(listPreferences: ListPreferences): List<SongSection> {
           val sections = linkedMapOf<String, MutableList<SortableSong>>()
           map { SortableSong(song = it, sortingMode = listPreferences.sortingMode, artist = normalizeText(it.artist), title = normalizeText(it.title)) }
               .sortedWith(SortableSong.ORDER)
               .forEach { sections.getOrPut(it.sectionKey) { mutableListOf() } += it }
           return sections.map { (key, songs) ->
               val first = songs.first()
               SongSection(
                   header = when (listPreferences.sortingMode) {
                       UserPreferences.SortingMode.BY_ARTIST -> SongSection.Header.Artist(name = first.song.artist, initial = first.initial, key = key)
                       UserPreferences.SortingMode.BY_TITLE -> first.initial?.let { SongSection.Header.Letter(it) } ?: SongSection.Header.Symbols
                   },
                   songs = songs.map { it.song },
               )
           }
       }

       /** @param artist Normalized, like [title]. */
       private class SortableSong(
           val song: Song,
           sortingMode: UserPreferences.SortingMode,
           artist: String,
           title: String,
       ) {
           /** The text the list is sorted by, and the one that orders the songs the first one ties. */
           val primaryText = if (sortingMode == UserPreferences.SortingMode.BY_ARTIST) artist else title
           val secondaryText = if (sortingMode == UserPreferences.SortingMode.BY_ARTIST) title else artist

           /** The upper case first character of [primaryText] where that is a letter, of any script. */
           val initial = primaryText.firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()

           /**
            * By artist a section is an artist; by title it is an initial, the empty key standing for every title that
            * has none. The upper case initial rather than the first character: two lower case letters can share an
            * upper case one (`ı` and `i`), and they share a header then.
            */
           val sectionKey = if (sortingMode == UserPreferences.SortingMode.BY_ARTIST) artist else initial?.toString().orEmpty()

           companion object {

               /**
                * Whatever starts with something other than a letter comes first, in either order. Left to the order of
                * the strings those texts land on both sides of the alphabet - a digit sorts before `a`, while `¿`, `…`,
                * a curly quote and every emoji sort after `z` - which is two runs under one header.
                */
               val ORDER = compareBy<SortableSong>({ it.initial != null }, { it.sectionKey }, { it.primaryText }, { it.secondaryText })
           }
       }
   ```

   `initial != null` is a function of `sectionKey` in both modes, so equal section keys are always adjacent. Plan 35
   appends `{ it.song.fileName }` as the last selector of `ORDER`; it is one comparator for both modes on purpose, so
   that there is one place to append to.

4. `CampfireViewModel.kt`:
   - add `import com.pandulapeter.campfire.domain.api.models.SongSection`;
   - delete `groupIntoSections` and `initialLetter` (1582–1610) and the nested `SongGroup.Header` (1718–1723);
     `SongGroup` becomes

     ```kotlin
         /** @param header Null for the results of a search, which are ranked rather than filed under anything. */
         data class SongGroup(
             val header: SongSection.Header?,
             val songs: List<Song>,
         )
     ```
   - next to `filteredSongs` (408) add

     ```kotlin
         /** [filteredSongs] as the domain layer cut it into sections, distinct for the same reason. */
         private val songSections = screenData.map { it.data?.songSections.orEmpty() }.distinctUntilChanged()
     ```
   - replace `songGroups` (458–468, its "Distinct on the sorting mode alone" comment goes with the parameter):

     ```kotlin
         // The sections arrive cut, from the same pass that sorted them. Cutting them here would take the sorting mode
         // from the preferences, which change before the list sorted by them arrives.
         val songGroups = combine(songSections, searchableSongs, songsSearch.activeQuery) { sections, songs, query ->
             if (query.isBlank()) {
                 sections.map { SongGroup(header = it.header, songs = it.songs) }
             } else {
                 // (existing comment and branch, unchanged)
                 songs.filterAndRank(query).takeIf { it.isNotEmpty() }?.let { listOf(SongGroup(header = null, songs = it)) }.orEmpty()
             }
         }.asState(emptyList())
     ```
     Each branch reads exactly one of the two lists, so the pair being momentarily out of step is harmless.
   - `normalizeText` stays (the search uses it). Update the class KDoc sentence about `songGroups` if it says the
     headers are computed in the ViewModel.

5. `SongsScreen.kt`: import `SongSection`, replace the six `CampfireViewModel.SongGroup.Header.` references (322–326,
   393–397) with `SongSection.Header.`, and key the header by the section key:

   ```kotlin
   stickyHeader(
       key = "header_${header.key}",
       contentType = "header",
   ) { headerIndex ->
   ```
   The key keeps its `header_` prefix (rows are `song_…`, the placeholder is `placeholder`). Do **not** key the
   header by the first song's file name, as the review suggested: that key changes whenever a song is added at the
   top of a section, which makes the grid treat the header as a new item (it fades instead of staying put, and the
   "keep the first visible key in place" logic loses it). The section key is as unique — it is a map key — and stable.

Do not touch `NormalizeTextUseCaseImpl` (search and file naming share its table) and do not strip punctuation before
sorting: `(I Can't Get No) Satisfaction` belongs under "0 - 9" today and stays there.

## Tests
None (`:domain:*` and the UI are untested). `sortIntoSections` is pure apart from `normalizeText`; if
`:domain:implementation` ever gets a test source set, the cases are: titles `["7 years", "abba", "¿donde?", "zz top",
"😀"]` by title → sections `Symbols[7 years, ¿donde?, 😀], A, Z`; `["ırmak", "island"]` → one `I` section; by artist
with artists `["", "2pac", "abba", "¡forward"]` → `["", "2pac", "¡forward", "abba"]`, four sections, distinct keys.

## Verify
1. `./gradlew :app:desktop:run`. Create songs titled `7 Years`, `¿Dónde estás?`, `…And Justice`, `Abba`, `Zebra`,
   `ırmak`, `Island`, and one with an emoji first. Sort by title: before the fix the app crashes as soon as the list
   is shown; after it there is one "0 - 9" section at the top holding all four symbol titles, then `A`, `I` (both
   songs), `Z`. Restart: still there.
2. Sort by artist with artists ``, `2Pac`, `¡Forward, Russia!`, `ABBA`: "Unknown artist", `2Pac`, `¡Forward, Russia!`
   come first in that order, the fast scroller bubble says `#` for all three and never again further down.
3. Add a Cyrillic title (`Катюша`): it is under `К`, after `Z`.
4. Flip the sorting mode back and forth quickly with the demo library plus a few dozen imported songs: no crash, the
   list scrolls to the top each time, headers animate as before.
5. Search: results have no headers, as before.
6. Compile checks: `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.

## Docs
- `domain/api/CLAUDE.md`, the `ScreenData` bullet: after "the songs filtered and sorted the way the preferences ask
  for" add "and the same list cut into its sections (`songSections`, `SongSection`: an artist or an initial per
  header, the texts that start with no letter first) — cut where it is sorted, so that a header cannot come up twice".
- `domain/implementation/CLAUDE.md`, the `GetScreenDataUseCaseImpl` bullet: "the sorting (by title or artist, through
  `NormalizeTextUseCase`, so accents are ignored)" becomes "the sorting and the sections it is listed under (by title
  or artist, through `NormalizeTextUseCase`, so accents are ignored; one key decides both, and whatever starts with
  no letter comes first)".
- `presentation/CLAUDE.md`, the `CampfireViewModel` paragraph: "the grouped song list (`songGroups`, section headers
  computed here so the UI never needs `NormalizeTextUseCase`)" becomes "the grouped song list (`songGroups`: the
  sections `ScreenData` arrives cut into, or one headerless group of ranked results while a search is on)".

## Touches
- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/models/SongSection.kt` (new)
- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/models/ScreenData.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImpl.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt`
- `domain/api/CLAUDE.md`
- `domain/implementation/CLAUDE.md`
- `presentation/CLAUDE.md`

## Depends on
Nothing. Plan 35 (tie-breakers) edits the same comparator and must land **after** this one: it appends
`{ it.song.fileName }` to `SortableSong.ORDER` instead of to the two comparators it was written against. Plan 36
(duplicate `song_…` key after a double create) touches the same `LazyVerticalGrid` but a different key.
