# 45 · The library size on the settings screen lists the whole library from disk again on every change, from app start on, even when Settings is never opened

**Severity:** performance (all platforms, worst on the web with a large library. Every launch, whether or not Settings is opened: each progressive hand-over of the first scan (about six for 4,000 songs) queues another full listing of both folders. After that, every song save, tag or language tap, setlist change (each transposition tap inside a setlist), import and live sync rescan queues one more. On OPFS a listing awaits `getFile()` for every entry one after another (`FileStorage.wasmJs.kt:184-196`), on the one thread the web build has, alongside the scan it slows down. Plain `map` runs every queued listing to the end) · **Area:** `:presentation` (`CampfireViewModel.librarySummary`), `:data:model` (`Song`, `Setlist`), `:data:source:local:*` (song and setlist sources), `:data:repository:*`, `:domain:*` (the size use case goes). Also a KDoc that this commit moved onto the wrong function.

Two reviewers found this; merged into one plan. A misplaced KDoc of the same commit is folded in because it is in
the lines this fix deletes.

## Symptom
- A web user with a few thousand songs opens the app. While the first scan fills the list, the library is also
  listed in full after every hand-over the scan makes. The launch takes noticeably longer than before 2821f023.
- Five quick transposition taps in a setlist, or a run of tag taps, queue five full listings of both folders, run
  back to back.
- None of this depends on Settings being open, because the state is started eagerly.
- The misplaced KDoc: `loadLibrarySize()` sits between `emptyPlaceholder`'s KDoc and `emptyPlaceholder`, so the helper carries "What
  an empty list has in its place: …" and `emptyPlaceholder` is left with a KDoc that only has its `@param`.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:513-521`:

```kotlin
val librarySummary = screenData
    .map { state -> state.data?.let { it.unfilteredSongs to it.setlists } }
    .distinctUntilChanged()
    .map { library ->
        library?.let { (songs, setlists) ->
            LibrarySummary(songCount = songs.size, setlistCount = setlists.size, size = loadLibrarySize())
        }
    }
    .asState(null)
```

- `asState` is `stateIn(viewModelScope, SharingStarted.Eagerly, …)` (`:1863-1867`), so this runs from launch.
- The first scan hands the growing list over after the first batch of 64 and then each time the list has doubled
  (`SongLocalSourceImpl.loadSongs`, `SongLocalSourceImpl.kt:56-69`, through `publishPartialData`). Each hand-over
  is a new `unfilteredSongs`, so `distinctUntilChanged` lets it through.
- Every later write changes a `Song` (new `lastModified`) or a `Setlist`, so that passes too.
- `loadLibrarySize()` → `GetLibrarySizeUseCase` → `LibraryRepositoryImpl.loadLibrarySize()` →
  `LibraryFileLocalSource.loadLibraryFiles()` → `FileStorage.list` on both folders, a stat per file (`getFile()` per
  file on the web).
- `map` does not cancel, so the listings queue up.

The listing is not needed at all. The scan already gets every file's size from the same `FileStorage.list`
(`StoredFileInfo.size`, used by `SongLocalSourceImpl.readSong` and `SetlistLocalSourceImpl.loadSetlists`) and throws it
away. Every later song write re-reads that one file's `info()` (`loadSong`, called by `SongRepositoryImpl` after
every save, create, import and rename, `SongRepositoryImpl.kt:66-91`). A setlist write knows the text it wrote.

The misplaced KDoc: `CampfireViewModel.kt:1995-2008`. The KDoc block "What an empty list has in its place…" is followed by
`private suspend fun loadLibrarySize()` and then by a second KDoc holding only `@param isImporting`, above
`emptyPlaceholder`.

## Fix
Carry each file's size in its model, and add the sizes up in memory. No listing, no use case, no repository, and
nothing to cancel.

Two things change on purpose, and the executor should keep them:
- The figure now counts the files the library shows. A song over 8 MiB or one that cannot be read is already missing
  from the song count, and is now missing from the size too. The listing counted it. That is consistent, and those
  files are ones the app refuses to open.
- The size row is always there with the counts, since it can no longer fail on its own.

### 1. `:data:model`
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/Song.kt`, after `lastModified`:

```kotlin
    val lastModified: Long,
    /** The bytes the file takes up, as the scan listed it, which is what the library's size on the settings screen adds up. */
    val size: Long,
```

`Setlist.kt`, after `entries`:

```kotlin
    val entries: List<Entry>,
    /**
     * The bytes the file takes up, as the scan listed it or as the last write left it, which is what the library's
     * size on the settings screen adds up. 0 for a setlist that is not in the library yet: one parsed out of an import.
     */
    val size: Long,
```

No default values. Every construction has to say where the number comes from.

### 2. `:data:source:local:implementation`
- `mapper/SongMappers.kt`, `toSong`: add `size = size,` after `lastModified = lastModified,`. The receiver is the
  `StoredFileInfo`.
- `mapper/SetlistMappers.kt`: `internal fun SetlistDocument.toModel(fileName: String, size: Long) = Setlist(`… with
  `size = size,` after `entries = …,`.
- `source/SetlistLocalSourceImpl.kt`:
  - `loadSetlists`: `.toModel(file.name, size = file.size)`.
  - `loadSetlist`: the `size` it already reads from `info()` goes into `.toModel(fileName, size = size)`.
  - `createSetlist`: build the `Setlist` with `size = 0,` and end with `return saveSetlist(setlist)` instead of
    `saveSetlist(setlist)` / `return setlist`.
  - `saveSetlist`:

    ```kotlin
    /** Returns [setlist] carrying the size of what was written, since that is what the cache keeps from now on. */
    override suspend fun saveSetlist(setlist: Setlist): Setlist {
        val text = json.encodeToString(setlist.toDocument())
        fileStorage.writeText(directory = StorageDirectory.SETLISTS, name = setlist.fileName, text = text)
        // Every platform writes the text as UTF-8 with nothing before it, so this is the file's size without asking
        // the storage for it again.
        return setlist.copy(size = text.encodeToByteArray().size.toLong())
    }
    ```

  - `renameSetlist`: in the `isNamed` branch, `return saveSetlist(renamed)`. After the move,
    `return saveSetlist...` cannot be used, because `moveFile` calls the writer twice for a case-only move. Keep the
    `moveFile` block as it is (a lambda returning the `Setlist` is fine where `suspend (String) -> Unit` is expected),
    and capture the result:

    ```kotlin
    var moved = renamed
    fileStorage.moveFile(StorageDirectory.SETLISTS, currentName = setlist.fileName, newName = fileName) { name ->
        moved = saveSetlist(renamed.copy(fileName = name))
    }
    return moved.copy(fileName = fileName)
    ```

    The last write `moveFile` makes is always the one under `newName`, and the size does not depend on the name.
  - `parseSetlist`: `.toModel(setlistFileName(it.title), size = 0)`.
  - `importSetlist`: `.let { saveSetlist(it) }` instead of `.also { saveSetlist(it) }`.

### 3. `:data:source:local:api`
`SetlistLocalSource.kt`: `suspend fun saveSetlist(setlist: Setlist): Setlist` with the KDoc
`/** Writes [setlist] under the file name it carries and returns it carrying the size of the file it became. */`.
Add to `createSetlist`, `renameSetlist` and `importSetlist`'s KDoc that the returned setlist carries its file's size.
Keep it to a clause each, for example "…and returns it, carrying the size of its file."

### 4. `:data:repository:implementation`
`SetlistRepositoryImpl.kt`:
- `write` returns what was saved:

  ```kotlin
  /** Callers hold [writeMutex]. Returns the setlist as it was written, carrying its file's size. */
  private suspend fun write(setlist: Setlist): Setlist {
      val saved = setlistLocalSource.saveSetlist(setlist)
      updateData { current -> current.orEmpty().filterNot { it.fileName == saved.fileName } + saved }
      return saved
  }
  ```

- `override suspend fun saveSetlist(setlist: Setlist) { writing { write(setlist) } }`. This has to be a block body:
  the interface returns `Unit`, and an expression body would now infer `Setlist`.
- `updateSetlist`: `latest(fileName)?.let(transform)?.let { write(it) }`.
- Delete `LibraryRepositoryImpl.kt`.

### 5. `:data:repository:api`, `:domain:*`
Delete `data/repository/api/.../LibraryRepository.kt`, `domain/api/.../useCases/GetLibrarySizeUseCase.kt` and
`domain/implementation/.../useCases/GetLibrarySizeUseCaseImpl.kt`. They are annotation-scanned (`@Single`,
`@Factory`), so no `Module.kt` changes. The Koin compiler check fails the build if anything still asks for them.
`LibraryFileLocalSource.loadLibraryFiles` stays, since sync uses it.

### 6. `:presentation`
`CampfireViewModel.kt`:
- Remove the `GetLibrarySizeUseCase` import (`:58`) and the `private val getLibrarySize: GetLibrarySizeUseCase,`
  constructor parameter (`:136`).
- Replace `librarySummary` and its KDoc (`:505-521`) with:

  ```kotlin
  /**
   * Null until the library has actually been read, so that the settings screen never flashes a count of zero. The size
   * is added up from what the scan read off every file, so it costs no listing of its own and arrives in the same value
   * as the counts.
   */
  val librarySummary = screenData
      .map { state ->
          state.data?.let { data ->
              LibrarySummary(
                  songCount = data.unfilteredSongs.size,
                  setlistCount = data.setlists.size,
                  size = data.unfilteredSongs.sumOf { it.size } + data.setlists.sumOf { it.size },
              )
          }
      }
      .asState(null)
  ```

  Summing a few thousand longs on every change of the screen data, filter taps included, costs nothing next to
  what those changes already do. `asState` drops equal results, so the settings row only recomposes when a number
  changes.
- Delete `loadLibrarySize()` (`:2000-2008`), and merge the two KDoc blocks around it into one KDoc directly above
  `emptyPlaceholder` (this is the misplaced KDoc):

  ```kotlin
  /**
   * What an empty list has in its place: it is only an error once the load that would have filled it has actually
   * failed, and only [whenEmpty] once a load has finished - until then it is still loading, and saying anything
   * else would have the screen answer a question it cannot answer yet.
   *
   * @param isImporting An empty library with an import running is a library being filled rather than an empty
   *   one, and is worth the same answer as a scan that has not finished. It is what keeps the first launch of the
   *   app from flashing "Your library is empty" over the songs it is planting, and any import into an empty
   *   library from doing the same.
   */
  private fun DataState<ScreenData>.emptyPlaceholder(whenEmpty: Placeholder, isImporting: Boolean) = when {
  ```

- `LibrarySummary`: `val size: Long,` (not nullable), and change its `@param size` to
  `@param size The bytes the song and setlist files that were counted take up on disk.`
- Check that `CancellationException` and `distinctUntilChanged` are still used elsewhere in the file before
  leaving their imports. Both are used elsewhere at HEAD (`asState` uses `distinctUntilChanged`), so they most
  likely stay.

`screens/settings/SettingsScreen.kt` (`:415-424`):

```kotlin
    AnimatedSettingsRow(value = librarySummary) { summary ->
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(stringResource(Res.string.settings_library_summary, summary.songCount, summary.setlistCount)) },
            supportingContent = { Text(stringResource(Res.string.settings_library_size, formattedSize(summary.size))) },
        )
    }
```

No string changes.

### 7. Constructions the new fields break
Add `size = 0L,` (after `lastModified` or `entries`) to the test helpers:
- `SongRepositoryImplTest.kt:186`
- `GetScreenDataUseCaseImplTest.kt` (its `song(...)` at `:185` and the `Setlist(` just above it)
- `SetlistRepositoryImplTest.kt:266`
- `RenameTest.kt:65`
- `LibraryListingTest.kt:100`
- `SetlistMappersTest.kt:43`

`SetlistMappersTest.kt:37` also calls `document.toModel("summer.setlist.json")`: add `size = 0`.
`SetlistRepositoryImplTest`'s `FakeSetlistLocalSource.saveSetlist` (`:276`) must return a `Setlist`: return the
stored setlist, with a size of your choice such as `setlist.copy(size = 1)`. Fix anything else the compiler names
the same way.

Do **not**:
- use `mapLatest` plus a filter on `DataState.Loading` around the listing. That was the smaller fix both reviewers
  offered. It still lists the whole library on every save and tap. A JS `for await` loop cannot be cancelled from
  Kotlin, so superseded listings keep running in the browser. And a filter on `Loading` either hides the row during
  every rescan (an animation narrating nothing) or needs extra state to hold it.
- make the state `WhileSubscribed`. `asState`'s KDoc explains why a state that stops comes back stale, and the row
  would then change under the user's eyes as Settings opens.

## Tests
- `data/source/local/implementation/src/desktopTest/.../source/LibraryListingTest.kt` (or `RenameTest.kt`, whichever
  sets up a `SetlistLocalSourceImpl` over a `JvmFileStorage` in a temporary folder):
  - `a saved setlist carries the size of its file`: `saveSetlist(...)` returns a setlist whose `size` equals the
    written file's `length()`. The same holds after `renameSetlist`, both for a case-only title change and for a
    real move, and for the result of `loadSetlists()`.
  - `a scanned song carries the size of its file`: write two songs, `SongLocalSourceImpl.loadSongs {}` returns songs
    whose `size` equals each file's length.
- `SetlistRepositoryImplTest.kt`: `the cache keeps the size the write reported`. After `updateSetlist`, the setlist
  in `repository.setlists` carries the size the fake's `saveSetlist` returned.

Run `./gradlew :data:source:local:implementation:desktopTest :data:repository:implementation:desktopTest :domain:implementation:desktopTest`.

## Verify
1. Web (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`) with a large library (import a zip of a few thousand
   songs). Reload. In DevTools' Performance panel, the launch shows one listing of each folder (the scan's) rather
   than one per hand-over. Tap transpose in a setlist a few times and check that no listing follows.
2. Settings → Library: the size row shows with the counts, and grows after saving a longer version of a song or
   adding a song to a setlist. After deleting a song it shrinks.
3. Desktop: the figure matches `du --apparent-size -b library/songs library/setlists` to the shown precision (minus
   hidden files and any file over 8 MiB).
4. Compile `:app:desktop`, `:app:android:assembleDebug`, `:app:ios:linkDebugFrameworkIosSimulatorArm64` and
   `:app:web:wasmJsBrowserDistribution`. The Koin graph check runs in `:app:di` and must pass with the use case gone.

## Docs
- `data/repository/api/CLAUDE.md`: delete the `LibraryRepository` bullet (the two lines starting
  "- `LibraryRepository` — the library as files rather than as models").
- `data/repository/implementation/CLAUDE.md`: delete the `LibraryRepositoryImpl` bullet (the two lines starting
  "- `LibraryRepositoryImpl` sums the sizes").
- `data/repository/api/CLAUDE.md`, after "Everything else keeps the cached list in step by updating the one entry it
  changed, so writing a song does not cost a directory scan.": add "The entry a setlist write puts in is the one
  `saveSetlist` returns, carrying the size of the file it wrote, since the settings screen adds the library's size up
  from the models."
- `data/model/CLAUDE.md`, the `domain/` bullet: after "…because every row of the song list asks it)" add ", and the
  size of its file, which is what the settings screen adds up as the library's size". After "…what the setlists
  screen's search reads besides the title" add ", and the size of its file, for the same sum".
- `data/source/local/api/CLAUDE.md`, the `SetlistLocalSource` bullet, append: "Every write returns the setlist carrying
  the size of the file it became, so the cache never needs a listing to know it."

## Touches
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/Song.kt`, `Setlist.kt`
- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/SetlistLocalSource.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/SongMappers.kt`, `mapper/SetlistMappers.kt`, `source/SetlistLocalSourceImpl.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImpl.kt`
- deleted: `data/repository/api/.../LibraryRepository.kt`, `data/repository/implementation/.../LibraryRepositoryImpl.kt`, `domain/api/.../useCases/GetLibrarySizeUseCase.kt`, `domain/implementation/.../useCases/GetLibrarySizeUseCaseImpl.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SettingsScreen.kt`
- the tests listed in step 7 and under Tests
- `data/repository/api/CLAUDE.md`, `data/repository/implementation/CLAUDE.md`, `data/model/CLAUDE.md`, `data/source/local/api/CLAUDE.md`

## Depends on
Nothing. It covers the second report of the same issue and the misplaced KDoc, which need no plans of
their own. Any other plan that constructs `Song` or `Setlist`,
or changes `SetlistLocalSource.saveSetlist`, has to add the new `size` argument once this has landed.
