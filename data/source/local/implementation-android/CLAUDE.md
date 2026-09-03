# :data:source:local:implementation-android

Room-backed implementation of `:data:source:local:api` for Android. Koin wiring in `Module.kt` (`dataLocalSourceAndroidModule`).

**This module is a near-duplicate of `:data:source:local:implementation-desktop`** — same entities, DAOs, mappers and sources, differing only in package name and how the Room database is built (`Room.databaseBuilder(context, ...)` here vs. the bundled SQLite driver there). Any schema or DAO change must be applied to both.

- `storage/StorageManager` — the `@Database` (version 2, `exportSchema = false`), one abstract getter per DAO.
- `storage/Migrations.kt` — hand-written migrations; `fallbackToDestructiveMigration(dropAllTables = true)` is the safety net. Bump the version and add a `MIGRATION_x_y` here (and in the desktop twin) when entities change.
- `model/` — `*Entity` classes, `internal`, with `TABLE_NAME` in a companion object.
- `mapper/` — entity <-> domain model conversion; `Mappers.kt` holds the comma-joined `List<String>` helpers.
