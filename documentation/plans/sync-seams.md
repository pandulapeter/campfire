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

**Status:** in progress: C1 done; C2 is next. Checked against the code on 2026-11-18 (`main` at 85e23a2db). Split out of
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
   `LostFileRestorer`), in a `SyncCollections` wrapper (never a `List` injected). The Dropbox provider takes the
   collections at construction (`DropboxEntries.libraryKind()` maps folder to kind today; `remotePath` uses `kind.id`).
   Campfire declares three with the same ids, so index keys and remote paths are byte for byte the same.
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
- **C2. Collections.** Seams 1 and 4: `SyncCollection` and `SyncCollections` in remote api replace `LibraryFileKind` in
  `SyncFolder`, `LibraryFileLocalSource`, `SyncKey`, the engine, and the Dropbox provider; Campfire declares the three.
  Done: `SyncPlannerUpgradeFrom520Test`, `TwoDeviceSyncTest`, `SyncSimulationTest`, and `DropboxFilesTest` green with
  only call sites changed; no `LibraryFileKind` left under `run/`, `index/`, or `dropbox/`.
- **C3. References and conflict policy.** Seams 2 and 3. Done: no chordpro import under `run/`;
  `SyncPlannerImageTest`, `SyncEngineSetlistDateTest`, and `SyncEnginePlantedDemoTest` unchanged; one new planner test
  over a test collection whose references come from a fake, not ChordPro.
- **C4. Observer, run steps, and synced preferences split.** Seams 5, 6, and 7. Done: `run/`, `index/`, `scheduling/`,
  `connection/`, and the merge import nothing from `data.repository`, `data.model.song|setlist|preferences|share`, or
  `shared/`; `SyncedPreferencesTest` and `SyncRunnerPreferencesRevisionTest` unchanged; `SyncSimulationTest` run by
  hand with far more seeds.
- **C5. Provider core apart from Dropbox.** Seam 8. Done: `DropboxAuthorizationTest` checks the same authorization URL;
  Android and iOS sign-in, the desktop loopback, and the web redirect compile; `auth/`, `network/`, and `crypto/`
  import nothing from `dropbox/`.
- **C6. The guard.** A check in `.github/scripts` beside `check_layers.py` (or a second table in it), with its
  `unittest`: main source files under the sync engine's packages (`run/`, `index/`, `scheduling/`, `connection/`, the
  merge) import nothing of chordpro, `data.repository`, `data.model.song|setlist|preferences|share`, or `shared/`, and
  the remote module's `auth/`, `network/`, and `crypto/` nothing of `dropbox/`. Run by the pre-commit hook, the Claude
  Code edit hook, and `tests.yml` like the others; no exception list, since C2–C5 leave the tree clean. Done: the check
  passes on the tree and its test shows each rule failing.
- **C7. Docs.** `data/sync/implementation/CLAUDE.md` and its package files, and the remote api and implementation
  ones, describe the seams, which side owns what, and the guard; the Drive plan's "What the groundwork left in place"
  is updated for collections and the redirect configuration.

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

Behaviour is unchanged when, after every step, these pass without edits other than imports and constructor wiring:
`TwoDeviceSyncTest`, `SyncSimulationTest` (and by hand with far more seeds after C4), the engine, planner, scheduling,
connection, and shared-links tests, `SyncPlannerUpgradeFrom520Test`, `SharedLinksUpgradeFrom520Test`,
`SharedLinksUpgradeFrom540Test`, C1's `*UpgradeFrom550Test`s, and the Dropbox and transport tests. Once, after C5: a live sync of an
existing install against a real Dropbox (the owner's live sync test recipe) shows no download of unchanged files and no
conflict copy.
