<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# data/sync/preferences

`preferences.json`, Campfire's folder document (`merge/` settles it, by the rules there): the per-song overrides
(transposition, tempo, capo, `autoScrollSpeed`, `beatLevels`), chord shapes (`chords`), and tag colors (`tags`);
`chords` and `tags` are written only once non-empty, so older documents keep their bytes.

- `SyncedPreferencesCodec` is its `MergedFolderDocumentCodec`, over `SyncedPreferencesDocument` and
  `UserPreferencesRepository`: songs are the entries (keyed by `LibraryCollections.SONGS`), a failure is
  `LibrarySyncSteps.PREFERENCES`, and a newer format is a higher `version`.
- A new field joins `SyncedPreferencesDocument.OWNED_FIELDS`; an index written by 4.7.0 records none, and its base's
  fields are `OWNED_FIELDS_OF_4_7_0`.
- Chord and tag entries are never dropped with a song.
