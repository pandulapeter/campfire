# 43 · With a large library every setlist tick, reorder or transposition step re-sorts the whole song list, and the first scan sorts it once per 64 files read

**Severity:** performance (all platforms; felt on the web, where `Dispatchers.Default` is the page's only thread, and on
low-end Android; grows with the library — negligible at a few hundred songs, measured below at 10,000) · **Area:**
`:domain:implementation` (`GetScreenDataUseCaseImpl`), `:data:source:local:implementation` (`SongLocalSourceImpl`)

## Symptom
With a library of several thousand songs:
1. In the song picker of a setlist, tick songs quickly; or drag songs around in a setlist; or press transpose
   repeatedly on a song opened from a setlist. Each of those is one setlist write, and each write rebuilds the whole
   song list — normalize every title and artist, filter, count tags and languages, sort, cut into sections. On the
   web that runs on the UI thread and the ticks stutter.
2. Start the app: while the library is being read, the same rebuild runs once per batch of 64 files, over a list that
   grows by 64 each time — quadratic in the size of the library.

Measured on the JVM (desktop, warm JIT, M-series Mac; throwaway benchmark over `GetScreenDataUseCaseImpl` with fake
repositories, 10,000 songs of random two-word titles, one tag and one language each):
- one full rebuild: **7.4 ms**;
- the 156 partial publishes of a first scan: **494 ms** in total.

Kotlin/Wasm is several times slower than a warm JVM for this kind of code and has no second thread, so on the web
that is a dropped frame or two per setlist tick and roughly a second or more of the UI thread during the first scan,
on top of the reading itself and of the view model's own derived states, which run per emission too.

## Cause
1. `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImpl.kt:57-114`
   is one `combine` of four inputs whose transform rebuilds everything:

   ```kotlin
   override operator fun invoke(songFilter: Flow<SongFilter>) = combine(
       setlistRepository.setlists,
       songRepository.songs,
       userPreferencesRepository.userPreferences.map { state -> state.mapData { it.toListPreferences() } }.distinctUntilChanged(),
       songFilter.distinctUntilChanged(),
   ) { setlistsDataState, songsDataState, listPreferencesDataState, filter ->
       fun createScreenData() = … songs.filterHasChords(…) … toTags() … toLanguages() … sortIntoSections(…) …
   ```

   A setlist write emits a new `setlists` state with the *same* songs, and the whole song half is recomputed for it.
   `ListPreferences` (`:294-299`) also mixes `setlistSortingMode` into the song half, so changing how setlists are
   sorted re-sorts the songs too. Review 1 plan 45 removed the same waste from the view model side
   (`filteredSongs.distinctUntilChanged()`), not from here.
2. `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SongLocalSourceImpl.kt:55-60`
   publishes after every batch but the last:

   ```kotlin
   batches.forEachIndexed { index, batch ->
       songs += batch.map { async { it.readSong() } }.awaitAll().filterNotNull()
       if (index < batches.lastIndex) onProgress(songs.toList())
   }
   ```

   Each publish is a `DataState.Loading(partial)` that the use case above turns into one full rebuild of a list of
   `64 × k` songs, for `k = 1 … n/64`. On the JVM the `StateFlow` conflation absorbs some of that while the scan
   outruns the rebuild; on the web the two share one thread and nothing is skipped.

## Fix
1. `GetScreenDataUseCaseImpl`: split the one `combine` into two halves and combine the halves.
   - `ListPreferences` becomes `SongListPreferences(shouldShowSongsWithoutChords, sortingMode, tagMatchMode)`; the
     setlist half needs only `setlistSortingMode` (plus `normalizeText` for `BY_TITLE`).
   - Song half: `combine(songRepository.songs, songListPreferences, songFilter.distinctUntilChanged()) { … }` producing a
     private `SongPart(songs, songSections, tags, languages, unfilteredSongs)` wrapped in the `DataState` it was
     built from (Failure if either input failed, Loading if either is loading, else Idle; `null` data where the inputs
     have none), with its own `@Volatile` last-good cache exactly as `cache` works today.
   - Setlist half: `combine(setlistRepository.setlists, setlistSortingMode) { … sortSetlists … }` → `DataState<List<Setlist>>`.
   - Final: `combine(setlistHalf, songHalf) { setlists, songs -> … }` assembling `ScreenData` with the same
     Failure/Loading/Idle precedence and the same fallback on `cache` as `:106-113`, then
     `.flowOn(Dispatchers.Default).distinctUntilChanged()` as now. Put `flowOn` on the final flow only; the halves
     run in the same context.
   - Keep every ordering rule, the "counted over the other group" rule and `withMissingSelected` exactly as they are;
     this is a move, not a rewrite. The `IllegalStateException` at `:112` stays reachable only as it is now (never,
     since `Idle` always carries data).
2. `SongLocalSourceImpl.loadSongs`: publish geometrically instead of every batch — after the first batch (so the list
   still appears after 64 files) and then each time the list has at least doubled since the last publish:

   ```kotlin
   var publishedCount = 0
   batches.forEachIndexed { index, batch ->
       songs += …
       if (index < batches.lastIndex && songs.size >= publishedCount * 2) {
           publishedCount = songs.size
           onProgress(songs.toList())
       }
   }
   ```

   The total work of the partial rebuilds is then under two full rebuilds whatever the library size (~15 ms instead of
   ~494 ms in the measurement above), and a library of a few hundred songs publishes exactly as today (64, 128, 256).
   Update the KDoc of `loadSongs` ("each finished batch is handed to the caller" → "the list is handed to the caller
   after the first batch and then each time it has doubled").

Do **not**:
- throttle by time (`sample`, a clock): the web has no second thread for a timer to be fair on, and tests would need a
  virtual clock for a rule that a count expresses exactly;
- memoize inside `sortIntoSections` by list identity: the repository hands out a new list on every change, so a
  cache keyed by it never hits, and one keyed by content costs what it saves;
- drop the partial publishes altogether — a library of thousands would show nothing for seconds, which is what review 1
  put them in for.

## Tests
`:domain:implementation` `commonTest` (`./gradlew :domain:implementation:desktopTest`), a new
`GetScreenDataUseCaseImplTest` with three fake repositories whose flows are `MutableStateFlow`s (every other member
`throw UnsupportedOperationException()`), `UserPreferences` built once in the test:
- `a setlist change does not rebuild the song list`: collect two emissions, the second after only the setlists
  changed; assert that `songSections` of both are the **same instance** (`assertSame`).
- `the setlists follow their own sorting mode without the songs being rebuilt`: change only `setlistSortingMode`;
  setlists reordered, `songSections` the same instance.
- the existing behaviour, as a regression guard: Failure beats Loading beats Idle, and a Loading with no data carries
  the last good `ScreenData`.

`:data:source:local:implementation` `desktopTest` (the JVM storage is already tested there, over a temporary
directory): `a scan of 1,000 songs publishes after 64, 128, 256 and 512 songs` — write 1,000 minimal `.cho` files,
collect the sizes handed to `onProgress`, assert `[64, 128, 256, 512]` and that the return value has 1,000.

## Verify
1. Web: import (or generate) 5,000–10,000 songs (`for i in $(seq 1 10000); do printf '{title: Song %s}\n{artist: A%s}\n[C]x\n' $i $((i%300)) > s$i.cho; done; zip lib.zip s*.cho`), import the zip,
   reload. DevTools → Performance: record the reload; before, the flame chart shows `sortIntoSections` once per 64
   files; after, about log₂(n/64)+1 times. Then open a setlist's song picker and tick ten songs quickly: before, each
   tick shows a long task of the rebuild; after, none.
2. Desktop and Android with the same library: the scan fills the list in a handful of steps; ticking in the picker is
   smooth; sorting and filtering (by title, by artist, a tag, a language, "songs without chords") give exactly the
   same lists and sections as before.
3. Change the setlist sorting mode: the setlists reorder, the song list does not flicker or scroll.

## Docs
`domain/implementation/CLAUDE.md`, the `GetScreenDataUseCaseImpl` bullet: after "combines the setlist, song and
preference flows, and the `SongFilter` flow the caller passes in, into one `Flow<DataState<ScreenData>>`" add "— in two
halves, songs and setlists, combined at the end, so that a setlist being written does not filter and sort the whole
library again". `data/source/local/implementation/CLAUDE.md`, the `source/` bullet: "each finished batch is handed to
the caller" → "the list is handed to the caller after the first batch and then whenever it has doubled, so that the
screen is rebuilt a handful of times rather than once per batch".
`data/repository/implementation/CLAUDE.md` says "publish them with `publishPartialData`, which is what the song scan does
with each batch" → "with the batches it has parsed".

## Touches
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImpl.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SongLocalSourceImpl.kt`
- the three `CLAUDE.md` files above

## Depends on
Nothing.
