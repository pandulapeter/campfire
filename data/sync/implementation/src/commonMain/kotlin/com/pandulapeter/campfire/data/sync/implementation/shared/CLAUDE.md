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

## The registry, mirror, and journal

- **Registry**: `shared-links.json` at the cloud folder's root (`SharedLinksFormat`), the account's one list of shares,
  read and edited by every device. Its revision is `RemoteListing.documentRevisions["shared-links.json"]`.
  - Edited only as operations (`SharedLinksRegistry.edit`): read, change, write against the revision read, and on a
    conflict all of it again, up to 3 times. No lock (not even the run's), no merge, no base in the index.
  - Members a version does not know are kept through its edits; a higher `version` is neither shown nor edited. One
    that does not decode counts as empty, and only a sharing action writes over it.
- **Mirror**: `preferences/shared-links.json` (`SharedLinksMirror`), the registry as last read or written, with that
  revision, so screens need no request. Replaced by a successful edit unless cleared since it began, and by a read
  unless the mirror changed while it was under way (a generation, since revisions are opaque; of two reads under one
  generation the later answer is dropped, one read behind at worst). The repository's `shares` are the
  mirror with the journal applied as it will be (the journal's entries kept in memory too), so a renamed or stopped
  item shows as it will be before the list is told.
- **Journal**: `preferences/shared-links-journal.json` (`SharedLinksJournal`), renames and deletions of shared items
  made here, applied by the next run before it looks items up. An unreadable one throws rather than being written over.
  - The domain's use cases note an entry (`followRename`, `stopSharingDeleted`, `stopSharingEverything`) for every
    rename and deletion while an account's credentials are stored (connected, or `ConnectionFailed` with credentials;
    a failed first connection stores none, and nothing would forget what it noted), since another device may have
    shared the item since the mirror was read. A deletion the mirror (as the journal would leave it) does not name is a
    `StopItem` by name, a deleted library adds a `StopAll`; both are resolved against the list when applied, which the
    applier then reads first. Without credentials, only what the mirror names is noted.
  - A `Rename` names its share (`shareId`) where the mirror knew it, and moves only that one. Accepted edges: a
    `StopAll` applied late also stops a share another device made after the deletion; a `Rename` without a `shareId`
    (an item the mirror did not name, or an older journal) matches by name when applied, so one applied late also moves
    another device's new share of a new item under the old name. This device's own new shares are safe: `share()`
    applies a non-empty journal first, and fails while entries stay.
  - Older builds read a journal with `stopItem` or `stopAll` as empty: downgrades are not supported.
  - Noted first, then applied at once where connected (`SharedLinksJournalApplier`, without the caller waiting), the
    whole journal in order, best effort; what it cannot write the run applies. The applier and the run's step never
    apply at once: each holds `SharedLinksJournal.whileApplying` from loading the entries until it removes them, so
    `remove`'s first-equal match is always the entry applied.
- Mirror and journal are forgotten with the index (disconnect, `forgetStoredConnection`, another account connected).

## Sharing (`SharedLinksRepositoryImpl`)

- A copy is named by a fresh `ShareIds` id, uploaded, linked, then added; a share of the same item found while adding
  (another device's, by `LibraryFiles.isSameLibraryName`) is answered instead, so an item never has two links. A share
  whose copy is gone (`copyMissing`) is never answered: the new entry replaces it in the same write.
- Whatever fails after the upload deletes the copy again, best effort, but only where the list surely does not name it:
  the edit, its re-read where it threw or was contended, and that decision run as one `NonCancellable` block, and a
  copy nothing could tell about is kept (a leftover at worst). **Stop sharing** deletes the copy before the
  entry, so no entry ever goes while its link still works. Settings' **Stop sharing everything**
  (`stopSharingEverythingNow`) deletes every copy the listing or the list names (leftovers of failed shares included),
  then removes the entries of those it deleted, after a failure too. Where that edit fails once the copies are gone,
  both note a `Stop` per share in the journal instead of failing, so the dead links show as stopped and the next run
  (or share) removes their entries; only a journal that cannot be written answers the edit's failure.

## Keeping copies current (`SharedCopySync`, the run's last step)

- Runs in `SyncRunner` after the preferences, before the completed index is saved and the watch point set. Skipped when
  the listing has no shared copy and no registry, and the journal is empty; the registry comes from the mirror when the listing names
  its revision and the journal is empty. The journal is applied first (a stop deletes the copy, then the entry; an id
  no copy can have, hand-edited, has none to delete: `deleteSharedCopy`, as Stop sharing too), and every change of the
  step goes in as one `SharedCopyChanges` edit at the end, also after a failure; journal entries are forgotten only
  once written.
- A copy is skipped unread when its `built` has the inputs fingerprint (`sharedCopyFingerprint`, from the run's index
  `localHash`es; a setlist is the one file read), a build no older than `CAMPFIRE_BUILD_NUMBER` (generated from
  `campfire.buildNumber`), and the listed hash (or none while `tooLarge`). Otherwise it is packed from the library's raw
  files as the export packs them (`SharedCopyContents`, undated) and uploaded only when its hash differs; a `Conflict`
  is dropped.
- Never stopped by inference: an item missing from the library makes the entry `orphaned`; an item back under its name
  makes it `ok`. A copy missing from a listing as new as the registry read makes it `orphaned` and `copyMissing` for
  good (a deleted file's link never comes back; a member, not a state, so older versions do not show it as working),
  whatever the library holds; such an entry is never built again.
- Run-ending failures end the run; anything else, `SyncScopeMissingException` included, is
  `SyncSummary.haveSharedCopiesFailed`, which never makes a run incomplete.
- The feed ignores `shared-links.json` (`SyncWatchPoint`): a share made or stopped anywhere starts no run.
