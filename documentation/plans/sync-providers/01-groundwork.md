<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan 01: groundwork for a second sync provider

**Status:** first draft, rewritten 2026-10-26. Nothing implemented.

**Goal:** refactors and fixes that ship in the **next release on their own**, with Dropbox the only provider and
nothing different for the user:

- no new screen, string, or setting;
- no file format change;
- no new network request.

Each step leaves the code where plan 02 (Google Drive) adds a provider instead of reworking the sync layer.

**Out of scope here, in plan 02:** anything that names or offers another provider. That includes the picker, switching,
redirects for other services, build keys, the v2 share-link format, the website, and the privacy text.

## What is already provider-neutral (keep it that way)

- The engine (`SyncPlanner`, `SyncRunner`, the index, `SharedCopySync`) sees only `SyncFolder`'s opaque strings.
  Revisions are compared for equality only, and the content hash is optional.
- The index is filed under `<providerId>:<accountId>` (`index/SyncAccountKey.kt`). Connecting another account already
  resets the index and the shared-links mirror and journal, as Disconnect does.
- A listing without a cursor (`RemoteListing.cursor = null`) simply isn't watched.
- `SyncFolder.awaitChanges` may poll inside the provider, so a service without a long poll needs no engine change.
- The 8 MiB limit (`ImportLimits`) and the synced preferences document don't depend on the provider.

## Steps

### G1. The stored credentials decide which provider is active

Today the active provider is "the first one whose `isConnected()` is true" (`SyncConnectionManager`, `SyncRunner`).
That is correct only because `DropboxSyncProvider` filters the stored credentials by its own `providerId`, a rule every
later provider would have to repeat by hand. And unreadable credentials are reported against `providers.first().id`.

- Give `SyncProviders` a lookup by `SyncProviderId`.
- Add a way to read the stored `providerId` without a provider (on `SyncCredentialsStore`). `SyncConnectionManager` and
  `SyncRunner` pick the provider by it instead of asking each provider in turn.
- Report unreadable credentials against the stored id when it can be read, and against the first provider only when
  even that fails.
- Tests: `SyncConnectionManager` tests with a **second fake provider** in `SyncProviders` (test code only). Credentials
  of fake B must never make Dropbox look connected, and the other way round.

### G2. Forgetting credentials only forgets its own

`DropboxSyncProvider.forgetStoredCredentials()` is `credentialsStore.save(null)`, which erases whatever is stored. The
cancel and back-out paths in `SyncConnectionManager` (`forgetCredentialsOf`) call it on the provider being connected.
With one provider that's harmless; with two, cancelling a connection to B would erase A's working tokens.

- `SyncCredentialsStore` gains "forget if it belongs to `providerId`". `forgetStoredCredentials()` and `disconnect()`
  use it.
- A pending authorization (`SyncCredentialsDocument.Pending`, which already carries `providerId`) is handed only to the
  provider that started it when `consumePendingRedirect` returns one (web, and Android after process death). A pending
  answer for a provider the build doesn't have is dropped with a log line.
- Tests: extend `SyncCredentialsStoreTest` and `SyncConnectionManagerForgettingTest` with the second fake provider.

### G3. OAuth and HTTP plumbing out of `dropbox/`

`DropboxTokens` and `DropboxTransport` hold rules any OAuth service needs. Move the general part next to the Dropbox
code, so the Drive provider inherits it instead of copying it:

- **`auth/OAuthTokens`**, configured with the token endpoint, client id, and an optional client secret:
  - renewal inside one `SyncCredentialsStore.update` (a shared refresh, bounded at 30 s);
  - keeping a rotated refresh token;
  - one forced refresh on 401;
  - only a 400/401 from the token endpoint counts as a refusal.
- **`network/SyncHttpTransport`:**
  - timeouts;
  - retries on 408/429/5xx with `Retry-After`, and the 120 s ceiling;
  - transport-failure retries;
  - the browser's `fetch` quirks.

  A provider supplies an error mapper (Dropbox's 409 `error_summary` stays in `dropbox/`).
- PKCE helpers and `RemoteAuthorizationRequest` stay as they are.
- `DropboxTokensTest` and `DropboxTransportTest` split into tests of the shared parts and thin Dropbox ones. Every
  existing case must keep passing unchanged in substance. This is a pure move.

### G4. Name matching becomes part of the provider contract

`run/SyncNameFolding.kt` folds a remote `Song.cho` onto a local `song.cho` and then addresses the file by the local
spelling. That's right on Dropbox, which ignores case. On a case-sensitive service, the download would miss and the
upload would create a twin.

- State in `SyncFolder`'s KDoc that a provider resolves names **case-insensitively** (by `LibraryFiles.identityKey`): a
  request for `song.cho` reaches the file listed as `Song.cho`. A case-sensitive service does this through its own
  name→id map built in `list`. Dropbox already complies.
- Reword the comment in `SyncNameFolding.kt` from a Dropbox fact to that rule.
- Test: a `TwoDeviceSyncTest` case whose fake folder keeps names case-sensitive but resolves them case-insensitively
  must converge. This guards the rule for the next provider.

### G5. Provider-neutral shared-link result (in memory only)

`SharedLinkResult.Created(fileId, rlkey)` is part of the remote API, so the sharing code above the provider is
Dropbox-shaped.

- Make `SharedLinkResult.Created` carry the provider and its parts. The Dropbox provider fills them with `fileId` and
  `rlkey`.
- `SharedLinksRepositoryImpl` turns them into a registry entry exactly as today.
- **No format change:**
  - `shared-links.json` keeps writing `fileId` / `rlkey`, so older builds on other devices keep reading it;
  - links stay version 1 (`ShareLinks.VERSION`);
  - the website is untouched.

  The v2 format belongs to plan 02.
- Tests: `SharedLinksFormatTest` and the repository tests are unchanged; that is the point.

### G6. UI code reads the provider instead of naming Dropbox

No visible change; only the code stops assuming the one provider:

- **`SharingSections.kt`:** the "Connect again" for the link permission uses `account.providerId`, not
  `SyncProviderId.DROPBOX`.
- **Connect buttons** in `DisconnectedSyncSettings.kt`, `export/LinkExport.kt`, `onboarding/OnboardingScreen.kt`,
  `ExportActionButton.kt`, and `onboarding/SyncStep.kt`: they connect "the provider this build offers" (the only one in
  `SyncProviders`) instead of hard-coding `DROPBOX`.
- **`CampfireViewModel.kt` and `OnboardingStep.kt`:** `canConnectDropbox` becomes `canConnectSync`.
- **Strings:** leave them as they are. Making them take the provider's name belongs with the picker in plan 02, where
  it can be checked against a second name.

### G7. Docs

- `data/source/remote/api/CLAUDE.md`: the provider contract gains the case-insensitive rule (G4), credential ownership
  (G1, G2), and the shared OAuth and HTTP pieces (G3).
- `data/source/remote/implementation/CLAUDE.md`: say which rules now live in `OAuthTokens` and `SyncHttpTransport`,
  and keep the Dropbox section for what is Dropbox's.
- The connection `CLAUDE.md` (`data/sync/implementation/.../connection/`): how the active provider is chosen.

## Order and size

- G1 and G2 go together (both touch the credentials store and the connection manager).
- G3, G4, G5, and G6 are independent of each other and of G1/G2.
- About a week of work in total. G3 is the largest, since it's a careful move of well-tested code. Each step is one
  commit, and the release notes mention none of it.

## Verification

- The existing tests carry the weight: `TwoDeviceSyncTest`, `DropboxTransportTest`, `DropboxTokensTest`, and the
  connection tests, plus the new second-fake-provider cases.
- G1 to G3 touch sign-in, so once they land, do one live Dropbox connect, sync, and disconnect on the desktop, as in
  the sync live-testing recipe.
