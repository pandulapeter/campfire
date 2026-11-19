<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# data/sync/merge

One document at the top of the cloud folder (Campfire's `preferences.json`), never listed by the engine, settled by
`MergedFolderDocument` only after a run the engine completed: a three-way merge, value by value, of this device's, the
folder's, and the base the index keeps. A change beats a removal; two changes keep this device's. What the document
holds is the app's `MergedFolderDocumentCodec`; this package knows its entries only as keyed by the codec's collection.

- Merged as a JSON tree (`FolderDocumentJson`), so unknown fields and unreadable values pass through untouched. The
  index names the fields the base's writer owned; others are removed from the base so a newly owned field merges as
  add-only.
- Not downloaded when the listing shows the revision the index pairs with the base; a retry after a conflict always
  downloads.
- A folder document that is missing or unreadable (past 64 levels or the token budget, checked before parsing; too large
  to download, then written over with this device's values) is merged as the base, never as a removal of everything; one
  with a newer format is neither applied nor overwritten.
- An entry whose document is no longer in the library is dropped, except a file the run failed on or one the folder
  gained since the last sync. Entries match documents ignoring case and Unicode form.
- A value changed here during the merge is kept. A `SyncRunEndingException` ends the run with its reason, keeping the
  folder's document as the base once it was applied here; any other failure puts the codec's `id` in
  `SyncSummary.failedSteps` and leaves the base unchanged.
