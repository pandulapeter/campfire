<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: the sync library, extracted inside the repo

**Status:** draft, checked against the code on 2026-11-18 (`main` at 85e23a2db). Nothing is implemented.

**Goal:** the sync engine, the provider API, the Dropbox provider, and what they need of local storage live in
app-agnostic `:libs:sync:*` modules, with Campfire their first and only consumer. A later app (a diary with attachments,
a notes app) would declare its own collections and get sync; a later provider (Google Drive) would be one library
module every app gets. `campfire-library` proves the split: it fails the build when a `:libs:*` module depends on a
project outside `:libs:` (`gradle/build-logic/.../plugins/LibraryPlugin.kt`, every configuration, tests included).

**Non-goals:** no behaviour change, no stored file changing shape (`sync-index.json`, the credentials, the folder's
`preferences.json`, `shared-links.json` and its mirror and journal), so existing installs keep syncing with no
re-download; no new provider; no UI change. Later, not this plan: publishing the modules as an external artifact (with
a package rename away from `com.pandulapeter.campfire`), a second app, a shared sync UI module.

## Target modules

```
libs:sync:api              explicitApi(). Collections, keys, SyncFolder / SyncConnection / SyncProvider /
                           SyncProviders, SyncAuthenticator, ConnectivityMonitor, exceptions, state models
                           (SyncState, SyncProgress, SyncOutcome, SyncSummary, SyncAccount, SyncProviderId, ...),
                           storage ports, LibraryFileLock, name identity (NFC + case fold), Sha256, localContentHash
libs:sync:engine           run/, index/, scheduling/, connection/, the generic half of preferences/ (three-way JSON
                           merge of one folder document, its base kept in the index)
libs:sync:provider-core    network/SyncHttpTransport, Backoff, HttpClientHolder and the platform clients, auth/
                           (OAuthTokens, SyncCredentialsStore and its document, PKCE, pending authorization, the four
                           platform authenticators, loopback listener, redirect), connectivity monitors. Ktor.
libs:sync:provider-dropbox dropbox/ and its tests; the app key, document size cap, and collections passed in
libs:storage               (step E11, independent) FileStorage, SecretStore, their platform implementations and
                           contract tests, with the directories declared by the app
```

`provider-core` is a module of its own (the brief suggested folding it into the Dropbox one) because Drive needs every
line of it: PKCE, token renewal, the 401 rule, retries, and the four authenticators are provider-neutral today
(`data/source/remote/implementation/CLAUDE.md`, "Every provider's rules"). `storage` is `libs:storage`, not
`libs:sync:storage`: the engine needs only the ports in `api`, while `FileStorage` serves the whole app (imports,
staging, exports, OPFS). `shared-links` stays in Campfire for this plan (seam 7).

What stays in Campfire: `:data:sync:implementation` becomes the adapter (collections, references, setlist comparison,
the synced preferences codec, the library refresher, `SyncRepositoryImpl`, `DemoLibraryRepositoryImpl`, shared links,
`DataSyncModule`); `:data:source:remote:implementation` keeps covers, catalogues, and import downloads, and builds the
Dropbox provider; `:data:source:local:implementation` implements the storage ports over `FileStorage`.

**Koin:** the `libs:sync:*` modules do not apply `campfire-koin` and carry no annotations; Campfire's
`DataSyncModule` and `DataRemoteSourceModule` build them with `@Single` functions. Unlike `libs:metronome` (one
`@Single` class with no app input), almost every engine class needs an app-supplied value Koin cannot inject: three of
the four definitions in `data/sync/implementation/.../Module.kt` and the Dropbox provider are already hand-built for
this reason. It also keeps the Koin compiler plugin's per-Kotlin-version pin out of a later artifact. The "never inject
a `List`" rule still applies to what the app binds: collections go in a `SyncCollections` wrapper.

## Seams (Campfire-specific today → generic interface → where Campfire's side lives)

1. **Collections.** `LibraryFileKind` (`data/model/.../library/LibraryFile.kt`: SONG `songs`, SETLIST `setlists`, FILE
   `files`, `matches`, `isDocument`) is in `SyncFolder`'s signatures, `LibraryFileLocalSource`, `SyncKey`
   (`SyncModels.kt`, `path = "${kind.id}/$name"`), and the engine (14 imports). Generic: `SyncCollection(id, matches,
   maxSize, role)`, where `role` is `DOCUMENT` (counted by `DeletionGuard`, conflicts resolved) or `CONTENT_FILE`
   (named by its content, hashed by size in `LocalHashes.kt`, never a conflict, kept while referenced, restorable by
   `LostFileRestorer`). The Dropbox provider takes the collections at construction (`DropboxEntries.libraryKind()` maps
   folder to kind today; `remotePath` uses `kind.id`). Campfire declares three with the same ids, so index keys and
   remote paths are byte for byte the same.
2. **References.** `libraryFilesNamedBy` (`:libs:chordpro`) is called by `run/LocalListingReader.kt:88`,
   `run/ImageTransfers.kt:99`, and `run/LostFileRestorer.kt:79` to learn which files a song names, which
   `SyncPlanner.withImagesKept` (`SyncPlanner.kt:99`) keeps. Generic: `SyncReferences.of(collection, bytes):
   Set<String>?`, null for a collection that names none; "every song read" becomes "every referencing document read".
   Campfire: songs → `libraryFilesNamedBy(bytes.decodeLibraryText())`. The fourth chordpro import,
   `shared/SharedCopyContentsImpl.kt`, already sits behind `SharedCopyContents` and stays.
3. **Conflict policy.** `ConflictResolver.kt:85` special-cases SETLIST through `SetlistComparison` (local api: a
   setlist differing only by date takes the remote one). Generic: an optional per-collection `ConflictPolicy`
   (`isSameApartFromVolatileParts`, `withoutVolatileParts`). Campfire: `SetlistComparisonImpl` behind it. The planted
   demo hash (`plantedContentHash`, a lambda over `UserPreferencesRepository` in `Module.kt`) is already a seam and
   stays a constructor lambda; `SyncKey.path` becomes public so `DemoLibraryRepositoryImpl` records the same keys.
4. **Limits.** `run/SyncFileLimits.kt` and `DropboxDownloads.MAXIMUM_DOCUMENT_SIZE` read `ImportLimits`. Generic:
   `SyncCollection.maxSize`, a `largeFileThreshold` and a document cap in the engine and provider configuration;
   Campfire passes today's constants.
5. **Library refresh.** `scheduling/SyncLibraryRefresher.kt` holds `SongRepository`, `SetlistRepository`, and
   `ImageRepository` (and reads `DataState`). Generic: a `SyncLibraryObserver` (`awaitFirstRead`, `onFileChanged`,
   `scheduleLiveRefresh`, `refreshAfterRun`, `rescan`); the throttling stays in the engine, the repository calls go to
   Campfire. `ImageRepository.withImagesKept` already reaches the engine as the `imageWritesGate` lambda.
6. **Synced preferences.** `preferences/` mixes a generic mechanism (one folder document, three-way merged as JSON
   against a base the index keeps, written against its revision, retried on conflict) with Campfire's shape
   (`SyncedPreferences` over `UserPreferences`, songs keyed and respelled by `LibraryFiles.identityKey`,
   `OWNED_FIELDS`, `OWNED_FIELDS_OF_4_7_0`). Generic: `MergedFolderDocument` with an app codec (snapshot, apply,
   respell, owned fields, file name). The index keeps its keys `syncedPreferences`, `syncedPreferencesFields`, and
   `syncedPreferencesRevision` (`index/SyncIndexDocument.kt:53-64`): the library supports one such document, and the
   names stay because the stored format does.
7. **Run steps and shared links.** `SyncRunner` calls `SyncedPreferencesSync` and `SharedCopySync` after the files, and
   `SyncConnectionManager.forgetSharedLinks` (`:391`) clears the mirror and journal under the run lock. Generic: a
   `SyncRunSteps` wrapper of `SyncRunStep(id, run(provider, completedIndex, documentRevisions): Boolean,
   forget())`, run inside the run lock in order, each failure reported as `SyncSummary.failedSteps` (replacing
   `havePreferencesFailed` and `haveSharedCopiesFailed`, in memory only). Shared links (`shared/`, 15 main files, the
   `CAMPFIRE_BUILD_NUMBER` generation in `build.gradle.kts`) stay in the adapter as a step; their song, setlist,
   picture, and attachment rules are Campfire's, and Drive's P3 (share links v2) reshapes them. A
   `libs:sync:shared-links` with `SharedCopyContents` as the app's payload builder is possible once a second app wants
   links.
8. **Names.** `LibraryFiles.identityKey`, `isSameLibraryName`, and `normalizedToNfc` (expect/actual in `:data:model`)
   are the folder's identity rule (`SyncFolder` KDoc) and move to `api`; `:data:model` delegates. `normalizedName`, the
   `artist-title` policy, and `isLibraryFileNamedFor` (content names) stay in the app; the content-name check reaches
   the engine through `SyncCollection` (`ImageTransfers.kt:33, 192`).
9. **Storage ports.** The engine reads `LibraryFileLocalSource` (collection files, free-name writes, `canHoldFileName`),
   `SyncIndexLocalSource`, and, through `SyncCredentialsStore`, `SyncCredentialsLocalSource`; `LibraryFileLock` and
   `LibraryStorageException` come from local api. All move to `api` as ports (typealiases left in local api for one
   step); their implementations stay in `:data:source:local:implementation` until E11.
10. **Configuration.** `DROPBOX_APP_KEY` and `CAMPFIRE_VERSION` are generated into the remote module from
    `campfire.dropbox.appKey` and `campfire.versionName`; `REDIRECT_SCHEME = "campfire"` / `REDIRECT_HOST = "oauth"`
    (`auth/SyncRedirect.kt`), `DesktopSyncAuthenticator.PORT = 53682`, and `userAgent` (`Campfire/<version> (<url>)`)
    are constants. Generic: `DropboxConfiguration(appKey, ...)` and `OAuthRedirectConfiguration(scheme, host,
    desktopPort)` and a user agent passed in; the generation tasks stay in the Campfire modules. The manifest and
    `Info.plist` registrations stay in the apps. `isSyncRedirect` / `onSyncRedirectReceived` (used by
    `CampfireMainActivity.kt` and `IosFileImport.kt`) move to `provider-core` with the scheme as a parameter.
11. **Shared primitives.** `Logger`, `recovering`, `JsonBounds`, `Sha256`, `isOutOfSpace`, and `SystemBrowser` /
    `AuthorizationCompletionPage` live in `:data:model`. `api` gets what the engine needs (`SyncLogger` as a fun
    interface the adapter wraps `Logger` in; `Sha256`, `JsonBounds`, the completion page and browser types moved, with
    `:data:model` typealiasing or delegating). Whether a typealias keeps every SAM call site compiling on all four
    targets is a question for E0.

## Steps

Each lands as one compiling commit with `desktopTest`, `spotlessCheck`, and the pre-commit checks green; every step that
moves a source set also compiles iOS (`linkDebugFrameworkIosSimulatorArm64`) and the web. Moves are `git mv`, so blame
follows.

- **E0. Spike (half a day, throwaway).** Answer: typealiases for the moved `:data:model` types (SAM constructors of
  `Logger`, sealed `SyncState` `when`s) on Native and Wasm; whether the Koin compiler plugin's graph check accepts
  `@Single` functions returning library types from modules without Koin; whether `engine` takes `explicitApi()`
  too (the default answer: `api` only, as with the other libs). Done: three answers written into this plan.
- **E1. Guard and fixtures.** Extend `LibraryPlugin`: a `:libs:sync:*` module may depend only on `:libs:sync:*`, so
  `:libs:chordpro` cannot slip in (the existing rule would allow it), with a `build-logic` test. Add `upgrade-5.4.0`
  fixtures, written by a worktree at tag 5.4.0, for what has none: the desktop/web `sync-credentials.json` (connected,
  and with a pending authorization) and the folder's `preferences.json`, each with a test reading it. Done: the tests
  pass on today's code.
- **E2. Collections inside the current modules.** Introduce `SyncCollection` and `SyncCollections` (in remote api for
  now) and replace `LibraryFileKind` in `SyncFolder`, `LibraryFileLocalSource`, `SyncKey`, the engine, and the Dropbox
  provider; Campfire declares the three. Seams 1 and 4. Done: `SyncPlannerUpgradeFrom520Test`, `TwoDeviceSyncTest`,
  `SyncSimulationTest`, and `DropboxFilesTest` green with only call sites changed; no `LibraryFileKind` left under
  `run/`, `index/`, `dropbox/`.
- **E3. References and conflict policy.** Seams 2 and 3 in place, still in `:data:sync:implementation`. Done: `grep -rn
  chordpro run/` empty; `SyncPlannerImageTest`, `SyncEngineSetlistDateTest`, and `SyncEnginePlantedDemoTest` unchanged.
- **E4. Observer, run steps, and synced preferences split.** Seams 5, 6, and 7: `SyncLibraryRefresher`'s repository
  calls behind `SyncLibraryObserver`; `SyncRunner` and `SyncConnectionManager` drive `SyncRunSteps`; `preferences/`
  split into the merge and Campfire's codec. Done: `run/`, `index/`, `scheduling/`, `connection/`, and the merge import
  nothing from `data.repository`, `data.model.song|setlist|preferences|share`, or `shared/`; `SyncedPreferencesTest`
  and `SyncRunnerPreferencesRevisionTest` unchanged.
- **E5. `libs:sync:api`.** Create the module; move the provider API (`data/source/remote/api/.../sync/`, `hashing/`,
  `ConnectivityMonitor`), the sync state models, identity and NFC, `Sha256`, the storage ports, and `LibraryFileLock`
  (seams 8, 9, 11), with their tests (`RedirectUriTest`, `SyncRunEndingExceptionTest`, `LocalContentHashTest`, the
  identity tests). The old modules re-export or typealias for this one step. Done: `campfire-library` passes;
  `api` depends on coroutines and serialization only.
- **E6. `libs:sync:engine`.** Move `run/`, `index/`, `scheduling/`, `connection/`, the merge, `SyncEnvironment`,
  `SyncStateHolder`, and the tests that use only them (planner, engine, deletion guard, index, scheduling,
  `SyncPlannerUpgradeFrom520Test` with its `upgrade-5.2.0/preferences/sync-index.json` and songs), rewriting their
  fakes over a test collection set with Campfire's ids. Done: engine tests green in the new module;
  `TwoDeviceSyncTest` and `SyncSimulationTest` still green in the adapter (see Verification).
- **E7. `libs:sync:provider-core`.** Move `network/` (sync transport, backoff, client holder and factories, stall and
  declared-length helpers, `Json`), `auth/`, `crypto/`, and the connectivity monitors with their platform source sets
  and tests (`OAuthTokensTest`, `SyncCredentialsStoreTest`, `PendingAuthorizationStoreTest`, `SyncHttpTransportTest`,
  `HttpClientHolderTest`, `LoopbackRedirectListenerTest`); seam 10's redirect and user agent. The remote module's
  covers and catalogues use the same `HttpClientHolder`, so the app still builds one client. Done: Android and iOS
  sign-in, desktop loopback, and web redirect compile; the four `HttpClientFactory` actuals moved with Ktor engines.
- **E8. `libs:sync:provider-dropbox`.** Move `dropbox/` and its 12 test files; the app key, document cap, and
  collections come in through `DropboxConfiguration`. `DataRemoteSourceModule.syncProviders` keeps its
  "no key, no provider" rule. Done: `DropboxAuthorizationTest` checks the same authorization URL.
- **E9. Wiring and old seams removed.** Drop E5's typealiases where only imports remain; `DataSyncModule` builds the
  engine, steps, and observer; `app:di` names no new module (none has Koin). Done: `:data:source:remote:api` holds no
  `sync/`; `:data:sync:implementation` depends on `libs:sync:engine` and `api` only through the adapter.
- **E10. Docs.** One `CLAUDE.md` per new module, carrying the contract and invariant sections from today's files
  (`data/sync/implementation/CLAUDE.md` and its six package files, remote api and implementation); the root
  `CLAUDE.md` module graph; and its Privacy section, which says `:data:source:remote:implementation` is "the only
  module that makes a network call" and will name `libs:sync:provider-*` too. Done: the `CLAUDE.md` names check passes.
- **E11. `libs:storage` (optional, independent of E2–E10's order after E5).** Move `storage/file/` and
  `storage/secret/` with `StorageDirectory` turned into app-declared directories (path segments, backup policy) and
  the contract tests (`iosSimulatorArm64Test`, `wasmJsBrowserTest` in headless Chrome); the sync port implementations
  (collection files, index, credentials) follow as generic ones. Open for the spike of this step: `takeInto(file: Any)`
  takes the web's `PickedFile` (`:data:model` `web/`), `OutOfSpaceException` is the import's too, and `FileNames.kt`'s
  numbering is shared by the import and sync's conflict copies. Done: both contract suites green.

## Drive and this plan

Neither depends on the other. If extraction lands first, Drive's provider is `libs:sync:provider-googledrive` over
`provider-core`, and its D-steps change paths only; its P1 capabilities belong in `api`. If Drive lands first, it is one
more `dropbox/`-like package that E7 and E8 move. Doing both at once is the one bad order: both rewrite `SyncFolder`'s
signatures (E2 here, D4 there).

## Risks

- **Stored formats.** Index keys are `"<collection id>/<name>"`; the collection ids must stay `songs`, `setlists`,
  `files`. JSON key names come from property names, so a rename of a serialized property is a format change; moved
  `@Serializable` sealed types need `@SerialName` (the journal's have it). The E1 fixtures and the existing
  `upgrade-5.2.0` / `upgrade-5.4.0` tests guard this, read from their new modules.
- **Lock order.** `imageWritesGate` outside `LibraryFileLock`, the repositories' locks before it, `NonCancellable`
  around a write and its `onLocalFileChanged`, the run lock around steps, the journal's lock inside it
  (`data/sync/implementation/CLAUDE.md`, Locks). Seams 5 and 7 move calls behind interfaces; they must not move them
  in or out of a lock. `SyncSimulationTest` with many seeds by hand after E4 and E6.
- **Test reach.** `TwoDeviceSyncTest` and `SyncSimulation` drive the whole Campfire wiring (`TestSyncRepository`,
  `FakeSyncCollaborators` with `SongRepository` and `UserPreferences`, `libraryFilesNamedBy` for references), and the
  plugin checks test configurations too, so they cannot move into `libs:` as they are. They stay in the adapter as the
  end-to-end proof; the engine gets its own generic two-device test only if a second app needs one.
- **Visibility.** Today's `internal` across `run/`, `scheduling/`, and `connection/` becomes `public` between engine and
  adapter; keep the adapter-facing surface to the engine's entry points, the ports, and the step and observer types.
- **Platforms.** `AndroidSyncAuthenticator` takes a Koin `@Provided Context`; without Koin it is built in the
  adapter's `androidMain`. The iOS framework links the new modules through `app:di`; no Swift code names the moved
  types (checked: only Kotlin in `app/ios` calls `isSyncRedirect`).
- **Performance.** Interface calls replace direct ones per file, not per byte; no measurable change expected.
  `:tools:screenshots:benchmark` before and after E6 if in doubt.

## Verification

Behaviour is unchanged when, after every step, these pass without edits other than imports and constructor wiring:
`TwoDeviceSyncTest`, `SyncSimulationTest` (and a run by hand with far more seeds after E4 and E6), the engine,
planner, scheduling, connection, and shared-links tests, `SyncPlannerUpgradeFrom520Test`,
`SharedLinksUpgradeFrom520Test`, `SharedLinksUpgradeFrom540Test`, the E1 fixtures, the Dropbox and transport tests, and
the storage contract tests on the iOS simulator and in headless Chrome. Once, after E9: a live sync of an existing
install against a real Dropbox (the owner's live sync test recipe) shows no download of unchanged files and no
conflict copy.
