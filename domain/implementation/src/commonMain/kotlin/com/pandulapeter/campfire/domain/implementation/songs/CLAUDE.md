<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# domain/implementation/songs

- Renaming and deleting a song share one walk (`SongReferences.kt`) over what names a song by file: setlists and the
  per-song preference maps. Rename writes the new file, then follows every reference (the shared link too), then deletes
  the old file, so a kill between any two writes leaves at worst a duplicate, never a reference to a missing file; a
  reference that did not follow keeps the old file. Delete drops them. Every reference is attempted even after one
  fails; failure is returned, not thrown; not cancellable once the new file is written or the file is gone. Setlists naming the song are found from the files, not the cache; one already naming the new name keeps
  one entry.
- The new song template carries empty `{key}`, `{capo: 0}`, `{tempo: 120}`, and `{time: 4/4}`: the values a song
  declaring none plays by.
- A pasted, dropped, or shared text is never written by a form: `DraftSongFromTextUseCaseImpl` fills a missing title
  and artist (a shared page's title, else a placeholder) and each template line it lacks, and prettifies it, for the
  editor to open on; `CreateSongFromTextUseCaseImpl` writes the editor's text byte for byte at its Save, named by its
  header.
- A duplicate is the original's current file text with only the changed fields set, named by the copy's own title and
  artist (an unchanged copy gets numbered).
