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
  a save that moves the file as well, since a setlist's name is derived from the title it has just been given. Every
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
- `LibraryFileLocalSource` — the library as *bytes*, which is what sync moves around. Deliberately does not look
  inside the files at all, so a song Campfire cannot parse still travels between devices unchanged. Its listing uses
  the same rule as the library scan (`LibraryFiles.isSongFileName` / `isSetlistFileName`), so sync never moves a
  file the app does not show.
  `writeLibraryFileToFreeName` is how an incoming copy of a file that changed on both sides lands next to the local
  one, numbered ` (2)` rather than with the `_2` of a name the app derived itself, and on a name sync says is free in
  the cloud folder too. `readLibraryFile` answers null only
  for a file that is not there; one that is there and cannot be read throws `LibraryStorageException`, since sync
  would carry a missing file out as a deletion on every device.
- `SyncStateLocalSource` — the two documents sync remembers between runs, kept next to the preferences and so outside
  `library/`: neither is the user's data and an export must not carry them. Both are **opaque strings** here — what
  is in them belongs to the layers that write them (the credentials to the remote source, the index to the
  repository), and the storage layer has no business knowing either shape. `loadSyncIndex` answers null only for an
  index that is not there; one that is there and cannot be read throws `LibraryStorageException`, since a run that
  took it for none would undo every deletion since the last one. Unreadable credentials are still treated as none,
  which connecting again answers.

**File naming is the storage layer's business.** Callers hand over a title, an artist and some text; the source decides
what the file is called, normalizes it to what every file system, shell and service agrees about and suffixes it until
the name is free. That is why
`createSong` and `importSong` return the `Song` they became rather than taking a file name. `importFileName` is the naming rule on its own, with nothing read or written, so that an import can work out what it would collide with before it collides with it — and it names an incoming song by its own header rather than by what its file was called, the arriving name standing in as the title only where the song declares none; `importSong` and `importSetlist` take `shouldReplace`, which is the one way either of them writes over a name that is taken.

There is one implementation (`:implementation`), multiplatform, with the platform difference pushed down into
`FileStorage`.
