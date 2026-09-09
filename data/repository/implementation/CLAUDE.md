# :data:repository:implementation

Implements `:data:repository:api` on top of `:data:source:local:api`. Koin wiring in `Module.kt`
(`dataRepositoryModule`), all repositories as `single`.

`base/BaseLocalDataRepository<T>` holds all the caching logic — new repositories should extend it rather than
reimplement state handling. It owns a `MutableStateFlow<DataState<T>>` that starts as `Loading(null)` (nothing has been
read yet is not an error), reads the local source on the first `loadDataIfNeeded()`, and guards that read with a
`Mutex` so that the parallel loads in `LoadScreenDataUseCase` wait for each other instead of racing. A re-read keeps the
data already on screen in the `Loading` state, so a refresh never blanks the list, and a failed read becomes
`Failure(previous data)` rather than an empty list.

Writing is deliberately **not** part of that shape. Songs and setlists are one file each, so a change writes that file
and updates the one cached entry (`updateData`); only the preferences are persisted as a whole (`writeData`). A
`rescan()` — the refresh action, and the last step of every import — is the only thing that walks the directory again.

- `SongContentRepositoryImpl` is not a `BaseLocalDataRepository`: it is a keyed in-memory cache of song *texts*, so
  paging through a setlist re-reads nothing. Bulk readers (the library export) pass `shouldCache = false` so that
  walking the whole library does not leave all of it in memory. The editor invalidates one entry after a save.
- `ArchiveRepositoryImpl` is a pass-through to the zip code in the local source implementation; it exists so the domain
  layer can reach it without depending on a local source.
