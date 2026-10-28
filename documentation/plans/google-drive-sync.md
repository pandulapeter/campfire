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

**Status:** second draft, checked against the code and against Google's current documentation on 2026-10-26. The
groundwork (the former plan 01) landed on 2026-10-26 (5a7253847..f2f6e418a) and ships in v5.1.0 with Dropbox the only
provider. Nothing of the Drive work below is implemented.

**What the groundwork left in place** (read the code; these are the hooks):

- The stored credentials name the active provider (`SyncProviders.stored()` / `connected()` / `byId`), and a provider
  forgets only its own (`SyncCredentialsStore.forget(providerId)`). `SyncProviderId` is a data class with a `DROPBOX`
  value, so Drive adds a second value beside it. `SyncProviders` is built in the remote module's `Module.kt`, which adds
  Dropbox only when `DROPBOX_APP_KEY` is non-empty.
- `auth/OAuthTokens` renews tokens and appends its optional `clientSecret` to renewals only. Each provider builds its
  own authorization-code form (Dropbox in `DropboxSyncProvider`), so a provider with a secret puts it there too.
- `network/SyncHttpTransport` retries through a per-provider `SyncHttpErrorMapper` (Dropbox's is
  `dropbox/DropboxErrors.kt`), which knows refusals beyond renewal, slow-downs, requested waits, and error summaries,
  nothing more. Storage full and 401 are mapped in `DropboxTransport`'s `ensureSuccessful`; Drive needs the same.
- `SyncFolder`'s KDoc makes case-insensitive name resolution part of the contract, and its cursors and revisions are
  opaque strings. `FakeSyncProvider`'s `resolvesListedNames` and a `TwoDeviceSyncTest` case already cover a service
  with exact names. The shared-copy methods are `uploadSharedFile`, `createSharedLink`, and `deleteSharedFile`. The
  `contentHashOf` KDoc still says "Drive with MD5"; D4 corrects it.
- `SharedLinkResult.Created(provider, parts)`. `storedParts()` in `SharedLinksRepositoryImpl.kt` still accepts only
  Dropbox's two parts; P3 widens it.
- The redirect is Dropbox's in three places: `auth/SyncRedirect.kt` (`campfire` / `oauth`, built into `campfire://oauth`
  by the Android and iOS authenticators; iOS passes `REDIRECT_SCHEME` as the session's `callbackURLScheme`),
  `DesktopSyncAuthenticator.PORT` (53682, because Dropbox matches redirects exactly), and the web's `currentPageUrl()`.
  `SyncAuthenticator.prepareRedirectUri()` takes no provider.
- The UI connects `SyncController.offeredSyncProvider` (the first of `SyncRepository.availableProviders`) or a
  connected account's own provider; P2's picker replaces the former. `canConnectSync` replaced `canConnectDropbox`.
  Every Connect label is `settings_sync_connect_dropbox`.
- **Not solved, for P2:** `SyncCredentialsDocument` holds one provider's tokens, so a second provider's finished token
  exchange (the provider's `credentialsStore.save(...)`) replaces the first's. Starting or cancelling B keeps A;
  finishing B loses A. Switching as Disconnect, then Connect, never meets this, but a "connect B while A is connected"
  flow would.

**Goal:** Google Drive as a second sync provider, the user picking Dropbox or Drive. It covers sync, the synced
preferences, and shared links. It runs on Android, iOS, and desktop, and on the web if spike S3 allows it.

**Verdict:** feasible. Drive differs from Dropbox in three ways the provider absorbs:

1. Files are addressed by id, and names can repeat.
2. There is no compare-and-swap write: v3 files have no ETag, and `files.update` documents no precondition. D4's
   detect-and-recover is the design, not a fallback.
3. There is no long poll.

Sign-in is the biggest change since the first draft. Google's native-app guide (updated 2026-10-16) says custom URI
scheme redirects are no longer supported, so Android signs in through Google Identity Services, iOS needs checking
(S2), and only the desktop keeps Dropbox's plain browser flow.

Steps D1–D8 are the Drive provider itself. P1–P5 are the product work around it: the picker, switching, the link
format, build, and docs.

## Spikes first (one to two days, throwaway code, before anything below)

| # | Question | Why it matters |
|---|---|---|
| S1 | With `drive.file`, does a file created through one client type (say desktop) show up for another client type (iOS) in the same Cloud project, both in `files.list` and in `changes.list`? Does the feed show only Campfire's files? | If not, a second device can't see the library folder, and Drive is dead for multi-platform use. Google documents neither answer. |
| S2 | iOS: does a plain code + PKCE flow through `ASWebAuthenticationSession`, with the iOS client's reversed-client-id redirect and no Google SDK, still complete with a refresh token? | Google's iOS guide still uses that redirect, through its SDK; the native-app guide now rules custom schemes out. Decides whether iOS keeps Dropbox's code path or needs the GoogleSignIn SDK behind D2's `NativeAuthorizer`. |
| S3 | Web: does `oauth2.googleapis.com/token` accept code + PKCE from a Web-application client without `client_secret`? If not, is an embedded secret acceptable? | Google marks the secret optional but documents no PKCE for web clients, and reports conflict. Decides whether the web build offers Drive. |
| S4 | Does an anonymous `fetch` from `https://campfire-songbook.com` of `https://www.googleapis.com/drive/v3/files/<id>?alt=media&key=<key>` for an `anyone`/`reader` file return the bytes, with no `resourceKey` needed, and a 404 once the file is deleted? Does a second `permissions.create(anyone, reader)` on the same file answer the same permission? | CORS headers on `www.googleapis.com` are confirmed; the anonymous download itself is common practice but undocumented. Decides whether shared links open on the web and on the landing page, and D6's one-link-per-copy. |
| S5 | Android: does Identity Services' `AuthorizationClient.authorize` answer silently, with no resolution to show, from the background sync run once granted? How does it fail on a device without Google Play services? | Shapes D2's Android path, the only one left. |

Dropped since the first draft: an `If-Match` spike (v3 has no conditional write to find) and Android custom schemes
(withdrawn).

## The provider (`:data:source:remote:implementation/googleDrive/`)

### D1. Cloud project and build keys

- One Google Cloud project, "Campfire". OAuth consent screen: External, **In production** (in Testing, refresh tokens
  die after 7 days).
- Scopes: `drive.file` only, plus `openid email profile`. All non-sensitive: no sensitive-scope review, no CASA
  assessment, no 100-user cap. Brand verification is still needed: a homepage on campfire-songbook.com that describes
  the app and links the privacy policy, the policy on the same domain and linked identically from the consent screen,
  and the domain verified in Search Console.
- Clients:
  - **iOS:** reversed-client-id redirect, no secret (S2).
  - **Desktop app:** loopback on any free port, Google's recommended path. The secret is marked optional, but
    exchanges without it are reported to fail, and Google says desktop apps can't keep it confidential, so it ships in
    the build.
  - **Android:** package name + SHA-1 of the release key and of the debug keystore; used by Identity Services, no
    redirect.
  - **Web application:** if S3 allows it. Redirects `https://campfire-songbook.com/app/` and `http://localhost:8080/`.
  - **API key**, restricted to the Drive API, for shared-link downloads (D6).
- Build properties: `campfire.googleDrive.iosClientId`, `.desktopClientId`, `.desktopClientSecret`, `.androidClientId`,
  `.webClientId`, and `.apiKey`, each defaulting to empty in `../../gradle.properties`.
  - `generateRemoteConfiguration` (`../../data/source/remote/implementation/build.gradle.kts`) writes them beside
    `DROPBOX_APP_KEY`.
  - The provider is left out of `SyncProviders` on a platform whose key is empty, as Dropbox is today.
- Token lifetimes to know: a refresh token unused for six months dies, and an account holds 100 per client (the oldest
  goes silently). Both end in a 401 the provider turns into `SyncAuthorizationException`, so reconnecting is the answer.

### D2. Sign-in per platform

- **Redirects per provider first.** Change `SyncAuthenticator.prepareRedirectUri()` to `prepareRedirectUri(provider)`,
  with each provider's `RedirectSpec` naming:
  - the custom scheme for iOS (the session's `callbackURLScheme`; Android's Drive path has no redirect at all);
  - whether the desktop loopback may take any free port (Google allows it; Dropbox needs 53682);
  - the web redirect.

  `DesktopSyncAuthenticatorTest` gains an any-port case. `../../app/android/src/main/AndroidManifest.xml` keeps only
  Dropbox's scheme, and `../../app/ios/CLAUDE.md` says `ASWebAuthenticationSession` needs no `CFBundleURLTypes` entry,
  so `Info.plist` likely stays as it is; S2 confirms.
- **iOS:** code + PKCE through `ASWebAuthenticationSession`, with the reversed client id as the callback scheme.
  Refresh tokens stay in the Keychain, as today. If S2 fails: the GoogleSignIn iOS SDK (added to the Xcode project,
  credited) behind the `NativeAuthorizer` below, keeping its own refresh token in the Keychain.
- **Desktop:** code + PKCE on a free loopback port, with the bundled secret in the provider's code-exchange form and in
  `OAuthTokens`'s renewals.
- **Android:** Identity Services `AuthorizationClient` (`play-services-auth`, current: Credential Manager is sign-in,
  this is scopes), behind a new optional `NativeAuthorizer` the Android platform supplies:
  - it gives a one-hour access token and no refresh token without a backend, so the provider asks for a new token
    whenever the old one expires (silent once granted: the result has no resolution to show);
  - only the account is stored;
  - it needs Google Play services, so the provider is hidden on devices without them;
  - it adds `play-services-auth`, which has to be credited (root `../../CLAUDE.md`, "Build").
- **Web**, decided by S3: an embedded Web-client secret with the normal code flow, or no Drive on the web. A secret in
  a wasm bundle isn't confidential, but a stolen one only completes exchanges for codes issued to the registered
  redirects. The token-only model (hourly popups, no background renewal) isn't worth building.
- **Account:** `about.get(fields=user)`: `permissionId` as the account id (it never changes), plus `displayName` and
  `emailAddress`.
- **Disconnect:** `POST oauth2.googleapis.com/revoke?token=<refresh token>` (`revokeAccess()` on Android). Google's
  docs say the endpoint has no CORS; a probe shows it does. On the web send it as a `no-cors` request, so the answer
  doesn't matter.

### D3. Finding the folder

`drive.file` sees only what Campfire created, which gives the same privacy as Dropbox's app folder, but there is no app
folder to find by path.

- **Layout:** a visible `Campfire` folder in My Drive, holding `songs/`, `setlists/`, `shared/`, `preferences.json`,
  and `shared-links.json`. Each folder is marked with `appProperties.campfire = "library" | "songs" | …`, so it is
  found by property, even after the user renames or moves it.
- **Lookup:** `files.list(q = "appProperties has { key='campfire' and value='library' } and trashed = false")`.
- **Caching the ids:** keep them in a new defaulted `extras: Map<String, String>` on `SyncCredentialsDocument`; every
  field of it is defaulted and unknown keys are ignored, so a document written by an older version keeps loading. Look
  them up again on any 404.
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
- **Hash:** Drive's `contentHashOf` is plain SHA-256, matching `sha256Checksum` (filled for content stored in Drive).
  Dropbox's stays its block hash. Fix the `SyncFolder` KDoc that says Drive uses MD5.
- **`download`:** metadata, then `alt=media`. Check the bytes against `sha256Checksum`; on a mismatch (the file changed
  between the two requests), read again once, then fail that file. Return the revision whose hash matched.
- **`upload`: detect and recover**, since there is no conditional write:
  1. Read `headRevisionId` and answer `Conflict` if it isn't the expected one.
  2. Upload (`files.update`, `uploadType=media`).
  3. List the file's revisions. If the revision before ours isn't the expected one, another device wrote between 1
     and 2: fetch that revision's bytes (`revisions.get alt=media`) and save them as a new numbered file beside it.

  Both versions survive, as the engine promises. The recovery reads the previous revision in the same run; Drive's
  retention (binary revisions typically 30 days, purging possible past 100, the head never purged) is only a margin.
- **Creating** a new file has no precondition. A race produces a duplicate name, which `list` resolves.
- **`delete`:** compare `headRevisionId`, then trash (`trashed=true`; the contract allows a trash). If an edit landed in
  between, the editing device's next run puts the file back, because an edit beats a deletion.
- **Batch deletes:** sequential, with a little parallelism. Measure with a 300-song library. The quota (projects
  created since 2026-05-01) is 325,000 units per user per minute: `files.list` 100, `files.update` 50, `files.get` 5,
  a download 200. Trashing 300 songs is 15,000 units.
- **Folder documents** (`downloadDocument` / `uploadDocument`): the same as files, by name in the library folder.
- **Errors:** `SyncHttpTransport` with a Drive `SyncHttpErrorMapper` handles `userRateLimitExceeded` / 429 (a
  slow-down) and the 401 renewal. A Drive `ensureSuccessful`, like `DropboxTransport`'s, maps the rest:
  - `storageQuotaExceeded` → `SyncRemoteStorageFullException`;
  - a refusal after renewal → `SyncAuthorizationException`;
  - `insufficientFilePermissions` → that file fails.

### D5. Watching for changes

- **Cursor:** at the end of `list`, `changes.getStartPageToken`.
- **`awaitChanges`:** polls `changes.list(pageToken, fields=newStartPageToken,changes(fileId,removed,file(parents)))`
  every 30 s, for up to 4 minutes, while the app is in front. It answers `true` at the first change inside the library
  folders. Under `drive.file`, the feed should only show Campfire's own files (S1 confirms). Two requests a minute in
  front cost nothing against the quota. The contract's KDoc describes a held request, but a poll that answers `false`
  after a few minutes meets it. Drive's event subscriptions (GA since 2026-05) need Pub/Sub, a server, so they're out.
- **`changesSince`:** maps the changed ids back to names through the last listing; an unknown id means "list again".
- A change to `shared-links.json` alone is reported like Dropbox's, so `SyncRemoteWatcher` keeps refreshing the mirror
  without a run.

### D6. Shared links

- **The copy:** `uploadSharedFile` writes `shared/<shareId>.zip` like a library file.
- **The link:** `createSharedLink` calls `permissions.create(type=anyone, role=reader)`. If S4 shows a repeat answers
  the same permission, a copy keeps one link by itself; otherwise `permissions.list` first. The link is the file id:
  `SharedLinkResult.Created` (already provider-neutral) carries the Drive provider and `[fileId]`.
- **Download address**, built in `:data:model` from the validated id, never taken from a link:
  `https://www.googleapis.com/drive/v3/files/<fileId>?alt=media&key=<API key>`, if S4 confirms it.
  - The key is public by nature. It can only be restricted to the Drive API, since the apps download with it too and
    have no referrer; a second, referrer-restricted key for the website and the web build is an option once S4 is in.
  - Every download spends the project's quota (200 units) and its 1 TB per day egress allowance before charges apply.
    Watch the quota page after release and set a billing alert.
  - The `drive.usercontent.google.com` address has no CORS, so don't use it.
- **Refusals:**
  - a Workspace domain that forbids `anyone` links (400 `invalidSharingRequest` / `publishOutNotPermitted`) →
    `NotPublic`;
  - an admin who blocks `drive.file` apps entirely makes the connection itself fail, with Google's message;
  - a copy Google flags as abusive can't be downloaded anonymously (`acknowledgeAbuse` is the owner's), so the import
    reports the copy missing, as for a deleted one.
- **Stopping:** `deleteSharedFile` calls `files.delete` (permanent, not trash), so the link dies at once; a 404 counts
  as success.
- **If S4 fails:** Drive ships without links (P1's capability flag), and the Link tab says links need Dropbox.

### D7. Tests

- Pure parts in `commonTest`, against Ktor `MockEngine` answers taken from real responses:
  - duplicate-name resolution;
  - folder merging;
  - detect-and-recover, with a write between steps 1 and 2;
  - the change feed's id→name mapping;
  - the error mapping, both the mapper's and `ensureSuccessful`'s.
- **Live test:** the sync live-testing recipe adapted to a test Google account, never the owner's real Drive without a
  snapshot.

### D8. Provider docs

- `../../data/source/remote/implementation/CLAUDE.md`: a Google Drive section like the Dropbox one (folder lookup,
  duplicates, detect-and-recover, the feed, links, auth per platform).

## The product around it

### P1. Capabilities

- `SyncProviderCapabilities` on `SyncConnection`: whether the provider makes shared links, and its display name.
- The UI reads it instead of comparing ids.
- A provider that can't work on the running platform (a missing key, Play services absent on Android, the web if S3
  fails) isn't added to `SyncProviders` there.

### P2. Picker and switching

- **Picker:** where a single Connect button is today (the Settings sync section while disconnected or after a failed
  connection, the onboarding sync page, the export Link tab, and the Sharing tab while sync isn't connected), show one
  button per provider in `SyncProviders`: "Connect Dropbox" and "Connect Google Drive". With one provider in the build
  (forks, debug builds without keys) it looks as today. No sheet; it isn't a text input, and two buttons fit. The
  "connect again" paths (the Sharing tab and the Link tab asking for a lost permission) use the account's own provider
  and need no picker.
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
- **Strings:** 17 keys in each of `values[-hu]/strings.xml` name Dropbox, from `settings_sync_connect_dropbox` to the
  `share_*` failures and the Sharing tab's descriptions. Turn the generic ones into `%1$s` strings with the provider's
  display name, and keep the facts that differ per service as per-provider strings:
  - the folder: today "your Dropbox folder" (`settings_library_delete_confirmation_sync`) or "the cloud folder"; Drive's
    is "the Campfire folder in your Google Drive";
  - the link permission.
- **Links without the capability:** the Link tab and Sharing tab explain it instead of offering a dead Connect.

### P3. Share links v2

Today's v1 payload is `1.<kind>.<shareId>.<fileId>.<rlkey>.<title>` (`ShareLinks.kt`, `VERSION = 1`), and the
download address is Dropbox's alone.

- **Dropbox keeps writing v1**, so every released app keeps opening Dropbox links. Drive writes v2:
  `2.gdrive.<kind>.<shareId>.<fileId>.<title>`, with the file id grammar `[A-Za-z0-9_-]{20,64}`. It matches the
  newer-version grammar released apps already have, so the `https://…/s/#`, `/app/#`, and `campfire://s/` forms get
  "update Campfire" (`SharedLink.FromNewerVersion`), the right message. A bare v2 payload pasted into a released app is
  silently not a link (`SharedLinkText.kt` drops `FromNewerVersion` for text starting with a digit); links are never
  shared bare, so accept it.
- `SharedLink.Readable` gains `provider`. `downloadUrl` is built per provider, never taken from the link;
  `DropboxSharedLinkAddress.kt` gets a Drive sibling, and `ShareLinks.create(kind, shareId, fileId, rlkey, title)` with
  its caller in `SharedLinksMappers.kt` take the provider's parts instead.
- `shared-links.json` entries (`SharedLinksEntry`) gain defaulted `provider` and `parts`, listed in
  `SharedLinksFormat.ENTRY_KEYS`. Dropbox entries keep `fileId` / `rlkey`, so older builds on a Dropbox account read the
  list as before (an unknown field already round-trips). A registry lives in one cloud folder, so it never mixes
  providers.
- `ImportRequest.sharedLinkFileId` becomes provider-qualified for Drive (`gdrive:<id>`); Dropbox keys stay a bare
  `fileId`, so remembered imports still match.
- **Opening a Drive link needs no Drive account and no connection**, on every platform, whatever the user's own
  provider.
- **The website's `/s/` landing page** (`CampfireWebsite/s/index.html`) gets the v2 regex and the Drive download
  builder in the same release.
- The QR code needs no check: `QrCode.encode` picks the smallest version that fits, and v2 is never longer than v1 (it
  drops `.<rlkey>` and adds `gdrive.`).
- Tests: `ShareLinksTest` (v1 unchanged, v2 Drive, crafted parts refused), `SharedLinksFormatTest`, and
  `RememberSharedLinkImportUseCaseImplTest`.

### P4. CI and release

- `.github/workflows/publish-*.yml` require `DROPBOX_APP_KEY` through `require_secrets.py` and write it with
  `write_local_properties.py`: keep it required, and pass the Google keys as secrets when present. A release without
  them simply lacks Drive.
- The Mac App Store build already has `com.apple.security.network.server` (`app/desktop/app-store.entitlements`, for
  the Dropbox listener and the single-instance socket), so an any-port loopback needs nothing more. Its justification
  in `documentation/store-listings/mac-app-store/en.md` names the Dropbox redirect; P5 rewords it.

### P5. Docs and listings

- Root `../../CLAUDE.md` privacy section: `www.googleapis.com`, `oauth2.googleapis.com`, and shared-link downloads from
  `www.googleapis.com` beside `dl.dropboxusercontent.com`.
- Root `../../CLAUDE.md` product rules: a user picks one provider.
- `../../data/source/remote/api/CLAUDE.md` (capabilities, redirect specs) and `../../app/ios/CLAUDE.md` /
  `../../app/web/CLAUDE.md` where they name Dropbox's redirects; the KDoc in `SharedLink.kt` ("shared from someone's
  Dropbox").
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
- **S2 fails:** iOS takes on the GoogleSignIn SDK, a dependency in the Xcode project.
- **Android without Play services** has no Drive.
- **Lost updates** if detect-and-recover has a bug: test it more heavily than any other part.
- **API key quota and egress abuse** through shared-link downloads.
- **Google policy changes:** custom schemes went in 2026; installed-app secrets or API-key downloads could follow.
