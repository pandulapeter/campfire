<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/dialogs/labels

The sheets that edit a song's tags and languages, one song or a selection at a time.

- **Manage tags / Manage languages** (`TagsSheet`, `LanguagesSheet`, shared with the one-song dialogs) run on
  `BulkLabelDraft`: a half-ticked row cycles on → off → half; Save writes only rows turned on or off, leaving half rows
  alone. Writes go through `EditSongsUseCase` on the file as on disk, run `NonCancellable`, and end in one snackbar.
- A tag's color (`TagColorButton`, a popup rather than a second sheet) is written at once (`setTagColor`), not on Save,
  and survives cancelling: the color belongs to the tag library-wide.
- Language picker: search over names and codes (`HUN` and `hu-HU` both find Hungarian, via `ChordProLanguages.code`);
  the song's own languages stay at the top while open, since a row reordering under the finger that ticked it is worse
  than scrolling.
