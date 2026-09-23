# 24 — One unreadable part of the library empties both list screens on the first read

**Severity:** a readable library shown as an error (all platforms, rare) · **Area:** `:domain:implementation` (`GetScreenDataUseCaseImpl.kt`), `:domain:api` (`ScreenData`), `:presentation` (`CampfireViewModel.kt`, three consumers)

**Read, not run.** This was found by reading `GetScreenDataUseCaseImpl` at HEAD (2065e47f); it has not been
reproduced in a running build. The "Verification" section below is how to confirm it, and confirming it is the first
step of the work.

## What the user sees

On a launch where the **first** read of one of the three inputs fails — the setlists directory cannot be listed, the
songs directory cannot be listed, or `preferences/preferences.json` cannot be read (permissions, a sync client
holding it locked on Windows, storage trouble on the web) — the Songs screen *and* the Setlists screen both show the
error placeholder, although the other parts were read fine. A preferences file that cannot be read hides the whole
library, when all it decides is the sort order.

`domain/implementation/CLAUDE.md` says of the initial load: "one unreadable part of the screen must not keep the rest
empty". `LoadScreenDataUseCaseImpl` keeps that promise (it waits for all reads, failures included); the combination
that follows breaks it.

On a later read (a rescan) the same failure is harmless: the use case's `cache` carries the last complete screen data.

## Cause

`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImpl.kt:65-103`:

```kotlin
        val songPart = combine(
            songRepository.songs,
            preferences.map { state -> state.mapData { it.toSongListPreferences() } }.distinctUntilChanged(),
            songFilter.distinctUntilChanged(),
        ) { songsDataState, songListPreferencesDataState, filter ->
            listOf(songsDataState, songListPreferencesDataState).combinedState(
                songsDataState.data?.let { songs ->
                    songListPreferencesDataState.data?.let { songListPreferences -> songs.toSongPart(songListPreferences, filter) }
                },
            )
        }
        val setlistPart = combine(
            setlistRepository.setlists,
            preferences.map { state -> state.mapData { it.setlistSortingMode } }.distinctUntilChanged(),
        ) { setlistsDataState, sortingModeDataState ->
            listOf(setlistsDataState, sortingModeDataState).combinedState(
                setlistsDataState.data?.let { setlists -> sortingModeDataState.data?.let { setlists.sortSetlists(it) } },
            )
        }
        return combine(setlistPart, songPart) { setlistsDataState, songsDataState ->
            val screenData = setlistsDataState.data?.let { setlists ->
                songsDataState.data?.let { songPart ->
                    ScreenData(
                        ...
                    ).also {
                        cache = it
                    }
                }
            }
            listOf(setlistsDataState, songsDataState).combinedState(screenData ?: cache)
        }.flowOn(Dispatchers.Default).distinctUntilChanged()
```

A failed first read is `DataState.Failure(null)` (`BaseLocalDataRepository.readOnce`,
`data/repository/implementation/.../base/BaseLocalDataRepository.kt:174-178`, `value = DataState.Failure(previousData)`
with nothing before it). Any `null` makes `screenData` null; `cache` is null on the first read; so the result is
`Failure(null)`. A preferences failure nulls *both* halves, since each needs a preference.

`presentation/.../ui/CampfireViewModel.kt:607-613` then shows `Placeholder.ERROR` on the Songs screen
(`data == null || data.unfilteredSongs.isEmpty() -> screenData.emptyPlaceholder(...)`, and `emptyPlaceholder` is
`ERROR` for a `Failure`, `:2163`), and `:670-673` does the same on the Setlists screen.

## The change

Invoke the **`code-style`** skill before the first edit.

A part whose **first** read failed stands in empty (songs, setlists) or at the defaults (preferences), so the parts
that were read can be shown; the combined state stays `Failure`, so the part that failed still says so. Only a
`Failure` with no data is filled in — never a `Loading(null)`: during the ordinary first load the three reads finish
at different moments, and filling in a part that is simply not there *yet* would show the songs sorted by the default
order and then re-sort them, or show the songs before the setlists have arrived (the "no incidental animations" rule
of `presentation`). With that restriction the new data can only ever appear together with a `Failure` state.

Because a value filled in like that is not the library, the few consumers that *decide something* from what the
library holds — plant the demo library into an empty one, offer the demo library, count the library — must be able to
tell. `ScreenData` gets a flag for it.

### `ScreenData` (`:domain:api`)

`domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/models/ScreenData.kt`, add after
`unfilteredSongs`:

```kotlin
    /**
     * False when the songs or the setlists could not be read at all and stand in here as empty, so that the part that
     * was read can still be shown - the state this arrives in is then a failure. Nothing that decides something from
     * what the library holds (whether it is empty, what it counts, whether the demo songs are in it) may take such a
     * value for the library.
     */
    val isWholeLibrary: Boolean = true,
```

(Preferences filled in at their defaults do not clear it: the library itself was read.)

### `GetScreenDataUseCaseImpl.kt`

1. The halves fill in a preference whose first read failed. Replace the two `preferences.map { ... }` inputs
   (`:70`, `:81`) with:

   ```kotlin
               preferences.map { state -> state.mapData { it.toSongListPreferences() }.orWhenUnreadable(DEFAULT_SONG_LIST_PREFERENCES) }
                   .distinctUntilChanged(),
   ```

   and

   ```kotlin
               preferences.map { state -> state.mapData { it.setlistSortingMode }.orWhenUnreadable(DEFAULT_SETLIST_SORTING_MODE) }
                   .distinctUntilChanged(),
   ```

2. The final combine fills in a part whose first read failed, without caching the result:

   ```kotlin
           return combine(setlistPart, songPart) { setlistsDataState, songsDataState ->
               val setlists = setlistsDataState.data
               val songPart = songsDataState.data
               val screenData = if (setlists != null && songPart != null) {
                   ScreenData(
                       setlists = setlists,
                       songs = songPart.songs,
                       songSections = songPart.songSections,
                       tags = songPart.tags,
                       languages = songPart.languages,
                       unfilteredSongs = songPart.unfilteredSongs,
                   ).also {
                       cache = it
                   }
               } else {
                   null
               }
               listOf(setlistsDataState, songsDataState).combinedState(
                   screenData ?: cache ?: partialScreenData(setlistsDataState, songsDataState),
               )
           }.flowOn(Dispatchers.Default).distinctUntilChanged()
   ```

   with

   ```kotlin
       /**
        * The part that was read, with the part whose first read failed standing in empty, so that one unreadable
        * directory does not keep the other one's screen empty. Null while either part is still being read for the
        * first time: filling that in would put something on screen that the real data then replaces. Never cached,
        * since it is not the library.
        */
       private fun partialScreenData(setlistsDataState: DataState<List<Setlist>>, songsDataState: DataState<SongPart>): ScreenData? {
           val setlists = setlistsDataState.data ?: emptyList<Setlist>().takeIf { setlistsDataState is DataState.Failure } ?: return null
           val songPart = songsDataState.data ?: EMPTY_SONG_PART.takeIf { songsDataState is DataState.Failure } ?: return null
           return ScreenData(
               setlists = setlists,
               songs = songPart.songs,
               songSections = songPart.songSections,
               tags = songPart.tags,
               languages = songPart.languages,
               unfilteredSongs = songPart.unfilteredSongs,
               isWholeLibrary = false,
           )
       }

       /** A value whose first read failed stands in as [default]; a value that is merely not read yet stays missing. */
       private fun <T> DataState<T>.orWhenUnreadable(default: T): DataState<T> =
           if (this is DataState.Failure && data == null) DataState.Failure(default) else this
   ```

   and in the companion (create one, `private companion object`):

   ```kotlin
           /** What `UserPreferencesDocument().toModel()` gives a device that has never saved any, see `:data:source:local`. */
           val DEFAULT_SONG_LIST_PREFERENCES = SongListPreferences(
               shouldShowSongsWithoutChords = true,
               sortingMode = UserPreferences.SortingMode.BY_ARTIST,
               tagMatchMode = UserPreferences.MatchMode.ANY,
               languageMatchMode = UserPreferences.MatchMode.ANY,
           )
           val DEFAULT_SETLIST_SORTING_MODE = UserPreferences.SetlistSortingMode.NEWEST_FIRST
           val EMPTY_SONG_PART = SongPart(songs = emptyList(), songSections = emptyList(), tags = emptyList(), languages = emptyList(), unfilteredSongs = emptyList())
   ```

   The defaults are copied from
   `data/source/local/implementation/src/commonMain/.../mapper/UserPreferencesMappers.kt:22-33` (`BY_ARTIST`,
   `NEWEST_FIRST`, `ANY`, `ANY`) and `UserPreferencesDocument.kt:23` (`shouldShowSongsWithoutChords = true`). The domain
   cannot reach that mapper, and `UserPreferences` has no defaults of its own; the KDoc says where they come from.
   `SongListPreferences` and `SongPart` are private classes of this file already (`:128`, `:345`), so the companion can
   see them.

   Notes:

   - A partial value only exists while `cache` is null (the first read), because `screenData ?: cache` wins otherwise.
     On a later failure the last complete library is still what is shown, as today.
   - `distinctUntilChanged` on the halves: a `Failure(default)` stays equal to itself, so a preference that keeps
     failing does not rebuild the song list.
   - `combinedState` (`:140-144`) is unchanged: any `Failure` input makes the result a `Failure`, carrying the partial
     data.

### `CampfireViewModel.kt` — the consumers that decide from the library

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`:

- `plantDemoLibraryOnFirstRun` (`~:1488-1500`): `library != null && library.unfilteredSongs.isEmpty() &&
  library.setlists.isEmpty()` becomes `library != null && library.isWholeLibrary && …` — an unreadable songs folder
  is not an empty library, and planting into it is exactly what the "Nothing is reported, not even a failure" first
  run must not do. (Today it cannot happen, because such a first read yields no data at all.)
- `demoLibraryOffer` (`:595-600`): `state.data?.takeIf { it.isWholeLibrary }?.let { DemoLibrary.isPresentIn(...) }` —
  no offer over a library that could not be read, as today.
- `librarySummary` (`:551-562`): `state.data?.takeIf { it.isWholeLibrary }?.let { data -> LibrarySummary(...) }` — no
  "0 songs" for a folder that could not be read, as today.
- `importDemoLibrary` (`:1467`) only asks `isPresentIn` and waits for the offer to go; a partial value answers
  "not present" and it does not wait — harmless, and it cannot reach there without the offer. No change.
- `navigateOnLaunch` (`:957-961`) resolves an opened address against the songs and setlists it gets; with a partial
  value an address naming a song in the unreadable half opens the songs, which is the documented answer for "an
  address naming nothing the library holds". No change.
- The placeholders (`:607-613`, `:670-673`) need no change: a half that stands in empty is empty *and* the state is a
  `Failure`, so that screen says `ERROR`; the other screen has data and shows it.

## Tests

In `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImplTest.kt`,
whose fakes are `MutableStateFlow`s of `DataState`s:

1. `an unreadable setlist folder does not hide the songs` — `setlists.value = DataState.Failure(null)` before
   collecting: the result is a `Failure` whose data holds both songs sorted (`["Hey Jude", "Yesterday"]`), no setlists,
   and `isWholeLibrary == false`.
2. `an unreadable song folder does not hide the setlists` — `songs.value = Failure(null)`: `Failure`, the setlists in
   order `["a", "b"]`, no songs, `isWholeLibrary == false`.
3. `unreadable preferences sort by the defaults` — `preferences.value = Failure(null)`: `Failure`, both parts present,
   songs sorted by artist (give the two songs different artists so the order proves it), setlists newest first,
   `isWholeLibrary == true`.
4. `a part that is still loading is not filled in` — `setlists.value = DataState.Loading(null)`: the result is
   `Loading(null)` (today's behaviour, pinned so that nobody "fixes" the first load into flashing).
5. `a failure after a complete read carries the complete library` — collect to `idle()`, then
   `setlists.value = Failure(null)`: the `Failure` carries the earlier complete value (`assertSame(last, …)`), with
   `isWholeLibrary == true`.
6. `a partial value is never cached` — start with `setlists = Failure(null)`, collect the partial value, then set
   `songs.value = Loading(null)`: the result is `Failure(null)` — the songs half is not read yet, so nothing is filled
   in, and nothing partial was cached to stand in for it. Then set both idle: `Idle` with `isWholeLibrary == true`;
   then `setlists.value = Failure(null)` again: the `Failure` carries that complete value (it is the cache now).

Run `./gradlew :domain:implementation:desktopTest` and compile `:presentation` for one target.

## Verification

Desktop, macOS or Linux:

1. With the app closed, `chmod 000 "~/Library/Application Support/Campfire/library/setlists"` (Linux:
   `~/.local/share/…`, whatever the desktop data directory is on that system — see `app/desktop/CLAUDE.md`).
2. Start the app.
   - **Before:** both list screens show the error placeholder.
   - **After:** the Songs screen lists the songs; the Setlists screen shows the error placeholder; Settings shows no
     library summary line and no demo offer.
3. `chmod 755` it back, use the refresh action: both screens are normal, the summary comes back.
4. Same with `chmod 000 preferences/preferences.json`: before, both screens error; after, both lists appear in the
   default orders (and the theme / language are the defaults — that part is plan 33's, lane D).
5. Same with the songs folder: the setlists appear, their entries shown as missing (the song list is empty), the Songs
   screen shows the error placeholder.
6. First run check (the dangerous one): an empty data directory whose `library/songs` exists but is unreadable, no
   `preferences.json`. Start the app: no demo library is planted (look at the folder after `chmod 755`).

## Docs

- `domain/implementation/CLAUDE.md`, the `GetScreenDataUseCaseImpl` bullet: after "keeps a `cache` so that a
  `Loading` or `Failure` state can still carry the last good data", add "— and where there is none yet, a part whose
  first read failed stands in empty (songs, setlists) or at the defaults (preferences), so the part that was read is
  still shown; such a value says so (`ScreenData.isWholeLibrary`) and is never cached. A part that is merely still
  loading is never filled in." The `LoadScreenDataUseCaseImpl` sentence ("one unreadable part of the screen must not
  keep the rest empty") is then true of the whole path.
- `domain/api/CLAUDE.md`, the `ScreenData` bullet: add "`isWholeLibrary` is false for a value in which an unreadable
  part stands in empty; the demo library and the library counts ask it."
- `presentation/CLAUDE.md`: if it describes the demo library's first-run check or the library summary, add that both
  need a whole library (grep "demo" / "LibrarySummary" before writing).

## Files touched

- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/models/ScreenData.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImpl.kt`
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImplTest.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `domain/implementation/CLAUDE.md`, `domain/api/CLAUDE.md` (and `presentation/CLAUDE.md` if it applies)

## Depends on

- Related to **33** (lane D, launch screen stuck on a preferences failure), which is about the theme/language path
  (`GetUserPreferencesUseCase`), not this one; both touch `CampfireViewModel.kt` in different places. Either order.
- `CampfireViewModel.kt` is edited by several plans (17, 23, lane D); the three edits here are one-line `takeIf`s.
- `GetScreenDataUseCaseImplTest.kt` gains a method in its `FakeSetlistRepository` from plan 23 and one in its
  `FakeSongRepository` from plan 26; merge.
