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
`DataRepositoryModule.syncEngine` builds it with — a `@Single` function rather than an annotated class, since the
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
`RepositoryEnvironment`), which is what lets `SyncRepositoryImplTest`, `SyncRunSchedulerTest` and
`SyncLibraryRefresherTest` run every
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
planner's tests cannot show, and `SyncRepositoryImplTest` runs the repository against the same fakes plus the ones
in `FakeSyncCollaborators.kt`. The pure parts of a run are tested on their own: `preparePass` (`PassListing.kt`,
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
