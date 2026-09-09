# :data:repository:api

Repository interfaces only. Consumed by `:domain:implementation`; implemented by `:data:repository:implementation`.

- `SongRepository` — an observable `songs: Flow<DataState<List<Song>>>` plus `loadSongsIfNeeded()`, `rescan()` (re-read
  the folder, which is what a refresh and the end of an import do), `saveSong`, `createSong`, `importSong`, `deleteSong`.
- `SetlistRepository` — the same shape over `*.setlist.json`, plus `parseSetlist` / `loadSetlistDocument` for the export
  and import paths.
- `SongContentRepository` — the *text* of the songs that have been opened, cached in memory so that paging through a
  setlist does not re-read the same files. Deliberately not a `DataState` flow: it is a lookup, not a screen's state.
- `UserPreferencesRepository` — one document, read once and written whole.
- `ArchiveRepository` — zip pack/unpack. It has no state to cache and exists only so that the use cases can reach the
  zip code without the domain layer having to see the local sources.

A `rescan()` is the only thing that re-reads the library folder. Everything else keeps the cached list in step by
updating the one entry it changed, so writing a song does not cost a directory scan.
