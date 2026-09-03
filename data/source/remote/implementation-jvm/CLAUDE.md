# :data:source:remote:implementation-jvm

Retrofit + Moshi + Retrosheet implementation of `:data:source:remote:api`, shared by both platforms. Koin wiring in `Module.kt` (`dataRemoteSourceJvmModule`).

- Song lists come from **Google Sheets read as a REST API** via `RetrosheetInterceptor`. Sheet name and column keys are declared in `SongResponse.Companion` (`SHEET_NAME`, `KEY_*`) and registered through `SongResponse.addSheet(...)` — a new column means updating both the data class and that call.
- `NetworkManager` builds and caches one `SongService` per database URL; `rawSongDetailsService` uses a dummy base URL because `RawSongDetailsService` takes a full `@Url` and streams the response body.
- `mapper/SongMappers.kt` validates every field; a missing required field throws `DataValidationException`, which is caught and turned into `null` so one bad row can't break a whole sheet. `SongRemoteSourceImpl` then `mapNotNull`s and de-duplicates by id.

Retrosheet is pinned to 2.x — 3.x is a Ktorfit/kotlinx.serialization rewrite with no `RetrosheetInterceptor`. `kotlin-reflect` is declared explicitly to keep it in step with the Kotlin version.
