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
- Names are resolved ignoring case and Unicode form (`LibraryFiles.identityKey`): a request for `song.cho` reaches the
  file listed as `Song.cho`, since the engine addresses a remote file by its local spelling. Dropbox does so itself; a
  service with exact names maps them to ids in its own `list`.
- Revisions are **opaque strings**, stored and handed back, never parsed.
- Every run lists the whole folder (correct even after an interrupted run).
- `download` returns the revision it actually fetched (`RemoteDocument`), which may be newer than the listing's.
- `upload` takes the expected revision and returns `RemoteWriteResult.Conflict` rather than overwriting.
- `contentHashOf` hashes in the service's own format, keeping service hashes out of the engine.
- `disconnect` forgets the credentials at once and answers the revocation for the caller to run in the background;
  `forgetStoredCredentials` only drops local storage (no request).
- `downloadDocument` / `uploadDocument`: folder-level documents by name (`preferences.json` and `shared-links.json`),
  never among `list`'s files; `RemoteListing.documentRevisions` names their revisions; same conflict rule as `upload`;
  one too large to download throws `SyncDocumentTooLargeException` with its revision.
- `SyncAuthorizationException`, `SyncNetworkException`, and `SyncRemoteStorageFullException` (subclasses of sealed
  `SyncRunEndingException`, each carrying its `SyncFailureReason`) are the only failures that end a run; storage full
  comes only from the uploads. Anything else is one file's problem.
- `SyncScopeMissingException`: a connection made before a permission was asked for. Deliberately not run-ending and not
  an authorization failure, since the files still sync; only connecting again fixes it.

## Shared copies (song links)

- `uploadSharedFile` / `createSharedLink` / `deleteSharedFile`, by a share id that must be a plain name (letters,
  digits, `-`, `_`; anything else is an `IllegalArgumentException` before a request).
- `list` reports them only in `RemoteListing.sharedFiles`, never in `files`, and `changesSince` never reports them, so
  the planner, the deletion guard, and the change feed never see them.
- `createSharedLink` answers the link a copy already has, so a copy keeps one link; `SharedLinkResult.Created` carries
  the provider and its link parts (Dropbox's `fileId` and `rlkey`), from which the app builds every download address
  itself. In memory only: the stored list and the links keep their own format.
- Deleting a copy ends its link for good; "already gone" is success.

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
