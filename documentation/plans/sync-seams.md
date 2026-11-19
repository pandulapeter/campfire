<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: sync's knowledge of songs behind seams

**Status:** done: C1–C7 landed and verified live on 2026-11-19 (`main` at ca2f34a5f, see Verification). Split out of
[sync-library-extraction.md](sync-library-extraction.md), which moves the result into `:libs:sync:*` modules later.

**Goal:** the sync engine's rules (what to upload, download, delete, keep, and call a conflict) depend on a few narrow
interfaces instead of songs, setlists, pictures, and the repositories directly, and a check keeps it that way. Everything
stays in today's modules. It pays off for Campfire alone:

- **Safety.** The rule that a picture or attachment a song names is never deleted becomes one interface
  ("what does this document reference?") that the planner and engine tests drive directly, instead of a ChordPro parse
  called from three places in `run/`.
- **Enforcement.** "The engine knows nothing of songs" becomes a failed commit, not a habit.
- **Google Drive.** The provider-neutral sign-in, transport, and credentials code is separated from Dropbox, and the
  redirect, desktop port, and app key are passed in, which Drive's D2 needs (its plan lists the redirect as Dropbox's
  in three places). Drive's D4 rewrites `SyncFolder` on ids; doing it over collections (C2) first means one rewrite.
- **Missing fixtures.** The desktop and web `sync-credentials.json` and the folder's `preferences.json` have no upgrade
  fixture today.

**Non-goals:** no new module, no behaviour change, no stored file changing shape (`sync-index.json`, the credentials,
the folder's `preferences.json`, `shared-links.json` and its mirror and journal), no UI change. Names, storage ports,
and shared primitives (`Logger`, `Sha256`, NFC) stay where they are; moving them only serves the extraction.

## Seams (Campfire-specific today → the interface → Campfire's side)

1. **Collections.** `LibraryFileKind` (`data/model/.../library/LibraryFile.kt`: SONG `songs`, SETLIST `setlists`, FILE
   `files`, `matches`, `isDocument`) is in `SyncFolder`'s signatures, `LibraryFileLocalSource`, `SyncKey`
   (`SyncModels.kt`, `path = "${kind.id}/$name"`), and the engine (14 imports). Interface: `SyncCollection(id, matches,
   maxSize, role)`, where `role` is `DOCUMENT` (counted by `DeletionGuard`, conflicts resolved) or `CONTENT_FILE`
   (named by its content, hashed by size in `LocalHashes.kt`, never a conflict, kept while referenced, restorable by
   `LostFileRestorer`), in a `SyncCollections` wrapper (never a `List` injected). Both live in `:data:model` (beside
   the file models), not in the remote api: `:data:source:local:api`, which holds `LibraryFileLocalSource`, sees only
   `:data:model`, and the two api modules stay apart. The Dropbox provider takes the collections at construction
   (`DropboxEntries.libraryKind()` mapped folder to kind; `remotePath` uses `kind.id`). Campfire declares three with the
   same ids, so index keys and remote paths are byte for byte the same.
2. **References.** `libraryFilesNamedBy` (`:libs:chordpro`) is called by `run/LocalListingReader.kt:88`,
   `run/ImageTransfers.kt:99`, and `run/LostFileRestorer.kt:79` to learn which files a song names, which
   `SyncPlanner.withImagesKept` (`SyncPlanner.kt:99`) keeps. Interface: `SyncReferences.of(collection, bytes):
   Set<String>?`, null for a collection that names none; "every song read" becomes "every referencing document read".
   Campfire: songs → `libraryFilesNamedBy(bytes.decodeLibraryText())`. `shared/SharedCopyContentsImpl.kt`'s chordpro
   import already sits behind `SharedCopyContents` and stays.
3. **Conflict policy.** `ConflictResolver.kt:85` special-cases SETLIST through `SetlistComparison` (a setlist differing
   only by date takes the remote one). Interface: an optional per-collection `ConflictPolicy`
   (`isSameApartFromVolatileParts`, `withoutVolatileParts`); Campfire puts `SetlistComparisonImpl` behind it. The
   planted demo hash (`plantedContentHash`) is already a constructor lambda and stays one.
4. **Limits.** `run/SyncFileLimits.kt` and `DropboxDownloads.MAXIMUM_DOCUMENT_SIZE` read `ImportLimits`. Interface:
   `SyncCollection.maxSize`, a `largeFileThreshold` and a document cap passed to the engine and provider; Campfire
   passes today's constants.
5. **Library refresh.** `scheduling/SyncLibraryRefresher.kt` holds `SongRepository`, `SetlistRepository`, and
   `ImageRepository` (and reads `DataState`). Interface: `SyncLibraryObserver` (`awaitFirstRead`, `onFileChanged`,
   `scheduleLiveRefresh`, `refreshAfterRun`, `rescan`); the throttling stays in `scheduling/`, the repository calls go
   to Campfire's implementation. `ImageRepository.withImagesKept` already reaches the engine as `imageWritesGate`.
6. **Synced preferences.** `preferences/` mixes a generic mechanism (one folder document, three-way merged as JSON
   against a base the index keeps, written against its revision, retried on conflict) with Campfire's shape
   (`SyncedPreferences` over `UserPreferences`, songs keyed by `LibraryFiles.identityKey`, `OWNED_FIELDS`,
   `OWNED_FIELDS_OF_4_7_0`). Split: `MergedFolderDocument` with a codec (snapshot, apply, respell, owned fields, file
   name). The index keeps its keys `syncedPreferences`, `syncedPreferencesFields`, and `syncedPreferencesRevision`
   (`index/SyncIndexDocument.kt:53-64`).
7. **Run steps.** `SyncRunner` calls `SyncedPreferencesSync` and `SharedCopySync` after the files, and
   `SyncConnectionManager.forgetSharedLinks` (`:391`) clears the mirror and journal under the run lock. Interface: a
   `SyncRunSteps` wrapper of `SyncRunStep(id, run(provider, completedIndex, documentRevisions): Boolean, forget())`,
   run inside the run lock in order, each failure reported as `SyncSummary.failedSteps` (replacing
   `havePreferencesFailed` and `haveSharedCopiesFailed`, in memory only). Shared links stay as they are, as a step.
8. **Provider configuration.** `DROPBOX_APP_KEY` and `CAMPFIRE_VERSION` are generated into the remote module;
   `REDIRECT_SCHEME = "campfire"` / `REDIRECT_HOST = "oauth"` (`auth/SyncRedirect.kt`), `DesktopSyncAuthenticator.PORT
   = 53682`, and `userAgent` are constants. Interface: `DropboxConfiguration(appKey, ...)` and
   `OAuthRedirectConfiguration(scheme, host, desktopPort)` built in `DataRemoteSourceModule`, the user agent passed
   in. `auth/`, `network/`, and `crypto/` become provider-core packages that import nothing from `dropbox/`.

## Steps

Each lands as one compiling commit with `desktopTest`, `spotlessCheck`, and the pre-commit checks green; a step that
touches platform source sets also compiles iOS (`linkDebugFrameworkIosSimulatorArm64`) and the web.

- **C1. Fixtures.** Done: `upgrade-5.5.0` fixtures, written by a worktree at tag 5.5.0, of the desktop/web
  `sync-credentials.json` (connected, and with a pending authorization; `SyncCredentialsUpgradeFrom550Test`) and the
  folder's `preferences.json` (`SyncedPreferencesUpgradeFrom550Test`).
- **C2. Collections.** Done: `SyncCollection` and `SyncCollections` in `:data:model` (not the remote api, which
  `LibraryFileLocalSource`'s module cannot see) replace `LibraryFileKind`, now deleted, everywhere; Campfire declares
  the three in `LibraryCollections`, with the large file threshold and the folder documents' cap, and
  `DataSyncModule` and `DataRemoteSourceModule` pass them to the engine and the Dropbox provider. The engine still names
  `LibraryCollections.SONGS`, `SETLISTS`, and `FILES` where C3's references and conflict policy will take over.
- **C3. References and conflict policy.** Done: `run/SyncReferences` (a `fun interface`, `of(document): Set<String>`)
  and `run/ConflictPolicy` (`isSameApartFromVolatileParts`, `withoutVolatileParts`), handed to `SyncEngine` as maps by
  collection rather than the one `of(collection, bytes): Set<String>?` sketched above: the engine also has to know
  whether a collection references anything without bytes in hand (a referencing document too large or unreadable stops
  every content file deletion; the planner holds deletions back while such a document comes down), which a map's keys
  answer and a null from `of` cannot. `SyncPlanner.planned` takes those keys as `referencing`. A reference names no
  collection, so `LostFileRestorer` asks each content collection in turn (one in Campfire, the same single request as
  before). Campfire's side is `LibrarySyncRules` (songs through `libraryFilesNamedBy`, setlists' policy over
  `SetlistComparison`), wired by `DataSyncModule.syncEngine`. No chordpro import under `run/`; the engine tests'
  `setlistComparison` fixture parameter stays, the planner tests gained a `referencing` argument;
  `SyncPlannerReferencesTest`, `SyncEngineReferencesTest`, and `SyncEngineConflictPolicyTest` drive a notes app's
  collections (`OtherAppLibrary`), `LibrarySyncRulesTest` Campfire's side. `SyncSimulationTest` passed 20,000 seeds by
  hand. Left for C4: `PassListing`'s `KEEP_AND_UPLOAD` still picks `LibraryCollections.SONGS` to drop forgotten songs from
  the synced `preferences.json` base, which is seam 6's codec (which documents the folder document keys entries by),
  not a reference or a conflict. Left for the extraction: `ImageTransfers` checks a content file against its name with
  `LibraryFiles.isLibraryFileNamedFor`, Campfire's content-name rule (`SyncCollection` could carry it, as its plan's
  "Names" says), and `run/`'s KDoc and log lines still speak of songs and pictures (C7).
- **C4. Observer, run steps, and synced preferences split.** Seams 5, 6, and 7. Done: `run/`, `index/`, `scheduling/`,
  `connection/`, and `merge/` import nothing from `data.repository`, `data.model.song|setlist|preferences|share`,
  `shared/`, `preferences/`, chordpro, or `LibraryCollections`; `SyncedPreferencesTest` and
  `SyncRunnerPreferencesRevisionTest` changed only at call sites (the JSON's bytes now come from `FolderDocumentJson`);
  `SyncSimulationTest` passed 50,000 seeds by hand.
  - **Seam 5.** `scheduling/SyncLibraryObserver` is `awaitFirstRead`, `refresh(keys)`, and `rescan`, what the code
    needed: `onFileChanged`, `scheduleLiveRefresh`, and `refreshAfterRun` are the throttle's and stay
    `SyncLibraryRefresher`'s. Campfire's is `LibrarySyncObserver` (the song, setlist, and image repositories).
  - **Seam 7.** `scheduling/SyncRunStep` is an interface rather than the sketched class: besides `id`, `run`, and
    `forget` it names its `documentNames` and reads one another device changed (`onDocumentChanged`), which is how
    `SyncRemoteWatcher` stopped naming the shared links. `run` takes the folder, the index, the listing's document
    revisions, and its shared files. `SyncRunSteps` wraps the list (`DataSyncModule.syncRunSteps`); Campfire's one step
    is `shared/SharedCopyStep`. `SyncSummary.failedSteps` holds ids; Campfire's are `LibrarySyncSteps` in
    `:data:model`, with `havePreferencesFailed` an extension there, so Settings reads the same.
  - **Seam 6.** The folder document is not a step: the index keeps its base and revision, the run pairs it with the
    listing, a stopped run saves its base, and the watch point compares it, so `SyncRunner` settles it before the steps
    and reports its failure under the codec's `id`. `merge/MergedFolderDocument` (one instance, Koin's
    `DataSyncModule.folderDocument`, taken as `MergedFolderDocument<*>`) holds the generic settle, `localChanges`, and
    what the engine, runner, and watcher ask of the base (`baseIn`, `unchangedIn`, `withoutEntriesOf`, `isOtherThan`);
    `merge/FolderDocumentJson` the bytes and the three-way JSON merge; `merge/MergedFolderDocumentCodec` the codec:
    shape (owned fields, readable, newer format, keys, respelling, the merge's tidying) and the device's values
    (`values`, `load`, `apply`). Campfire's is `preferences/SyncedPreferencesCodec` over `SyncedPreferencesDocument`;
    the codec's `keyedBy` collection replaces `PassListing`'s `LibraryCollections.SONGS`. `SyncIndexDocument.of` takes
    the owned fields. The tests' notes app has a codec too (`merge/NotesSettingsCodec`).
  - Also: `connection/` answered `SyncRepository.RestoreResult`, a `data.repository` type; it answers
    `RestoredConnection`, which `SyncRepositoryImpl` maps.
  - For C5–C7: the index's keys still say `syncedPreferences*` (stored names); C6's guard counts `merge/` among the engine's
    packages and forbids them `preferences/` (Campfire's codec) too; C7's docs describe the observer, steps, and codec.
- **C5. Provider core apart from Dropbox.** Seam 8. Done: `DropboxAuthorizationTest` checks the same authorization URL
  (now byte for byte, for both registered redirects); Android and iOS sign-in, the desktop loopback, and the web
  redirect compile; `auth/`, `network/`, and `crypto/` import nothing from `dropbox/` and name no app.
  - `auth/OAuthRedirectConfiguration(scheme, host, desktopPort)` with `uri` and `isRedirect`; Campfire's is
    `CAMPFIRE_OAUTH_REDIRECT` at the module root, a value rather than only a Koin definition
    (`DataRemoteSourceModule.oAuthRedirectConfiguration`) because the shells' `isSyncRedirect` (moved to the root
    package, Android's and iOS's) asks before an iOS cold start has a graph. The authenticators take it; the web's
    redirect stays the page. `onSyncRedirectReceived` stays in `auth/`, naming no app.
  - `dropbox/DropboxConfiguration(appKey, collections, maxDocumentSize)`, built in `syncProviders`, which keeps its "no
    key, no provider" rule; `DROPBOX_APP_KEY` and `CAMPFIRE_VERSION` stay generated.
  - `USER_AGENT` moved from `network/` to the module root and is passed to `createHttpClient(userAgent)`;
    `configureClient` takes it nullable (null in the browser) instead of `sendsUserAgent`.
  - `crypto/ContentHash.kt`'s `dropboxContentHash` moved to `dropbox/DropboxContentHash.kt`; `crypto/` is `Pkce` alone.
  - Tests: `OAuthRedirectConfigurationTest` (another app's scheme and host), `DesktopSyncAuthenticatorTest` (a
    configured port). For C6: `LoopbackRedirectListenerTest` (a test, under `auth/`) reads `CAMPFIRE_OAUTH_REDIRECT`;
    the guard checks main sources only. For C7: KDoc in `network/` still uses Dropbox as its example in places.
- **C6. The guard.** Done: `.github/scripts/check_sync_seams.py` (its own script, since `check_layers.py`'s table is
  `:presentation`'s layer order), with `test_check_sync_seams.py`, run by the pre-commit hook, the Claude Code edit hook,
  and `tests.yml` (`--all` judges the whole tree, which passed with no change). Main source files under `run/`,
  `index/`, `scheduling/`, `connection/`, and `merge/` import nothing of chordpro, `data.repository`,
  `data.model.song|setlist|preferences|share|importing`, `LibraryCollections`, `LibrarySyncSteps` (and
  `havePreferencesFailed`), `SetlistComparison`, `shared/`, `preferences/`, or the sync root's Campfire adapters
  (`LibrarySyncObserver`, `LibrarySyncRules`, `DemoLibraryRepositoryImpl`, `SyncRepositoryImpl`, `DataSyncModule`);
  the remote module's `auth/`, `network/`, and `crypto/` nothing of `dropbox/`, of the catalogue packages, or of the
  module's root package (`CAMPFIRE_OAUTH_REDIRECT`, `USER_AGENT`, the generated `RemoteConfiguration`). A star import
  fails when it would bring in a forbidden name. No exception list.
- **C7. Docs.** Done: `data/sync/implementation/CLAUDE.md` lists the seams and which side owns each, with the guard;
  the folder document's generic rules moved to a new `merge/CLAUDE.md`, `preferences/CLAUDE.md` keeping Campfire's
  shape; the package files, `data/model`'s, and the remote api and implementation ones say the same of collections,
  steps, and the provider core (the implementation's cut under its 1,500-word budget from about 2,300). KDoc and log
  lines in the engine packages and the provider core speak of documents and content files, Campfire's songs and
  pictures only as examples. The Drive plan's groundwork lists collections and the provider core.

## Risks

- **Stored formats.** Index keys are `"<collection id>/<name>"`, so the ids stay `songs`, `setlists`, and `files`. JSON
  key names come from property names; a renamed serialized property is a format change. C1's fixtures and the
  `upgrade-5.2.0` / `upgrade-5.4.0` tests guard this.
- **Lock order.** `imageWritesGate` outside `LibraryFileLock`, the repositories' locks before it, `NonCancellable`
  around a write and its `onLocalFileChanged`, the run lock around steps, the journal's lock inside it
  (`data/sync/implementation/CLAUDE.md`, Locks). Seams 5 and 7 move calls behind interfaces and must not move them in
  or out of a lock; the many-seed simulation run after C4 checks it.
- **Indirection.** Each seam is an interface with one implementation. Keep them to the eight above; anything else that
  only the extraction would need waits for it.

## Drive and this plan

Land C1–C7 before Drive's D-steps (after its spikes S1–S5, which are throwaway). Both rewrite `SyncFolder`'s
signatures, so running them at once is the one bad order. Afterwards Drive is one more package beside `dropbox/` over
the provider-core packages, constructed with its own `OAuthRedirectConfiguration`.

## Verification

Behaviour is unchanged when, after every step, these pass without edits other than imports and constructor wiring
(and, from C4, the folder document's bytes read through `FolderDocumentJson`):
`TwoDeviceSyncTest`, `SyncSimulationTest` (and by hand with far more seeds after C4), the engine, planner, scheduling,
connection, and shared-links tests, `SyncPlannerUpgradeFrom520Test`, `SharedLinksUpgradeFrom520Test`,
`SharedLinksUpgradeFrom540Test`, C1's `*UpgradeFrom550Test`s, and the Dropbox and transport tests. Once, after C5: a live sync of an
existing install against a real Dropbox (the owner's live sync test recipe) shows no download of unchanged files and no
conflict copy.

Done on 2026-11-19: a desktop home synced by the 5.5.0 release against the owner's real Dropbox (335 files), then two
runs of `main` at ca2f34a5f: zero downloads, uploads, deletions, conflicts, and failed steps. The folder was unchanged
except `/shared-links.json`, whose entries' `built.build` was re-stamped (60 → 61) by `SharedCopySync.isBuiltFrom`,
which re-packs copies on every build-number bump (behaviour since 5.5.0, not C1–C5; the re-packed zips were
byte-identical and not uploaded); that file was restored afterwards.
