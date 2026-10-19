<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :data:source:remote:api

The sync, cover art, import download, cover search, and song details lookup contracts. Consumed by
`:data:sync:implementation` and `:data:repository:implementation`, implemented by `:data:source:remote:implementation`;
depends only on `:data:model`. A second sync provider must be one new class, not an engine change; the rules are in the
KDoc of `SyncFolder` and `SyncConnection`.

## Sync provider contract

- `SyncProvider` = `SyncConnection` (the account) + `SyncFolder` (the files). A run is handed only the `SyncFolder`, so
  it never connects or re-authorizes.
- One flat folder addressed by `(LibraryFileKind, name)`. Names must be unique; a service allowing duplicates resolves
  that in its own `list`.
- Revisions are **opaque strings**, stored and handed back, never parsed.
- Every run lists the whole folder (correct even after an interrupted run).
- `download` returns the revision it actually fetched (`RemoteDocument`), which may be newer than the listing's.
- `upload` takes the expected revision and returns `RemoteWriteResult.Conflict` rather than overwriting.
- `contentHashOf` hashes in the service's own format, keeping service hashes out of the engine.
- `disconnect` revokes the token; `forgetStoredCredentials` only drops local storage (no request).
- `downloadDocument` / `uploadDocument`: folder-level documents by name (only `preferences.json`), never in `list`,
  same conflict rule as `upload`.
- `SyncAuthorizationException`, `SyncNetworkException`, and `SyncRemoteStorageFullException` (subclasses of sealed
  `SyncRunEndingException`, each carrying its `SyncFailureReason`) are the only failures that end a run; storage full
  comes only from `upload` / `uploadDocument`. Anything else is one file's problem.

## Authorization

- `SyncAuthenticator.authorize` returns an outcome (possibly `Redirected`), not a URL, because the web navigates away;
  `consumePendingRedirect` picks the answer up at the next start (web always, Android after process death).
  `prepareRedirectUri` exists because the desktop's loopback socket must be opened first.
- `PendingAuthorizationStore` keeps the PKCE verifier across a full page reload.
- `SystemBrowser` and `AuthorizationCompletionPage` live in `:data:model` because their only implementer / builder is
  `:presentation`. The completion page is the only Campfire text shown outside the app, so the UI hands its translated
  strings down.

## Hashing

`Sha256` is hand-written (avoids a dependency on four targets). `localContentHash` is Campfire's own fingerprint and
decides whether a file changed; it is never compared with a service hash and is never a timestamp.

## Cover art, import download, and catalogue searches

- `CoverArtRemoteSource`: never throws but cancellation; returns the image, `Missing` (asking again won't help), or
  `Unreachable` (may change soon), so the caller can choose how long to remember a failure.
- `ImportDownloadRemoteSource`: one download, nothing kept, never reads past the given ceiling, never throws but
  cancellation (`ImportDownload`: file, `TooLarge`, `Missing`, `Unreachable`). Does not judge by content type.
- `CoverArtSearchRemoteSource` / `SongDetailsSearchRemoteSource`: an implementation keeps its own pace, waits out
  refusals itself (reporting via `onBusy`), and throws `CatalogueSearchException` only once it gives up. A catalogue's
  pace is shared between both searches, so one object implements both per catalogue. `loadSongCredits` answers null
  without a request for a catalogue that never names songwriters. Registered as `CoverArtSearchRemoteSources` /
  `SongDetailsSearchRemoteSources`, never a bare list (see the root `CLAUDE.md` on `List<T>`).
