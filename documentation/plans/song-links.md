<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Song links — implementation plan

Rewritten 2026-10-16 against `87b2922a9`. This replaces the 2026-10-07 plan, which carried the song inside the link
and so could not hold a setlist of any size. Now a song or a whole setlist is shared as a short link, backed by a zip
in the sharer's own Dropbox that Campfire keeps current as the library changes. On a device with Campfire, opening
the link opens the import screen, which downloads it. Without Campfire, the link leads through a landing page to the
store, and the installed app imports it after onboarding. There is still no Campfire server.

**Read §11 (corner cases) before writing any code.** Most of this feature is its edges.

## 1. Decisions (settled with the user; do not reopen)

1. **Dropbox only.** Creating a link needs the sync connection; opening one needs no account.
2. **Connecting from the export screen connects sync**: the same connection Settings makes, so the whole library
   starts syncing. The screen says so plainly before the sign-in (§6.2). There is no "sharing only" connection.
3. **A link always serves the current version.** Sync runs rebuild the shared zip whenever the setlist or any of its
   songs changed (§5).
   - Copies people already imported never change. Recipients update by opening the same link again, and the import
     screen reconciles.
   - There is no live sync to recipients and no read-only "subscribed" setlist: an imported copy is the recipient's
     own.
4. **One link per shared item, kept for as long as it is shared.**
   - A rename keeps the link (§5.4).
   - Deleting the item in Campfire stops sharing it.
   - **Stop sharing** (export screen, Settings) deletes the shared file from Dropbox, and the link stops working.
5. **A single song is shared as a zip too**, so both kinds take one path: download, then the import review.
6. **Settings → Library → Shared links** lists everything shared from this Dropbox account, on every device.
7. **The recipient's screens never mention Dropbox.** The import screen names the song or setlist, and the landing
   page says "Download the file". The sharer's screens do mention Dropbox, since it is their account.
8. **Install, then import:**
   - **Android:** automatic (Play install referrer).
   - **iOS:** one tap (the clipboard, at the end of onboarding).
   - **Desktop:** one tap (the landing page's **Open in Campfire** after installing).
   - **Web:** automatic.
9. **`campfire://s/<payload>` is never the shared link.** It is only the target of the landing page's buttons and of
   Android's `intent://` (§9): a custom scheme has no fallback when the app is missing.

## 2. Facts to establish first (step 0, throwaway code, findings written back here)

Each of these changes code if it comes out differently. Use a real Dropbox account and the development app
("Campfire Sync") with `sharing.write` and `sharing.read` ticked in the App Console (§13).

1. **`sharing/create_shared_link_with_settings` on an app folder file**, with `{"requested_visibility": "public",
   "audience": "public", "access": "viewer"}`. Record:
   - the returned `url` shape (expected `https://www.dropbox.com/scl/fi/<fileId>/<name>?rlkey=<key>&st=<x>&dl=0`);
   - what a Basic account, a Plus account, and a team account whose admin forbids public links answer;
   - `link_permissions.resolved_visibility`;
   - the `shared_link_already_exists` error, and whether its `metadata` carries the existing link.
2. **Which parts of that address an unauthenticated download needs.** Try `fileId` + `rlkey` without the name and
   without `st`, and `dl=1` in place of `dl=0`. Record the redirect chain, the final host, and `Content-Disposition`.
   The payload (§3) carries only what is needed.
3. **Overwriting keeps the link.** Upload over the file with `update:<rev>`; the same link must serve the new bytes.
   Record how soon (CDN caching).
4. **Deleting the file kills the link.** Record what a download then answers: status, body, and whether it is an
   HTML page with 200.
5. **CORS.** From a page on `https://campfire-songbook.com` (or a local page with that origin through a tunnel),
   `fetch` the `dl=1` address. Does every hop of the redirect chain send `Access-Control-Allow-Origin`? If not, the
   web takes the fallback of §9.4.
6. **What a refused or banned link answers** (a link over its traffic limit, a suspended account): status and body.
   Whatever it is, §8.3's zip check must turn it into "This link no longer works".
7. **`missing_scope`.** With a token granted before the new scopes were ticked, call the sharing endpoint. Record the
   status (expected 401) and `error_summary` (expected `missing_scope/…`).
8. **Android `intent://`** with a path payload and an `S.browser_fallback_url` to Play carrying a `referrer`:
   - From Chrome, with and without the app installed: does it open the app, or Play?
   - Does the referrer arrive through `InstallReferrerClient` after an install from Play's internal testing track?
9. **iOS pasteboard.** On iOS 16+, `UIPasteboard.general.detectPatterns(for: [.probableWebURL])` must not show the
   paste prompt. Reading `.url` or `.string` afterwards from a button tap shows "Allow Paste" once, which is
   acceptable.

## 3. The link

```
https://campfire-songbook.com/s/#1.setlist.<shareId>.<fileId>.<rlkey>.<title>
campfire://s/1.setlist.<shareId>.<fileId>.<rlkey>.<title>          (the buttons' form, §9)
```

- **`1`**: the format version. A reader that does not know a version says "made by a newer version of Campfire"
  rather than calling the link broken.
- **Kind**: `song` or `setlist`. It only chooses wording; the content decides what is imported.
- **`shareId`**: 16 random characters `[a-z2-7]`. It is the shared file's name in Dropbox (`/shared/<shareId>.zip`),
  fixed for the life of the share.
- **`fileId`, `rlkey`**: from the Dropbox link, each `[A-Za-z0-9_-]+`, at most 64 characters. Step 0 may drop
  `shareId` from the payload if the download does not need the name.
- **`title`**: the song's or setlist's title when shared, percent-encoded UTF-8, at most 120 bytes before encoding.
  It is for display only (landing page, import screen) and never trusted for anything else.
- **The grammar is strict**: one regex in `ShareLinks`, and anything else is unreadable.
- **The app never downloads from an address taken from the payload.** It builds
  `https://www.dropbox.com/scl/fi/<fileId>/<shareId>.zip?rlkey=<rlkey>&dl=1` itself, so a crafted link cannot point
  Campfire at another host.
- **Why a fragment, and why a path in the scheme**: a browser never sends the part after `#`, so the ids stay out of
  every server log. The scheme form uses a path instead because Android's `intent://` syntax claims the `#` (§9.1),
  and a custom scheme reaches no server anyway.
- About 130 characters: a version 7 QR code at level M.

## 4. Dropbox (`:data:source:remote`)

### 4.1 Calls

**`SyncFolder`** (`data/source/remote/api/.../sync/SyncFolder.kt`) gains a small sharing surface, implemented in
`DropboxSyncProvider`:
- `uploadSharedFile(shareId, bytes, expectedRevision: String?): RemoteWriteResult`: writes `/shared/<shareId>.zip`
  with `add` or `update:<rev>`, `autorename: false`, and `mute: true`, as `upload()` does.
- `createSharedLink(shareId): SharedLinkResult`, one of `Created(fileId, rlkey)`, `NotPublic`, or `Refused`:
  - calls `create_shared_link_with_settings`;
  - reads `shared_link_already_exists`' metadata as success;
  - parses the URL into its parts (pure, tested);
  - a link whose resolved visibility is not public is `NotPublic`.
- `deleteSharedFile(shareId)`: "already gone" is success, as library deletes are.
- Documents: the registry (§4.2) uses the existing `downloadDocument` / `uploadDocument` under its own name.

**The listing**: `DropboxSyncProvider.list()` already lists the whole app folder recursively and keeps only `/songs`
and `/setlists` (`toRemoteFiles`, around line 375).
- It now also returns the `/shared/*.zip` entries, with their `content_hash` and revision, as a separate
  `sharedFiles` list. That costs no extra request.
- `PassListing` and `DeletionGuard` never see them.

**`missing_scope`**: today `DropboxTransport.request` refreshes the token on every 401 and then throws
`SyncAuthorizationException`, which a run turns into `ConnectionFailed(AUTHORIZATION)`.
- **A 401 whose `error_summary` starts with `missing_scope` must not do that.** Detect it before the refresh and throw
  a new `SyncScopeMissingException`.
- Sharing calls catch it and report "connect again to allow links" (§6.2).
- A run that meets it in its shared-copy step (§5) skips the step and leaves the connection alone.
- Tested in `DropboxTransportTest` with both a plain 401 and a `missing_scope` 401.

**Storage full**: `insufficient_space` already maps to `SyncRemoteStorageFullException`; sharing reports it as "Your
Dropbox is full".

### 4.2 The registry: `shared-links.json` at the app folder root

```json
{ "version": 1, "links": [ {
    "shareId": "k3j…", "kind": "setlist", "fileName": "summer_gig.setlist.json", "title": "Summer gig",
    "fileId": "…", "rlkey": "…", "sharedAt": "2026-10-16", "state": "ok"
} ] }
```

- **`state`** is one of:
  - `ok`;
  - `orphaned` (§5.4): the item is no longer in the library, and nobody stopped sharing it;
  - `tooLarge` (§5.3): the latest version could not be shared.
- **`sharedAt`** is for display only, never a decision.
- **Edited only as operations**: read, change, `uploadDocument(expectedRevision)`, and on `Conflict` re-read and
  re-apply, up to 3 times.
  - The operations are add an entry, remove an entry by `shareId`, set `fileName` / `title`, and set `state`. Each is
    idempotent.
  - Unlike `preferences.json`, this needs no three-way merge and no base in the index.
- **Not under the run lock.** The share's file and the registry entry are written with revision checks, so a sharing
  action and a run's shared-copy step never clobber each other (§11.2). Taking the lock would make the first share
  after connecting wait for the whole first sync.
- **The local mirror**, `preferences/shared-links.json`, is the last registry read, so the export screen and Settings
  know what is shared without a request. It is tied to an account, like the sync index:
  - replaced on every read and every successful operation;
  - **excluded from device backups** (Android `res/xml` rules; iOS `FileStorage.keepOutOfDeviceBackup`);
  - deleted on disconnect and by `forgetStoredConnection`;
  - never exported.
- **The local journal**, `preferences/shared-links-journal.json`: renames and deletions of shared items made on this
  device, applied to the registry by the next run (§5.4). Same backup and forget rules as the mirror.
- The root `CLAUDE.md` library layout gains both files.

## 5. Keeping shared copies current (`:data:sync:implementation`)

### 5.1 The step

`synchronizeSharedCopies(provider, listing)` is a new step in its own package `shared/`, with a `CLAUDE.md`. It runs
in `SyncRunner.run()`'s `SyncEngine.Result.Completed` branch, right after `synchronizePreferences` (around
`scheduling/SyncRunner.kt:151-182`).

1. **Skip it entirely** when the listing has no `/shared/` files and the local journal is empty. This is the common
   case, and it costs no request.
2. Read the registry (one download). Apply the journal (§5.4), then clear the journal entries that were applied.
3. For each entry in the `ok` or `tooLarge` state:
   1. Find the item in the local library by `fileName`. If it is missing, the entry becomes `orphaned` (§5.4), and
      nothing is uploaded.
   2. Build its zip (§5.2). Over `ImportLimits.MAX_IMPORT_SIZE`, the entry becomes `tooLarge` and the file is left as
      it was, so the last version that fit keeps being served.
   3. Compare the zip's Dropbox content hash (`crypto/ContentHash.kt`) with the listing's `content_hash` for
      `/shared/<shareId>.zip`. If they are equal, do nothing: content decides, never a clock.
   4. Otherwise, upload with `update:<listing revision>`.
      - `Conflict` (another device uploaded in between) is dropped for this run.
      - A missing file (deleted by hand in Dropbox, §11.3) makes the entry `orphaned`.
   5. If the entry's `title` changed, update it in the registry (display only).
4. **Errors**: follow `synchronizePreferences`. Rethrow cancellation and `SyncAuthorizationException`. Catch
   `SyncScopeMissingException` and everything else, log it, and name it in the `SyncSummary`. A failure here never
   fails the run.

### 5.2 Deterministic zips

- **Shared zips must be byte-identical for identical content**, or two devices would re-upload each other's copies on
  every run. `ArchiveLocalSourceImpl.pack` stamps every entry with the current local time (around lines 46-53).
  - Add `pack(entries, modifiedAt: DosTimestamp)`, and pass `DosTimestamp.EARLIEST` for shared copies.
  - The export screen's own Zip keeps today's timestamp.
- **What goes in**:
  - A shared setlist zip is exactly `ExportSetlistUseCase(setlistFileName, songFileNames = null)`: the stored
    setlist document plus every song it names, in setlist order.
  - A shared song zip is `ExportSongsUseCase(listOf(fileName), isArchive = true)`, named `<shareId>.zip` regardless.
- **What stays out**: per-device overrides (transposition, capo, and tempo outside a setlist) are not in the files, so
  they never travel. A setlist's own entries do.
- **Test**: two packs of the same library, minutes apart and in two time zones, give identical bytes.

### 5.3 What "changed" covers

Anything that changes the bytes counts:
- the setlist's title, description, date, countdown, archive flag, or order;
- its entries' transposition, tempo, and capo;
- any song's text;
- a song added or removed.

Renaming a song inside a shared setlist updates the setlist document (`followSongReferences`), so the copy follows.
Deleting a song removes it from every setlist (`DeleteSongUseCaseImpl`), so the copy loses it.

### 5.4 Renames and deletions

After the fact, the library cannot tell a rename from a deletion: sync sees a rename as a delete plus a create.
**Shares are therefore never stopped by inference.** Only an explicit action, on the device that performed it, stops
a share.

- **Renames**: `RenameSongFileUseCaseImpl`, and a setlist title change (`EditSetlistUseCaseImpl` → `renameSetlist`).
  - If the old name is in the local mirror, append `rename(old, new)` to the journal.
  - If connected and online, also apply it to the registry at once, best effort. The next run applies whatever is
    left.
  - The link survives because the Dropbox file is named by `shareId`, never by the item.
- **Deletions in Campfire**: `DeleteSongUseCaseImpl`, `DeleteSetlistUseCaseImpl`, and **Delete library**.
  - If the name is in the mirror, append `stop(shareId)` to the journal.
  - It is applied the same way: delete the file, then remove the entry.
  - The delete confirmation says so (§7.3).
- **Missing without an explicit action**: the item was deleted outside Campfire, renamed on a device without the
  mirror, or its journal was lost with the app's data.
  - The entry becomes `orphaned`, and the file keeps serving the last version.
  - Settings shows "No longer in your library" with **Stop sharing**.
  - If the item reappears under the same name (a delete-and-create still in flight from another device), the next run
    sets it back to `ok`.
- **Order matters**: the journal is applied before items are looked up (§5.1, step 2), so a rename arriving in the
  same run as its library pass never orphans the entry.
- **A rename arriving from another device**: device B renames and syncs, and its own run applies its journal right
  after its library pass. Device A sees the new name in the registry on its next read. If B's run dies between the
  two, A may mark the entry `orphaned` for one run, and B's next run repairs it.

## 6. The export screen (`ui/screens/export`)

### 6.1 The format

- `PrintSettings.Format` (`data/model/.../preferences/PrintSettings.kt:42`) gains `LINK("link")`. Older versions read
  `link` as PDF (`UserPreferencesMappers.kt:131`).
- `FormatChoice` (`PrintOptions.kt:308`) has three segments for a setlist or one song: PDF, Zip / ChordPro, and Link.
  - It is not offered for several songs at once (§12).
  - It is hidden where sync is not configured in the build (`SyncProviders` empty), as Settings already handles.
- A setlist's song ticks are hidden for Link: a link always shares the whole setlist.
- Link is offered in read only mode, since it writes nothing to the library. Connecting follows whatever Settings' sync
  section allows there.

### 6.2 Its states

| State | Preview | FAB | Options |
| --- | --- | --- | --- |
| Sync not connected | "Share a link anyone can open, made from your Dropbox. Connecting Dropbox also keeps your whole library in sync with it on all your devices." | **Connect Dropbox** | — |
| Connecting | "Connecting…" | **Cancel** | — |
| `ConnectionFailed` | the Settings wording for it | **Connect again** | — |
| Connected, not shared | "Anyone with the link can open this setlist and its songs in Campfire. It stays up to date as you change it; copies people already imported don't change, and they can open the link again to update." | **Create link** | — |
| Creating | `LayoutIndicator` | — | — |
| Shared | QR code (white card, black modules), the link (monospace, selectable), "Shared on 16 October" | **Copy link** | **Save QR code**, **Stop sharing** |
| Shared, `tooLarge` | the above, plus "The latest version is too large to share; the link gives the last one that fit." | **Copy link** | same |
| Shared, `orphaned` | not reachable from the export screen (the item is gone) | — | — |
| Scope missing | "Connect Dropbox again to allow Campfire to make links." | **Connect again** | — |
| Too large to share now | "Too large to share as a link (over 24 MB)." | — | — |
| Dropbox refuses public links | "Your Dropbox account doesn't allow public links." (the uploaded file is deleted again) | — | — |
| Offline or unreachable | "Couldn't reach Dropbox. Check the connection and try again." | **Try again** | — |
| Dropbox full | "Your Dropbox is full." | — | — |

- **Share** in the app bar shares the link as text on Android and iOS (`FilePicker.shareText`, §6.4).
- Ctrl / Cmd + S saves the QR code where there is one.
- **Stop sharing** asks first: "Stop sharing "%1$s"? The link stops working for everyone. Copies people already
  imported stay theirs."

### 6.3 Creating a share (`CreateShareLinkUseCase`)

1. Re-read the registry (into the mirror) and match by `fileName`. If this item is already shared, show that link: two
   devices sharing one item get one link.
2. Build the zip (§5.2). Over the limit is the "too large" state.
3. Generate a `shareId`, then call `uploadSharedFile(shareId, bytes, null)`.
4. Call `createSharedLink(shareId)`. On `NotPublic` or `Refused`, delete the file and report.
5. Add the registry entry (an operation, §4.2) and update the mirror.

A failure after step 3 deletes the uploaded file, best effort. A file left behind is unreferenced and harmless, and
**Stop sharing everything** in Settings removes unreferenced `/shared/` files too (§7.1).

### 6.4 Connecting from here, and coming back

- `CampfireViewModel.connectSyncProvider` is the same call Settings makes. Android, iOS, and the desktop come back to
  the same screen, since the export dialog stays open.
- **Android after process death**: `consumePendingRedirect` restores the connection, but the export dialog is not
  restored (dialogs are not in the back stack), so the user would land on the songs list, connected.
  - Add a `SyncConsentOrigin.EXPORT` that carries the dialog's target (`Song(fileName)` / `Setlist(fileName)`),
    stored with the pending authorization's origin.
  - `showSyncConsentAnswer` then reopens the export dialog on Link.
- **Web**: the page leaves for Dropbox and comes back to bare `/app/`.
  - `SyncConsentOrigin` already lives in session storage (`SyncConsentOrigin.wasmJs.kt`). Give it the same `EXPORT`
    value with the target.
  - `Navigator.showSyncConsentAnswer` (`ui/sync/SyncConsent.kt:75`) reopens the export dialog on Link, under the same
    "the stack is still the start" condition it uses for Settings.
- **The first sync starts right away** (`ConnectSyncProviderUseCaseImpl` calls `synchronizeLibrary`). **Create link**
  does not wait for it.
- **Connecting again for the scope** while already connected: `connect()` today expects a disconnected or failed
  state.
  - Allow it from `Connected` for this purpose. When the account is the same, keep the index, as
    `completePendingAuthorization` already does.
  - A different account resets the index (existing behaviour), and the mirror and journal are cleared with it.
- **Clipboard and text sharing**:
  - `ui/platform/Clipboard.kt`: `expect fun plainTextClipEntry`, through `LocalClipboard.current.setClipEntry`. No
    "copied" message on Android 13+, which confirms by itself.
  - `FilePicker.shareText(text, subject)`: `ACTION_SEND` with `text/plain` on Android; `UIActivityViewController`
    with an `NSURL` on iOS.

### 6.5 The QR code (`ui/share/`, pure, tested)

- **`QrCode.encode(text, minimumLevel = M): QrCode?`**: byte mode, versions 1–40. It picks the smallest version that
  fits, then the highest error correction level that still fits.
  - It covers Reed–Solomon over GF(256), interleaving, the function patterns, the format and version BCH codes, all
    eight masks, and the penalty rules.
  - It is written from ISO/IEC 18004, without a library.
- **`qrCodePng(code, moduleSize = 8)`**: a 1-bit grayscale PNG with a quiet zone of 4 modules.
  - The `IDAT` is zlib from the existing `PrintDeflater` (`ui/print/`). Its Adler-32 and a CRC-32 for the chunks live
    beside it.
- **Tests** (`desktopTest`):
  - decode what the encoder draws with ZXing (`com.google.zxing:core`, in `desktopTest` only, nothing shipped) at
    every version and level, with every mask forced;
  - read the PNG back with `javax.imageio` and decode it again.
- **Save QR code**: an `ExportedFile` (`image/png`, `ExportedFile.PNG_MIME_TYPE`) through `save()`, named by
  `qrCodeFileName(source)` beside `pdfFileName`.
  - iOS `Info.plist` gains `NSPhotoLibraryAddUsageDescription`: without it, the share sheet's **Save Image** ends the
    process.

## 7. Settings and the rest of the app

### 7.1 Settings → Library → Shared links

- **The row**: in `LibrarySections.kt`, "Shared links" with the count, shown while connected and the mirror is not
  empty.
- **The sheet**: the row opens a bottom sheet (`DialogType.SharedLinks`, `CampfireBottomSheet`). Settings has no
  sub-screens, so a sheet is the pattern.
- **Refreshing**: opening it refreshes the mirror from the registry (one download). The list shows the mirror at once
  and updates when the refresh lands, with no animation over arriving data (root UI rules).
- **Each row** shows the title, a song or setlist icon, and "Shared on 16 October".
  - An `orphaned` row shows "No longer in your library"; a `tooLarge` row shows "Too large to update".
  - Its three-dot menu is a popup, never a sheet. It offers **Copy link** and **Show QR code** (which opens the
    export screen on Link for that item), neither for `orphaned` rows, and **Stop sharing** with §6.2's
    confirmation.
- **The sheet header** has **Stop sharing everything**, with a confirmation naming the count. It also deletes
  unreferenced `/shared/` files.
- **Offline**: the list shows the mirror, and **Stop sharing** says it needs a connection.

### 7.2 Disconnect and account changes

- When the mirror is not empty, the disconnect confirmation (`settings_sync_disconnect_confirmation`) gains: "Shared
  links keep working. Connect the same account again to manage them."
- Disconnecting clears the mirror and the journal. The registry and the files stay in Dropbox.
- Connecting a different account shows that account's shares.

### 7.3 Delete confirmations

When the item is in the mirror, the confirmation gains a line:
- **Song**: "…The file is removed from the library and from every setlist, and its shared link stops working."
- **Setlist**: "…The songs stay in your library. Its shared link stops working."
- **Delete library**: "…Every shared link stops working."

A song that is only inside a shared setlist needs no extra line: the setlist's copy simply loses it.

### 7.4 Root `CLAUDE.md`

- **Privacy: what reaches the network** gains **Shared links**:
  - sharing uploads the item to the user's own Dropbox as a public link, and sync runs keep it current;
  - opening a link downloads that file from Dropbox, with no account and nothing kept beyond the import.
- **Product rules**:
  - "never a link followed" becomes "never a link followed, except a Campfire share link";
  - a new line points at `ui/screens/export` and `data/sync/implementation/shared`.
- **Library layout** gains the mirror and the journal.
- **Feature invariants**, Sync, gains: "shared copies follow the library; a share stops only by an explicit action".

## 8. Opening a link (common code)

### 8.1 Recognising one

- **`data/model/.../share/ShareLinks.kt`**: `parse(address: String): SharedLink?`.
  - It accepts `https://campfire-songbook.com/s/#<payload>`, `/app/#<payload>` (the web app), and
    `campfire://s/<payload>`, plus a bare `<payload>` for the web shell.
  - The host must be exactly `campfire-songbook.com`, never a look-alike such as `campfire-songbook.com.example`.
  - `SharedLink(version, kind, shareId, fileId, rlkey, title)` has `downloadUrl` and `landingUrl`.
  - **Tested**: every rejection (another host, `http`, a look-alike host, a wrong version, a bad character in any
    part, an over-long part, `campfire://oauth?code=…`, an empty payload), and a title with every kind of character.
- **Text that is a share link** is imported as a link, instead of becoming `Message.SharedLinkNotImported` or
  `paste_only_links`. That covers text pasted (`PastedSongController.newSongFromClipboard`), dropped, or shared to
  Campfire (Android `SEND`, the iOS share extension's text). A text holding a share link *and* other words is treated
  as text, as today.

### 8.2 The import

- **`ImportSource.SharedLink(link: SharedLink)`**, beside `Address`:
  - its `name` is the title from the payload, never the host;
  - it downloads through the existing `DownloadImportFileUseCase(link.downloadUrl)`, so the DOWNLOADING phase,
    Cancel, and Try again (which keeps the bytes) are all the import's own;
  - `importController.importSharedLink(link)` calls `enqueueImport(..., shouldOpenSong = true)`.
- **The review subtitle**: `plan.sourceNames` would show `<shareId>.zip`. For a `SharedLink` source, show the
  setlist's title from the zip's setlist document, or the song's title, never a file name.
- **Updating on purpose**: opening a link again is how a recipient updates, and Keep both would litter `_2` copies.
  So a shared link's import defaults the conflict group choice (`ImportChoices`) to **Replace** instead.
  - Replace still goes through `DialogType.ConfirmImportReplace`.
  - The review shows a line above the conflicts: "This replaces your copies of the songs and setlist with the shared
    version. Choose Keep both to keep your own changes."
  - Untouched files are disregarded as duplicates, as today.
- **Failures** use shared-link wording instead of the address wording:
  - `Missing`: "This link no longer works. The person who shared it may have stopped sharing it."
  - `Unreachable`: "Couldn't download "%1$s". Check the internet connection and try again."
  - `TooLarge`: today's wording.
  - Unreadable: §8.3.
- **Waiting needs nothing new**:
  - The queue waits for the demo library (`demoLibraryDecision`).
  - The download runs behind onboarding.
  - The import screen, and opening the song, wait for onboarding, dialogs, and unsaved editor text
    (`ImportReportState.awaitNothingInTheWay`).
  - While an import waits, onboarding shows **Next** instead of **Start playing** (`isImportWaiting`), as it already
    does for any `Loading` report.

### 8.3 What the download must be

- **A zip** (`LibraryFiles.isZipArchive` on the bytes) whose contents the import reads.
- **Anything else is "This link no longer works"**: Dropbox's HTML for a banned link, a login page for a team-only
  link, or an empty file. It is never an "unreadable file" row named `<shareId>.zip`.
- **The archive is untrusted input**, like any dropped file. It goes through `ArchiveLocalSource.unpack` with every
  limit it already has: depth 3, the shared inflated budget, and hidden entries dropped.

### 8.4 Messages and strings

- Every string goes in `values` and `values-hu`.
- Counted ones are `<plurals>`.
- Titles from links are read with `textResource`, never as markup.

## 9. The platforms

### 9.1 Android (`app/android`)

- **Manifest**: two new filters on `.CampfireIntentActivity`, beside the `campfire://oauth` filter (which stays as it
  is):
  - an `autoVerify` App Link: `https`, host `campfire-songbook.com`, `pathPrefix="/s/"`. Only `/s/`, never `/app/`;
  - the scheme: `campfire`, host `s`, no `autoVerify`.
- **`CampfireMainActivity.handle`** (lines 125-132) today sends every non-OAuth `VIEW` to the file import.
  - Check `ShareLinks.parse` before `importFrom`. The OAuth check stays first.
  - A match goes to a new `pendingShareLinks` channel in `AndroidFileImport.kt`, then through `CampfireAndroidApp` to
    `CampfireApp`'s new `shareLinks: Flow<SharedLink>`.
- **Recreated activity and Recents**: `handle` is skipped then (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`), as for files,
  so reopening the app never imports a link twice.
- **The landing page's Android button** (§10) is
  `intent://s/<payload>#Intent;scheme=campfire;package=com.pandulapeter.campfire;S.browser_fallback_url=<Play URL>;end`.
  - The Play URL is
    `https://play.google.com/store/apps/details?id=com.pandulapeter.campfire&referrer=<encoded "campfire_link=<payload>">`.
  - With the app installed, Chrome opens it; without, Chrome opens Play.
- **The install referrer**: `com.android.installreferrer:installreferrer`.
  - **Credit it**: an entry in `OpenSourceComponents.kt`, its license under `androidMain/composeResources/files/licenses`,
    and a README row.
  - **Read it once**, on the first launch only (`IsFirstRunUseCase`), off the main thread, through
    `InstallReferrerClient`.
  - Play keeps answering the same referrer for 90 days, so record `UserPreferences.isInstallReferrerHandled = true`
    whatever the answer, and never ask again.
  - **Accept only** `campfire_link=<payload>` that `ShareLinks.parse` reads. Ignore any other referrer, such as a
    campaign or Play's `utm_source=google-play`.
  - On `SERVICE_UNAVAILABLE`, `FEATURE_NOT_SUPPORTED`, or no Play Store app, ignore it and record it as handled.
  - The answer goes into the same `pendingShareLinks`, so the import queues behind the demo library and onboarding like
    any link.
  - The privacy section already names Play over IPC; extend that line to the install referrer.
- **Debug builds**: the `.debug` package is not in `assetlinks.json`, because the debug keystore is public. Test with
  `adb shell am start -a android.intent.action.VIEW -d '<link>' com.pandulapeter.campfire.debug`.

### 9.2 iOS (`app/ios`)

- **Universal Links**: `iosApp.entitlements` gains `com.apple.developer.associated-domains` set to
  `applinks:campfire-songbook.com`. The App ID gets the capability by hand (§13).
- **Delivery**: `iOSApp.swift` has only `.onOpenURL`. A SwiftUI lifecycle app gets Universal Links there too. If step
  0 shows otherwise, add `.onContinueUserActivity(NSUserActivityTypeBrowsingWeb)` instead. **Never both**: the link
  would import twice.
- **`IosFileImport.openUrl`** (lines 58-67) today treats every non-OAuth URL as a file, so `campfire://s/…` would be
  reported as skipped.
  - After `isSyncRedirect`, check `ShareLinks.parse`.
  - A match goes to a `pendingShareLinks` channel, then through `CampfireViewController` and `CampfireIosApp` to
    `CampfireApp`.
- **The scheme**: `campfire` is already declared (`Info.plist:77-89`) for the OAuth callback, and it covers
  `campfire://s`.
- **The share extension**: a shared text or URL that is a share link goes to the inbox as text, as today, and the app
  side (§8.1) imports it as a link. The extension never fetches it.
- **After an App Store install (the clipboard)**:
  1. The landing page's App Store button copies the `https` link (`navigator.clipboard.writeText`, inside the tap),
     then opens the App Store. The page says: "We copied the link, so Campfire can open it once it's installed."
  2. On the **first launch only**, the app checks `detectPatterns(for: [.probableWebURL])`, which shows no prompt
     (step 0).
  3. If it matches, the onboarding's last page shows a card: "Did someone send you a song or setlist? **Open it**".
  4. Tapping it reads the pasteboard (one system "Allow Paste" prompt), and `ShareLinks.parse` decides. A match is
     imported; anything else says "That isn't a Campfire link" and nothing more.
  - **Never read the pasteboard without the tap.** The common `ClipboardText` rule (`ClipboardText.kt:31`) stays.
  - Record the check as done (`UserPreferences`) whatever happens, so it never shows again.
- **Smart App Banner** on the landing page (`apple-itunes-app`, `app-id=6815160850`, `app-argument=<link>`):
  - Safari shows it when the Universal Link did not open the app.
  - `app-argument` reaches `.onOpenURL` only for an installed app, so it is not the install path.

### 9.3 Desktop (`app/desktop`)

The `https` link always opens the browser, and the landing page's **Open in Campfire** opens
`campfire://s/<payload>`.

- **macOS** (the `.dmg` and the Mac App Store build):
  - `macos/url-types.plist`: a `CFBundleURLTypes` entry for `campfire` (role `Viewer`), joined into
    `infoPlist.extraKeysRawXml` with the other fragments (`build.gradle.kts:166-168`).
  - `OpenedFiles.listenForSystemRequests` also sets `Desktop.setOpenURIHandler` where `APP_OPEN_URI` is supported:
    registered with the open-file handler, after the single-instance check, and never removed.
  - macOS only: asking `Desktop` starts AWT, which must not happen on Linux before the display scale is set.
- **Windows** (the Microsoft Store's `.msix`; the `.msi` is published by nothing):
  - `AppxManifest.xml` gains
    `<uap:Extension Category="windows.protocol"><uap:Protocol Name="campfire" /></uap:Extension>` beside the file
    type association.
  - A full trust app gets the address as its argument (step 0, on a Store-signed build or a sideloaded test package).
- **Linux** (the `.deb`):
  - The `.desktop` entry gains `MimeType=x-scheme-handler/campfire;`, and its `Exec` gains `%u` if jpackage wrote it
    without one.
  - This is done by the task that adds `StartupWMClass` (`AddStartupWmClassToDeb`), renamed
    `RewriteDebDesktopEntry`, with the helper tested beside `withStartupWmClass` in `PackagingTextTest`.
  - The `postinst` runs `update-desktop-database -q` when present. Without it, the scheme is only known after the next
    login.
- **`main(args)`** (`CampfireDesktopApplication.kt:65`) today makes every argument an absolute path (line 68), which
  would mangle `campfire://s/…`. Split the arguments first:
  - arguments `ShareLinks.parse` reads go to a `SharedLinks` channel beside `OpenedFiles` (the macOS handler sends
    there too), passed to `CampfireDesktopApp` and on as `shareLinks`;
  - the rest stay paths.
- **A running instance**: `claimSingleInstance` must hand links over untouched.
  - Today the caller resolves every argument to a path before the hand-over. Resolve only the path arguments, and let
    the holder split what arrives the same way.
  - The protocol (`"CAMPFIRE 1 <token>"`, one URL-encoded line per argument) needs no change.
  - The window comes forward, as it does for a file.
- `run --args="campfire://s/<payload>"` tests it.

### 9.4 Web (`app/web`, `presentation/src/wasmJsMain`)

- **Take the fragment before anything rewrites the address.** Today the hash is lost on the first `replace` or
  `pushHistoryEntry` (`BrowserHistory.kt:335-357`), or by `clearQueryString` after an OAuth return.
  - In `index.html`, add a pure `sharedLinkPayload(hash)` to `<script id="campfire-launch">` (tested in
    `offline.test.cjs`).
  - Before `claimLibrary`, append the payload to a JSON list in session storage (`campfire-shared-links`), and remove
    it from the address with `history.replaceState`. A reload then never imports twice and never loses a link.
  - Where session storage throws, keep the payload in a variable for this page.
- **`ui/platform/ShareLinks.wasmJs.kt`**: `sharedLinks(): Flow<SharedLink>`, taking one entry off the stored list as
  it is handed to the view model. `CampfireWebApp` passes it as `shareLinks`.
- **A second tab**: `showAlreadyOpen` posts the stored payloads on a `BroadcastChannel("campfire-shared-links")`.
  - The tab holding the library adds them to its own list, answers, and imports them.
  - The second tab then says "Sent to Campfire in your other tab." and drops them.
  - With no answer within 2 seconds, the payload stays for Retry.
- **CORS (step 0, item 5)**: if the redirect chain is not readable from a page, the web app cannot download a link.
  Then:
  - the web app reports "This link can't be opened in the browser version. Open it in the Campfire app, or download
    the file and import it." with the file's direct download;
  - the landing page leads with the download on the web, and does not offer the web app as the way in;
  - do not route downloads through the user's own Dropbox token: a recipient usually has none.
- **No new route**: the payload rides on `/app/` and is removed before the first history write. `service-worker.js`
  and `404.html` already keep the hash.
- **Offline**: the app starts from the kept build, and the download fails with the Unreachable wording. The payload
  stays in session storage until the import screen has it, and Try again works from there.
- The web registers no scheme: `registerProtocolHandler` only takes `web+` schemes.

## 10. The website (`../CampfireWebsite`, GitHub Pages, `.nojekyll` already there)

- **`.well-known/assetlinks.json`**: `delegate_permission/common.handle_all_urls` for `com.pandulapeter.campfire`,
  with the SHA-256 of Play's app signing certificate (Play Console → App signing).
- **`.well-known/apple-app-site-association`** (no extension):
  `{"applinks": {"details": [{"appIDs": ["N45A6ZHDGY.com.pandulapeter.campfire"], "components": [{"/": "/s/*"}]}]}}`.
- **`s/index.html`, the landing page**:
  - `noindex`; Open Graph says "A song or setlist shared from Campfire", which is all a link preview sees.
  - **It never fetches anything.** It reads the fragment with the same grammar as the app (a hand-copied regex, with a
    comment naming `ShareLinks.kt`), and shows the title as text, never markup.
  - Its buttons, by platform:

    | Visitor | First button | Then |
    | --- | --- | --- |
    | Android | **Open in Campfire** (`intent://…`, §9.1: the app if installed, otherwise Play with the referrer) | the web app, **Download the file** |
    | iPhone, iPad | **Get Campfire** (copies the link, then the App Store, §9.2) | **Open in Campfire** (`campfire://s/…`), "Already installed it? Open it", the web app, **Download the file** |
    | Mac, Windows, Linux | **Open in Campfire** (`campfire://s/…`) | "Don't have it yet?" with the store or download for the platform (the site's existing `native` map), the web app, **Download the file** |
    | In-app browsers (Instagram, Messenger, …) | as their platform | plus "Open this page in your browser if the buttons don't work" |

  - Under **Open in Campfire**: "Nothing happened? Campfire isn't installed here." No timers, no automatic redirects,
    and no attempts to detect the app.
  - **Download the file** is the Dropbox `dl=1` address, labelled only as the file.
  - English and Hungarian, by `navigator.language`.
  - An unreadable or missing fragment shows "This link is incomplete. Ask for it again." with the normal downloads.
- **The privacy policy**:
  - sharing a link puts the item in the sharer's own Dropbox as a public file, kept current by sync until they stop
    sharing;
  - opening a link downloads that file from Dropbox;
  - the landing page reads the link in the browser and sends nothing;
  - on Android, Play hands the link to the app after an install.
- **The support page**: "How do I send a setlist to my band?"
- **The website's README**: its file list.

## 11. Corner cases — read these first

### 11.1 Sharing

| Case | What happens |
| --- | --- |
| Not connected | The export screen offers Connect and says the library will sync (§6.2). |
| Connected, but the token predates the sharing scopes | `missing_scope` → "Connect Dropbox again…"; **never** `ConnectionFailed` (§4.1). |
| Connecting from export on Android, process killed while in the browser | The connection is restored on return, and `SyncConsentOrigin.EXPORT` reopens the export dialog on Link (§6.4). |
| Connecting from export on the web | The page comes back to `/app/`, and the `EXPORT` origin reopens the dialog (§6.4). The OAuth return clears the query **and the hash**, so a share payload must already be in session storage (§9.4). |
| Connecting from export with a different account than before | The index is reset (existing), the mirror and journal are cleared, and that account's shares appear. |
| The first sync is still running after connecting | **Create link** works at once: it does not take the run lock (§4.2). |
| The same item shared from two devices at once | Both read the registry first. A second **Create link** that loses the race finds the entry on its add-operation retry, deletes its own uploaded file, and shows the existing link. Test it. |
| A setlist over 24 MB | Not shareable now (§6.2). One that grows over later stays at its last version, as `tooLarge` (§5.1). |
| A team account that forbids public links | `NotPublic`: the file is deleted, and the screen says the account doesn't allow public links. |
| Dropbox full | A message; nothing is left behind. |
| Offline | "Couldn't reach Dropbox", with **Try again**. |
| A failure between the upload and the registry | The uploaded file is deleted, best effort; leftovers are removed by **Stop sharing everything** (§7.1). |
| Read only mode | Link is offered: it writes nothing to the library. |
| Sync not configured in the build (no app key) | The Link format is hidden. |
| A song shared alone and also inside a shared setlist | Two independent shares; both follow the song. |

### 11.2 Keeping copies current

| Case | What happens |
| --- | --- |
| Nothing shared | The step costs nothing: no `/shared/` files and no journal means it is skipped (§5.1). |
| Two devices rebuild the same copy | Deterministic bytes (§5.2) give the same hash, so neither uploads. If both do, the second gets `Conflict` and drops it. |
| A run ends in `DeletionsNeedConfirmation`, or fails | No rebuild; the next completed run does it. |
| **Stop syncing** pressed mid-run | It cancels the run, as today, so the step is skipped. |
| Sync stays disconnected | Links keep serving the last version, and nothing updates them. The disconnect confirmation says so (§7.2). |
| Per-device transposition, capo, and tempo | Never in the copy. A setlist entry's values are. |
| A song edited only in its cover, tags, or links | These are directives in the file, so the copy updates. |
| The registry hand-edited into invalid JSON | Treated as empty for this run, with a logged error. A run **never** overwrites it; only a sharing action rewrites it, and that re-reads first. |
| A newer registry `version` | This version leaves it alone, as it does `preferences.json`, and skips the step. |
| Stop sharing while a run uploads the same copy | The run's `update:<rev>` fails because the file is gone, and the run drops it. The entry is already removed. |

### 11.3 Renames, deletions, and things done outside Campfire

| Case | What happens |
| --- | --- |
| A shared setlist's title edited (renaming its file) | A `rename` in the journal; the link is unchanged. The payload's title stays the old one (display only); the import shows the real title (§8.2). |
| **Update file name** on a shared song | The same as above. |
| A song inside a shared setlist renamed or deleted | The setlist document changes (`followSongReferences`), so the copy is rebuilt. |
| A shared item deleted in Campfire | A `stop` in the journal: the file is deleted and the entry removed. The confirmation said so (§7.3). |
| **Delete library** | Every shared item gets a `stop`. |
| A rename made offline and synced later | The journal survives until a run applies it, before items are looked up (§5.4). |
| An item deleted outside Campfire (a file manager, Dropbox on the web, another app) | `orphaned`: the link keeps serving the last version, and Settings offers **Stop sharing**. **Never stopped automatically.** |
| The item reappears under its name | Back to `ok` on the next run. |
| `/shared/<id>.zip` deleted by hand in Dropbox | The link is dead. The run finds no file and marks the entry `orphaned`; **Stop sharing** in Settings removes the entry. |
| `/shared/` files edited by hand | The next run's hash differs, so the library's version overwrites them. |
| App data cleared, or the app reinstalled | The mirror and journal are gone. On reconnecting, the registry is read again. Pending renames are lost, so those entries may become orphaned (Settings shows them). |

### 11.4 Opening

| Case | What happens |
| --- | --- |
| App installed (Android, iOS) | The App Link or Universal Link opens the app on the import screen, downloading. |
| App Link not verified, or a debug build | The browser shows the landing page; **Open in Campfire** opens the app. |
| An in-app browser that keeps `https` links | The landing page, with its buttons and the line about opening it in the browser. |
| Desktop | The browser shows the landing page; **Open in Campfire** opens a running or new instance. |
| Web | The landing page leads to `/app/#…`; the payload is stored and the address cleaned (§9.4). |
| The same link opened twice | Everything is identical: "already in your library". |
| The sharer opens their own link | Identical, so disregarded. |
| The link opened again after the sharer changed things | Changed songs and the setlist are conflicts defaulting to **Replace** (§8.2); unchanged ones are disregarded. |
| The recipient edited their copy, then opens the link again | Their edits are conflicts; the review line explains that Keep both keeps them. |
| The sharer renamed the setlist's title since | The setlist arrives under a new name, so it is imported as **new**, and the old copy stays. Expected, and shown in the review. |
| The link stopped, or its file deleted | "This link no longer works…". |
| Dropbox answers with HTML (banned, team-only, a login page) | The zip check makes it "no longer works" (§8.3). |
| A link from a newer format version | "made by a newer version of Campfire". |
| A malformed or crafted payload | Unreadable; the download address is always built by the app (§3). |
| A zip bomb, or deep nesting, in the shared file | The archive limits stop it (§8.3). |
| Offline | The Unreachable wording, with Try again. |
| The process killed during the download | The link is not persisted; the existing "import interrupted" message appears, and the user opens the link again. |
| Opened during onboarding, a dialog, or with unsaved editor text | The import screen waits for them (existing). |
| Opened while another import, or a library deletion, runs | Queued behind it (existing). |
| Opened in read only mode | Imported, as "Open with" is (existing rule). |
| Setlists switched off in Features | The setlist is still written, and the tab stays hidden, as with a zip. |
| A shared song opened | A review with one row; after Import, the song opens (unless an editor holds unsaved text). |
| Cover addresses inside shared songs | Downloaded by the recipient if their Cover art switch is on, as for any import. |
| A second web tab | Handed to the tab that owns the library (§9.4). |

### 11.5 Install, then import

| Case | What happens |
| --- | --- |
| Android, installed from Play through the button | The referrer is read once on the first launch, then the demo library, onboarding, and the import screen. |
| Android, installed from Play some other way | No referrer, so nothing happens; the landing page still has **Open in Campfire**. |
| Android, a later launch | The referrer is never read again: first launch only, recorded as handled (§9.1). |
| Android, a campaign's referrer | Ignored. |
| iOS, link copied, installed from the App Store | On the first launch, `detectPatterns` matches, the onboarding's last page offers **Open it**, one paste prompt appears, and the import follows. |
| iOS, something else copied in between | Not a Campfire link: "That isn't a Campfire link". |
| iOS, the user declines the paste prompt | Nothing happens; the landing page's "Already installed it? Open it" remains. |
| Desktop, installed from a store | The landing page's tab is still open; **Open in Campfire**. |
| Onboarding skipped | The import screen follows at once: it waits only while onboarding is on the stack. |
| The demo library | Still planted on a fresh install; the shared item arrives beside it. |

### 11.6 Privacy and security

| Case | What happens |
| --- | --- |
| Link previews in messengers | They see only the landing page's Open Graph text; the fragment never reaches a server. |
| Anyone with the link | Can download the file: that is what a public link is. The export screen says "Anyone with the link". |
| Another app registering `campfire://` (Android, macOS, Linux) | It may receive a button tap's address: the same public link, nothing more. |
| A link making the app fetch another host | Impossible: the address is built from validated parts (§3). |
| Backups | The mirror and journal are excluded; `library/` and `preferences.json` are backed up as today. |

## 12. Not in this plan

- Live updates of recipients' copies, read-only subscribed setlists, and notifications of changes (decision 3).
- Links for a selection of several songs, or for the whole library.
- Expiring or password-protected links (Dropbox's paid features), and view counts.
- Sync providers other than Dropbox.
- A QR scanner in the app: every phone's camera reads them.
- Registering the protocol in the `.msi`, which is published by nothing.
- Skipping the demo library when the install came from a link.

## 13. Outside the repository

- **Dropbox App Console** ("Campfire Sync"): tick `sharing.write` and `sharing.read`.
  - The authorization URL sends no `scope=`, so new connections get them at once. Existing tokens do not (§4.1).
  - Update the scope list in `data/source/remote/implementation/CLAUDE.md`.
- **Apple Developer**: the Associated Domains capability on `com.pandulapeter.campfire`. That invalidates the
  provisioning profiles:
  - xcodebuild makes the iOS one again (`-allowProvisioningUpdates`);
  - `app_store_signing.py` makes the Mac App Store one when none is active.
- **Play Console**: nothing to fill in. Deep links shows the domain verified after the release.
- **Microsoft Store**: the protocol is declared in the manifest; nothing to do in Partner Center.
- **The website** (§10): it must be deployed before the first app release that relies on it, since Apple caches the
  association file for a day or more.
- **Store privacy answers** (the App Store's label, Play's Data safety): content goes to the user's own Dropbox at
  their request, not to the developer. Review the answers at release time.
- **What's new**: by the prepare-release skill.

## 14. Order of work

Each step builds and tests green on its own, one commit each.

1. **Step 0** (§2). Write the findings into §2, and adjust §3, §4, and §9 where they differ.
2. **Deterministic `pack`** (§5.2), with its test.
3. **`ShareLinks`** (§8.1), with its tests.
4. **Dropbox** (§4.1): the sharing calls, the shared files in the listing, and `missing_scope`, with
   `DropboxTransportTest` and URL-parsing tests.
5. **The registry, mirror, and journal** (§4.2), and `CreateShareLinkUseCase`, `StopSharingUseCase`, and
   `GetSharedLinksUseCase` in `:domain`. Use fakes of `SyncFolder` for the races in §11.1.
6. **The run's shared-copy step** (§5), with cases in `TwoDeviceSyncTest`:
   - a rebuild;
   - a rename on one device, seen by the other;
   - a deletion;
   - an orphan, and its return;
   - two devices rebuilding at once.
7. **The rename and deletion journals** in the use cases, and the confirmation strings (§5.4, §7.3).
8. **The import side** (§8): `ImportSource.SharedLink`, the Replace default, the zip check, the wording, and links in
   pasted, dropped, and shared text.
9. **The QR encoder and PNG** (§6.5), with ZXing in `desktopTest`.
10. **The export screen's Link format**, connecting from it, and `SyncConsentOrigin.EXPORT` (§6).
11. **Settings' Shared links sheet** and the disconnect wording (§7).
12. **The website** (§10).
13. **Android** (§9.1): the filters, `handle`, and the install referrer.
14. **iOS** (§9.2): associated domains, `openUrl`, and the first-launch clipboard card.
15. **Desktop** (§9.3): the arguments, the macOS handler, and the three registrations.
16. **Web** (§9.4): the fragment, the second tab, and the CORS outcome, with Node tests.
17. **Docs**:
    - the root `CLAUDE.md` (§7.4);
    - the `CLAUDE.md` files of `data/sync/implementation` (and `shared/`), `data/source/remote/implementation`,
      `presentation` (Export, Settings, importing), each `app/*` touched, and `domain/implementation`;
    - the README's feature list and Third-party table.

## 15. What only a device can tell

Name these once in the hand-off, not as a standing list:
- the Universal Link and the clipboard card on a real iPhone (TestFlight);
- the install referrer, through Play's internal testing track;
- the `.msix` protocol activation on Windows.

Everything else is covered by tests, step 0, or the offscreen UI tests.
