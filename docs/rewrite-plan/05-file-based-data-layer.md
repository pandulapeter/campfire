# Step 05: the file-based data layer

**Goal:** replace Room / `localStorage` with the `.cho` library on `FileStorage` (step 02) and the `:chordpro`
parser (step 01). After this step, `.cho` files copied into the library folder show up in the Songs list and open in
the (still old-style) song details screen; setlists and preferences persist as files. Room, the hierarchy templates,
KSP and the wasm `localStorage` code are gone.

**Depends on:** 01, 02, 04.

## 1. Models (`:data:model`)

Replace the domain models:

```kotlin
data class Song(
    val fileName: String,      // identity; "Oasis - Wonderwall.cho"
    val title: String,         // {title}, else file name without extension
    val artist: String,        // {artist}, else {subtitle}, else ""
    val key: String?,          // {key}
    val hasChords: Boolean,
    val lastModified: Long
)

data class Setlist(
    val fileName: String,      // "friday-gig.setlist.json"
    val title: String,
    val priority: Int,         // newest first, as today
    val entries: List<Entry>
) {
    data class Entry(val songFileName: String, val transposition: Int = 0)
}

data class UserPreferences(
    val shouldShowSongsWithoutChords: Boolean,
    val isLyricsOnlyModeEnabled: Boolean,
    val isHorizontalSectionFlowEnabled: Boolean,
    val fontScale: Float,
    val sortingMode: SortingMode,
    val uiMode: UiMode,
    val language: Language,
    val transpositions: Map<String, Int>   // song file name -> semitones, for songs opened from the library
)
```

Delete `RawSongDetails.kt` and `TranspositionKey.kt`. Keep `DataState.kt`.

Add `data/model/src/commonMain/.../domain/SongContent.kt`:
```kotlin
data class SongContent(val fileName: String, val text: String)
```

Add `:chordpro` as an `api` dependency of `:data:model`? **No.** Keep `:data:model` free of it; the local source
implementation depends on `:chordpro` directly (see 3) and the presentation module gets it in step 06.

## 2. Local source APIs (`:data:source:local:api`)

Replace the existing interfaces with:

```kotlin
interface SongLocalSource {
    suspend fun loadSongs(): List<Song>                       // scans SONGS, parses metadata of every .cho
    suspend fun loadSongContent(fileName: String): SongContent? // null if missing
    suspend fun saveSongContent(content: SongContent)         // create or overwrite
    suspend fun deleteSong(fileName: String)
    suspend fun renameSong(from: String, to: String)
    suspend fun exists(fileName: String): Boolean
}

interface SetlistLocalSource {
    suspend fun loadSetlists(): List<Setlist>
    suspend fun saveSetlist(setlist: Setlist)
    suspend fun deleteSetlist(fileName: String)
}

interface UserPreferencesLocalSource {
    suspend fun loadUserPreferences(): UserPreferences?
    suspend fun saveUserPreferences(userPreferences: UserPreferences)
}
```

Delete `DatabaseLocalSource` (gone in 04), `RawSongDetailsLocalSource`, `TranspositionLocalSource`.

## 3. Local source implementation (`:data:source:local:implementation`)

Build file:
- Remove the `applyHierarchyTemplate { … }` block, the `roomMain` dependencies, the `ksp` plugin and the four
  `add("ksp…", libs.androidx.room.codegen)` lines. Keep `kotlin-serialization` (needed for JSON) and add
  `implementation(libs.kotlin.serialization.json)` to `commonMain`. Add `implementation(project(":chordpro"))` to
  `commonMain`. Keep `wasmJsMain.dependencies { implementation(libs.kotlin.browser) }` (OPFS interop from step 02).
- Move the `roomMain` `Module.kt` content that survives into `commonMain/Module.kt` and turn `dataLocalSourceModule`
  from `expect val` into a plain `val`. Delete the `roomMain` and the wasm `localStorage` directories completely
  (`model/`, `mapper/`, `source/`, `storage/StorageManager*.kt`, `storage/dao/`).
- If step 02 duplicated the JVM `FileStorage` for Android and desktop, keep the duplication (no intermediate source
  set: the default hierarchy template has no "jvm+android" group and adding one is not worth it).

Implementation classes (all `internal`, in `source/`):

- `SongLocalSourceImpl(fileStorage: FileStorage)`:
  - `loadSongs()`: `fileStorage.list(SONGS)` filtered to `.cho` (case-insensitive), then for each file `readText`,
    `ChordProParser.parseMetadata(text)` and `ChordProParser.hasChords(text)`, mapped through
    `mapper/SongMappers.kt` (`fun StoredFileInfo.toSong(metadata, hasChords)` with the title/artist fallbacks from
    step 05 §1). Files that fail to read are skipped with a `println`. Run the reads concurrently
    (`coroutineScope { map { async { … } }.awaitAll() }`), the library may hold thousands of files.
  - `saveSongContent`: `writeText(SONGS, fileName, text)`.
  - Other functions delegate 1:1.
- `SetlistLocalSourceImpl(fileStorage)`: files `*.setlist.json` in `SETLISTS`; `@Serializable` `SetlistDocument`
  (`title`, `priority`, `songs: List<SetlistSongDocument(file, transposition = 0)>`) in `model/` with mappers in
  `mapper/SetlistMappers.kt`. Use `Json { ignoreUnknownKeys = true; prettyPrint = true }`. Unparseable files are
  skipped with a `println` (not deleted).
- `UserPreferencesLocalSourceImpl(fileStorage)`: `preferences.json` in `PREFERENCES`, `@Serializable`
  `UserPreferencesDocument` mirroring the model with **every field defaulted** so older documents keep loading;
  enums stored by their `id`.

Koin (`commonMain/Module.kt`):
```kotlin
val dataLocalSourceModule = module {
    single<FileStorage> { createFileStorage() }
    single<SongLocalSource> { SongLocalSourceImpl(get()) }
    single<SetlistLocalSource> { SetlistLocalSourceImpl(get()) }
    single<UserPreferencesLocalSource> { UserPreferencesLocalSourceImpl(get()) }
}
```

Remove `androidx-room`, `androidx-sqlite`, `kotlin-ksp` (if nothing else uses KSP: check with grep) from
`libs.versions.toml` and `alias(libs.plugins.ksp) apply false` from the root build file.

### File naming helpers (commonMain, `FileNames.kt`, `internal`)

- `fun sanitizeFileName(raw: String): String`: replace `/ \ : * ? " < > |` and control characters with a space,
  collapse whitespace, trim, trim trailing dots, cut to 120 characters; empty → "Untitled".
- `fun songFileName(title: String, artist: String): String`: `"$artist - $title.cho"` when artist is not blank,
  otherwise `"$title.cho"`, sanitized.
- `fun setlistFileName(title: String): String`: `sanitize(title) + ".setlist.json"`.
- `suspend fun FileStorage.uniqueName(directory, desired: String): String`: appends ` (2)`, ` (3)`… before the
  extension until `exists` is false.

## 4. Repositories

`:data:repository:api`:

```kotlin
interface SongRepository {
    val songs: Flow<DataState<List<Song>>>
    suspend fun loadSongsIfNeeded(): List<Song>?
    suspend fun rescan()                                   // re-reads the directory; used by refresh and after imports
    suspend fun saveSong(content: SongContent)             // writes and updates the cached list entry
    suspend fun deleteSong(fileName: String)
}

interface SongContentRepository {
    suspend fun loadSongContent(fileName: String): SongContent?   // cached per file name; cache dropped on save/delete/rescan
}

interface SetlistRepository {
    val setlists: Flow<DataState<List<Setlist>>>
    suspend fun loadSetlistsIfNeeded(): List<Setlist>?
    suspend fun saveSetlist(setlist: Setlist)
    suspend fun deleteSetlist(fileName: String)
}

interface UserPreferencesRepository  // unchanged shape
```

Delete `RawSongDetailsRepository`, `TranspositionRepository`, their implementations and `SetlistRepository.saveSetlists(List)`.
`BaseLocalDataRepository` stays and backs `SongRepositoryImpl` (data = `List<Song>`), `SetlistRepositoryImpl` and
`UserPreferencesRepositoryImpl`. `SongRepositoryImpl.saveSong` writes through the local source, re-parses the
metadata of that one file (`loadSongs` would rescan everything; add `SongLocalSource.loadSong(fileName): Song?`
instead) and replaces the entry in the cached list via `saveData`-like state update without writing the list (the
list is not persisted, so add a `protected fun updateData(data: T)` to `BaseLocalDataRepository` that sets
`DataState.Idle(data)`). `SongContentRepositoryImpl` is a `single` holding a `MutableMap<String, SongContent>` guarded
by a `Mutex`; `SongRepositoryImpl` gets it injected and invalidates entries on save/delete/rescan.

## 5. Use cases (`:domain`)

Replace the current set with:

| Use case | Signature | Notes |
| --- | --- | --- |
| `GetScreenDataUseCase` | `operator fun invoke(): Flow<DataState<ScreenData>>` | combines songs, setlists, preferences. `ScreenData(setlists, songs, userPreferences)`; `transpositions` removed (they live in preferences / setlists now). |
| `LoadScreenDataUseCase` | `suspend operator fun invoke(isRescan: Boolean)` | loads all three; `isRescan` calls `songRepository.rescan()`. |
| `GetSongContentUseCase` | `suspend operator fun invoke(fileName: String): SongContent?` | |
| `SaveSongContentUseCase` | `suspend operator fun invoke(content: SongContent)` | |
| `CreateSongUseCase` | `suspend operator fun invoke(title: String, artist: String): Song` | builds the file name (unique), writes a template `{title: …}\n{artist: …}\n\n`, returns the new `Song`. |
| `DeleteSongUseCase` | `suspend operator fun invoke(fileName: String)` | also removes the song from every setlist and from `preferences.transpositions`. |
| `SaveSetlistUseCase` / `DeleteSetlistUseCase` | | |
| `SaveUserPreferencesUseCase` | unchanged | |
| `NormalizeTextUseCase` | unchanged | |
| `ParseChordProUseCase` | `operator fun invoke(text: String): ChordProSong` | thin wrapper over `ChordProParser.parse`; `:domain:api` gets `api(project(":chordpro"))`. |
| `TransposeChordProUseCase` | `operator fun invoke(song: ChordProSong, semitones: Int): ChordProSong` | wraps `ChordProTransposer.transpose`. |

Delete `GetSongDetailsUseCase`, `LoadSongDetailsUseCase`, `SaveSetlistsUseCase`, `SaveTranspositionsUseCase`,
`TransposeRawSongDetailsUseCase`. Update `domainModule`.

## 6. View model and UI: compile-fix only

Adjust `CampfireViewModel` and the screens to the new types with the **smallest possible** changes; the real UI work
is steps 06–09:

- Song identity everywhere: `song.id` → `song.fileName`, `song.url` gone.
- `CampfireDestination.SongDetails(songFileNames: List<String>, setlistFileName: String?, initialIndex: Int)`.
- Transposition: `getTransposition(fileName, setlistFileName?)` reads from the setlist entry or
  `preferences.transpositions`; `setTransposition` writes to the right place (save the setlist or the preferences).
- Song details still receives raw text (`SongContent.text`) and renders it with the existing `SongLyrics` parser: the
  existing parser understands `[chords]` and `{c: …}` headings, so Campfire 3 style files render already; other
  ChordPro renders as plain lines until step 06.
- Setlists screen: `setlist.songIds` → `setlist.entries.map { it.songFileName }`; add/remove/move go through
  `SaveSetlistUseCase` with a modified copy.
- Delete the `DialogType.NewDatabase` / `DeleteDatabase` if they survived step 04.

## Verify

- Full build for all four platforms; `desktopTest` of `:chordpro` and `:data:source:local:implementation` pass.
- Desktop: run the app, quit, copy two `.cho` files into the desktop library folder (`~/Library/Application Support/Campfire/library/songs` on macOS), start again: both songs are listed with title/artist from their directives, open, and transpose. Restart: the transposition is remembered. Create a setlist, add a song, restart: still there; the `library/setlists/*.setlist.json` file is human-readable.
- Android: `adb push` a `.cho` into `/data/data/com.pandulapeter.campfire.debug/files/library/songs/` (`adb shell run-as` is needed on a non-rooted device) and pull to refresh: the song appears.
- Web: start the dev server, open DevTools → Application → Storage → "Origin Private File System" (Chromium) and check `preferences/preferences.json` appears after changing a setting.
- `grep -rn "androidx.room\|Room\.\|localStorage" --include=*.kt --include=*.kts . | grep -v /build/` returns nothing.

## Execution notes

- **File naming lives in the storage layer, not in the use cases.** `FileNames.kt` is `internal` to
  `:data:source:local:implementation`, so a use case cannot build a unique name itself. `SongLocalSource` therefore
  gained `createSong(title, artist, text): Song` and `SetlistLocalSource` gained `createSetlist(title, priority)`,
  each picking a free name via `uniqueName` and returning what it wrote; `CreateSongUseCase` only decides the
  content. `SongRepository` / `SetlistRepository` expose the same two calls.
- **`CreateSetlistUseCase` was added**, which the step's table does not list. Creating a setlist needs a unique file
  name for exactly the reason above, so it could not stay in the view model.
- **`TransposeChordProTextUseCase` was added.** Step 06 still renders raw text through the old `SongLyrics` parser,
  so the viewer needs text-to-text transposition (`ChordProTransposer.transposeText`), not the model-to-model
  `TransposeChordProUseCase`. The two model-level use cases the table asks for (`ParseChordProUseCase`,
  `TransposeChordProUseCase`) are implemented and Koin-wired but nothing calls them until step 06.
- **`BaseLocalDataRepository` was reshaped further than the step describes.** It asks for a `protected updateData`,
  which is there, but the whole-list `saveDataToLocalSource` constructor parameter was also dropped: songs and
  setlists are one file each now, so only the preferences are still written as a whole. The base therefore takes just
  a loader and offers `loadDataIfNeeded()`, `reloadData()` (for `rescan()`), `updateData(data)` and
  `writeData(data, persist)`. A re-read now keeps the previous data visible while it runs, so a refresh no longer
  blanks the list.
- **Transpositions are looked up through a small value type** (`CampfireViewModel.Transpositions`) with
  `get(songFileName, setlistFileName)`, instead of the deleted `TranspositionKey` map. It folds the two places a
  transposition can live (the setlist entry, the preferences) into one lookup that cannot mix them up.
- **`setlists` and `userPreferences` are now eager states.** Both are read by write paths (adding a song to a
  setlist, transposing, `updateUserPreferences`), and a `WhileSubscribed` state with no subscriber holds its initial
  value, so those writes silently did nothing whenever no screen happened to be subscribed. This was found while
  verifying: `createSetlist` appeared to succeed but produced no setlist. `screenData` is already collected eagerly,
  so neither change costs an extra subscription. Every call site happened to have a subscriber before, so this was
  latent rather than user-visible, but it made the write paths correct by construction.
- `SongLocalSource.loadSong(fileName)` was added, as the step anticipates, so that saving one song does not rescan
  the library.
- **kotlinx.serialization omits default values**, so a setlist at priority 0 has no `"priority"` key and an
  untouched preference has no entry. Every document field is defaulted, so these read back correctly; it just means
  the files only carry what differs from the defaults.
- `data/model/CLAUDE.md` and `data/source/local/implementation/CLAUDE.md` still describe the Room/Database world.
  They were already stale before this step, so they are left for step 11 with the rest of the documentation.

### Verified

- All four platforms build; `:chordpro:desktopTest` (41 tests) and `:data:source:local:implementation:desktopTest`
  (28 tests) pass, and the module's common tests also run on iOS and in the browser.
- `grep -rn "androidx.room\|Room\.\|localStorage"` over `.kt`/`.kts` returns nothing, as does a search for `ksp`.
- Desktop, with two `.cho` files copied into `~/Library/Application Support/Campfire/library/songs`: both are listed
  with the title, artist and key read from their directives, grouped by artist. Koin starts 23 definitions with no
  failure.
- Transposition and setlists were driven through the real view model (a temporary `LaunchedEffect` in the desktop
  `main`, removed afterwards) and survive a restart. The library transposition lands in `preferences.json` and the
  setlist one inside `Friday gig.setlist.json`, both pretty printed and in the documented shape; after a restart the
  same song reads back as +3 from the library and -2 from inside the setlist.
- The macOS window manager would not give the app keyboard focus during this session, so `osascript` clicks could not
  be used; the in-app driver replaced them (see [[desktop-target-verification]] in the session notes for both
  techniques).
