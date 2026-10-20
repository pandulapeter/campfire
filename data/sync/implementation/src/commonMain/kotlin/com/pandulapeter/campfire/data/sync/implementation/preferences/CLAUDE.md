<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# data/sync/preferences

`preferences.json` sits at the top of the cloud folder, never listed by the engine: the per-song overrides
(transposition, tempo, capo, `beatLevels`), chord shapes (`chords`), and tag colors (`tags`); `chords` and `tags` are
written only once non-empty, so older documents keep their bytes.

- Settled by `SyncedPreferencesSync` only after a run the engine completed: a three-way merge, value by value, of this
  device's, the folder's, and the base the index keeps. A change beats a removal; two changes keep this device's.
- Merged as a JSON tree, so unknown fields and unreadable values of known fields pass through untouched. The index names
  the fields the base's writer owned; others are removed from the base so a newly owned field merges as add-only. A new
  field joins `SyncedPreferencesDocument.OWNED_FIELDS`.
- The folder's document is not downloaded when the run's listing shows the revision the index pairs with the base
  (`syncedPreferencesRevision`, written only beside the document a settle left in the folder); a retry after a
  conflict always downloads.
- A folder document that is missing or unreadable is merged as the base, never as a removal of everything; one with a
  newer `version` is neither applied nor overwritten.
- A song no longer in the library loses its entry, except a file the run failed on or one the folder gained since the
  last sync. Chord and tag entries are never dropped with a song. Entries match songs ignoring case and Unicode form.
- A value changed here during the merge is kept. A failure that ends a run (`SyncRunEndingException`) ends this one with
  its reason, keeping the folder's document as the base once it was applied here; any other failure sets
  `SyncSummary.havePreferencesFailed` and leaves the base unchanged.
