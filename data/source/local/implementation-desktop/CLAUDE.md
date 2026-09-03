# :data:source:local:implementation-desktop

Room-backed implementation of `:data:source:local:api` for JVM desktop. Koin wiring in `Module.kt` (`dataLocalSourceDesktopModule`).

**Near-duplicate of `:data:source:local:implementation-android`** — same entities, DAOs, mappers and sources. The only real difference is database construction: `Room.databaseBuilder<StorageManager>(name = "campfireDatabase.db")` with `BundledSQLiteDriver()` and `Dispatchers.IO` as query context. Keep schema, migrations and DAOs in sync with the Android twin.

Structure mirrors the Android module exactly: `storage/StorageManager` (`@Database` version 2), `storage/Migrations.kt`, `storage/dao/`, `model/`, `mapper/`.
