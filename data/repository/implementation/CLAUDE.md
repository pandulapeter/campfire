# :data:repository:implementation

Implements `:data:repository:api` on top of `:data:source:local:api` and `:data:source:remote:api`. Koin wiring in `Module.kt` (`dataRepositoryModule`), all repositories as `single`.

Two base classes in `base/` hold all the caching logic — new repositories should extend one rather than reimplement state handling:

- `BaseLocalDataRepository<T>` — local storage only. `loadDataIfNeeded()` reads once and caches in a `MutableStateFlow<DataState<T>>`; `saveData()` writes through.
- `BaseLocalRemoteDataRepository<T>` — keyed by database URL, `Map<String, List<T>>`. Loads local data first, then refreshes from remote in parallel (`async`/`awaitAll`) and writes results back to local. Subclasses define `List<T>?.isValid()`, which decides whether a cached entry needs refetching. Remote failures are swallowed into a `DataState.Failure` that still carries the cache.

`DatabaseRepositoryImpl` merges user-added databases with a hardcoded list of official Google Sheets (main / Hungarian / Romanian) — that list lives in its companion object.
