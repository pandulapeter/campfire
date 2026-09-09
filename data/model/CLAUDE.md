# :data:model

Pure Kotlin domain models with no dependencies. Every other module depends on this (usually transitively via an `api` module).

- `DataState<T>` — `Idle` / `Loading` / `Failure`, each carrying nullable cached `data`. The universal wrapper for anything flowing out of a repository.
- `domain/` — `Song` (the metadata of one `.cho` file, without its text), `SongContent` (that text), `Setlist` (with per-entry transposition, so it travels with the setlist), `UserPreferences` (with `SortingMode`, `UiMode`, `Language` enums whose `id` values are persisted, plus the library-wide transpositions), `ImportedFile` / `ExportedFile` / `ImportResult` for the import and export paths.
- `domain/LibraryFiles.kt` — the extension vocabulary: `.cho` is what Campfire writes, `SONG_EXTENSIONS` is the whole ChordPro family it reads and registers with each operating system, and `IMPORTABLE_EXTENSIONS` adds the types (zip, plain text, JSON) it will read when handed one but never claims system wide. The import rules, the storage layer and the three OS registrations (Android manifest, iOS plist, desktop Gradle file) all have to agree with this list.

These types are the layer-crossing currency: stored documents and file bytes are mapped to/from them and never leak past their own module.

Song and setlist identity is the **file name**, extension included — not a generated id. Two songs with the same title are two files with different names, and a rename in the library folder is a different song as far as the app is concerned.
