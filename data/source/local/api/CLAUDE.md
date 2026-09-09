# :data:source:local:api

Local persistence interfaces, one per kind of file. Plain suspend functions over `:data:model` types — no flows, no
platform types.

- `SongLocalSource` — the `.cho` files: scan the folder into `Song` metadata, read and write one song's text, create or
  import one under a free file name, delete.
- `SetlistLocalSource` — the same for `*.setlist.json`, plus parsing an exported document and reading one back
  unchanged for export.
- `UserPreferencesLocalSource` — one document; `loadUserPreferences()` never returns null, because a missing or
  unreadable document means the defaults, which are defined once next to the document itself.
- `ArchiveLocalSource` — zip pack and unpack, over bytes.

**File naming is the storage layer's business.** Callers hand over a title, an artist and some text; the source decides
what the file is called, sanitises it for every platform's rules and suffixes it until the name is free. That is why
`createSong` and `importSong` return the `Song` they became rather than taking a file name.

There is one implementation (`:implementation`), multiplatform, with the platform difference pushed down into
`FileStorage`.
