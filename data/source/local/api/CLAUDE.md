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
  import one under a free file name, rename one to the name its own metadata gives it, delete.
- `SetlistLocalSource` — the same for `*.setlist.json`, plus parsing an exported document and reading one back
  unchanged for export, and `loadSetlist`, one setlist read from its file for a change to build on. `renameSetlist` is
  a save that moves the file as well when the title changed, since a setlist's name is derived from the title it has
  just been given; a save whose title gives the name the stored one gives moves nothing. Every
  write returns the setlist carrying the size of the file it became, so the cache never needs a listing to know it.
- `UserPreferencesLocalSource` — one document; `loadUserPreferences()` never returns null, because a missing
  document means the defaults, which are defined once next to the document itself; a document that is there and
  cannot be read throws `LibraryStorageException` instead, since defaults handed out in its place would be saved over
  it, and one that does not decode gives up only the fields that are wrong.
  `hasStoredUserPreferences()` is what that rule leaves nobody able to ask, and it is asked about the *document*
  rather than about what is in it: the preferences are the first thing the app writes about itself, so their absence
  is what an installation that has never been used looks like from the inside.
- `ArchiveLocalSource` — zip pack and unpack, over bytes. Unpacking hands back only files somebody put in the
  archive: the hidden ones an archiving tool writes for itself (macOS packs an AppleDouble `._name.cho` beside
  every entry, under the extension of the file it belongs to) are dropped where they are read, so no caller has
  to count them, decode them or report them. Every other entry that was not read — not something an import looks
  inside, over the size the caller allows, or unreadable — comes back as an `ImportedFile.unread`, so that it is
  reported rather than lost, and one bad entry never fails the archive around it.
- `DocumentLocalSource` — local, bounded text extraction from PDF and Word bytes into positioned pages, lines and
  styled spans (`ExtractedDocument`), on `Dispatchers.Default`. Null means unsupported, malformed, protected or
  without readable text; cancellation still propagates. Original bytes are not stored and no network is involved.
- `LibraryFileLocalSource` — the library as *bytes*, which is what sync moves around. Deliberately does not look
  inside the files at all, so a song Campfire cannot parse still travels between devices unchanged. Its listing uses
  the same rule as the library scan (`LibraryFiles.isSongFileName` / `isSetlistFileName`), so sync never moves a
  file the app does not show.
  `writeLibraryFileToFreeName` is how an incoming copy of a file that changed on both sides lands next to the local
  one, numbered ` (2)` rather than with the `_2` of a name the app derived itself, and on a name sync says is free in
  the cloud folder too. `readLibraryFile` answers null only
  for a file that is not there; one that is there and cannot be read throws `LibraryStorageException`, since sync
  would carry a missing file out as a deletion on every device.
- `SetlistComparison` — the one look sync takes inside a file: whether two setlist files differ only in the day they
  name, and what a file was before a read gave it a day, so that the day every device gives an undated setlist on its
  own is not a conflict copy of every setlist. Answers false or null for bytes that are not a setlist document.
- `CoverArtLocalSource` — the copies of the cover images the songs name, as bytes under a key the caller derives from
  the address (the repository hashes it, so a name every storage can hold). Outside `library/`: never exported,
  synced or backed up, since the address travels in the song and the image can be downloaded again. Nothing here
  throws — a cover is never worth failing over — and `keepOnlyCoverArt` is how the ones no song names any more go;
  `getCoverArtCacheSize` adds up what the rest take.
- `SyncCredentialsLocalSource` and `SyncIndexLocalSource` — the two documents sync remembers between runs, one per
  client: the credentials for the remote source, and the index with the note that forgetting the credentials is still
  owed (see `SyncRepository.forgetStoredConnection`) for the repository, which is the one that decides it. All kept
  next to the preferences and so outside `library/`: none is the user's data and an export must not carry them. Both
  documents are **opaque strings** here — what is in them belongs to the layers that write them — and the storage
  layer has no business knowing either shape. `loadSyncIndex` answers null only for an
  index that is not there; one that is there and cannot be read throws `LibraryStorageException`, since a run that
  took it for none would undo every deletion since the last one. Unreadable credentials are still treated as none,
  which connecting again answers.
- `EditorDraftLocalSource` — the editor's unsaved text, `preferences/editor-draft.json`, kept so that the system
  ending the app in the background does not end the text with it. Outside `library/`, so it is never exported or
  synced, and kept out of the device backup. A draft that cannot be read is answered as none: it is a copy worth
  keeping, not one worth failing over.

**File naming is the storage layer's business.** Callers hand over a title, an artist and some text; the source decides
what the file is called, normalizes it to what every file system, shell and service agrees about and suffixes it until
the name is free. That is why
`createSong` and `importSong` return the `Song` they became rather than taking a file name. `importFileName` is the naming rule on its own, with nothing read or written, so that an import can work out what it would collide with before it collides with it — and it names an incoming song by its own header rather than by what its file was called, the arriving name standing in as the title only where the song declares none; `importSong` and `importSetlist` take `shouldReplace`, which is the one way either of them writes over a name that is taken.

There is one implementation (`:implementation`), multiplatform, with the platform difference pushed down into
`FileStorage`.
