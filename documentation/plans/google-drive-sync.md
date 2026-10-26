<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan 02: Google Drive

**Status:** first draft, rewritten 2026-10-26. The groundwork (the former plan 01) landed on 2026-10-26
(5a7253847..f2f6e418a) and ships in v5.1.0 with Dropbox the only provider. Nothing of the Drive work below is
implemented.

**What the groundwork left in place** (read the code; these are the hooks):

- The stored credentials name the active provider (`SyncProviders.stored()` / `connected()` / `byId`), and a provider
  forgets only its own (`SyncCredentialsStore.forget(providerId)`). `SyncProviderId` is a data class with a `DROPBOX`
  value, so Drive adds a second value beside it.
- `auth/OAuthTokens` (with an optional client secret, for D2's desktop client) and `network/SyncHttpTransport` with a
  per-provider `SyncHttpErrorMapper` (Dropbox's is `dropbox/DropboxErrors.kt`).
- `SyncFolder`'s KDoc makes case-insensitive name resolution part of the contract; `FakeSyncProvider`'s
  `resolvesListedNames` and a `TwoDeviceSyncTest` case already cover a service with exact names.
- `SharedLinkResult.Created(provider, parts)`. `SharedLinksRepositoryImpl.storedParts()` still accepts only Dropbox's
  two parts; P3 widens it.
- The UI connects `SyncController.offeredSyncProvider` (the first in `SyncProviders`) or a connected account's own
  provider; P2's picker replaces the former. `canConnectSync` replaced `canConnectDropbox`. Strings still name Dropbox.
- **Not solved, for P2:** the credentials are one document, so a second provider's finished token exchange overwrites
  the first's tokens. Cancelling B is safe until that exchange; after it, A is gone. Switching as Disconnect, then
  Connect, never meets this, but a "connect B while A is connected" flow would.

**Goal:** Google Drive as a second sync provider, the user picking Dropbox or Drive. It covers sync, the synced
preferences, and shared links. It runs on Android, iOS, and desktop, and on the web if spike S3 allows it.

**Verdict:** feasible. Drive differs from Dropbox in three ways the provider absorbs:

1. Files are addressed by id, and names can repeat.
2. There is no compare-and-swap write.
3. There is no long poll.

Sign-in is harder than Dropbox's on Android and on the web.

Steps D1–D8 are the Drive provider itself. P1–P5 are the product work around it: the picker, switching, the link
format, build, and docs.

## Spikes first (one to two days, throwaway code, before anything below)

| # | Question | Why it matters |
|---|---|---|
| S1 | With `drive.file`, does a file created through one client type (say desktop) show up for another client type (iOS) in the same Cloud project? | If not, a second device can't see the library folder, and Drive is dead for multi-platform use. |
| S2 | Does `files.update` with `uploadType=media` honour `If-Match` (a 412 on mismatch)? | If yes, D4's detect-and-recover becomes a plain conditional write, as on Dropbox. |
| S3 | Web: does `oauth2.googleapis.com/token` accept code + PKCE from a Web-application client without `client_secret`? If not, is an embedded secret acceptable? Does `oauth2.googleapis.com/revoke` answer CORS? | Decides whether the web build offers Drive. |
| S4 | Does an anonymous `fetch` from `https://campfire-songbook.com` of `https://www.googleapis.com/drive/v3/files/<id>?alt=media&key=<key>` for an `anyone`/`reader` file return the bytes with `Access-Control-Allow-Origin`, and a 404 once the file is deleted? | Decides whether shared links open on the web and on the landing page. |
| S5 | Android: does an Android client with custom URI schemes re-enabled (Advanced settings) complete code + PKCE from the user's browser, with a refresh token? Does Identity Services' `AuthorizationClient.authorize` answer silently from the background sync service? | Picks D2's Android path. |

## The provider (`:data:source:remote:implementation/googleDrive/`)

### D1. Cloud project and build keys

- One Google Cloud project, "Campfire". OAuth consent screen: External, **In production** (in Testing, refresh tokens
  die after 7 days).
- Scopes: `drive.file` only, plus `openid email profile`. These are all non-sensitive, so no CASA security assessment
  and no 100-user cap. Do the brand verification: the homepage and privacy policy on campfire-songbook.com, with the
  domain verified in Search Console.
- Clients:
  - **iOS:** reversed-client-id redirect, no secret.
  - **Desktop app:** loopback, any port. Google's installed-app "secret" isn't confidential and reportedly has to be
    sent anyway, so it ships in the build.
  - **Android:** package name + SHA-1 of the release key and of the debug keystore.
  - **Web application:** if S3 allows it. Redirects `https://campfire-songbook.com/app/` and `http://localhost:8080/`.
  - **API key**, restricted to the Drive API, for shared-link downloads (D6).
- Build properties: `campfire.googleDrive.iosClientId`, `.desktopClientId`, `.desktopClientSecret`, `.androidClientId`,
  `.webClientId`, and `.apiKey`, each defaulting to empty in `../../gradle.properties`.
  - `generateRemoteConfiguration` (`../../data/source/remote/implementation/build.gradle.kts`) writes them beside
    `DROPBOX_APP_KEY`.
  - The provider is left out of `SyncProviders` on a platform whose key is empty, as Dropbox is today.

### D2. Sign-in per platform

- **Redirects per provider first.** `SyncAuthenticator.prepareRedirectUri()` takes no provider, and the redirect is one
  constant (`auth/SyncRedirect.kt`: `campfire://oauth`, desktop `http://127.0.0.1:53682`, web: the page). Change it to
  `prepareRedirectUri(provider)`, with each provider's `RedirectSpec` naming:
  - the custom scheme for Android and iOS (iOS's `ASWebAuthenticationSession` takes a single `callbackURLScheme`, today
    `REDIRECT_SCHEME` in `IosSyncAuthenticator`);
  - whether the desktop loopback may take any free port (Google allows it; Dropbox needs 53682);
  - the web redirect.

  Add Drive's scheme to `../../app/android/src/main/AndroidManifest.xml` and `../../app/ios/iosApp/iosApp/Info.plist`
  (`CFBundleURLSchemes`), checked by a test against the constants so they can't drift. `DesktopSyncAuthenticatorTest`
  gains an any-port case.
- **iOS:** code + PKCE through `ASWebAuthenticationSession`, with the reversed client id as the callback scheme.
  Refresh tokens stay in the Keychain, as today.
- **Desktop:** code + PKCE on a free loopback port, plus the bundled secret, through `OAuthTokens`.
- **Android**, decided by S5:
  - (a) If custom schemes still work on an Android client: the same browser flow as iOS. One code path, but Google
    discourages it and may switch it off.
  - (b) Otherwise, Identity Services `AuthorizationClient`, behind a new optional `NativeAuthorizer` the Android
    platform supplies:
    - it gives a one-hour access token and no refresh token without a backend, so the provider asks for a new token
      whenever the old one expires (silent once granted);
    - only the account is stored;
    - it needs Google Play services, so the provider is hidden on devices without them;
    - it adds `play-services-auth`, which has to be credited (root `../../CLAUDE.md`, "Build").
- **Web**, decided by S3: an embedded Web-client secret with the normal code flow, or no Drive on the web. The
  token-only model (hourly popups, no background renewal) isn't worth building.
- **Account:** `about.get(fields=user)`: `permissionId` as the account id (it never changes), plus `displayName` and
  `emailAddress`.
- **Disconnect:** `POST oauth2.googleapis.com/revoke?token=<refresh token>` (`revokeAccess()` on path b).

### D3. Finding the folder

`drive.file` sees only what Campfire created, which gives the same privacy as Dropbox's app folder, but there is no app
folder to find by path.

- **Layout:** a visible `Campfire` folder in My Drive, holding `songs/`, `setlists/`, `shared/`, `preferences.json`,
  and `shared-links.json`. Each folder is marked with `appProperties.campfire = "library" | "songs" | …`, so it is
  found by property, even after the user renames or moves it.
- **Lookup:** `files.list(q = "appProperties has { key='campfire' and value='library' } and trashed = false")`.
- **Caching the ids:** keep them in a new defaulted `extras: Map<String, String>` on `SyncCredentialsDocument`, so a
  document written by an older version keeps loading. Look them up again on any 404.
- **Two libraries at once:** two devices connecting at the same moment can each create a library folder. When `list`
  finds more than one, it keeps the oldest by `createdTime` (the same answer on every device), moves the other
  folders' children into it (`files.update addParents/removeParents`), and trashes the empty folders. It is
  idempotent, so every device may try it.
- Sharing the `Campfire` folder with another person or moving it to a shared drive isn't supported; the support page
  says so.

### D4. Files: the `SyncFolder` contract on ids

- **`list`:** one `files.list` per subfolder, `fields=files(id,name,headRevisionId,sha256Checksum,size,createdTime)`,
  paged.
  - Names are resolved **case-insensitively** through a name→id map (`SyncFolder`'s contract).
  - **Duplicate names** (two devices creating the same new song at once): keep the oldest by `createdTime` under the
    name, and rename the others on the service to the next free numbered name, as `FileNames.kt` numbers them. The
    next run then sees them as new files on every device, the same result as the engine's conflict copies, and
    nothing is hidden.
- **Revision:** `headRevisionId` (content only, so sharing permissions don't count as a change).
- **Hash:** `contentHashOf` is SHA-256, matching `sha256Checksum`.
- **`download`:** metadata, then `alt=media`. Check the bytes against `sha256Checksum`; on a mismatch (the file changed
  between the two requests), read again once, then fail that file. Return the revision whose hash matched.
- **`upload`, if S2 says `If-Match` works:** a conditional `files.update`. Otherwise, **detect and recover**:
  1. Read `headRevisionId` and answer `Conflict` if it isn't the expected one.
  2. Upload.
  3. List the file's revisions. If the revision before ours isn't the expected one, another device wrote in between:
     fetch that revision's bytes (`revisions.get alt=media`) and save them as a new numbered file beside it.

  Both versions survive, as the engine promises. Drive keeps revisions for 30 days, so recovery is always possible.
- **Creating** a new file has no precondition. A race produces a duplicate name, which `list` resolves.
- **`delete`:** compare `headRevisionId`, then trash (`trashed=true`; the contract allows a trash). If an edit landed in
  between, the editing device's next run puts the file back, because an edit beats a deletion.
- **Batch deletes:** sequential, with a little parallelism. Measure with a 300-song library; quota is 325,000 units per
  user per minute.
- **Folder documents** (`downloadDocument` / `uploadDocument`): the same as files, by name in the library folder.
- **Errors**, through `SyncHttpTransport` with a Drive error mapper:
  - `storageQuotaExceeded` → `SyncRemoteStorageFullException`;
  - `userRateLimitExceeded` / 429 → retry;
  - 401 → refresh, then `SyncAuthorizationException`;
  - `insufficientFilePermissions` → that file fails.

### D5. Watching for changes

- **Cursor:** at the end of `list`, `changes.getStartPageToken`.
- **`awaitChanges`:** polls `changes.list(pageToken, fields=newStartPageToken,changes(fileId,removed,file(parents)))`
  every 30 s, for up to 4 minutes, while the app is in front. It answers `true` at the first change inside the library
  folders. Under `drive.file`, the feed only shows Campfire's own files. That's 2 requests a minute in front, costing
  nothing against the quota.
- **`changesSince`:** maps the changed ids back to names through the last listing; an unknown id means "list again".
- A change to `shared-links.json` alone is reported like Dropbox's, so `SyncRemoteWatcher` keeps refreshing the mirror
  without a run.

### D6. Shared links

- **The copy:** `shared/<shareId>.zip`, uploaded like a library file.
- **The link:** `permissions.create(type=anyone, role=reader)`. Creating it again answers the same permission, so a
  copy keeps one link. The link is the file id: `SharedLinkResult.Created` (already provider-neutral)
  carries the Drive provider and `[fileId]`.
- **Download address**, built in `:data:model` from the validated id, never taken from a link:
  `https://www.googleapis.com/drive/v3/files/<fileId>?alt=media&key=<API key>`, if S4 confirms it.
  - The key is public by nature and restricted to the Drive API. It spends Campfire's project quota, so watch the
    quota page after release.
  - The `drive.usercontent.google.com` address has no CORS, so don't use it.
- **Refusals:**
  - a Workspace domain that forbids `anyone` links (400 `invalidSharingRequest` / `publishOutNotPermitted`) →
    `NotPublic`;
  - an admin who blocks `drive.file` apps entirely makes the connection itself fail, with Google's message.
- **Stopping:** `files.delete` (permanent, not trash), so the link dies at once; a 404 counts as success.
- **If S4 fails:** Drive ships without links (P1's capability flag), and the Link tab says links need Dropbox.

### D7. Tests

- Pure parts in `commonTest`, against Ktor `MockEngine` answers taken from real responses:
  - duplicate-name resolution;
  - folder merging;
  - detect-and-recover;
  - the change feed's id→name mapping;
  - the error mapping.
- **Live test:** the sync live-testing recipe adapted to a test Google account, never the owner's real Drive without a
  snapshot.

### D8. Provider docs

- `../../data/source/remote/implementation/CLAUDE.md`: a Google Drive section like the Dropbox one (folder lookup,
  duplicates, detect-and-recover, the feed, links, auth per platform).

## The product around it

### P1. Capabilities

- `SyncProviderCapabilities` on `SyncConnection`: whether the provider makes shared links, and its display name.
- The UI reads it instead of comparing ids.
- A provider that can't work on the running platform (a missing key, Play services absent on Android path b, the web if
  S3 fails) isn't added to `SyncProviders` there.

### P2. Picker and switching

- **Picker:** where a single Connect button is today (Settings, the onboarding sync page, the export Link tab, and the
  Sharing tab when empty), show one button per provider in `SyncProviders`: "Connect Dropbox" and "Connect Google
  Drive". With one provider in the build (forks, debug builds without keys) it looks as today. No sheet; it isn't a
  text input, and two buttons fit.
- **Switching** is Disconnect, then connect the other; there is no separate Switch action. The Disconnect confirmation
  gains:
  - while shares exist, a choice to **stop sharing everything first**. Links made in the old provider otherwise keep
    working, frozen, and can't be stopped from the app once the mirror is forgotten. This extends today's
    `hasPendingStops` note;
  - a line saying other devices stay on the old provider until they are switched too. Nothing can detect that split
    across services, so saying it is the only defence.
- **Joining a provider that already holds songs** works like a new device joining today: on a conflict, the cloud's
  version keeps the name, and this device's becomes the ` (2)` copy. Add a `TwoDeviceSyncTest` case for "device
  switched providers, folder already populated".
- **Strings:** about 17 en/hu pairs name Dropbox (`values[-hu]/strings.xml`). Turn the generic ones into `%1$s`
  strings with the provider's display name, and keep the facts that differ per service as per-provider strings:
  - the folder: `Apps/Campfire Sync` versus "the Campfire folder in your Google Drive";
  - the link permission.
- **Links without the capability:** the Link tab and Sharing tab explain it instead of offering a dead Connect.

### P3. Share links v2

Today's v1 payload is `1.<kind>.<shareId>.<fileId>.<rlkey>.<title>`, and the download address is Dropbox's alone.

- **Dropbox keeps writing v1**, so every released app keeps opening Dropbox links. Drive writes v2:
  `2.gdrive.<kind>.<shareId>.<fileId>.<title>`, with the file id grammar `[A-Za-z0-9_-]{20,64}`. Released apps answer
  v2 with "update Campfire" (`SharedLink.FromNewerVersion`), the right message.
- `SharedLink.Readable` gains `provider`. `downloadUrl` is built per provider, never taken from the link.
- `shared-links.json` entries gain defaulted `provider` and `parts`. Dropbox entries keep `fileId` / `rlkey`, so older
  builds on a Dropbox account read the list as before. A registry lives in one cloud folder, so it never mixes
  providers.
- `ImportRequest.sharedLinkFileId` becomes provider-qualified for Drive (`gdrive:<id>`); Dropbox keys stay a bare
  `fileId`, so remembered imports still match.
- **Opening a Drive link needs no Drive account and no connection**, on every platform, whatever the user's own
  provider.
- **The website's `/s/` landing page** (`CampfireWebsite/s/index.html`) gets the v2 regex and the Drive download
  builder in the same release.
- Check that the longest v2 payload still fits the QR version the export screen uses.
- Tests: `ShareLinksTest` (v1 unchanged, v2 Drive, crafted parts refused), `SharedLinksFormatTest`, and
  `RememberSharedLinkImportUseCaseImplTest`.

### P4. CI and release

- `.github/workflows/publish-*.yml`: keep `DROPBOX_APP_KEY` required, and pass the Google keys as secrets when present.
  A release without them simply lacks Drive.
- The Mac App Store build: a loopback listener needs `com.apple.security.network.server`. Check whether the current
  Dropbox flow already has it.

### P5. Docs and listings

- Root `../../CLAUDE.md` privacy section: `www.googleapis.com`, `oauth2.googleapis.com`, and shared-link downloads from
  `www.googleapis.com` beside `dl.dropboxusercontent.com`.
- Root `../../CLAUDE.md` product rules: a user picks one provider.
- `../../data/source/remote/api/CLAUDE.md` (capabilities, redirect specs) and `../../app/ios/CLAUDE.md` / `../../app/web/CLAUDE.md`
  where they name Dropbox's redirects.
- The store listings (`documentation/store-listings/*`, including the Mac App Store entitlement text), the website's
  privacy and support pages, and the README.

## Order

1. Spikes S1–S5. Stop if S1 fails (see Risks).
2. D1–D5 with the provider hidden behind an empty key in release builds.
3. P1, P2, and D6 + P3 together with the website.
4. P4 and P5 in the release that turns it on.

## Risks

- **S1 fails** (a `drive.file` grant tied to one client type): Drive can't be offered across platforms. Fallback:
  `drive.appdata`, which gives sync only (a hidden folder, no shared links). That's a much weaker product, probably not
  worth shipping.
- **Android without Play services** (path b) has no Drive.
- **Lost updates** if detect-and-recover has a bug: test it more heavily than any other part.
- **API key quota abuse** through shared-link downloads.
- **Google policy changes** to custom schemes or to installed-app secrets.
