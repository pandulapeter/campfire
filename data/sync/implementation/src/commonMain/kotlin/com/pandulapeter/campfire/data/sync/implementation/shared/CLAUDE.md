<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# shared/ (song links)

The plan is `documentation/plans/song-links.md` (§4.2, §5, §6.3, §11).

## The registry, mirror, and journal

- **Registry**: `shared-links.json` at the cloud folder's root (`SharedLinksFormat`), the account's one list of shares,
  read and edited by every device. Its revision is `RemoteListing.documentRevisions["shared-links.json"]`.
  - Edited only as operations (`SharedLinksRegistry.edit`): read, change, write against the revision read, and on a
    conflict all of it again, up to 3 times. No lock (not even the run's), no merge, no base in the index.
  - Members a version does not know are kept through its edits; a higher `version` is neither shown nor edited. One
    that does not decode counts as empty, and only a sharing action writes over it.
- **Mirror**: `preferences/shared-links.json` (`SharedLinksMirror`), the registry as last read or written, with that
  revision, so screens need no request. Replaced on every read and successful edit.
- **Journal**: `preferences/shared-links-journal.json` (`SharedLinksJournal`), renames and deletions of shared items
  made here, applied by the next run before it looks items up. An unreadable one throws rather than being written over.
- Mirror and journal are forgotten with the index (disconnect, `forgetStoredConnection`, another account connected).

## Sharing (`SharedLinksRepositoryImpl`)

- A copy is named by a fresh `ShareIds` id, uploaded, linked, then added; a share of the same item found while adding
  (another device's, by `LibraryFiles.isSameLibraryName`) is answered instead, so an item never has two links.
- Whatever fails after the upload deletes the copy again, best effort; **Stop sharing** deletes the copy before the
  entry, so no entry ever goes while its link still works.

## Keeping copies current (`SharedCopySync`, the run's last step)

- Runs in `SyncRunner` after the preferences, before the completed index is saved and the watch point set. Skipped when
  the listing has no shared copy and the journal is empty; the registry comes from the mirror when the listing names
  its revision and the journal is empty. The journal is applied first (a stop deletes the copy, then the entry), and
  every change of the step goes in as one `SharedCopyChanges` edit at the end, also after a failure; journal entries
  are forgotten only once written.
- A copy is skipped unread when its `built` has the inputs fingerprint (`sharedCopyFingerprint`, from the run's index
  `localHash`es; a setlist is the one file read), a build no older than `CAMPFIRE_BUILD_NUMBER` (generated from
  `campfire.buildNumber`), and the listed hash (or none while `tooLarge`). Otherwise it is packed from the library's raw
  files as the export packs them (`SharedCopyContents`, undated) and uploaded only when its hash differs; a `Conflict`
  is dropped.
- Never stopped by inference: an item missing from the library, or a copy missing from a listing as new as the
  registry read, makes the entry `orphaned`; an item back under its name makes it `ok`.
- Run-ending failures end the run; anything else, `SyncScopeMissingException` included, is
  `SyncSummary.haveSharedCopiesFailed`, which never makes a run incomplete.
- The feed ignores `shared-links.json` (`SyncWatchPoint`): a share made or stopped anywhere starts no run.
