<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :data:sync:implementation

Implements `SyncRepository` and `DemoLibraryRepository` over `:data:source:local:api`, `:data:source:remote:api`, and
the song, setlist, and preferences repositories' API. Everything is `internal`. `DataSyncModule` builds
`SyncEnvironment` and `SyncEngine` with `@Single` functions; `SyncEngine`'s demo lookup must stay a module function,
since the Koin plugin would hand an annotated constructor its `{ null }` default. `DemoLibraryRepositoryImpl` lives here
because it records demo files under `SyncKey.path`, the key the engine looks them up by.

## Locks

- `SyncConnectionManager`'s restore lock: never held across a run; forgetting the stored connection runs under it.
- `SyncRunner`'s run lock (`withRunLock`): held by a run, and by `disconnect` around the index deletion only.
- `LibraryFileLock`: held by the engine around local file calls only, never a request. Not reentrant: the song and
  setlist repositories take their own locks first and this one inside; nothing holding it calls anything that takes it.
- `SyncLibraryRefresher.changedFilesMutex`: innermost. Taken under `LibraryFileLock`, so never held while anything takes
  `LibraryFileLock` (the refresh releases it before calling `refresh`).

## Invariants

- **Content decides what changed, never a clock**: the platforms disagree about modification times and the web has
  none. **An edit always beats a deletion.** A file changed on both sides is never merged.
- `SyncPlanner` is a pure function of (local hashes, remote listing, index); keep it pure and tested.
- `LibraryFileKind.matches` is applied to the remote listing and the loaded index as well as the local listing: a file
  listed on one side only reads as a deletion. Anything else in the cloud folder is invisible and left alone.
- Names match ignoring case and Unicode form: a remote name differing from a local one only that way takes the local
  spelling (`foldRemoteNamesOntoLocal`), and an index entry neither listing has moves to the listed spelling that folds
  to it (`foldIndexNamesOntoListings`; without it a song moved to another spelling was re-downloaded after deletion).
- Files left out of the plan on both sides, and named among the run's failures, rather than read as deletions:
  - a download above `MAXIMUM_FILE_SIZE` (= `ImportLimits.MAX_TEXT_FILE_SIZE`) is a per-file failure, not filtered out
    of the listing;
  - a local file larger than that is not read or uploaded, and its index entry is left out too;
  - a remote name this device cannot hold (`LibraryFileLocalSource.canHoldFileName`) is split off *before* name folding
    so it is never matched onto a local file, and named once rather than failing every run;
  - a local file whose read fails keeps its index entry (the planner just does not see it), so a later run decides as
    usual. If no file at all can be read, the run ends as a storage failure.
- **Conflicts**: the incoming version is written next to the local one *before* the local one is uploaded, as ` (2)` or
  the first number free on both sides (the pass's remote listing is passed to the free-name search, so a file not yet
  downloaded is not taken for the copy). The copy is taken back if the service then says the remote file is still
  there, so the losing version is never held only in memory. A conflict is uploaded over the revision it was compared
  with. Two exceptions take the cloud folder's version with no copy (`ConflictResolver.resolveWith` / `takeRemote`,
  built by the engine, not Koin), written only over the bytes compared:
  - a setlist whose versions differ only in the day, or whose only local change is the day a read gave an undated file
    (`SetlistComparison` answers both, so the engine never sees the setlist format). A day set on purpose on two
    offline devices loses to the folder's;
  - a demo file this device planted that still hashes to `UserPreferences.demoLibraryContentHashes` and has no index
    entry (it is another version's demo). With an index entry it was changed back on purpose and keeps its copy.
- **A download is decided twice**: before the request (skip files in step) and just before the write, so a save made
  meanwhile becomes a conflict. The index records the revision the download fetched, not the listing's. The second check
  and the write, and every other local change (deletion and its check, conflict copy, taking a copy back), happen under
  `LibraryFileLock`, which the repositories hold from their check to their write: a save lands before the engine's
  check or after its write, never overwritten or deleted silently.
- **Deletion guard** (`DeletionGuard`, under `SyncDeletionPolicy.ASK`): a plan is not applied if its deletions on one
  side exceed half the index (and number at least `MIN_DELETIONS_TO_ASK`) or are the whole index, or if it would delete
  anything remotely while the local listing is empty and the index is not (a missing library folder lists as empty on
  every platform). The engine returns `Result.DeletionsNeedConfirmation` with the `SyncDeletionDirection` before those
  deletions move, asking about this device first; the repository reports it as the outcome and refreshes what earlier
  passes moved.
  - `DELETE_LOCALLY` / `DELETE_REMOTELY` apply the plan as is. `KEEP_AND_UPLOAD` drops the index entries of files here
    and not there (so they read as new local files) and drops those songs from the last synced `preferences.json` so
    their overrides travel back with them; `KEEP_AND_DOWNLOAD` is the mirror image.
  - An answer waives its own direction's guard only. The policy is a parameter of one run, never state; an ordinary run
    asks again while the folder stays that way. Settings' library deletion is the one run started with the cloud
    folder's answer already given.
  - Remote deletions go to `SyncProvider.delete` in one call so the folder spends as little time as possible half
    deleted (what another device's guard would see). On Dropbox that is still ~7 files/s, so another device may follow
    part of a large approved deletion unasked; its next run asks about the rest.
- **Failures**: one file failing does not end a run; only `SyncRunEndingException` subclasses do (credentials refused,
  service unreachable, folder full), reported with their `reason`. `CancellationException` is caught *first* and
  rethrown. A file named in `SyncSummary.failed`, or still contested after the last of `MAXIMUM_PASSES`, keeps the run
  from moving `lastSyncedAt`. Operations run `CONCURRENT_TRANSFERS` at a time within each ordering group.
- Log-only failures go through `recovering` (rethrows cancellation, catches only `Exception`). The repository scope has
  a logging `CoroutineExceptionHandler`; a run ending in a non-`Exception` throwable (a `js(...)` failure, an `Error`)
  is finished and reported like a failed one, and rescans the whole library since it may have come mid-write.

## The index

- Told about a file only once it has actually moved, so an interrupted run's half-done work just looks unsynced.
- Filed under `SyncAccount.indexKey` (the service's account id), never a user-changeable name or address. An index an
  earlier version filed under the e-mail address is adopted (`adoptedBy`): a run without an index brings back every
  file deleted since.
- Snapshots are written at most every `INDEX_WRITE_INTERVAL` and once more on the way out of a stopped or failed run,
  after waiting for (not cancelling) the periodic writer: a cancelled write on the web carries on and could land last.
  Those writes never throw (`SyncIndexStore.saveQuietly`); only the write opening a run and the one completing it do,
  ending the run as `SyncFailureReason.STORAGE`.
- An index that exists and cannot be read ends the run as `STORAGE` before anything is written; start-up and a new
  connection leave it alone. Only one that reads but does not decode is taken for none.
- `SyncIndexDocumentTest` reads hand-written index files as devices have them, since lenient decoding would read a
  renamed field as an empty index. Never rename its fields.
- The `isRunInProgress` marker is written before anything moves and cleared at the end. Found at `restore`, it is
  reported as interrupted and `RestoreResult.wasInterrupted` stops the launch run (which would replace the message). An
  automatic run (`isAutomaticRunInProgress`) is cleared silently and followed by the ordinary launch run.

## Scheduling and lifetime

- A run belongs to the app: `SyncRunScheduler` owns an application-lifetime scope. Android keeps the process with a
  foreground service and iOS with a background task, both via the shell-provided `SyncNotifier` (strings resolved in the
  UI, so they follow the in-app language).
- `scheduleSynchronization` (every app change to a song or setlist file, announced through `LibraryChanges`; synced
  preference changes via `localChanges`) sets the run `AUTOMATIC_RUN_DELAY` after the latest request; one `collectLatest`
  is the debounce. A request during a run is carried out after it. Files a run writes go around the repositories, so a
  run never schedules the next one.
- `synchronize` (button, new connection, launch) replaces a waiting run; `cancelSynchronization` and `disconnect` drop
  it. `startScheduledSynchronization` (the app leaving the front, the last moment a phone keeps a run alive) starts it on
  the caller's thread and puts `SyncProgress()` in the state before returning, so the shell can hand the run to the
  platform keep-alive in the same callback; a completion handler clears it if the run is cancelled before its body. The
  run slot is an `AtomicReference` swapped with `compareAndSet` (main thread and repository scope both start runs).
- `SyncLibraryRefresher` re-reads only the files a run changed (`onLocalFileChanged`, `refresh`, never a rescan), after
  any ending (`finishRunCutShort`, under `NonCancellable`). The live refresh during a run waits five times what the last
  one took (`liveRescanPauseAfter`); a stopped one puts back what it had not read.
- A run waits for an in-progress first library read before reading local files; a read or failed repository is not
  waited for.
- `SyncEnvironment` supplies the scope, time sources, and dispatcher so tests run on virtual time; the scope is
  background work there, so advance with `runCurrent` / `advanceTimeBy`, never `advanceUntilIdle`.

## Connection

- `restore` is called once per ViewModel; finding `Connected` it answers from state and reads nothing (re-reading would
  make a running run look interrupted and clear its marker). It shows the stored account and asks the service behind
  it, so a slow network never shows disconnected. It never throws for refused credentials (starts disconnected) or for
  credentials unreadable right now (connection failure with a storage reason, file left alone); a redirect no
  authorization waits for is ignored.
- A previous installation's credentials that could not be forgotten are noted
  (`SyncIndexLocalSource.setForgettingCredentialsOwed`) and retried by every `restore` first; until then `restore`
  answers disconnected. Connecting again writes over them and clears the note. A start-up carrying the answer to a waiting
  authorization forgets nothing, even if it looks like a first launch.
- `connect` never throws but cancellation; every exit not ending connected calls `forgetStoredCredentials` (no request),
  so an abandoned authorization is never restored next launch. A refused clean-up is logged, not made the outcome.
  `cancelConnection` leaves `Connecting` without the original `connect()` running (on the web it never is).
- A run finding `Connected` but no connected provider, or refused by the service (`SyncAuthorizationException`, thrown
  only after renewal fails), sets `ConnectionFailed` rather than a failed outcome: Disconnect would delete the index,
  Connect keeps it. Backing out of that Connect returns to the failed state while the provider still holds credentials;
  if none were stored, to disconnected.
- Disconnecting cancels and awaits a running run, then deletes the index under the run lock. Once it starts tearing
  down it completes regardless of caller cancellation. A run writes its outcome only into a state still `Connected`.

## Synced preferences

`preferences.json` sits at the top of the cloud folder beside `songs/` and `setlists/`, never listed by the engine:
`{"version": 1, "songs": {"<file>": {"transposition", "tempo", "capo", "beatLevels": {"3/4": [...]}}}, "chords": {...},
"tags": {...}}` — the library's per-song overrides (`UserPreferences.transpositions`, `tempos`, `capos`, `beatLevels`),
chord shapes (`chordVoicings`, keyed by instrument or `ChordTuning.voicingKey`, then chord id), and tag colors
(`tagColors`). `chords` and `tags` are written only once non-empty, so older documents keep their bytes.

- Settled by `SyncedPreferencesSync` only after a run the engine completed. Three-way merge, value by value (each beat
  signature a value), of this device's, the folder's, and the base the index keeps (`SyncIndexDocument.syncedPreferences`,
  dropped with an index for another account). A change beats a removal; two changes keep this device's.
- Merged as a JSON tree: this side is the base with only this version's fields replaced (`localDocument`), so unknown
  fields and unreadable values of known fields (tempo 400, capo 13) pass through untouched.
- The index names the fields the base's writer owned (`syncedPreferencesFields`; absent means transposition, tempo,
  capo, and chords). Other fields are removed from the base (`syncedPreferencesBase`) so a newly owned field merges as
  add-only on the first run after upgrade. A new field joins `SyncedPreferencesDocument.OWNED_FIELDS`.
- A folder document that is missing, does not decode, or has no `songs` object (`isReadable`) is merged as the base,
  never as a removal of everything. One with a newer `version` (`isNewerFormat`) is neither applied nor overwritten, and
  the run still succeeds.
- A song no longer in the library loses its entry, except a file the run failed on or one the folder's document gained
  since the last sync. Chord and tag entries are never dropped with a song. Entries match songs ignoring case and Unicode
  form, are applied under this device's spelling, and the document keeps one spelling per song (first in sort order of
  the folder's, then the base's).
- Preferences are changed before the upload; a value changed here during the merge is kept (`SyncedPreferences.applyTo`).
  A conflict merges once more; failure otherwise sets `SyncSummary.havePreferencesFailed`, which keeps the run from
  counting as successful, and leaves the base unchanged.
- `localChanges` schedules a run when these values change, filtering out the step's own writes.

## Renames

A rename reaches sync as a deletion plus a new file (`SyncPlanner` knows no moves). By "an edit beats a deletion", a
device that edited the old name since the last run puts it back, leaving both.

Tests: `./gradlew :data:sync:implementation:desktopTest`.
