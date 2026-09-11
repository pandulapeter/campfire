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

The sync contracts, and nothing else. Consumed by `:data:repository:implementation` (the engine) and implemented by
`:data:source:remote:implementation` (the providers). Depends only on `:data:model`.

Everything here is shaped so that a second provider is one new class rather than a change to the engine — the rules,
and why each of them is load bearing, are spelled out in the KDoc of `SyncProvider`.

- `SyncProvider` — one cloud service, seen as a single flat folder addressed by `(LibraryFileKind, name)`, which is
  exactly the shape the library has. Revisions are **opaque strings**: the engine stores them and hands them back,
  and never parses one, so a Dropbox `rev` and a Drive `headRevisionId` are equally fine. Every run lists the whole
  folder rather than asking what changed — one request for a library of songs, and right even after a run that was
  interrupted half way. `upload` takes
  the revision the caller believes the file has and reports `RemoteWriteResult.Conflict` rather than clobbering a
  change made elsewhere. `contentHashOf` hashes local bytes in the *service's own* format, which is what keeps
  Dropbox's block hashing and Drive's MD5 out of the engine. Names must be unique, and a service that allows
  duplicates has to resolve that inside its own `list`.
- `SyncAuthenticator` — the platform half of the OAuth flow. The interface is shaped by the awkward platform rather
  than the easy ones: the web *navigates away* from the running app to ask for consent, so `authorize` returns an
  outcome (which may be `Redirected`) instead of a URL, and `consumePendingRedirect` picks the answer up at the next
  start. `prepareRedirectUri` is separate because the desktop's redirect URI is a loopback socket that does not
  exist until it is opened.
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
- `SyncAuthorizationException` / `SyncNetworkException` — the two failures the engine treats as reasons to stop a
  run. Everything else is one file's problem and must not keep the other four hundred from travelling.
