<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
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
- `LibraryFileLocalSource` — the library as *bytes*, which is what sync moves around. Deliberately does not look
  inside the files at all, so a song Campfire cannot parse still travels between devices unchanged.
  `writeLibraryFileToFreeName` is how an incoming copy of a file that changed on both sides lands next to the local
  one, under the same ` (2)` rule as a colliding import.
- `SyncStateLocalSource` — the two documents sync remembers between runs, kept next to the preferences and so outside
  `library/`: neither is the user's data and an export must not carry them. Both are **opaque strings** here — what
  is in them belongs to the layers that write them (the credentials to the remote source, the index to the
  repository), and the storage layer has no business knowing either shape.

**File naming is the storage layer's business.** Callers hand over a title, an artist and some text; the source decides
what the file is called, sanitises it for every platform's rules and suffixes it until the name is free. That is why
`createSong` and `importSong` return the `Song` they became rather than taking a file name.

There is one implementation (`:implementation`), multiplatform, with the platform difference pushed down into
`FileStorage`.
