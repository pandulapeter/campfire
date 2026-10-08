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

Implements `SyncRepository` and `DemoLibraryRepository` of `:data:repository:api`, on top of `:data:source:local:api`,
`:data:source:remote:api` and the other repositories' API — the song and setlist repositories it reads the library
through and refreshes after a run, and the preferences. Koin wiring: `Module.kt` holds the `@Module @ComponentScan object
DataSyncModule`, whose two `@Single` functions build what is not simply constructed — `SyncEnvironment` and
`SyncEngine` — and every other class is a `@Single` of its own. `LibraryFileLock` and `LibraryChanges` come from
`DataLocalSourceModule`, the `Logger` from `DataRepositoryModule`. Everything here is `internal`; the
`DemoLibraryRepositoryImpl` is here because the key it records a demo file under is `SyncKey.path`, the one the engine
looks it up by.

Sync is where local and remote meet, which is why it is a module of its own beside the repositories rather than part of
either source.
`SyncRepositoryImpl` is a facade over the classes that do the work, each a `@Single` of this module:
`SyncStateHolder` (the one `SyncState`), `SyncConnectionManager` (restore, connect, disconnect, forgetting),
`SyncRunScheduler` (when a run starts, the debounce), `SyncRunner` (one run: the engine, `preferences.json`, the index
writes, the outcome), `SyncIndexStore` (`sync-index.json` as a document) and `SyncLibraryRefresher` (the song and
setlist repositories told what a run changed). `SyncEngine` is built by `DataSyncModule.syncEngine`. Who holds
which lock:
  - `SyncConnectionManager`'s restore lock — never held across a run; forgetting the stored connection runs under it.
  - `SyncRunner`'s run lock (`withRunLock`) — held by a run, and by `disconnect` around the index deletion only.
  - `LibraryFileLock` — held by the engine around local file calls, and by the song and setlist repositories inside
    their own locks; never held across a request.
  - `SyncLibraryRefresher`'s `changedFilesMutex` — innermost: the engine reports a changed file both inside
    `LibraryFileLock` and outside it, so it is taken under `LibraryFileLock` and must never be held while anything
    takes `LibraryFileLock` — which is why the refresh lets go of it before it calls `refresh`.
`SyncPlanner` is a **pure function** of (local hashes, remote listing, the index of what the last run saw) and is
the one part of sync worth testing — `commonTest` covers every way a file can differ between two devices,
including the ones that would otherwise only show up as a song someone lost. `SyncEngine` carries the plan out and
applies `LibraryFileKind.matches` to both the remote listing and the index it loads, the same rule the local listing
applies, because a file listed on one side only reads as a deletion. Names are matched by case where a service
ignores it, and by Unicode form, which the file systems disagree about: a remote name that differs from a local one
only by case or by form takes the local spelling
(`foldRemoteNamesOntoLocal`), and an index entry whose name neither listing has moves to the one listed spelling
that folds to it (`foldIndexNamesOntoListings`). Without the second, a song moved to another spelling of its own
name was downloaded again after it was deleted. A download above `MAXIMUM_FILE_SIZE` (the largest
file an import reads, `ImportLimits.MAX_TEXT_FILE_SIZE`) is a per-file failure rather than filtered out of the
listing for the same reason. A local file larger than a run downloads is not read or uploaded either: it is left out
of the plan on both sides — its index entry too, so that it is not taken for a deletion — and named among the run's
failures. So is a remote file whose name this device's file system cannot hold (`LibraryFileLocalSource.canHoldFileName`:
no for a name that is a path on every platform, and for `? : * " < > |` on Windows), split off before the remote
names are folded onto the local ones so that it is never matched onto a local file either. So is a local file whose
read fails: it is folded onto and left out like a too-large one, but its index entry is kept — the planner just does
not see it — so the run that can read it again decides as usual; a library of which no file can be read ends the
run instead. A conflict's incoming version is written next to the local one *before* the local
one goes up, under a name free on both sides: the pass's remote listing is passed to the free-name search, since a
file under the copy's name that has not come down yet would otherwise be taken for the copy changed here. The copy
is taken back if the service then says the remote file is still there, so the version that loses is never held only
in memory. A setlist is the exception: one whose two versions differ only in the day they name — which every device
gives an undated setlist on its own, the day it first reads it — or where this device's only change is the day its
read gave an undated file (the index version, re-encoded with no day, is the local one's), takes the cloud folder's
version with no copy, written only over the bytes it was compared with (`ConflictResolver.resolveWith`, `takeRemote`,
built by the engine with its own collaborators and never by Koin). The engine
never sees the setlist format for it: `SetlistComparison` (`:data:source:local:api`) answers both questions. A day
set on purpose on two devices offline loses to the folder's. A demo file this device planted is the other exception:
one that still hashes to what `UserPreferences.demoLibraryContentHashes` recorded when it was planted, met with no
index entry (the first time this device compares that name with this folder), takes the folder's version the same
way, since it is another version's demo; the record is written by `DemoLibraryRepositoryImpl`, under the same
`SyncKey.path` and `localContentHash` the engine looks it up by, and the engine asks for it through the lookup
`DataSyncModule.syncEngine` builds it with — a `@Single` function rather than an annotated class, since the
Koin compiler plugin would hand an annotated constructor its `{ null }` default instead — and one with an index entry that is back at its planted bytes was changed back on purpose and keeps its copy. A
device that planted before the record existed has none and still makes copies. A download is decided about twice — before its request, so that a file already in step is
not transferred, and again just before the write, so that a save made while the request was in flight is resolved
as a conflict rather than written over; the index records the revision the download fetched rather than the one the
listing named, so a file another device wrote again in between is not this device's next edit's conflict, and a
conflict is uploaded over the revision it was compared with. That second check and the write, and every other change the engine makes to
a local file (a local deletion and the check before it, a conflict copy, taking a copy back), happen under
`LibraryFileLock`, a `@Single` the song and setlist repositories hold around their own writes from whatever they
check to the write, so that a save lands either before the engine's check, which then sees it, or after its write,
and is never overwritten or deleted with no conflict to show for it. It is only ever held around local file
calls, never a request, and is not reentrant: the repositories take their own locks first and this one inside
them, and nothing that holds it calls anything that takes it. The engine is written so that an
interrupted run leaves the library usable: the index (`SyncIndexDocument`, the on-disk shape of `sync-index.json`)
is only told about a file once that file has actually moved, so anything half done simply looks unsynced next time.
It is filed under the account's id as the service gives it (`SyncAccount.indexKey`), never under a name or an
address the user can change; an index an earlier version filed under the e-mail address is adopted on the next run
(`adoptedBy`) rather than ignored, since a run without an index brings back every file deleted since.
It is told as the run goes rather than when a pass completes: every finished operation
hands `SyncRunner` a way to take a snapshot, which it does at most every `INDEX_WRITE_INTERVAL` —
building one costs as much as the index is long — and once more on the way out of a stopped or failed run (the
periodic writer waited for first, not cancelled: on the web a cancelled write carries on in the browser, so it could
land after the final write). Those writes and the periodic one never throw (`SyncIndexStore.saveQuietly`): only the write
that opens a run and the one that completes it do, which is how a device that cannot write ends a run as
`SyncFailureReason.STORAGE`. Reading it is the same: an index that is there and cannot be read ends the run as
`STORAGE` before anything is written, and start up and a new connection leave such a file alone. Only one that reads
and does not decode is taken for none. The scope, the time source, the wall clock and the dispatcher documents are
coded on come from `SyncEnvironment` (a `@Single` function of `DataSyncModule`, the same shape as the repositories'
`RepositoryEnvironment`), which is what lets `SyncRepositoryImplTest`, the `SyncConnectionManager…Test` files,
`SyncRunSchedulerTest` and `SyncLibraryRefresherTest` run every
launched job and the ten-second debounce on the test scheduler's virtual time — the scope being
background work there, so a test advances it with `runCurrent` or `advanceTimeBy`, never `advanceUntilIdle`. The
repository's scope carries a `CoroutineExceptionHandler` that logs, since nothing
launched there has anyone to throw to, and a run that ends in a throwable that is not an `Exception` (a synchronous
`js(...)` failure on the web, a real `Error`) is finished and reported like a failed one rather than left to it. The failures that are only worth a line in the
log — a clean-up, a quiet index write, a file that could not be read — go through `recovering` (the same eight lines as the repositories' own), which rethrows a
cancellation, logs and falls back on any other `Exception` and catches nothing else; a block with an extra typed arm
or work to do on cancellation keeps its own `try`. An interrupted run therefore keeps what it transferred, and only a completed
one with no failed files moves `lastSyncedAt`.
The engine reports every library file it changes (`onLocalFileChanged`), once the change is on disk, and the library
counts are kept moving during a run by a live refresh of those files that waits five times what the previous one
took (`liveRescanPauseAfter`), so that re-reading what a run wrote never becomes most of what it does.
A run the app never came back from is found by the index's `isRunInProgress` marker at `restore`, reported as
interrupted next time, and that run is left for the user to start: `RestoreResult.wasInterrupted` keeps
`RestoreSyncUseCase` from starting one on launch, which would replace the message before it could be read. Only a
run somebody asked for, or a launch started, is treated so: the marker also says whether the run was an automatic
one (`isAutomaticRunInProgress`), and one of those — which being swiped away right after an edit routinely cuts
off — is cleared without a word and followed by the ordinary launch run, which carries the same changes.
**Automatic runs wait for the library to settle**: `scheduleSynchronization` — asked for by every change the app
makes to a song or setlist file, which the two repositories announce through `LibraryChanges` —
sets when the run is due, ten seconds after the latest request (`AUTOMATIC_RUN_DELAY`), and one `collectLatest` over
that moment is the debounce: a request that lands while the previous one is still waiting moves the start rather than
adding a run. A request made while a run is going is carried out after it, since the run may have read the file
before the change. The files a run writes itself go around the repositories, so no run schedules the next one.
`synchronize` — the button, a new connection and a launch — takes the place of a waiting run, `cancelSynchronization` drops it with the run it
stops, `disconnect` drops it too, and `startScheduledSynchronization` starts it at once, which the app asks for as
it leaves the front, the last moment a phone lets it start a run that survives the background. It starts the run
on the caller's thread rather than leaving it to the debounce, and answers the progress: `startRun` puts
`SyncProgress()` in the state before it returns — and a completion handler takes it down again for a run cancelled
before its body could — so the app can hand the run to the platform's keep-alive inside the same callback. The run slot is an
`AtomicReference` swapped with `compareAndSet`, since the buttons start runs from the main thread and the debounce
from the repository's own scope.
`restore` is asked once per ViewModel — on Android once per activity — so a call that finds the state already
`Connected` answers from it and reads nothing: read again from the disk, a run that is going would look like one
that was interrupted, and its marker would be cleared under it.
`commonTest` runs the engine against an in-memory `SyncProvider` and `LibraryFileLocalSource` for the behaviour the
planner's tests cannot show (the `SyncEngine…Test` files, one per behaviour, over the helpers in
`SyncEngineFixtures.kt`), and `SyncRepositoryImplTest` and the `SyncConnectionManager…Test` files run the repository
against the same fakes plus the ones in `FakeSyncCollaborators.kt`, built by `TestSyncRepository.kt`.
`SyncIndexDocumentTest` reads `sync-index.json` as devices already have it, written out by hand, since the store's
lenient decoding would read a renamed field as an empty index rather than fail. The pure parts of a run are tested on their own: `preparePass` (`PassListing.kt`,
`PassListingTest`) works out what one pass may touch from the two listings and the index, and `DeletionGuard`
(`DeletionGuardTest`) is the question below; the engine threads one `SyncRun` and one `SyncPass` (`SyncRun.kt`)
through its steps instead of their values one by one. A plan whose deletions on one side are more than half of the index (at least
`DeletionGuard.MIN_DELETIONS_TO_ASK` of them) or the whole of it is not applied under `SyncDeletionPolicy.ASK`, and neither is one
that would delete anything remotely while the local listing is empty and the index is not — a library folder that
went missing lists as empty on every platform, however small the library was. The engine returns
`Result.DeletionsNeedConfirmation` with the `SyncDeletionDirection` before those deletions move, asking about this
device first, and the repository reports it as the run's outcome, refreshing whatever an earlier pass of the same
run had already moved. `DELETE_LOCALLY` and `DELETE_REMOTELY` apply such a plan as it is;
`KEEP_AND_UPLOAD` first drops the index entries of every file that is here and not there, which the planner then
reads as new local files — and drops those songs from the last synced `preferences.json` too, so that their
overrides travel back with them rather than following a folder another device emptied — and `KEEP_AND_DOWNLOAD` those of every file that is there and not here, which it reads as
new remote ones. An answer waives the guard of its own direction only, so a run told to delete here still stops if it
would also empty the cloud folder. The policy is a parameter of the one run it was given to, never state. A failure on one file does not end a run; only the three failures
that make every further call pointless (the credentials refused, the service unreachable, the remote folder full,
the subclasses of the sealed `SyncRunEndingException`, whose `reason` is what the run is reported as) do — and a `CancellationException` is caught *first* and rethrown, since a stopped run is not a few
hundred files that failed. A file that failed is named in `SyncSummary.failed`, and a run that has any does not
move `lastSyncedAt`. So is a file the service still reported as contested in the last of the `MAXIMUM_PASSES`
passes (another device writing it under every upload): the two sides still differ, and the run did not settle it. Operations run `CONCURRENT_TRANSFERS` at a time within each ordering group rather than
one after another: every one of them is a request, and serialising them made a first sync as slow as the round
trip times added up. The remote deletions are the exception: they go to `SyncProvider.delete` in one call, so that
a large deletion spends as little time as possible half done, which is what another device's guard would see. On
Dropbox that is still about seven files a second, so a device that syncs during a large approved deletion can
still see less than half of it gone and follow that part without asking; its next run asks about the rest. `SyncRunScheduler` owns an application-lifetime scope, so a run outlives the screen and
(on Android) the activity that started it, and `SyncLibraryRefresher` is what tells the song and setlist
repositories to read the files it changed again (`refresh`, never a whole rescan) — after a completed run, and after a stopped or failed one
(`finishRunCutShort`, always under `NonCancellable`), since files that moved before the run ended are on disk
whichever way it ended. A live refresh that is stopped puts back what it had not read. Only a run that ends in a
throwable that is not an `Exception`, which may have come from the middle of a write, rescans the whole library. The use case cannot, now that it returns before the run does. A run
waits for a first read of the library that is still going before it reads any local file, so that a connected
launch does not read every file twice at once; a repository that has been read, or has failed, is not waited for.
Disconnecting cancels a run that is still going and waits for it; once it has begun to take the connection apart it
is carried to the end whoever cancels its caller, and a run that finds the state `Connected` but no provider
connected turns it into `ConnectionFailed` rather than doing nothing, so an account whose credentials are gone never
stays on screen with a button that cannot work. A run the service refuses (`SyncAuthorizationException`, which the
provider only throws once a renewal has been refused) ends the same way, rather than as a failed outcome under the
account: its only way on would be Disconnect, which deletes the index, while Connect keeps the index for the same
account. Backing out of that Connect — the consent page closed, the waiting given up — returns to the failed state
rather than to disconnected while the provider still holds the refused credentials, since the next launch restores a
connection from them; a failure that stored none backs out to disconnected. Disconnecting deletes the index under the run lock, so that a run stopped a
moment earlier has finished writing it, and a run only ever writes its outcome into a state that is still
`Connected`: a run that outlived the account it ran against must not bring that account back on screen.
**`preferences.json` is settled after the files**, by `SyncedPreferencesSync`, and only after a run the engine
completed: the songs it may name are then the ones the run left on both sides. `SyncedPreferencesDocument` keeps it
as a JSON tree and merges it three ways, value by value, against the document the last run settled
(`SyncIndexDocument.syncedPreferences`, carried through every snapshot the engine hands out and dropped with the rest
of an index written for another account); this device's side is that base with this version's own fields replaced
by the preferences (`localDocument`), so whatever a later version writes passes through untouched — a value of one of
those fields that this version cannot read (a tempo of 400, a capo of 13) included, like an unknown field. A document that
is missing from the folder, does not decode or holds no `songs` object (`isReadable`) is merged as the base, so it
is replaced with this device's values rather than read as a removal of everything; one whose `version` is newer
than this version's (`isNewerFormat`) is neither applied nor written over, and the step answers the base it was
given (an empty one where there was none), so the run still counts as successful. A song no longer
in the library — compared by case and Unicode form, the run's failed files counting as there, and a song the folder's
document has gained since the last synced one counting as there too, since it may have reached the folder after the
run listed it — is dropped from the merged document before it is applied. Entries are matched to songs by case and Unicode form too, and applied under
this device's spelling of the file, which is the one its screens read; the document keeps one spelling per song,
the first in sort order of those the folder's document (then the base) already holds, all three sides being put on
it before the merge, so two devices that spell one file differently share its overrides. The preferences are changed before the upload, so a failed upload only leaves
the next run a change to carry; a value changed here while the merge ran is kept (`SyncedPreferences.applyTo`). A
conflict merges again against the document it lost to, once; a document that still cannot be settled, or any
failure but the authorization's, is reported as `SyncSummary.havePreferencesFailed` (which, like a failed file, keeps
the run from counting as the last successful one) and leaves the base where it was.
The player's chord shapes (`UserPreferences.chordVoicings`) are a `chords` member beside `songs`, by instrument and
then by the chord's id, merged the same way value by value; a shape is any string and an instrument this version does
not know is kept, a non-string value passes through, nothing in it is ever dropped with a song, and the member is only
written once there is something in it, so a document from before it stays the same bytes.
`localChanges` is what schedules a run when the three maps or the chord shapes change, filtering out the values the
step wrote itself.
`SyncedPreferencesTest` covers the merge, the document and the step against the fakes.
`cancelConnection` is the way out of `Connecting` that does not need the `connect()` that got there to be running
still — on the web it never is, and a page restored from the back/forward cache is otherwise connecting for good.
`connect` never throws anything but a cancellation — every way out of it leaves `Connecting`, and every way out of it
that does not end connected forgets whatever the provider stored during the token exchange
(`forgetStoredCredentials`, no request), so an authorization given up after the tokens arrived is not a connection
the next launch restores and syncs. A clean-up the storage refuses is logged rather than allowed to replace the outcome. `restore` never throws
for a service that refuses the stored credentials — the app starts disconnected and says so — nor for credentials
the device cannot read right now, which it reports as a connection failure with a storage reason and leaves alone,
and a redirect that
no authorization is waiting for is ignored, the stored account restored as usual. It shows the account from what is
stored and asks the service behind that, so a slow network never makes a connected account look disconnected; a
refusal that arrives later takes the connection down then. A forgetting of a previous installation's credentials
that failed is noted (`SyncIndexLocalSource.setForgettingCredentialsOwed`) and retried by every `restore` before
anything else is read; while it keeps failing, `restore` answers disconnected — not a storage failure like
unreadable credentials, since what cannot be removed belongs to an earlier installation, and connecting again, which
writes over it and crosses the note off, is the right answer to it.

Tested with `commonTest`, run with `./gradlew :data:sync:implementation:desktopTest`.

## Sync as the app sees it

- `SyncProvider` sees one flat remote folder addressed by `(kind, name)`, the same shape the library has. Revisions
  are **opaque strings** the engine never parses, and a service's content hash stays in the provider — which is what
  keeps Drive's file ids and MD5s out of the engine when it arrives. What is not a song or a setlist by its extension
  is invisible to the engine on both sides, so whatever else the user keeps in the folder is left alone. A remote file
  whose name the device cannot hold (a `\` anywhere, or `? : * " < > |` on Windows) is left out too, and named once in the run's summary
  rather than failed on every run.
- `SyncPlanner` is a pure function of (local hashes, remote listing, the index of what the last run saw) and is the
  part that is tested. Content decides what changed, never a clock: the platforms disagree about modification times
  and the web has none. An edit always beats a deletion.
- A plan that would delete, **on this device or in the cloud folder**, more than half of the files the index knows
  (and at least five of them), or every one of them, is not carried out: the run stops before anything moves and
  Settings asks, naming the side. On this device that is the shape of a remote folder that was emptied, renamed or
  replaced; in the cloud folder, of a library folder that was moved or deleted under the app — which lists as empty,
  so an empty library with an index that is not always asks, however small. Carried out faithfully either would leave
  every device with only what had been edited since the last run. **Delete them here too** / **Delete them from the
  cloud too** runs again with the deletions allowed; **Keep them and upload** / **Keep them and download** runs again
  with those files' index entries dropped, so they are new on the side that still has them and are copied back. An
  answer waives the guard of its own direction only, this device being asked about first. The answer belongs to that
  one run, and an ordinary run asks again for as long as the folder stays that way. The one run that starts with the
  cloud folder's answer already given is the one Settings' library deletion starts, since typing `DELETE` in a sheet
  that says the folder goes too is that answer.
- A fresh installation never inherits a connection: a launch that finds no preferences document forgets whatever
  credentials a previous installation left in a store that outlived it (the iOS Keychain), locally and without a
  request, before anything restores them (`ForgetSyncConnectionUseCase`), so no run starts on an account nobody
  connected here. One that cannot forget them notes that it still owes it, in a file of its own (removed with the
  app, unlike the Keychain), and every start up tries again and restores nothing until it has; connecting on this
  installation crosses the note off.
- A run belongs to the **app**, not to the screen that started it: `SyncRepository` is a singleton with its own
  scope, so a run carries on while the user moves around or leaves. Android keeps the process alive with a
  foreground service and iOS with a background task, both driven by `SyncNotifier`, which each app shell provides
  the way it provides `FilePicker`. The strings are resolved in the UI so the notification follows the language
  chosen *in the app*, not the system's.
- `SyncEngine` runs the plan a few files at a time rather than one after another (which made a first sync one round
  trip per file), except the remote deletions, which go to the provider in one call — on Dropbox one batch job, about
  seven files a second rather than one — so that the folder spends as little time as possible half deleted, the
  state in which another device's guard can let part of a large deletion through unasked. It retries when the service asks
  it to slow down — being rate limited is the expected answer to a first sync of a whole library, not a reason to
  give up on it. A file that fails on its own is named in the
  run's summary rather than ending it, and such a run does not count as the last successful one.
- **A run starts on its own at launch and after every change the app makes to a song or a setlist** — a save, a tag, a
  new, imported, renamed or deleted file. The launch's starts at once; a change's ten seconds after the latest such
  request, so a burst of edits or an import is one run (`SyncRepository.scheduleSynchronization`). A request made during
  a run is carried out after it. Every run that starts at once — the launch's, Sync now, and the first run after
  connecting — takes the place of one that is waiting; Stop drops the waiting one too. The
  app leaving the front starts a waiting run at once, since a phone only keeps alive a run it was told about while the
  app was still in front, and a desktop quit hides the window and lets the run finish (for up to fifteen seconds, then
  stops it) before the process ends. The files a run writes go around the repositories that announce changes, so a run never
  schedules the next one.
- The index carries an "a run was going" marker, written before anything moves and cleared when it finishes, so a
  run the app never came back from — killed, swiped away, suspended by iOS — is reported as interrupted next time
  rather than silently forgotten, and that run is left for the user to start rather than started on launch. That is
  only for a run the user or a launch started: an automatic one cut short is not reported, and the launch run that
  follows carries its changes.
- A file changed on both sides is never merged: the local one keeps the name and the incoming one lands next to it
  as ` (2)` — or the first number free both on this device and in the cloud folder, so that it never takes the name
  of a file still on its way down — a name of the other device's making, numbered the way any document is, rather
  than with the underscore a name the app derived itself collides with (`_2`) — except a setlist whose two versions
  differ only in the day they name, which every device gives an undated setlist on its own, or where this device's
  only change is the day its read gave an undated file, or a demo file this device planted that still holds exactly
  what was planted, met in the folder for the first time: the cloud folder's version is taken.
- **The library's per-song overrides travel too**: the transposition, tempo and capo of a song opened from the library
  (`UserPreferences.transpositions`, `tempos`, `capos`; a setlist's own are in its file already) are one
  `preferences.json` at the top of the cloud folder, beside `songs/` and `setlists/`, where the engine never looks:
  `{"version": 1, "songs": {"<file name>": {"transposition": 2, "tempo": 92, "capo": 1}}}`, an entry only for a song
  something is set for. Every run that completes ends by settling it (`SyncedPreferencesSync`) — a three-way merge,
  value by value, of this device's, the folder's and the last synced one, which the index keeps — so two devices that
  changed different songs or fields both keep their change, a change beats a removal, and two changes of one value
  keep this device's. It is merged as a JSON tree and this version only writes the fields it knows, so settings that
  have nothing to do with the songs can join `songs` at the top level later without an older version dropping them;
  a document that is missing or cannot be read is taken as unchanged and replaced with this device's values, never
  read as one that removed everything, and one whose `version` is newer than this one's is left alone.
  A song no longer in the library after the run takes its entry with it, here and in the folder, unless the run
  failed to move it or it reached the folder after the run listed it. **The player's chord shapes travel the same way**
  (`UserPreferences.chordVoicings`): a `chords` member beside `songs`, by instrument and then by the chord's notes
  (`{"guitar": {"F:0.4.7": "x x 3 2 1 1"}}`), merged value by value, so two devices that chose for different chords both
  keep their choice and two choices for one chord keep this device's; an instrument this version does not know passes
  through, and no entry is ever dropped with a song, since none belongs to one. A change to those three maps or to the
  chord shapes schedules a run like a change to a file does; the run's own write does not.
- Authorization is OAuth 2.0 with PKCE and no client secret, which is what lets this work with no backend. The four
  platforms get back from the consent page in four different ways, all behind `SyncAuthenticator`.

## Renames

- A rename reaches **sync** as a deletion and a new file, since `SyncPlanner` is keyed by name and knows no moves. The
  "an edit beats a deletion" rule then applies: a device that edited the file under its old name since the last run
  puts that file back, leaving both.
