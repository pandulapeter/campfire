# :data:repository:api

Repository interfaces only. Consumed by `:domain:implementation`; implemented by `:data:repository:implementation`.

Shape of each repository: an observable `Flow<DataState<T>>` property plus suspend functions to load and save. Local-only repositories expose `loadXIfNeeded()`; the song repository (local + remote) exposes `loadSongs(databaseUrls, isForceRefresh)` and `deleteLocalSongs()`.

One repository per model: `Database`, `Setlist`, `Song`, `RawSongDetails`, `UserPreferences`, `Transposition` (song id -> semitone offset).
