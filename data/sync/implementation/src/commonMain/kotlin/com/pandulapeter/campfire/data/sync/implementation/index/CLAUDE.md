<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# data/sync/index

- Told about a file only once it has actually moved, so an interrupted run's half-done work just looks unsynced.
- Filed under `SyncAccount.indexKey` (the service's account id), never a user-changeable name. An index an earlier
  version filed under the e-mail address is adopted: a run without an index brings back every file deleted since.
- Periodic snapshots never throw; on the way out of a stopped run the last is written after waiting for (not
  cancelling) the periodic writer, since a cancelled write on the web carries on and could land last. Only the writes
  opening and completing a run throw, ending it as `STORAGE`.
- An index that exists and cannot be read ends the run as `STORAGE` before anything is written. Only one that reads but
  does not decode is taken for none. **Never rename its fields** (`SyncIndexDocumentTest` reads devices' real files),
  since lenient decoding would read a renamed field as an empty index.
- `syncedPreferencesRevision` is the folder's revision of `preferences.json` holding exactly `syncedPreferences`; only
  a completed settle writes it, every other write of a run leaves it null, so that run's successor downloads.
- An `isRunInProgress` marker found at `restore` is reported as interrupted and stops the launch run.
