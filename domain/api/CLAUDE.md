# :domain:api

Use case interfaces and the `ScreenData` aggregate. This is what the presentation layer depends on — it never sees repositories.

Conventions: one interface per use case, a single `operator fun invoke(...)`, named `Get*` (observe a flow), `Load*` (trigger a fetch), `Save*` (persist), or a verb for pure transforms (`NormalizeText`, `TransposeRawSongDetails`).

`ScreenData` bundles everything the UI needs in one object: databases, setlists, songs, user preferences, raw song details and transpositions.
