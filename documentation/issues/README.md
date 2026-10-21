<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# The 42nd review: shared song and setlist links

**Reviewed commit:** `a869c9ff7` on `master` (the review's own fixes start there, up to `9df31bee4`, not pushed).
**Angle:** the shared song and setlist links feature end to end: the link grammar, the Dropbox registry, mirror, and
journal, sync keeping the copies current, rename and delete, opening links on all four platforms, privacy, and a bad
network. **Budget:** standard: four Opus area reviewers (registry and lifecycle; Dropbox calls, copies, grammar, and
opening; presentation; platform shells), a Sonnet docs check (no false claims in the changed `CLAUDE.md` lines), Opus
plan writers, an Opus regression read, and two Opus challengers.

## Headlines

- A share whose copy is gone becomes a dead link that Create link keeps handing out and Settings calls "No longer in
  your library" (01).
- A rename or deletion on a device with a stale mirror is not journaled, so the link stops following the song, or a
  deleted song's copy stays public (02); the share lookup also ignores the pending journal (03).
- A reconnect does not stop a running sync, an account switch races it, and a failed token exchange loses working
  credentials (06).
- The iOS share extension refuses a forwarded Campfire link (07).
- The QR code cannot be shown whole in a short window (08).

## Fixed during the review

Twelve commits, `a869c9ff7..9df31bee4` (the last two came from the regression read):

- `c445fbb8f` Keep the QR code's modules on whole pixels so dark ones show no seams.
- `da419451b` Keep the QR code on the shared link while it fades out after Stop sharing.
- `149796701` Fade a shared link's row in Settings while it stops sharing instead of snapping.
- `470a5ab08` Match a shared song or setlist to its file under any case of its name, as the shared marks do.
- `4408a9b40` Say on the Sharing tab when its list of links is the one this device last saw.
- `84dd8a49b` Ask to connect Dropbox again when stopping a share fails for want of the permission to make links.
- `94b7b8fe2` Keep the Stop sharing confirmations open when the system stops the app behind them.
- `7b284ec45` Document the install referrer permission merged into the Android manifest.
- `3426655da` Stop a share whose hand-edited id names no copy instead of failing at it on every sync.
- `f7ff3d945` Keep line breaks and text direction overrides out of a shared link's title.
- `bd96608c1` Ask a Stop sharing question restored after the app was stopped only while there is still a link to stop.
- `9df31bee4` Rewrap the shared links notes and the link export's KDoc to the files' width.

## Index

The challenge ran on every plan. Its outcome is each plan's **Challenged:** line.

| No. | Plan | Severity | Lane | Challenge |
| --- | --- | --- | --- | --- |
| 01 | [Replace a share whose copy is gone](01-replace-a-share-whose-copy-is-gone-instead-of-handing-out-its-dead-link.md) | medium | R | amended: the edit and the decision are one `NonCancellable` block; leftovers stay with Stop sharing everything; a late write named as an accepted gap |
| 02 | [Journal every rename and deletion](02-journal-every-rename-and-deletion-so-a-stale-mirror-cannot-lose-them.md) | medium | R | amended: `ConnectionFailed` counts only while credentials exist; the extra read only for `StopItem` / `StopAll`; a late by-name rename named |
| 03 | [Look shares up through the journal](03-look-shares-up-through-the-journal-and-apply-it-under-one-lock.md) | medium | R | amended: `share()` applies a non-empty journal first and fails where entries stay; the applier-versus-run test launches the run's step |
| 04 | [Keep a slow registry read from rolling the mirror back](04-keep-a-slow-registry-read-from-rolling-the-mirror-back.md) | low | R | amended: an edit's own save does not refill a cleared mirror; the first test needs the fake to snapshot first; two concurrent reads named |
| 06 | [Stop the running sync before reconnecting](06-stop-the-running-sync-before-reconnecting-and-reset-for-another-account-under-the-run-lock.md) | medium | K | amended: fixed order in `connect`; the back-out schedules a run; a test for it; the applier's window named |
| 07 | [Let the iOS share extension pass a Campfire link](07-let-the-ios-share-extension-pass-a-campfire-link-to-the-app.md) | medium | I | amended: the Swift check copies `ShareLinks`' regex; a URL attachment is no longer skipped; the test is a pin |
| 08 | [Fit the QR code whole in a short window](08-fit-the-qr-code-whole-in-a-short-window.md) | medium | U | amended: the collapse and end column only in the Shared state; the `LinkButton` overload; the UI test compares clipped bounds with size |
| 09 | [Keep one share creation per item](09-keep-one-share-creation-per-item.md) | medium | U | amended: the controller test uses `backgroundScope`; the state cases are pins; leftover entries named |
| 10 | [Open the export on Link without storing the format](10-open-the-export-on-link-without-storing-the-format.md) | low | U | sound |
| 11 | [Offer the clipboard link after a skipped onboarding](11-offer-the-clipboard-link-after-a-skipped-onboarding.md) | low | U | amended: "the card was shown" is read from the page left; the snackbar is the reader's only offer; one file leaves the list |

## Lanes

| Lane | Area | Plans, in order | Files owned |
| --- | --- | --- | --- |
| R | registry and sync | 01, 02, 03, 04 | `data/sync/implementation/.../shared/` and its tests, `data/model/.../share/ShareState.kt`, `SharedLinksRepository` (`data/repository/api`), `domain/implementation/.../sharing/CreateShareLinkUseCaseImpl.kt` and its test, `domain/implementation/CLAUDE.md`, `TwoDeviceSyncTest.kt`, and 01's presentation additions: `LinkExportState.kt` (`CopyMissing`), `LinkPreview.kt`, `SharedLinkList.kt`, `screens/export/CLAUDE.md`, `LinkExportStateTest.kt`, the strings |
| K | connection | 06 | `data/sync/implementation/.../connection/` and `scheduling/` with their `CLAUDE.md` files, `data/sync/implementation/CLAUDE.md` (Locks), `SyncConnection.kt` (`data/source/remote/api`), `SyncConnectionManagerTest.kt`, plus 06's message: `Message.kt`, `Messages.kt`, `SyncController.kt`, the strings |
| I | iOS | 07 | `app/ios/iosApp/ShareExtension/`, `app/ios/CLAUDE.md`, `PastedSongControllerTest.kt` |
| U | presentation | 08, 09, 10, 11 | `ui/screens/export/` (`LinkPreview`, `ExportScreen`, `ExportScreenDecisions`, `LinkButton`, `LinkExport`, `LinkExportState`, `ExportState`, `ExportController`, `ExportFormats`, `CLAUDE.md`), `ui/share/ShareLinkController.kt`, `ui/dialogs/DialogType.kt`, `SavedDialogRestorer.kt`, `ui/screens/settings/SharingSections.kt` and its `CLAUDE.md`, `ui/CampfireViewModel.kt`, `ui/firstRun/`, `ui/navigation/Navigator.kt`, `ui/messages/`, `ui/screens/onboarding/CLAUDE.md`, the strings, and the tests of these |

Each plan's exact list is its **Files:** line.

**Merge order: R, K, I, U.** R first because the others build on its `CopyMissing` state and journal (08's `CopyMissing`
note, 09's `LinkExportState`); K next, then I (both small and independent); U last because it touches the most shared UI
files. Lane U is cut from the main checkout's `HEAD` after R has merged, not from `START`; R, K, and I start from
`START`.

### Shared files

- `FakeSyncProvider.kt` (`data/sync/implementation` `commonTest`): 04 changes `downloadDocument`'s snapshot timing, 06
  touches other members. Each touches only its own members.
- `presentation/src/commonMain/composeResources/values/strings.xml` and `values-hu/strings.xml`: R (01), K (06's
  message), and U add keys, each in its own commented group. Merge word by word, keeping every key.
- `Message.kt` and `Messages.kt`: K (06) and U (11) each add one entry. Keep both.
- Module `CLAUDE.md` files: append or edit only the paragraphs about the plan's own behaviour.

## Decisions

All answered by the user on 2026-10-21, each the recommended default, and folded into the plans (**Decided** lines):

- 07: no `public.url` in the share extension's activation rule.
- 11: a one-time snackbar with "Open it" after a skipped onboarding; the clipboard is read only on that tap.
- 06: keep the working credentials when a reconnect's token exchange fails, and tell the user the reconnect did not go
  through (a message, English and Hungarian).
- 08: icon-only Copy link in an end column, only while shared in a short side-by-side window.

Defaults taken without asking: 05 dropped; 09 one creation per item, keyed by target; 03's `share()` may wait behind a
run's shared-copy step while this device's journal is not empty.

## Checked and found solid

- QR encoder and PNG writer (ZXing decodes every version, level, and mask).
- Link grammar: the host matched whole, https only, fixed alphabets, the download host fixed.
- Opening a link: always reviewed, the size cap, non-zip and 4xx/5xx answers handled, the zip limits.
- Dropbox: `missing_scope` handled without failing sync, retries idempotent.
- Rebuilt copies keep their link (`update:<rev>`), content decides, undated zips deterministic.
- The registry edit loop is idempotent and conflict-safe; the journal survives replay and process death.
- Disconnect and forget clear the mirror and the journal.
- Backup exclusions (the Android allow-list, iOS `keepOutOfDeviceBackup`).
- Platform routing: Android App Link `autoVerify` only for `/s/`, the `campfire://` filter, `singleTask` `onNewIntent`;
  desktop macOS Apple Events, the MSIX protocol, the `.deb` `x-scheme-handler`; iOS applinks and `onOpenURL`; web
  fragment kept through the redirect, service worker, and 404, and the second-tab hand-off.
- The website's association files, verified live.
- The install referrer, read on the first launch only; the iOS clipboard, read only on a tap.
- Strings complete in both languages.

## Dropped after verification

- **05, "Stop sharing everything deletes only copies the list names"**: the race (another device sharing while the
  same user stops everything) is rare, plan 01 now shows the resulting dead link and offers a new one, and deleting only
  listed copies would leave copies with live links (a share killed midway, a corrupt list overwritten, 01's undecidable
  case) that nothing in the app could remove.
- Not filed: a crafted link to any public Dropbox file only reaches a reviewed import (no other host is reachable);
  `deleteSharedCopy` catching `IllegalArgumentException` cannot swallow a real failure (the transport wraps everything
  else as `SyncNetworkException`).
