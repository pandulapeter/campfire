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

A second sync provider must be one new class, not an engine change; the rules are in the KDoc of `SyncFolder` and
`SyncConnection`.

## Sync provider contract

- A run is handed only the `SyncFolder`, so it never connects or re-authorizes.
- One flat folder addressed by `(LibraryFileKind, name)`. Names are unique (a service allowing duplicates resolves that
  in its own `list`) and resolved ignoring case and Unicode form (`LibraryFiles.identityKey`), since the engine
  addresses a remote file by its local spelling. Dropbox does so itself; a service with exact names maps them to ids.
- Revisions are **opaque strings**, never parsed. Every run lists the whole folder (correct after an interrupted run).
- `download` returns the revision it actually fetched, which may be newer than the listing's. `upload` takes the
  expected revision and returns `RemoteWriteResult.Conflict` rather than overwriting.
- `contentHashOf` hashes in the service's own format, keeping service hashes out of the engine.
- One provider's credentials are stored at a time, and they name it: `SyncProviders.stored` reads that name without
  asking any provider, and only the provider named is asked whether it is connected or handed a run. A provider forgets
  only its own tokens and the authorization it started, and a consent page's answer goes only to the provider that
  started that authorization.
- `disconnect` forgets the credentials at once and answers the revocation for the caller to run in the background;
  `forgetStoredCredentials` only drops local storage.
- `downloadDocument` / `uploadDocument`: folder-level documents by name (`preferences.json`, `shared-links.json`), never
  among `list`'s files; same conflict rule as `upload`; one too large throws `SyncDocumentTooLargeException` with its
  revision.
- Only `SyncRunEndingException`s (authorization, network, storage full — the last only from uploads) end a run; anything
  else is one file's problem. `SyncScopeMissingException` (a connection made before a permission was asked for) is
  deliberately neither run-ending nor an authorization failure, since the files still sync; only connecting again fixes
  it.

## Shared copies (song links)

- A share id must be a plain name (letters, digits, `-`, `_`), else `IllegalArgumentException` before a request.
- Shared copies appear only in `RemoteListing.sharedFiles`, never in `files` or `changesSince`, so the planner, the
  deletion guard, and the change feed never see them.
- `createSharedLink` answers the link a copy already has, so a copy keeps one link. `SharedLinkResult.Created` carries
  the link parts (Dropbox's `fileId` and `rlkey`), from which the app builds every download address itself; in memory
  only, the stored list keeps its own format.
- Deleting a copy ends its link for good; "already gone" is success.

## Authorization

- `SyncAuthenticator.authorize` returns an outcome (possibly `Redirected`), not a URL, because the web navigates away;
  `consumePendingRedirect` picks the answer up at the next start (web always, Android after process death).
  `prepareRedirectUri` exists because the desktop's loopback socket must be opened first.
- `PendingAuthorizationStore` keeps the PKCE verifier across a full page reload.
- The completion page is the only Campfire text shown outside the app, so the UI hands its translated strings down.

## Hashing

`Sha256` is hand-written (avoids a dependency on four targets). `localContentHash` decides whether a file changed; it is
never compared with a service hash and is never a timestamp.

## Cover art, import download, and catalogue searches

- `CoverArtRemoteSource` never throws but cancellation; `Missing` (asking again won't help) vs. `Unreachable` (may
  change soon) lets the caller choose how long to remember a failure.
- `ImportDownloadRemoteSource`: nothing kept, never reads past the given ceiling, never throws but cancellation, does
  not judge by content type.
- Catalogue searches keep their own pace, wait out refusals themselves (reporting via `onBusy`), and throw
  `CatalogueSearchException` only once they give up. A catalogue's pace is shared between both searches, so one object
  implements both per catalogue. `loadSongCredits` answers null without a request for a catalogue that never names
  songwriters.
