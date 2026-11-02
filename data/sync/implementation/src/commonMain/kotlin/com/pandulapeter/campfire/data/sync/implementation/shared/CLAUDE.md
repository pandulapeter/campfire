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

- **Registry**: `shared-links.json` at the cloud folder's root, the account's one list of shares, edited by every
  device.
  - Edited only as operations (`SharedLinksRegistry.edit`): read, change, write against the revision read, and on a
    conflict all again, up to 3 times. No lock (not even the run's), no merge, no base in the index.
  - Unknown members are kept through edits; a higher `version` is neither shown nor edited. One that does not decode, is
    past the bounds checked before parsing, or is too large to download counts as empty, and only a sharing action
    writes over it.
- **Mirror**: `preferences/shared-links.json` (`SharedLinksMirror`), the registry as last read or written, with that
  revision, so screens need no request. Replaced by a successful edit unless cleared since it began, and by a read
  unless the mirror changed meanwhile (a generation, since revisions are opaque; of two reads under one generation the
  later answer is dropped, one read behind at worst). The repository's `shares` are the mirror with the journal applied
  as it will be, so a renamed or stopped item shows as it will be before the list is told.
- **Journal**: `preferences/shared-links-journal.json` (`SharedLinksJournal`), renames and deletions of shared items
  made here, applied by the next run before it looks items up. An unreadable one throws rather than being written over.
  - The domain's use cases note an entry for every rename and deletion while an account's credentials are stored
    (connected, or `ConnectionFailed` with credentials; a failed first connection stores none, and nothing would forget
    what it noted), since another device may have shared the item since the mirror was read. A deletion the mirror (as
    the journal would leave it) does not name is a `StopItem` by name, a deleted library adds a `StopAll`; both are
    resolved against the list when applied, which the applier then reads first. Without credentials, only what the
    mirror names is noted.
  - A `Rename` names its share (`shareId`) where the mirror knew it, and moves only that one. Accepted edges: a
    `StopAll` applied late also stops a share another device made after the deletion; a `Rename` without a `shareId`
    matches by name, so one applied late also moves another device's new share of a new item under the old name. This
    device's own new shares are safe: `share()` applies a non-empty journal first, and fails while entries stay.
  - Older builds read a journal with `stopItem` or `stopAll` as empty: downgrades are not supported.
  - Noted first, then applied at once where connected (`SharedLinksJournalApplier`, the caller not waiting), the whole
    journal in order, best effort; what it cannot write the run applies. Applier and run step never apply at once (see
    the module's locks), so `remove`'s first-equal match is always the entry applied.
- Mirror and journal are forgotten with the index (disconnect, `forgetStoredConnection`, another account): a stop still
  in the journal then leaves its link working, which the Disconnect confirmation says (`hasPendingStops`).

## Sharing (`SharedLinksRepositoryImpl`)

- A copy is named by a fresh `ShareIds` id, uploaded, linked, then added; a share of the same item found while adding
  (another device's, by `LibraryFiles.isSameLibraryName`) is answered instead, so an item never has two links. A share
  whose copy is gone (`copyMissing`) is never answered: the new entry replaces it in the same write.
- Whatever fails after the upload deletes the copy again, best effort, only where the list surely does not name it: the
  edit, its re-read where it threw or was contended, and that decision run as one `NonCancellable` block; a copy nothing
  could tell about is kept (a leftover at worst).
- **Stop sharing** deletes the copy before the entry, so no entry goes while its link works. **Stop sharing everything**
  (`stopSharingEverythingNow`) deletes every copy the listing or the list names (failed shares' leftovers included),
  then removes the entries of those it deleted, after a failure too. Where that edit fails once the copies are gone,
  both note a `Stop` per share in the journal instead of failing, so the dead links show as stopped and the next run (or
  share) removes their entries; only an unwritable journal answers the edit's failure. Both wait for a run's shared-copy
  step, which would otherwise upload a copy it read before the stop.

## Keeping copies current (`SharedCopySync`, the run's last step)

- Runs after the preferences, before the completed index is saved and the watch point set. Skipped when the listing has
  no shared copy and no registry and the journal is empty; the registry comes from the mirror when the listing names its
  revision and the journal is empty. The journal is applied first (a stop deletes the copy, then the entry; a
  hand-edited id no copy can have has none to delete), and every change of the step goes in as one `SharedCopyChanges`
  edit at the end, also after a failure; journal entries are forgotten only once written.
- A copy is skipped unread when its `built` has the inputs fingerprint (`sharedCopyFingerprint`, from the index's
  `localHash`es; a setlist is the one file read), a build no older than `CAMPFIRE_BUILD_NUMBER`, and the listed hash (or
  none while `tooLarge`). Otherwise it is packed from the library's raw files as the export packs them (undated) and
  uploaded only when its hash differs; a `Conflict` is dropped.
- Never stopped by inference: an item missing from the library makes the entry `orphaned`; back under its name, `ok`. A
  copy missing from a listing as new as the registry read makes it `orphaned` and `copyMissing` for good (a deleted
  file's link never comes back; a member, not a state, so older versions do not show it as working), whatever the
  library holds; such an entry is never built again.
- Run-ending failures end the run; anything else, `SyncScopeMissingException` included, is
  `SyncSummary.haveSharedCopiesFailed`, which never makes a run incomplete.
