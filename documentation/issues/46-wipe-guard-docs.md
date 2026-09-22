# 46 · The docs describe the sync wipe guard differently from the code

**Severity:** docs (all platforms. No behaviour changes) · **Area:** root `CLAUDE.md` (Sync section), `documentation/sync.md` ("How a run decides")

## Symptom
- The root `CLAUDE.md` says "A plan that would delete **more than half** of the files the index knows … is not
  carried out". The guard only counts deletions **on this device**. A plan that deletes every file from the cloud
  folder always runs.
- `documentation/sync.md`, for users: "A run that would delete most of your library — more than half of the files it
  has synced before, or all of them — stops". It leaves out the minimum of five. A library of four synced songs that
  loses three of them on another device does not ask.

## Cause
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt:137-138`:

```kotlin
val deletions = plan.count { it is SyncOperation.DeleteLocal }
if (deletionPolicy == SyncDeletionPolicy.ASK && index.isNotEmpty() && deletions.isTooManyToDeleteOutOf(index.size)) {
```

and `:558-559`:
`this > 0 && (this == total || (this >= MIN_DELETIONS_TO_ASK && this * 2 > total))` with `MIN_DELETIONS_TO_ASK = 5`
(`:596`). `data/repository/implementation/CLAUDE.md:125` already says "local deletions" correctly.

Counting only `DeleteLocal` is intended: a `DeleteRemote` is a deletion the user made on this device. Only the wording
is wrong.

## Fix
Docs only.

1. Root `CLAUDE.md`, Sync section: replace "A plan that would delete **more than half** of the files the index knows
   (and at least five of them), or every one of them, is not carried out:" with "A plan that would delete **on this
   device** more than half of the files the index knows (and at least five of them), or every one of them, is not
   carried out:". The rest of the bullet stays. Deletions made here and sent up to the folder are the user's own and
   never ask.
2. `documentation/sync.md`, "How a run decides": replace "A run that would delete most of your library — more than
   half of the files it has synced before, or all of them — stops before anything moves and asks." with "A run that
   would delete most of your library on this device — more than half of the files it has synced before and at least
   five of them, or all of them — stops before anything moves and asks." The rest of the bullet stays.

## Tests
None (docs).

## Verify
Read both paragraphs against `SyncEngine.isTooManyToDeleteOutOf`.

## Docs
This plan is the doc change.

## Touches
- `CLAUDE.md`
- `documentation/sync.md`

## Depends on
Nothing.
