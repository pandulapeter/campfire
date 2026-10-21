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

- A single song exported alone is the `.cho` as on disk; anything else (several songs, a setlist, the library) is a
  zip. Transposition is never baked in.
- A zip is dated (`isDated`) when the user saves it; a shared link's copy is undated, so it packs to the same bytes
  wherever and whenever it is built.
- `ExportFileNames.kt` names a single song by the import's rule (`SongRepository.importFileName`), so it comes back
  under the same name. Inside an archive, library names are kept, since setlists point at songs by file name.
- A setlist exported with some songs (`songFileNames`) packs and names only those (the rest of the document kept); a
  ticked song missing from the library is still named. Exported whole, the document is the stored file unchanged.
- A library export whose song or setlist scan failed returns null, never a partial archive. Files are taken from the
  folder (`loadSongFileSizes`, `loadSetlistFileSizes`), not the scan, so new files are included and undecodable
  setlist JSON is backed up as stored. Unreadable or oversized files are named beside the archive.
