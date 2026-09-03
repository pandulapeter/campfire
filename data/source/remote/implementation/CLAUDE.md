# :data:source:remote:implementation

Ktor + Ktorfit + Retrosheet + kotlinx.serialization implementation of `:data:source:remote:api`, shared by every platform. Koin wiring in `Module.kt` (`dataRemoteSourceModule`).

- Song lists come from **Google Sheets read as a REST API**. One `HttpClient` is configured with `createRetrosheetPlugin(config)` (rewrites `<databaseUrl>/songs` to the gviz CSV endpoint) and JSON content negotiation. `NetworkManager` builds and caches one Ktorfit-generated `SongService` per database URL, using `RetrosheetConverter` to turn the CSV response into `List<SongResponse>`.
- Sheet name and column keys are declared in `SongResponse.Companion` (`SHEET_NAME`, `KEY_*`) and registered through `SongResponse.addSheet(...)`; a new column means updating both the `@Serializable` data class (field names are matched against the sheet's header cells) and that call.
- Raw song text files (GitHub-hosted) are downloaded with the same `HttpClient` directly in `RawSongDetailsRemoteSourceImpl`, no service interface needed.
- `mapper/SongMappers.kt` validates every field; a missing required field throws `DataValidationException`, which is caught and turned into `null` so one bad row can't break a whole sheet. `SongRemoteSourceImpl` then `mapNotNull`s and de-duplicates by id.

Ktorfit's Gradle plugin (`libs.plugins.ktorfit`) generates the `createSongService()` extension via KSP; the kotlin-serialization plugin is required for `@Serializable`.
