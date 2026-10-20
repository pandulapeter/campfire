<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Skip the preferences download when the run's listing shows the revision of preferences.json the last run left

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — every test drove `SyncedPreferencesSync` or the `Device` mirror, so `SyncRunner`'s wiring (the one place that can silently turn the skip off, by asking `result.index`, whose revision `of` always leaves null, instead of the loaded index) went untested: a test through the real runner is added, with the index it saves. The Dropbox side drops an entry whose `rev` is empty (`DropboxEntry.rev` defaults to ""), and `unchangedPreferences` refuses an empty revision. The formats paragraph names every released reader (4.5.0 on), and the wider listing-to-settle window's cost to the two settle attempts is stated. Checked and kept: one index decode path, `ignoreUnknownKeys` in every release since sync shipped; the pair stays true on every write (`markedAsRunning` / `adoptedBy` copies of the loaded index, the engine's snapshots and the stop / guard / failure paths built from `of`); reconnecting another account fails the `accountId` check, a disconnect removes the index; a newer-format or older peer's document only ever changes the revision; the name is ASCII, so NFC / NFD folding cannot touch it, and another case only costs the download. Worth its surface: a third of an ordinary run and one request on every return and every edit.
**Files:**
`data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/sync/model/RemoteListing.kt`,
`data/source/remote/api/CLAUDE.md`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxEntries.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxChangeFeedTest.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/run/SyncEngine.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/index/SyncIndexDocument.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/index/CLAUDE.md`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/preferences/SyncedPreferencesSync.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/preferences/CLAUDE.md`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncRunner.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/FakeSyncProvider.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/Device.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/TwoDeviceSyncTest.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/preferences/SyncedPreferencesTest.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/index/SyncIndexDocumentTest.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncRunnerPreferencesRevisionTest.kt` (new)

Lane S, after 13. Shares only `DropboxChangeFeedTest.kt` with 13 (a separate test each); 01, 02, and 13 touch none of
its main sources.

## Problem

Every completed run ends by downloading `preferences.json`, whether or not it changed. `SyncedPreferencesSync.synchronize`
(`SyncedPreferencesSync.kt:116-118`):

```kotlin
repeat(MAXIMUM_ATTEMPTS) {
    val remote = provider.downloadDocument(SyncedPreferencesDocument.FILE_NAME)
    val remoteDocument = remote?.bytes?.let(SyncedPreferencesDocument::decode)
```

Measured live on the desktop against Dropbox (`live/B.log`): a run bringing one song in was `list_folder` (610 ms), the
song's download (835 ms), and the preferences download (843 ms), from `1792489513.678` to `1792489516.116`: the
document is about a third of the run's 2.4 s, and 0.6–0.9 s on every run measured. Most runs (an edit uploaded, a song
brought in) leave the document untouched.

The run already has what it needs to know the document did not change, except that both halves are thrown away:

- **The listing sees its revision.** `DropboxSyncProvider.list` lists the app folder recursively from `""`, so
  `/preferences.json` is among the entries with its `rev`; `toRemoteFiles` (`DropboxEntries.kt:15`) keeps only files
  in the two library folders, and `RemoteListing` (`RemoteListing.kt`) has only `files` and `cursor`.
- **The index keeps the document, not its revision.** `SyncIndexDocument.syncedPreferences` holds the document the last
  settle left on both sides; the revision `uploadDocument` answered (`RemoteWriteResult.Written(revision)`) or the one
  the download carried (`RemoteDocument.revision`) is dropped.

A revision is the folder's identity for one version of a file's content (the api notes: "Revisions are opaque strings",
compared for equality only), and the engine already skips a song's download on exactly this rule (index
`remoteRevision` equal to the listed one). Content still decides.

**Formats.** The cloud folder's format does not change: `preferences.json` is written as now, so older and newer peers
see nothing new. Only the device's own `sync-index.json`, which never leaves the device (not synced, not in system
backup), gains an optional field. Every released version decodes the index with `ignoreUnknownKeys = true` (4.5.0
through 4.7.0 in `SyncRepositoryImpl`'s `json`, 4.8.0 on in `SyncIndexStore`, today the only decode path; the tests'
`decodeSyncIndex` mirrors it), so a downgrade reads it and drops the field at
its next write, and an index without the field (every existing one) simply downloads as today. So no sync format
change is needed, and the plan holds.

## Fix

**The invariant** that makes skipping safe: the index pairs a revision with a document only when the folder's
`preferences.json` at that revision decodes to exactly that document. Then a listing that shows the same revision means
the folder still held those bytes when it was listed, and the document can be used in place of the download. Nothing
else changes in the settle: the merge, the pruning, the newer-format check, and the upload with its expected revision
run on it exactly as on a downloaded one.

1. **`RemoteListing`** (api): add

   ```kotlin
   /**
    * The revision of each folder-level document (see `SyncFolder.downloadDocument`) by name, as listed. A provider that
    * does not say leaves it empty, which costs the run a download, never a wrong answer.
    */
   val documentRevisions: Map<String, String> = emptyMap(),
   ```

   with a `@param` in the class KDoc beside `cursor`. In `data/source/remote/api/CLAUDE.md`, the `downloadDocument` /
   `uploadDocument` line: "never in `list`" becomes "never among `list`'s files; `RemoteListing.documentRevisions`
   names their revisions".

2. **Dropbox**: in `DropboxEntries.kt` add `internal fun List<DropboxEntry>.toDocumentRevisions() = filter { it.tag ==
   "file" && it.depth == 1 && it.rev.isNotEmpty() }.associate { it.name to it.rev }` (KDoc: the files at the top of
   the app folder, where `documentPath` puts the documents; an entry without a revision, which `DropboxEntry` decodes
   as "", names none, so it costs a download). In `DropboxSyncProvider.list`, collect it over every page as `files` is, and
   return `RemoteListing(files = files, cursor = ..., documentRevisions = documents)`. A document spelled with other
   case (`Preferences.json`) is then not found under `FILE_NAME`, which only costs the download.

3. **`SyncEngine`**: keep the last pass's `listing.documentRevisions` beside `cursor` (`var documentRevisions =
   emptyMap<String, String>()`, set where `cursor = listing.cursor` is) and add `val documentRevisions: Map<String,
   String>` to `Result.Completed` (KDoc: as the run's last listing named them). `SyncEngine.kt` is already over 500
   lines; growing it by a few is allowed by `check_sizes.py` (only crossing fails).

4. **`SyncIndexDocument`**: add, after `syncedPreferencesFields` (never rename it later, per `index/CLAUDE.md`):

   ```kotlin
   /**
    * The folder's revision of `preferences.json` whose content is exactly [syncedPreferences], or null where that is not
    * known. Written only by a run whose settle left that document in the folder (see `SyncRunner`); [of], which every
    * other write of a run starts from, leaves it null, so a run that ends any other way downloads the document next time.
    */
   val syncedPreferencesRevision: String? = null,
   ```

   `of(...)` does not set it (the default). `copy`s of a loaded document (`markedAsRunning`, `markedAsFinished`,
   `adoptedBy`) keep it beside the same `syncedPreferences`, which keeps the pair true. Add:

   ```kotlin
   /**
    * `preferences.json` as the folder still holds it, when [listedRevisions] (a listing's documentRevisions) show the
    * revision this index pairs with [syncedPreferences] for [accountId]: what downloading it would give, without the
    * download. Null otherwise. The raw document, not [syncedPreferencesBase], since it stands for the folder's bytes.
    */
   fun unchangedPreferences(accountId: String, listedRevisions: Map<String, String>): RemoteDocument? {
       val revision = syncedPreferencesRevision?.takeIf { it.isNotEmpty() } ?: return null
       val document = syncedPreferences ?: return null
       if (this.accountId != accountId || listedRevisions[SyncedPreferencesDocument.FILE_NAME] != revision) return null
       return RemoteDocument(bytes = SyncedPreferencesDocument.encode(document), revision = revision)
   }
   ```

   Encoding it back to bytes makes the settle decode it exactly as it would the download: `decode` is a JSON parse and
   `encode` prints the same tree sorted, and `JsonObject` equality ignores key order, so for a document that was itself
   decoded (or uploaded as `encode(document)`) the round trip gives an equal document.

5. **`SyncedPreferencesSync.synchronize`**: add a parameter `unchanged: RemoteDocument?` (KDoc: the folder's document
   as the last settle left it, when the run's listing shows it unchanged since, used in place of the first download; a
   retry after a conflict always downloads) and use it on the first attempt only:

   ```kotlin
   repeat(MAXIMUM_ATTEMPTS) { attempt ->
       val remote = unchanged.takeIf { attempt == 0 } ?: provider.downloadDocument(SyncedPreferencesDocument.FILE_NAME)
   ```

   Make it answer the revision with the document: return a small `SettledPreferences(document: JsonObject, revision:
   String?)` (a data class in the `preferences` package, own file, KDoc: the document a settle left in the folder and
   the folder's revision of it, null where the folder does not hold that document) instead of `JsonObject?`:
   - `if (merged == remoteDocument) return SettledPreferences(merged, remote?.revision)` (`remote` is non-null there,
     since `remoteDocument` is);
   - `is RemoteWriteResult.Written -> return SettledPreferences(merged, result.revision)`;
   - the newer-format branch returns `SettledPreferences(previous ?: JsonObject(emptyMap()), revision = null)`: the
     folder holds another document there, which must never be paired with this base, or the next run would merge
     against it and write over the newer format.
   Update the KDoc's return description. `synchronize` stays under 120 lines (about 100 after the change).

6. **`SyncRunner`** (the `Completed` branch, and `synchronizePreferences`): pass
   `unchanged = document.unchangedPreferences(connected.account.indexKey(), result.documentRevisions)`, where `document` is
   the index loaded at the start of the run (after `adoptedBy`; pass it into `synchronizePreferences`, or compute it at
   the call). Never `result.index`: `of` leaves its revision null, so the skip would silently never happen, which only
   the runner test below catches; then

   ```kotlin
   val completed = result.index.copy(
       lastSyncedAt = syncedAt,
       syncedPreferences = settled?.document ?: result.index.syncedPreferences,
       syncedPreferencesRevision = settled?.revision,
   )
   ```

   and `summary.copy(havePreferencesFailed = settled == null)`. The stopped-run path (`result.index.copy(syncedPreferences
   = base)`) and the failure path keep the null revision `of` gave `result.index`. The watch point keeps reading
   `completed.syncedPreferences`.

7. **Notes.** `preferences/CLAUDE.md`: add "The folder's document is not downloaded when the run's listing shows the
   revision the index pairs with the base (`syncedPreferencesRevision`, written only beside the document a settle left
   in the folder); a retry after a conflict always downloads." `index/CLAUDE.md`: add the field and that only a completed
   settle writes it.

**What changes in behaviour, and why it is safe.** Only one case differs from today: another device writes
`preferences.json` after this run's listing and before its settle (seconds, usually). With a change of this device's to
upload, nothing differs: the upload carries the stale expected revision, gets `Conflict`, and the second attempt
downloads and merges as now. With nothing to upload, this run does not apply that write; the index keeps the true pair
(the old document with the old revision), so the next run lists the new revision and downloads it, and while the app is
in front the watcher sees the write past the run's cursor and schedules that run at once. Nothing is lost or reverted.

The window is from the run's last listing to its settle, which on a large run (a first sync of a few hundred songs)
is minutes rather than the sub-second between a download and its upload today. A write elsewhere inside it costs
the first attempt (`MAXIMUM_ATTEMPTS` is 2), as a write between download and upload does today, so only the second
attempt's narrow window is left before the settle gives up for this run (`havePreferencesFailed`, settled by the
next). Rare, and recovered as today; no extra attempt is added.

## Tests

- `SyncIndexDocumentTest`: `` `an index pairs its preferences with the revision it saw` `` (an index with
  `syncedPreferencesRevision = "r1"` answers the document's bytes from `unchangedPreferences` for listed `"r1"`, and
  null for listed `"r2"`, for no listed document, for another account, and for an index whose revision is `""` against
  a listed `""`); `` `an index without a preferences revision
  never skips the download` ``. Check the existing real-file tests (`` `an index written before the newer fields existed
  is read whole` ``) still read with the field null, and add the field to `` `an index in the current format is read
  with every field` ``.
- `SyncedPreferencesTest` (calls `synchronize` directly; update the existing calls for the new parameter, `unchanged =
  null`, and the return type, `synced?.document`):
  - `` `a document the folder still holds as the last run left it is not downloaded` ``: folder `encoded(D)` at `"r1"`,
    `provider.onDownloadDocument = { throw AssertionError("Downloaded an unchanged document.") }`, a local change, and
    `unchanged = RemoteDocument(encoded(D), "r1")`: the folder ends with the change, and the answer's revision is the
    folder's new one.
  - `` `a document written elsewhere after the listing is merged on the second attempt` ``: the folder at `"r2"` holds
    another device's value, `unchanged` names `"r1"` with the older document, and a local change: both values end in the
    folder and here.
  - `` `a document written elsewhere after the listing, with nothing to upload, is not paired with its revision` ``: as
    above without the local change: nothing uploaded, the answer's revision is `"r1"`, not `"r2"` (so the next run
    downloads).
  - `` `a document of a newer format is never paired with a revision` ``: extend the existing newer-format test to assert
    the answer's revision is null.
- `FakeSyncProvider`: report `documentRevisions = documents.mapValues { it.value.second }` in `listing()` and count
  `downloadDocument` calls (`var documentDownloadCount = 0`).
- `Device.sync` mirrors `SyncRunner`: pass `index.unchangedPreferences(ACCOUNT_ID, completed.documentRevisions)` (`ACCOUNT_ID`
  from `SyncEngineFixtures.kt`, the account the device's runs file their index under) and keep the revision as step 6 does. Then in `TwoDeviceSyncTest`:
  `` `a device downloads the preferences only when another device changed them` ``: two devices with preferences in
  step; `a.sync(cloud)` again downloads nothing (`cloud.documentDownloadCount` unchanged); `b` changes a value and
  syncs; `a.sync(cloud)` downloads once and has the value; `assertConverges(a, b)`. The existing preferences cases in
  that file must pass unchanged.
- Through the real `SyncRunner` (a new `scheduling/SyncRunnerPreferencesRevisionTest.kt`, built with
  `TestSyncRepository.kt`'s `syncRepository(...)` as `SyncRunnerStoppedLateTest` is, since that file and
  `SyncRepositoryImplTest` are long already): `` `a run downloads the preferences only when the folder's revision moved` ``:
  a library of `song(1)` in step, the folder's document seeded at `"r1"` as in `SyncRunnerStoppedLateTest`, preferences
  that match it, a `FakeSyncIndexLocalSource()`; `restore {}`, a first `synchronize(ASK)` to `Success`; assert the saved
  index (`stateLocalSource.document`) has `syncedPreferencesRevision` equal to the folder's revision
  (`provider.documents.getValue(FILE_NAME).second`); a second run: `provider.documentDownloadCount` unchanged; then
  `provider.writeDocumentElsewhere(FILE_NAME, ...)` with another capo, a third run: one more download and the capo in
  `preferences.current`. Fails before the change on the second run's count, and fails if step 6 passes `result.index`.
  A second case, `` `a run stopped by the deletion guard leaves the revision it found` ``, is not needed: that path
  writes the loaded index's copy, whose pair is still true, or the engine's `of`, whose revision is null.
- `DropboxChangeFeedTest`, beside `` `a listing names the cursor of its last page` ``: `` `a listing names the revision
  of each document at the top of the folder` ``: entries `/preferences.json` (file, `rev` `"r9"`), `/songs` (folder),
  `/songs/a.cho` (file), and `/notes.txt` (file, no `rev`), across two pages; `documentRevisions ==
  mapOf("preferences.json" to "r9")` and the document is not among `files`.

Run `./gradlew :data:sync:implementation:desktopTest :data:source:remote:implementation:desktopTest`.
