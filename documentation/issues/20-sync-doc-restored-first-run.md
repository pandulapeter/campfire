# 20 — The sync guide promises that a restored phone's first sync "duplicates nothing"

**Severity:** docs — a user-facing promise the code does not keep · **Area:** `documentation/sync.md` (and a test that
pins the behaviour down)

**Read, not run.** This was found by reading the planner at HEAD (`2065e47f`); the behaviour described is what
`SyncPlanner` returns for an empty index, which its unit tests can show without a device.

> **Work in progress at review time.** `documentation/sync.md` had uncommitted modifications by another agent (the
> Dropbox folder's name in "What it sees"); the paragraph this plan changes was not among them, but apply the edit
> to whatever is committed.

Reviewer finding 3-sync#6.

## What the user sees

`documentation/sync.md:64-70` ("Signing in"):

> On a phone neither the token nor the record of the last run is part of the device's backup, so a phone restored
> from one, or a new phone the library was moved to, starts disconnected: you sign in again, and the first run compares
> the two sides by content **and duplicates nothing**.

A user restores a three-month-old phone backup, signs in, and syncs. Since that backup:

- a song was **deleted** on another device → it is uploaded again from the restored phone and reappears everywhere;
- a song was **renamed** (Update file name, or a setlist retitled) on another device → the old name is uploaded again,
  and the song exists twice, under both names;
- a song was **edited** on another device → the restored phone has the old text under the same name; with no index,
  both sides "changed", and the conflict rule keeps the **local** (older) version under the name and brings the newer
  one down as `name (2).cho` — nothing is lost, but the newer edit is now the copy.

## Cause

With no index (`SyncPlanner.kt:131-144`), a file on one side only is always new, and a file on both sides with
different content is always a conflict:

```kotlin
        local != null -> when {
            // Never seen by a sync run: new here, and the remote has no opinion about it yet.
            indexEntry == null -> SyncOperation.Upload(key, expectedRevision = null)
            ...
        }

        remote != null -> when {
            indexEntry == null -> SyncOperation.Download(key, remote.revision)
            ...
        }
```

and at `:117-127` both `hasChangedLocally` and `hasChangedRemotely` are true when `indexEntry == null`, so differing
content is a `Resolve`, whose local side keeps the name. That is the deliberate, safe reading — without the index a
deletion cannot be told from a file that is new — so the code is right and the sentence is wrong. The root
`CLAUDE.md` (HEAD `:70-74`) says only "its first sync run compares by content", which is true.

## The change

Docs only; no behaviour change is proposed. `documentation/sync.md`, the sentence in "Signing in":

> …starts disconnected: you sign in again, and the first run compares the two sides by content. Files that are the same
> on both sides are simply recorded, and nothing is deleted anywhere. What changed elsewhere since the backup comes
> back the safe way rather than the tidy way: a song deleted on another device is uploaded again, a song renamed there
> is there under both names, and a song edited there keeps the phone's older text under its name, with the newer one
> next to it as ` (2)`. Nothing is lost; a few things may need tidying up.

Optional behaviour change, **not** part of this plan (listed so the user can decide whether to plan it): on a run
with no index, a local-only file whose content hash equals a remote file under another name could be taken for a
rename done elsewhere and not uploaded. It handles the rename case only, needs the provider's content hash for every
remote file (Dropbox has it; a future provider may not), and it trades a harmless duplicate for a guess — which is why
the reviewer marked it optional.

## Tests

Pin the documented behaviour so the doc and the planner cannot drift apart again. In
`data/repository/implementation/src/commonTest/.../sync/SyncPlannerTest.kt` (check first whether equivalent cases
already exist; if so, add only the missing ones, named for the restore scenario):

1. `` `with no index a file only this device has is uploaded` `` → `Upload(key, expectedRevision = null)`.
2. `` `with no index a file only the cloud has is downloaded` `` → `Download`.
3. `` `with no index a file that differs on the two sides is resolved rather than overwritten` `` → `Resolve`.
4. `` `with no index a file that is the same on both sides moves nothing` `` → the planner returns `Resolve` for it
   (both "changed"), and the engine turns identical content into an index entry without a transfer
   (`SyncEngine.resolve` → `isSameContent`); if that is the case at HEAD, assert it at the engine level instead:
   `SyncEngineTest`, same bytes on both sides, no index → `summary.hasChanges == false`, no upload, one index entry.

## Verification

Read the new paragraph against the four tests; nothing to run on a device.

## Docs

- `documentation/sync.md` as above.
- `documentation/testing/07-sync-multi-device.md:110-112` (SYNC-006) expects "no `(2)` copies, nothing re-uploaded that
  is already the same" after a reinstall — that is right for its setup (nothing changed elsewhere), so no change; if a
  restore-from-old-backup case is wanted there, it belongs to lane E's testing-docs plan (47), not here.
- Root `CLAUDE.md`: no change.

## Files touched

- `documentation/sync.md`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncPlannerTest.kt`
  (and/or `SyncEngineTest.kt` for case 4)

## Depends on

Nothing. `documentation/sync.md` is also edited by plans 09 and 16 (other paragraphs) and had uncommitted work at
review time — rebase only.
