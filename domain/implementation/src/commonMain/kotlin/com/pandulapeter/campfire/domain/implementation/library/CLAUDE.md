<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# domain/implementation/library

### Screen data (`GetScreenDataUseCaseImpl`)

- Built on `Dispatchers.Default` in two halves (songs, setlists) combined at the end, so a setlist write does not
  re-filter and re-sort the library.
- The library is sorted once per change of library or sorting mode (`sortMemo`); filters only narrow that order. One
  key (`NormalizeTextUseCase`, accents ignored) decides both order and sections.
- `cache` lets `Loading` / `Failure` still carry the last good data. With none yet, a part whose first read failed
  stands in empty (songs, setlists) or at defaults (preferences), flagged by `ScreenData.isWholeLibrary = false`, and is
  never cached. A part merely still loading is never filled in.
- Setlists are never narrowed by the song filters. **Every order ends in the file name**: repository lists are
  unordered (a write moves its item to the end).
- `ScreenData` carries the filter and the three preferences it was built for, so two equal lists built for different
  filters are distinct values to the final `distinctUntilChanged`.
- Tags and languages (`SongLanguage.UNKNOWN` = declares none) are each counted over the songs the *other* group
  leaves. Neither can empty the other: what counts as a tag/language is decided before either filter runs; a selected
  value no song carries any more is ignored (not emptying the list); a value the other group narrowed to nothing stays
  with count zero, sorted last, drawn disabled, so chips hold still.
- `ScreenData.libraryTags` / `libraryLanguages` are the whole library's counts the filters start from, for controls that
  ignore the filters (the song picker); tags are compared by `tagKey` everywhere.

- `DeleteLibraryUseCase` (Settings → Library, after typing `DELETE`) deletes every song and setlist: it stops any
  running sync, then runs one with deletions allowed (the typed word answering the guard), emptying the cloud folder and
  every synced device too.
