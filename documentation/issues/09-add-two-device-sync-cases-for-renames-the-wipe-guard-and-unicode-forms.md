# Add two-device sync cases: a rename against an edit, the wipe guard across devices, and two Unicode forms of a name

**Challenged:** amended — the rename case starts from `devicesInStep()` (a one-song library makes the rename a
whole-library deletion, which A's own guard stops) and covers both orders; the wipe case pins count, total, and A's
`DELETE_REMOTELY` run; `Device` keeps `sync` asserting; the NFD name is written with escapes; the revision check covers
the passed `cloud` and its documents, and an existing case it fails is a finding, not a weakened check.

**Kind:** test  ·  **Severity:** medium  ·  **Platforms:** all (common tests)
**Files:** `data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/TwoDeviceSyncTest.kt`,
`.../Device.kt`

## Problem

`TwoDeviceSyncTest` (real engine, one fake cloud folder) has eight cases but none for three documented data-loss paths:
- "A rename reaches sync as delete + create, and an edit beats a deletion" (`data/sync/implementation/CLAUDE.md`):
  A renames a song while B edits the old name — both files must survive on both devices. No test at any level.
- The wipe guard across devices: after A deletes most of the library, B's run must stop and ask
  (`DeletionsNeedConfirmation(direction = LOCAL)`), and `KEEP_AND_UPLOAD` must restore the cloud folder. `Device.sync`
  asserts `Completed`, so it cannot express this.
- Dropbox treats NFC and NFD spellings as one path (memory: measured). The fake's `ignoresCase` mode now folds Unicode
  form too (c3cff5b92), but no two-device case uses it.
Also `assertConverges` compares bytes only, so a pair of runs that re-uploads identical bytes every time passes.

## Fix

1. Keep `Device.sync(cloud, deletionPolicy = SyncDeletionPolicy.ASK)` as the asserting one (existing calls unchanged)
   and add `syncResult(cloud, deletionPolicy)` returning the `SyncEngine.Result`; `sync` calls it, asserts
   `Completed`, and only then stores the index and settles preferences.
2. Cases:
   - `a song renamed on one device and edited under its old name on the other is kept twice`: start from
     `devicesInStep()` (three songs; with `x.cho` alone, A's rename deletes everything the index knows and A's own run
     stops with `DeletionsNeedConfirmation(REMOTE)`, see `DeletionGuard.isTooManyToDeleteOutOf`). Both orders in one
     case, as the edit-beats-deletion case does: A renames `song_1` to `song_one`, B renames `song_2` to `song_two`
     (delete + create with the same bytes); B edits `song_1`, A edits `song_2`; A syncs, B syncs, A syncs. Expected
     everywhere: `song_1` (B's text), `song_one` ("Song 1"), `song_2` (A's text), `song_two` ("Song 2"), `song_3`; then
     `assertConverges`. A's rename reaches the folder before B's edit (B re-uploads the edited file); B's rename comes
     after A's edit (B's run downloads `song_2` back).
   - `a device that sees most of the library deleted elsewhere asks before following`: `Device(librarySongs(10))` and
     an empty `Device()`, both synced. A deletes 6 and syncs with `DELETE_REMOTELY` (6 of 10 is over the guard:
     at least `MIN_DELETIONS_TO_ASK` = 5 and more than half). B's `syncResult` equals
     `DeletionsNeedConfirmation(count = 6, total = 10, direction = LOCAL)`, B still holds all 10, and the cloud the
     4; B syncs with `KEEP_AND_UPLOAD` (waives the local guard; `preparePass` drops the index entries of files here
     and not there, so they upload); the cloud holds all 10; A syncs and gets the 6 back; `assertEverywhere` the 10,
     then `assertConverges`.
   - `a song spelled in two Unicode forms on the two devices settles on one file`: `FakeSyncProvider(ignoresCase =
     true)`; A holds `song("caf\u00e9")`, B `song("cafe\u0301")` with identical bytes. Write both with `\u` escapes,
     never the literal characters, so no editor or formatter folds them into one. Mirror the existing case test
     (`a song spelled with different case on the two devices settles on one file`): each device keeps its own spelling,
     the cloud A's, B's edit reaches A.
3. `assertConverges` also compares the revisions of the `cloud` it is given (its parameter, not `this.cloud`): files
   (`files.mapValues { it.value.second }`) and documents (`documents.mapValues { it.value.second }`; the preferences
   step skips an unchanged upload), so a converged pair of runs writes nothing. If an existing case fails it, that is a
   finding: report it rather than weaken the check.

## Tests

This is the test. If a case fails on current code, `@Ignore` it with the reason and report it as a finding.
