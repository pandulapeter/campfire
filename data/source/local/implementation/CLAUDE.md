# :data:source:local:implementation

Room-backed multiplatform implementation of `:data:source:local:api` (Android, desktop, iOS). Koin wiring in `Module.kt` (`dataLocalSourceModule`).

- All entities, DAOs, mappers and sources live in `commonMain`. `StorageManager` is the `@Database` (version 1, `exportSchema = false`) and carries `@ConstructedBy(StorageManagerConstructor::class)` — the `expect object` whose `actual` Room generates per target, which is what makes the database instantiable on Kotlin/Native.
- Only database *location* differs per platform: `storage/StorageManagerBuilder.kt` declares `expect fun Scope.createStorageManagerBuilder()` and `androidMain` / `desktopMain` / `iosMain` provide it (Android pulls the `Context` from Koin, desktop keeps the file in the working directory, iOS uses the documents directory). Everything else (driver, query context) is configured once in `Module.kt`.
- There are no migrations: the database is at version 1 and `Module.kt` sets `fallbackToDestructiveMigration(dropAllTables = true)`, so any schema change simply wipes the local cache. Bump the version and keep relying on that until the next release ships.
- Room's KSP compiler is added per target in `build.gradle.kts` (`kspAndroid`, `kspDesktop`, `kspIosArm64`, `kspIosSimulatorArm64`); add a line there if a new target appears.
