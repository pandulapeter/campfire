<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# domain/implementation/export

- A single song exported alone is the `.cho` as on disk, unless it shows a picture (below); anything else is a zip.
  Transposition is never baked in.
- **Pictures** (`ExportedImages`): a song showing a picture the library holds leaves as a zip named after it, the song
  plus `images/<name>`; every zip carries under `images/` the library's files its songs name (`libraryFilesNamedBy`:
  pictures and attachments), each once, after all else, in the order of their names (a shared copy is rebuilt in the
  same order). One lazy entry per library file,
  each left out unless a song written before it named it, since songs are read only as the writer reaches them.
  Versions before pictures skip `images/` and keep the directives.
- **An archive is a file, never held whole** (`exportedArchive`): written into `exports/` entry by entry, each read
  only when the writer reaches it (the library's songs 64 at a time), an entry read as nothing left out, and the file
  removed on any failure, a cancellation, or no entry at all. Whoever gets the `ExportedFile` owns its file: the
  platform once handed over, else `DiscardExportedFileUseCase` (a shared link's copy is read back and discarded).
- A zip is dated when the user saves it; a shared link's copy (`isSharedCopy`) is undated, so it packs to the same
  bytes wherever and whenever it is built, and carries only the attachments `sharedCopyAttachments` picks within their
  budget (by the listed sizes, decided when the first `images/` entry is reached), counting the rest
  (`ExportedFile.leftOutAttachmentCount`). Its entries are compressed, so an `ExportedFile` carries what an import
  unpacks it to (`exportedArchive`), how much of it is pictures and how much attachments (a file no song of the archive shows as a
  picture), and `isTooLargeToImport` judges that by the import's three budgets as well as the archive's own size. A
  library with large attachments exports to an archive the import cannot take back whole; it says so, and sync moves it.
- A single song is named by the import's rule (`SongRepository.importFileName`), so it comes back under the same name.
  Inside an archive, library names are kept, since setlists point at songs by file name.
- A setlist is exported whole, its document the stored file unchanged; a song missing from the library is still named.
- A library export whose song or setlist scan failed returns null, never a partial archive. Files are taken from the
  folder, not the scan, so new files are included and undecodable setlist JSON is backed up as stored. Unreadable or
  oversized files are named beside the archive.
- An attachment opened from a song is copied whole into `exports/` under the name the song shows it by
  (`attachmentFileName`: no character a file system refuses, its file's extension kept), replacing an earlier copy of
  that name; the platform's opener owns it from then on.
