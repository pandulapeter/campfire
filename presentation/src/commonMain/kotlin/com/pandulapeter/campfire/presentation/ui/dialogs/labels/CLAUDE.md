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

- Manage tags / languages run on `BulkLabelDraft`: a half-ticked row cycles on → off → half; Save writes only rows
  turned on or off, on the file as on disk, `NonCancellable`, ending in one snackbar.
- `ImportTagsSheet`'s Done writes nothing; the import does.
- A tag's color is written at once, not on Save, and survives cancelling: it belongs to the tag library-wide.
- Language search matches names and codes (`HUN`, `hu-HU`); the song's own languages stay at the top while open, since
  a row reordering under the finger that ticked it is worse than scrolling.
