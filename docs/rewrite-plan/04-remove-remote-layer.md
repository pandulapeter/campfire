# Step 04: remove the remote layer and the databases

**Goal:** delete every trace of networking and of the "database" (Google Sheets) concept. The app keeps building and
running on all platforms; the song list is empty because nothing feeds it yet (step 05 does).

**Depends on:** nothing in code, but do it after 01–03 so the parallel work has landed and the tree is calm.

## 1. Delete modules

- Remove `":data:source:remote:api"` and `":data:source:remote:implementation"` from `settings.gradle.kts` and delete
  the `data/source/remote` directory entirely.
- Remove the `implementation(project(":data:source:remote:implementation"))` line from `app/android/build.gradle.kts`,
  `app/desktop/build.gradle.kts`, `app/ios/build.gradle.kts` and `app/web/build.gradle.kts`, and `dataRemoteSourceModule`
  from the four Koin startups (`CampfireAndroidApplication.kt`, `CampfireDesktopApplication.kt`,
  `CampfireViewController.kt`, `CampfireWebApplication.kt`).
- `data/repository/implementation/build.gradle.kts`: remove the dependency on `:data:source:remote:api`.

## 2. Delete the database concept

Delete these files and everything that only exists for them:

- `data/model/.../domain/Database.kt`
- `data/repository/api/.../DatabaseRepository.kt`, `data/repository/implementation/.../DatabaseRepositoryImpl.kt`
- `data/source/local/api/.../DatabaseLocalSource.kt`; in `roomMain` and `wasmJsMain`: `DatabaseLocalSourceImpl.kt`,
  `DatabaseEntity.kt`, `DatabaseMappers.kt`, `storage/dao/DatabaseDao.kt` and the entity/DAO references in
  `StorageManager.kt` (Room: drop it from `@Database(entities = …)`, bump `version = 6` to 7, and make sure the
  builder in `StorageManagerBuilder.kt` calls `fallbackToDestructiveMigration(true)`, adding it if it is not there;
  wasm: drop `KEY_DATABASES` and the two functions).
- `domain/api/.../GetDatabasesUseCase.kt`, `SaveDatabasesUseCase.kt` and their `Impl`s; remove them from
  `domainModule`.
- `UserPreferences.unselectedDatabaseUrls` (model, entities, mappers, default value in
  `UserPreferencesRepositoryImpl`) and `UserPreferences.showOnlyDownloadedSongs` (the concept "downloaded" is gone).
- In `CampfireViewModel`: the `getDatabases` / `saveDatabases` constructor parameters, `databases` state,
  `addDatabase`, `setDatabaseEnabled`, `removeDatabase`, `setDatabaseSelected`, `refresh()`'s network branch,
  `failedSongUrls`, `refreshFailedEvents`, and the `NewDatabase` / `DeleteDatabase` dialog types.
- In the UI: the "Active databases" section of `SettingsScreen.kt` (`DatabaseItem` and the add/remove dialogs in
  `Dialogs.kt`), the `RefreshFailedSnackbar` in `CampfireApp.kt`, and the "downloaded only" filter in
  `SongsScreen.kt` / `SongsControls` dialog if present.
- Strings: delete `settings_active_databases`, `settings_add_new_database*`, `settings_add`,
  `settings_remove_database*`, `error_refresh_failed`, and change `error_no_data_hint` / `song_details_no_data_hint`
  to not mention the internet (English: "The library could not be read." / "The file could not be read."; Hungarian:
  "A könyvtárat nem sikerült beolvasni." / "A fájlt nem sikerült beolvasni."). Both `values/strings.xml` and
  `values-hu/strings.xml`.

## 3. Make the song repositories local-only (temporary shape, replaced in step 05)

- `SongRepository` / `SongRepositoryImpl`: currently `BaseLocalRemoteDataRepository` keyed by database URL. Change it
  to extend `BaseLocalDataRepository<List<Song>>` reading `SongLocalSource.loadSongs()`; `loadSongsIfNeeded()`
  returns the list; drop the remote parameters. The Room `SongEntity` keeps whatever columns it has; nothing writes
  songs anymore, so the list is empty.
- `RawSongDetailsRepository` / `Impl`: same treatment; `loadRawSongDetails(song)` just returns the local copy or null.
- Delete `BaseLocalRemoteDataRepository.kt`.
- `GetScreenDataUseCaseImpl`: remove the database and `downloadedSongUrls` inputs; `ScreenData` loses
  `downloadedSongUrls`; songs = `songRepository.songs` filtered by `hasChords` preference and sorted, as today.
- `LoadScreenDataUseCase(isForceRefresh: Boolean)` / `LoadSongDetailsUseCase(url, isForceRefresh)`: keep the
  signatures for now (step 05 replaces them) but make `isForceRefresh` a no-op; they only trigger the local load.

## 4. Remove the network dependencies and permission

- `gradle/libs.versions.toml`: delete `ktor`, `ktorfit`, `theapache64-retrosheet`, `softwork-csv` versions, their
  `[libraries]` entries and the `ktorfit` plugin. Delete `alias(libs.plugins.ktorfit) apply false` from the root
  `build.gradle.kts`.
- `app/android/src/main/AndroidManifest.xml`: remove `<uses-permission android:name="android.permission.INTERNET" />`.
  (Opening the website through Custom Tabs does not need it: the browser does the networking.)
- `app/web/src/wasmJsMain/resources/index.html` needs no change.
- Search the whole tree for `ktor`, `retrosheet`, `Ktorfit`, `Database`, `downloaded`, `refreshFailed`, `INTERNET`
  and remove leftovers (`grep -rni --include=*.kt --include=*.kts --include=*.xml --include=*.toml …` excluding
  `build/` directories).

## 5. Placeholder behaviour

- Songs screen: with an empty list it shows the existing `songs_no_data` empty state. Change its hint string to
  "Import songs or create a new one from the Settings screen." now; step 07 replaces the whole empty state with actions.
  Hungarian: "Importálj dalokat, vagy hozz létre egy újat a Beállítások képernyőn."
- Pull to refresh / the refresh action must still work (it reloads the local sources) and must not show any error.

## Verify

- Full build for all four platforms.
- Launch desktop and Android: three tabs, empty Songs list with the empty state, empty Setlists, Settings without a
  databases section, theme/language switches still work, no crash on refresh.
- `grep -rn "retrosheet\|ktor" --include=*.kt --include=*.kts . | grep -v /build/` returns nothing.

## Execution notes

- **`failedSongUrls` was kept**, against the instruction in section 2. It is the only thing that drives the song
  details error state (`SongDetailsPage(hasFailed = ...)` and its retry button), whose hint string
  `song_details_no_data_hint` this same step keeps and merely rewords to "The file could not be read.". Removing the
  state would have left that string unreachable and forced step 06 to reinvent it. `refreshFailedEvents` and the
  `RefreshFailedSnackbar` *were* removed as instructed, and `error_refresh_failed` is gone.
- **`SongEntity.databaseUrl` was dropped** (both the Room and the wasm entity), although section 3 said the entity
  keeps whatever columns it has. The schema version goes 6 -> 7 with destructive fallback either way, so removing the
  column costs nothing and it was the last `Database`-named thing left in the data layer. `SongLocalSource` therefore
  became `loadSongs()` / `saveSongs(songs)` without the database url, and `Song.toEntity()` lost its parameter.
- **The "downloaded" concept was removed one level deeper than listed**: `RawSongDetailsLocalSource.loadDownloadedSongUrls()`
  and `RawSongDetailsDao.getAllUrls()` are gone too, since nothing reads them once `ScreenData.downloadedSongUrls` and
  the song list's dimming (`SongListItem.isDownloaded`) are gone. The wasm `StorageManager` keeps its per-song url
  bookkeeping, now private and renamed to `loadSavedSongUrls()`, because `saveRawSongDetails` still needs it.
- **`fallbackToDestructiveMigration(dropAllTables = true)` was already present**, in `roomMain/Module.kt` rather than
  in `StorageManagerBuilder.kt` where section 2 expected it. Nothing had to be added.
- **KSP was not removed.** Section 4 only lists the networking dependencies, and Room still generates its
  implementation through KSP; the README's "Deleted" list covers the end state after step 05 removes Room.
- **`kotlin-js-store/wasm/yarn.lock` had to be regenerated** with `./gradlew kotlinWasmUpgradeYarnLock`. Dropping
  `ktor-client-js` removes `ws@8.20.1` from the npm dependency set, and `:app:web:wasmJsBrowserDistribution` fails on
  `kotlinWasmStoreYarnLock` ("Lock file was changed") until the lock file is updated.
- `LoadScreenDataUseCase` and `LoadSongDetailsUseCase` keep their `isForceRefresh` parameters as no-ops, each marked
  with a `// TODO(step 05)` comment.
- `data/source/local/implementation/CLAUDE.md` is stale (it still describes database version 1, entities in
  `commonMain` and no wasm target). It was already stale before this step, so it is left for step 11 along with the
  rest of the documentation.

### Verified

- `./gradlew :app:android:assembleDebug :app:desktop:build :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`
  succeeds, as do `:chordpro:desktopTest` (41 tests) and `:data:source:local:implementation:desktopTest` (28 tests).
- The desktop app launches (Koin resolves all 27 definitions, no exceptions), shows the three tabs, the empty Songs
  list with the new hint, an empty Setlists tab, and a Settings screen that now starts at "Song display" with no
  databases section. The refresh action completes without an error state.
- `grep -rni "retrosheet\|ktor" --include='*.kt' --include='*.kts' .` and the `INTERNET` search return nothing.
