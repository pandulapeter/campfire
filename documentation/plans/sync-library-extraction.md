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

**Status:** draft, checked against the code on 2026-11-18 (`main` at 85e23a2db). Nothing is implemented. Parked until
a second app is actually being built: before that, the moves below are churn in the riskiest code with no consumer to
shape the API. It starts once [sync-seams.md](sync-seams.md) has landed, whose eight seams (collections, references,
conflict policy, limits, library observer, synced preferences split, run steps, and provider configuration) are this
plan's groundwork.

**Goal:** the sync engine, the provider API, the Dropbox provider, and what they need of local storage live in
app-agnostic `:libs:sync:*` modules, with Campfire their first consumer and the second app shaping the API. A later
provider (Google Drive) is then one library module every app gets. `campfire-library` proves the split: it fails the
build when a `:libs:*` module depends on a project outside `:libs:` (`gradle/build-logic/.../plugins/LibraryPlugin.kt`,
every configuration, tests included).

**Non-goals:** no behaviour change, no stored file changing shape, no new provider, no UI change. Later, not this plan:
publishing the modules as an external artifact (with a package rename away from `com.pandulapeter.campfire`), a shared
sync UI module.

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
libs:storage               (step X7, independent) FileStorage, SecretStore, their platform implementations and
                           contract tests, with the directories declared by the app
```

`provider-core` is its own module because Drive needs every line of it. `storage` is `libs:storage`, not
`libs:sync:storage`: the engine needs only the ports in `api`, while `FileStorage` serves the whole app (imports,
staging, exports, OPFS). Shared links stay in Campfire; a `libs:sync:shared-links` with `SharedCopyContents` as the
app's payload builder is possible once a second app wants links.

What stays in Campfire: `:data:sync:implementation` becomes the adapter (collections, references, setlist comparison,
the synced preferences codec, the library observer, `SyncRepositoryImpl`, `DemoLibraryRepositoryImpl`, shared links,
`DataSyncModule`); `:data:source:remote:implementation` keeps covers, catalogues, and import downloads, and builds the
Dropbox provider; `:data:source:local:implementation` implements the storage ports over `FileStorage`.

**Koin:** the `libs:sync:*` modules do not apply `campfire-koin` and carry no annotations; Campfire's
`DataSyncModule` and `DataRemoteSourceModule` build them with `@Single` functions. Unlike `libs:metronome` (one
`@Single` class with no app input), almost every engine class needs an app-supplied value Koin cannot inject: three of
the four definitions in `data/sync/implementation/.../Module.kt` and the Dropbox provider are already hand-built for
this reason. It also keeps the Koin compiler plugin's per-Kotlin-version pin out of a later artifact.

## What moves beyond the seams

1. **Names.** `LibraryFiles.identityKey`, `isSameLibraryName`, and `normalizedToNfc` (expect/actual in `:data:model`)
   are the folder's identity rule (`SyncFolder` KDoc) and move to `api`; `:data:model` delegates. `normalizedName`,
   the `artist-title` policy, and `isLibraryFileNamedFor` (content names) stay in the app; the content-name check
   reaches the engine through `SyncCollection`.
2. **Storage ports.** The engine reads `LibraryFileLocalSource` (collection files, free-name writes,
   `canHoldFileName`), `SyncIndexLocalSource`, and, through `SyncCredentialsStore`, `SyncCredentialsLocalSource`;
   `LibraryFileLock` and `LibraryStorageException` come from local api. All move to `api` as ports (typealiases left
   in local api for one step); their implementations stay in `:data:source:local:implementation` until X7.
3. **Shared primitives.** `Logger`, `recovering`, `JsonBounds`, `Sha256`, `isOutOfSpace`, and `SystemBrowser` /
   `AuthorizationCompletionPage` live in `:data:model`. `api` gets what the engine needs (`SyncLogger` as a fun
   interface the adapter wraps `Logger` in; `Sha256`, `JsonBounds`, the completion page and browser types moved, with
   `:data:model` typealiasing or delegating).
4. **Visibility.** Today's `internal` across `run/`, `scheduling/`, and `connection/` becomes `public` between engine
   and adapter; keep the adapter-facing surface to the engine's entry points, the ports, and the step and observer
   types.

## Steps

Each lands as one compiling commit with `desktopTest`, `spotlessCheck`, and the pre-commit checks green; every step
that moves a source set also compiles iOS (`linkDebugFrameworkIosSimulatorArm64`) and the web. Moves are `git mv`, so
blame follows.

- **X0. Spike (half a day, throwaway).** Answer: typealiases for the moved `:data:model` types (SAM constructors of
  `Logger`, sealed `SyncState` `when`s) on Native and Wasm; whether the Koin compiler plugin's graph check accepts
  `@Single` functions returning library types from modules without Koin; whether `engine` takes `explicitApi()` too
  (the default answer: `api` only, as with the other libs). Done: three answers written into this plan.
- **X1. Guard.** Extend `LibraryPlugin`: a `:libs:sync:*` module may depend only on `:libs:sync:*`, so
  `:libs:chordpro` cannot slip in (the existing rule would allow it), with a `build-logic` test. The seams plan's
  import check then retires for the moved packages.
- **X2. `libs:sync:api`.** Create the module; move the provider API (`data/source/remote/api/.../sync/`, `hashing/`,
  `ConnectivityMonitor`), the sync state models, identity and NFC, `Sha256`, the storage ports, and `LibraryFileLock`,
  with their tests (`RedirectUriTest`, `SyncRunEndingExceptionTest`, `LocalContentHashTest`, the identity tests). The
  old modules re-export or typealias for this one step. Done: `campfire-library` passes; `api` depends on coroutines
  and serialization only.
- **X3. `libs:sync:engine`.** Move `run/`, `index/`, `scheduling/`, `connection/`, the merge, `SyncEnvironment`,
  `SyncStateHolder`, and the tests that use only them (planner, engine, deletion guard, index, scheduling,
  `SyncPlannerUpgradeFrom520Test` with its `upgrade-5.2.0/preferences/sync-index.json` and songs), rewriting their
  fakes over a test collection set with Campfire's ids. Done: engine tests green in the new module;
  `TwoDeviceSyncTest` and `SyncSimulationTest` still green in the adapter.
- **X4. `libs:sync:provider-core`.** Move `network/` (sync transport, backoff, client holder and factories, stall and
  declared-length helpers, `Json`), `auth/`, `crypto/`, and the connectivity monitors with their platform source sets
  and tests (`OAuthTokensTest`, `SyncCredentialsStoreTest`, `PendingAuthorizationStoreTest`, `SyncHttpTransportTest`,
  `HttpClientHolderTest`, `LoopbackRedirectListenerTest`). `isSyncRedirect` / `onSyncRedirectReceived` (used by
  `CampfireMainActivity.kt` and `IosFileImport.kt`) move with the scheme as a parameter. The remote module's covers
  and catalogues use the same `HttpClientHolder`, so the app still builds one client. `AndroidSyncAuthenticator`'s
  `@Provided Context` is handed in by the adapter's `androidMain`. Done: Android and iOS sign-in, desktop loopback, and
  web redirect compile; the four `HttpClientFactory` actuals moved with their Ktor engines.
- **X5. `libs:sync:provider-dropbox`.** Move `dropbox/` and its 12 test files. `DataRemoteSourceModule.syncProviders`
  keeps its "no key, no provider" rule. Done: `DropboxAuthorizationTest` checks the same authorization URL.
- **X6. Wiring and docs.** Drop X2's typealiases where only imports remain; `DataSyncModule` builds the engine, steps,
  and observer; `app:di` names no new module (none has Koin). One `CLAUDE.md` per new module, carrying the contract and
  invariant sections from today's files; the root `CLAUDE.md` module graph; and its Privacy section, which says
  `:data:source:remote:implementation` is "the only module that makes a network call" and will name
  `libs:sync:provider-*` too. Done: `:data:source:remote:api` holds no `sync/`; the `CLAUDE.md` names check passes.
- **X7. `libs:storage` (optional, after X2).** Move `storage/file/` and `storage/secret/` with `StorageDirectory`
  turned into app-declared directories (path segments, backup policy) and the contract tests (`iosSimulatorArm64Test`,
  `wasmJsBrowserTest` in headless Chrome); the sync port implementations (collection files, index, credentials) follow
  as generic ones. Open for this step's spike: `takeInto(file: Any)` takes the web's `PickedFile` (`:data:model`
  `web/`), `OutOfSpaceException` is the import's too, and `FileNames.kt`'s numbering is shared by the import and sync's
  conflict copies. Done: both contract suites green.

## Risks

- **Stored formats.** Moved `@Serializable` sealed types need `@SerialName` (the journal's have it); the
  `upgrade-5.2.0` / `upgrade-5.4.0` tests, read from their new modules, guard the rest.
- **Test reach.** `TwoDeviceSyncTest` and `SyncSimulationTest` drive Campfire's whole wiring (`TestSyncRepository`,
  `FakeSyncCollaborators` with `SongRepository` and `UserPreferences`), and the plugin checks test configurations too,
  so they stay in the adapter as the end-to-end proof; the engine gets its own generic two-device test with the second
  app.
- **Platforms.** The iOS framework links the new modules through `app:di`; no Swift code names the moved types
  (checked: only Kotlin in `app/ios` calls `isSyncRedirect`).
- **Performance.** No measurable change expected; `:tools:screenshots:benchmark` before and after X3 if in doubt.

## Verification

After every step, these pass without edits other than imports and constructor wiring: `TwoDeviceSyncTest`,
`SyncSimulationTest` (and by hand with far more seeds after X3), the engine, planner, scheduling, connection, and
shared-links tests, `SyncPlannerUpgradeFrom520Test`, `SharedLinksUpgradeFrom520Test`, `SharedLinksUpgradeFrom540Test`,
the seams plan's fixtures, the Dropbox and transport tests, and the storage contract tests on the iOS simulator and in
headless Chrome. Once, after X6: a live sync of an existing install against a real Dropbox shows no download of
unchanged files and no conflict copy.
