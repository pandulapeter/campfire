# :data:source:remote:api

Two interfaces, no implementation details:

- `SongRemoteSource.loadSongs(databaseUrl)` — the song list for one Google Sheets database.
- `RawSongDetailsRemoteSource.loadRawSongDetails(url)` — raw lyrics/chords text for one song.

Implemented by `:data:source:remote:implementation-jvm`, shared by Android and desktop.
