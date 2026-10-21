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

Local persistence interfaces, one per kind of file: plain suspend functions over `:data:model` types, no flows, no
platform types. One multiplatform implementation, with platform differences in `FileStorage`.

## Rules

- **File naming is the storage layer's business.** Callers hand over a title, artist, and text; the source names,
  normalizes, and suffixes the file, so `createSong` / `importSong` return the `Song` they became. `importFileName` is
  the naming rule alone (nothing read or written), so an import can predict collisions; it names a song by its header,
  the arriving name standing in only where there is no title. `shouldReplace` on `importSong` / `importSetlist` is the
  only way either writes over a taken name.
- **Missing vs. unreadable**: `readLibraryFile` and `loadSyncIndex` answer null only for a file that is not there; an
  unreadable one throws `LibraryStorageException`, since sync would carry a missing file out as a deletion and a missing
  index would undo every deletion since the last run. `loadUserPreferences()` never returns null (missing means
  defaults) and throws for an unreadable document, since defaults would be saved over it; a document that does not
  decode loses only its bad fields. Credentials that can never be read again count as none; ones refused for a moment
  (a Keystore or Keychain that refuses, a file somebody else holds) throw `LibraryStorageException`, so nothing saves
  over them. An unreadable editor draft counts as none.

## Sources

- `SetlistLocalSource`: `renameSetlist` saves and moves the file when the title changes the derived name. Every write
  returns the setlist with its new file size, so the cache never lists.
- `ArchiveLocalSource`: nested archives (by name, or by bytes under any name but a document's; an entry under an unknown
  name that is not a binary one, or a text name over the text limit, is peeked at for them) are unpacked in place, three
  levels deep, within one inflation budget; unpacking drops tools' hidden files (macOS AppleDouble `._name.cho`) and
  everything inside a hidden folder, unreported; every other unread entry (not importable, too large, unreadable) comes
  back as `ImportedFile.unread`, and one bad entry never fails the archive.
  Another app's document that is no archive (a JSON export of songs) comes back as its songs, inside an archive and
  loose through `songsIn` (null where the file is not one). `pack` dates entries only when asked (`isDated`, what the
  user saves); undated, the same files always pack to the same bytes, for copies compared by their bytes.
- `DocumentLocalSource`: local, bounded PDF/Word text extraction on `Dispatchers.Default`; null means unsupported or
  unreadable, cancellation propagates. Nothing stored, no network.
- `LibraryFileLocalSource`: the library as bytes for sync. Never looks inside files, so unparseable songs still travel;
  lists with the same rule as the scan (`LibraryFiles.isSongFileName` / `isSetlistFileName`). `writeLibraryFileToFreeName`
  lands a conflict copy as ` (2)` on a name also free in the cloud folder; `numberedVariantHolding` finds one that
  already holds the incoming bytes, so sync reuses it instead of writing another.
- `SetlistComparison`: sync's one look inside a file (setlists differing only in their day, and a file before a read
  dated it). False or null for non-setlist bytes.
- `CoverArtLocalSource`: bytes under a caller-derived key, outside `library/` (never exported, synced, or backed up).
  Never throws.
- `SyncCredentialsLocalSource` / `SyncIndexLocalSource`: opaque strings, outside `library/`; the index source also holds
  the forget-credentials-owed note.
- `EditorDraftLocalSource`: `preferences/editor-draft.json`, never exported, synced, or backed up.
