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
