# :data:model

Pure Kotlin domain models with no dependencies. Every other module depends on this (usually transitively via an `api` module).

- `DataState<T>` — `Idle` / `Loading` / `Failure`, each carrying nullable cached `data`. The universal wrapper for anything flowing out of a repository.
- `domain/` — `Song`, `Setlist`, `Database` (a Google Sheets source), `RawSongDetails` (unparsed lyrics/chords text keyed by URL), `UserPreferences` (with `SortingMode`, `UiMode`, `Language` enums whose `id` values are persisted).

These types are the layer-crossing currency: entities and network responses are mapped to/from them and never leak past their own module.
