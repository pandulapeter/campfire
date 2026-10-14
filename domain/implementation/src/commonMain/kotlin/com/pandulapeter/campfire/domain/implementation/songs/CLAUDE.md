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

- `RenameSongFileUseCaseImpl` and `DeleteSongUseCaseImpl` share one walk (`SongReferences.kt`) over what
  names a song by file: setlists and the per-song preference maps (`UserPreferences.withSongRenamed`). Rename moves
  the file first, then follows every reference; delete drops them. Every reference is attempted even after one fails;
  failure is returned, not thrown; not cancellable once the file has moved / is gone. Setlists naming the song are
  found from the files, not the cache, and changed through `updateSetlist`; one already naming the new name keeps
  one entry.
- `CreateSongUseCaseImpl`'s template carries empty `{key}`, `{capo: 0}`, `{tempo: 120}`, and `{time: 4/4}`: the values
  a song declaring none plays by.
- `DuplicateSongUseCaseImpl`: the original's current file text with only the changed fields set, named by the copy's
  own title and artist (unchanged copy gets numbered). Null if the original is unreadable.
