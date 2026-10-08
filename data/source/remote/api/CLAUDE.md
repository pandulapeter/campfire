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

The sync contracts and the two cover art ones. Consumed by `:data:repository:implementation` (the engine and the cover
repository) and implemented by `:data:source:remote:implementation`. Depends only on `:data:model`.

Everything here is shaped so that a second provider is one new class rather than a change to the engine — the rules,
and why each of them is load bearing, are spelled out in the KDoc of `SyncFolder` and `SyncConnection`.

- `SyncProvider` — one cloud service, the two halves of it named apart: `SyncConnection`, the account (`isConnected`,
  the authorization, `disconnect`, `forgetStoredCredentials`, `loadAccount`, `storedAccount`), and `SyncFolder`, the
  files a run reads and writes, which is all the engine and `SyncedPreferencesSync` are handed — so a run's type says
  it never connects or re-authorizes anything. The folder is a single flat one addressed by `(LibraryFileKind, name)`,
  exactly the shape the library has. Revisions are **opaque strings**: the engine stores them and hands them back,
  and never parses one, so a Dropbox `rev` and a Drive `headRevisionId` are equally fine. Every run lists the whole
  folder rather than asking what changed — one request for a library of songs, and right even after a run that was
  interrupted half way. `download` answers the bytes with the revision it actually fetched (`RemoteDocument`), which
  may be newer than the listing's: another device can write the file again in between. `upload` takes
  the revision the caller believes the file has and reports `RemoteWriteResult.Conflict` rather than clobbering a
  change made elsewhere. `contentHashOf` hashes local bytes in the *service's own* format, which is what keeps
  Dropbox's block hashing and Drive's MD5 out of the engine. Names must be unique, and a service that allows
  duplicates has to resolve that inside its own `list`. `storedAccount` answers who is connected without a request,
  `loadAccount` with one. `disconnect` is the user's disconnect and revokes the token; `forgetStoredCredentials` only
  drops what is stored, telling the service nothing, for a fresh installation that found a previous one's credentials.
  `downloadDocument` / `uploadDocument` reach a **document of the folder's own** by name alone — beside the library
  folders, never in `list`, and coming back with its revision (`RemoteDocument`), since it is read once per run rather
  than compared — and write it with the same conflict rule as `upload`. `preferences.json` is the one there is.
- `SyncAuthenticator` — the platform half of the OAuth flow. The interface is shaped by the awkward platform rather
  than the easy ones: the web *navigates away* from the running app to ask for consent, so `authorize` returns an
  outcome (which may be `Redirected`) instead of a URL, and `consumePendingRedirect` picks the answer up at the next
  start (the web always, Android after process death). `prepareRedirectUri` is separate because the desktop's redirect URI is a loopback socket that does not
  exist until it is opened.
- `SystemBrowser` — opening a URL in the user's browser, which only the desktop's authorization needs from outside
  itself (the other three open theirs through an API of their own). A contract here because the one implementation
  is the desktop shell's URL opener in `:presentation`, which the data layer cannot see.
- `PendingAuthorizationStore` — the authorization that has been started and not finished, for the same reason: the
  PKCE verifier has to outlive a full page reload.
- `hashing/` — `Sha256` (written out; a multiplatform hashing library would be one more dependency on four targets
  for eighty lines of arithmetic) and `localContentHash`, **Campfire's own** fingerprint of a file. It is what
  decides whether a file changed, and is never compared against anything a service reports — deliberately not a
  timestamp, since the platforms disagree about those and the web has none.
- `model/` — `RemoteFile`, `RemoteListing`, `RemoteWriteResult`, the authorization request and response, and
  `redirectParameters`, which picks a redirect URI apart (four platforms, four URL libraries, one URL to parse).
  `AuthorizationCompletionPage` is the odd one: the desktop has no custom scheme to be redirected to and answers the
  browser with a page of its own, which is the only Campfire text rendered outside the app — so its two strings are
  handed down from the UI, which is the only layer that knows the translations and the chosen language. The other
  three platforms ignore it, because their browser closes itself.
- `CoverArtRemoteSource` — downloads the image a song's `{meta: cover …}` names, from whatever host that is. It never
  throws for anything but a cancellation: what comes back is a `CoverArtDownload` — the image, `Missing` (the address
  answered with something that is not a cover, which asking again will not change) or `Unreachable` (no answer, which
  may be different a minute later) — so the caller can decide how long to remember a failure.
- `CoverArtSearchRemoteSource` — the records a song may have come out on in one catalogue (its `service`), each with
  the address of its front cover. The contract is shaped by MusicBrainz's rules: an implementation keeps its own pace
  and waits out a refusal by itself, reporting each wait through `onBusy` so the sheet can say why it is taking a
  while, and throws `CoverArtSearchException` only once it has given up. Every source is registered as one
  `CoverArtSearchRemoteSources`, never a bare list, for the reason `SyncProviders` is.
- `SyncAuthorizationException` / `SyncNetworkException` / `SyncRemoteStorageFullException` — the three failures the
  engine treats as reasons to stop a run; the last one only ever comes from `upload`, since every upload after it
  would be refused the same way. Everything else is one file's problem and must not keep the other four hundred from
  travelling.
