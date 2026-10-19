<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 36th review: bad network and sync

Reviewed at `9587e98fa` on `master` (2026-10-19). Angle: flaky, slow, stalled, and captive-portal links during sync,
cover downloads, and lookups, on every platform. Lean: three Opus area reviewers (transport with MockEngine and raw
socket fault-injection probes, sync engine with fake-provider fault injection, UI) and the docs reviewer; a regression
read of the review's own fixes; two writers and three challengers (11 plans amended, 6 sound, 0 dropped; 18 amended).

## Headlines

- **06 (high, data loss):** a download cut short with no declared length (an HTTP/1.0-style proxy) is written as the
  song and indexed as in sync; the user's next edit uploads the truncated text over the whole one.
- **13:** nothing retries a run that failed for the network, so an offline edit stays off the cloud until the next
  change, Sync now, or launch.
- **01, 03:** a dropped connection ends a run at once (only timeouts are retried), while a stalled link on the web holds
  a run for over an hour.
- **04:** any 4xx but 429 on a token renewal (a proxy's 403/407, a 408) disconnects a working account.
- **15:** a flaky link adds one identical conflict copy per failed attempt, and they spread to every device.
- Probes found no path where a partial run makes the index claim files were synced when they were not.

## Fixed during the review

77 commits, `28dbdeb9a..` the restore of `.run/iOS.run.xml` (`Restore the shared iOS run configuration.`, after the
decisions). Product fixes: the desktop browser tab no longer says "Campfire is connected" before
it is (or after a refusal); "Connecting…" replaces "Waiting for the browser…" (shown during the token exchange too);
the sync status line cross-fades when a run ends or its report changes; the required update screen says updating needs
a connection. The rest correct, cut, and move `CLAUDE.md` and skill text (about 20 false claims; network claims checked
against the code), plus four rules restored after the regression read.

- `28dbdeb9a` Stop the desktop browser tab from saying Campfire is connected before it is.
- `ef8b61deb` Correct the root notes on imported formats, single-platform modules, links, and the test commands.
- `38b52672c` Correct the Dropbox upload modes, retries, scopes, and disconnect order in the remote source notes.
- `385fc785c` Name the cover search contract and the document upload's storage full failure in the remote api notes.
- `b003036a1` Drop the package index and test command from the sync engine notes.
- `d4e525d4c` Spell out the web launch's answer window, update note, and stall timeout in the web notes.
- `67584ce62` Say how the web finds a new build in the update notes.
- `72c22cbcb` Keep only the ordering rule for forgetting credentials in the sync notes.
- `e32751c4e` Trim narration and inventories from the settings screen notes.
- `58e67561a` Replace the web route inventory and correct which unknown files a web drop reads.
- `0c6d175cf` Note that the iOS share extension cannot offer OnSong and OpenSongApp files.
- `fd8ac31ee` Say that Android keeps a song's text under an unknown name in the Android notes.
- `f536b5a55` Trim stale asides from the desktop notes.
- `3889b8f60` Name the screenshot tool as a caller of the Koin application in its notes.
- `72785f5d6` Drop a repeated sentence from the Baseline Profile notes.
- `14d21b0a2` Drop two inventory lines from the release pipeline notes.
- `6c5a78fff` Replace the task class inventory in the build logic notes.
- `7350f76bb` Replace the golden case list in the screenshot tool notes.
- `bf1634879` Drop the whoosh level's history from the promo video notes.
- `d209ac17c` Say that a shared page's title names a drafted song in the song use case notes.
- `ff967811a` Say that archive entries under binary names are never peeked at in the local source api notes.
- `d218218e8` Fold the other apps' song readers into one paragraph in the local source notes.
- `eac384b4a` Point the import notes at the archive source's rules and drop what the code says.
- `db5a16ef2` Drop the package index from the model notes.
- `0a97a7d3e` Drop a repeat of the root conventions from the use case notes.
- `1ddefdca4` Drop the test command from the formats notes.
- `fe036bf71` Drop the test command from the ChordPro notes.
- `1f06da884` Move the setlist date rule from the setlists screen notes to the repository notes.
- `3140f694d` Correct which unknown files a desktop drop reads in the desktop shell notes.
- `1aaf86a84` Cut repeats and narration from the presentation ui notes.
- `b43611743` Move the instrument, keyboard, and tab rules into notes of their own packages.
- `08646fb73` Trim narration from the navigation notes.
- `422fd6153` Drop what the platform KDoc already says from the platform notes.
- `550d0b510` Cut palette values and repeats from the theme notes.
- `cfdadae6b` Trim a clause from the chrome notes.
- `94efa39c2` Cut narration from the editor notes and name the shared page title a new song takes.
- `2140abaa2` Regroup the song details notes and cut their narration.
- `5f1e8d9c1` Name both ways into the About the song sheet in the song info notes.
- `f27665275` Say what Try again repeats in the import screen notes.
- `544836d4f` Move the list bar's styling from the screens notes into the app bar notes and split its run-on bullet.
- `56bb148ff` Shorten the card holds in the songs screen notes to their rules.
- `a85536d40` Move the export formats and options from the print notes to the export screen notes and trim both.
- `12b9a47c1` Cut narration from the onboarding notes.
- `42403503d` Trim the pedal stepping notes.
- `428bfe0b7` Cut narration from the song view notes.
- `09e629f0b` Shorten the magazine layout's pricing in the song grid notes.
- `244568546` Shorten the model build rule in the song model notes.
- `6b08563ba` Name the export screen among the delayed loading indicator's users.
- `7f44acc3a` Correct which menu entries never leave the menu in the menu notes.
- `603a1b615` Correct where the song column width is computed in the song card notes.
- `8538da7d8` Correct when the cover search sheet's search is asked in the lookup notes.
- `b1d9f92bb` Correct when the cover search state is cleared in its KDoc.
- `d6f0312de` Correct what serializes the picker's ticks in the picker notes.
- `962eeae10` Point the licenses notes at the third party table and drop a repeat of the root.
- `26b1153cc` Drop a repeat of the root from the form sheet notes.
- `04c53210f` Drop a repeated line from the bottom sheet notes.
- `67c362023` Cut narration and a repeat from the tuner notes.
- `184b7d244` Cut repeats and narration from the metronome notes.
- `bafcfc2e4` Move the chord shape button and the chips' layout out of the chord notes and trim the chord sheet to its rules.
- `b76ff4641` Join a broken paragraph in the list notes.
- `d39f2657c` Correct the platform and Koin rules in the code style skill.
- `4c95a7f0f` Point the review skill at the code style skill's budgets.
- `c3600f99f` Drop a false claim from the commit message skill.
- `1fd558aa3` Correct the update priority example and the empty notes fallback in the release skill.
- `6c4efdce5` Point the promo video skill at the tool's notes for what a change touches.
- `d99ded31a` Name the promo target in the store screenshots skill.
- `d33edf1a7` Bring the web notes back under their word budget.
- `ee35d02df` Bring the remote source notes back under their word budget.
- `457dc1186` Cross-fade the sync status line in Settings when a run starts, fails, or finishes.
- `a4546fb14` Say Connecting rather than Waiting for the browser while sync finishes connecting.
- `1c23b346f` Say on the required update screen that updating needs an internet connection.
- `d895ee807` Keep the sync status line still when a run starts over the same words.
- `75c12a6e5` Quote the new browser page title in the desktop sign-in test.
- `4ea0135ec` Say that Settings is what links only to the website and stores, and rewrap the root notes.
- `0502ca6ab` Say which disconnect steps wait at most ten seconds in the remote source notes.
- `ef73cd15c` Restore four rules the notes trim lost, about the display, chord shapes, credentials, and tempo pages.

## Index

| # | Plan | Severity | Lane | Challenge |
|---|---|---|---|---|
| 01 | Retry a Dropbox call after any transport failure, not only after a timeout | medium | T | amended |
| 02 | Give up rather than wait when Dropbox or MusicBrainz asks for a wait longer than a cap | medium | T | sound |
| 03 | Bound each Dropbox call on the browser by what it carries, not by one ten-minute limit | medium | T | amended |
| 04 | Treat only a refusal by Dropbox's token endpoint as an authorization failure | medium | T | sound |
| 05 | Retry a token renewal that could not reach Dropbox, and bound the credentials lock | low | T | amended |
| 06 | Refuse a downloaded file whose bytes do not match Dropbox's size and content hash | high | T | amended |
| 07 | Decode every Dropbox answer inside the transport, so a foreign answer reads as the network | medium | T | amended |
| 08 | Require the fields a decision rests on, so an empty answer is not an empty folder | medium | T | sound |
| 09 | Treat a cover that arrives shorter than its declared length as unreachable | medium | C | sound |
| 10 | Show a cover search whose thumbnails could not be reached as failed, with Retry | medium | C | sound |
| 11 | Bound each cover download to a minute | medium | C | amended |
| 12 | Let only a cover whose thumbnail has loaded be chosen | low | C | amended |
| 13 | Retry a run that failed for the network, after a capped back-off | medium | S | amended |
| 14 | End the run with its reason when preferences.json fails for the network or a full folder | low | S | sound |
| 15 | Reuse a conflict copy that already holds the incoming version | medium | S | amended |
| 16 | Show Connecting, with a working Cancel, while start up finishes a consent answer | medium | U | amended |
| 17 | Disconnect at once on a slow network, revoking the token in the background | low | U | amended |
| 18 | Let the user past a required update while the device has no connection | medium | A | amended |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|---|---|---|---|
| T | Dropbox transport | 01 → 02 → 03 → 04 → 05 → 06 → 07 → 08 | `data/source/remote/implementation/.../dropbox/`, `network/HttpClientConfiguration.kt`, `musicBrainz/MusicBrainzRemoteSource.kt`, `Backoff.kt`, `SyncProvider`/`SyncFolder` KDoc in `:data:source:remote:api`, their tests |
| S | Sync engine and scheduling | 13 → 14 → 15 | `data/sync/implementation/.../scheduling/`, `run/ConflictResolver.kt`, `LibraryFileLocalSource` (+ `FileNames.kt`), `FakeSyncProvider.kt` (`onList`), their tests and `CLAUDE.md` files |
| C | Covers | 09 → 10 → 11 → 12 | `coverArt/`, `importDownload/`, `CoverArtRepositoryImpl`, `CoverArtResult` in `:data:model`, the cover use case, `ui/dialogs/lookup/` cover sheet, `tools/screenshots` `TestFakes.kt` |
| A | Android update gate | 18 | `ui/update/`, its `androidMain` actual, `AndroidManifest.xml`, two strings |
| U | The connection | 16 → 17 | `data/sync/implementation/.../connection/`, `SyncRepository` api/impl, the domain sync use cases, `SyncController`, `tools/screenshots` `Fakes.kt`, and 17's edits to `DropboxSyncProvider.disconnect`, `DropboxTokens`, `FakeSyncProvider.disconnect` |

**Merge order: T, S, C, A, then U.** T and S run first, in parallel. C and A start from `START` as build slots free up. U is
cut from the main checkout's `HEAD` after T and S have merged: 17 builds on T's `DropboxSyncProvider.kt` and
`DropboxTokens.kt` (03, 05–08) and calls S's changed `stopForDisconnect` (13); both S and U change `FakeSyncProvider.kt`.
C and A merge before U because U touches the most shared files.

### Shared files

- `data/source/remote/implementation/CLAUDE.md`: T (retry, timeouts, downloads), C (09, 11: the downloads section), U
  (17: the `disconnect` bullet). Each lane edits only its own sentences; merged by section, every lane's wording kept.
- `data/source/remote/api` KDoc: T (06's integrity promise), U (17's `disconnect` contract).
- `tools/screenshots`: C changes `src/test/.../TestFakes.kt`, U changes `src/main/.../Fakes.kt`; each must compile
  with the other's api change after the merge.
- `strings.xml`: only 18 adds a key (10 reuses an existing failure string).
- Cross-lane facts the plans rely on: 13 retries every `Failure(NETWORK)`, so what T classifies as the network (02's
  cap, 07/08's foreign answers) is retried every 30 minutes; 06 fails only the one file, so a mismatch never ends runs.

## Decisions (all answered 2026-10-19)

- **04 — what counts as a refusal on token renewal:** option B, as recommended. Only a 400 or 401 from the token
  endpoint is a refusal; any other answer (3xx, 403, 407, 408) is the network.
- **13 — the retry schedule:** 1, 2, 5, 15, and 30 minutes, then every 30 minutes while the app is alive, as
  recommended. Only after `Failure(NETWORK)`, reset by any finished or manual run.
- **`.run/iOS.run.xml`:** restored, as recommended. It had been deleted inside an unrelated commit (`041638d1d`).
- **Android required update offline:** allowed, against the recommendation. That became plan 18 (lane A): the
  blocking screen offers Continue offline while there is no validated network.

## Checked and found solid

- Interrupted runs at every step (mid-download, upload landed but its answer lost, delete batch cut halfway, index lost
  in the 2 s kill window) converge on the next run with no loss, no wrong deletion, and no wrong question.
- Every operation is recorded only after it finishes; index writes are atomic on every platform; cancellation is never
  swallowed in the transport, engine, or runner.
- A failed or HTML `list()` ends the run before anything moves; a partial listing is never used.
- A 401 gets one forced renewal; concurrent refusals share it; offline launch shows the stored account; only a real
  refusal disconnects (apart from 04).
- The web build check never reloads mid-use, a captive portal's page is never kept (SHA-256 per file), a failed update
  starts the kept build; the web's `?code=` return works offline.
- The lookup sheets write nothing on a failure; repeated Search never stacks requests; a shared link is never followed.
- Every Hungarian string exists (819 keys each).

## Dropped after verification

- **Engine-side hash check of downloads** (sync reviewer's 4): folded into 06, at the provider, which has the header.
- **Silent end of a Connect that cannot load Dropbox** (UI reviewer's 10): the browser shows its own offline page;
  nothing to fix.
- **A captive portal faking Dropbox or `build.json` answers**: both are HTTPS; a portal can only cause a TLS failure
  (the network). Only a TLS-intercepting proxy reaches 07/08.
- **Disconnect hanging behind a stalled renewal**: the app's `disconnect` cancels the run and the account refresh first.
- **Delete the dead import-download chain**: kept by the 30th review's decision.

## Measurements (probes, virtual time)

| Situation, with 01, 03, and 05 landed | Time before the run fails |
|---|---|
| Immediate failure (DNS, failed fetch) | about 14 s |
| Connect timeouts | about 3.9 min |
| Stalled native call | about 8.6 min |
| Stalled renewal | about 5.1 min |
| Stalled web RPC | about 8.6 min |
| Stalled web listing page or download | about 15.6 min (today about 71.6) |

Today: a `Retry-After: 86400` is waited 86,400 s; a connection reset ends the run after 1 attempt; three failed
attempts on a both-sides edit leave four identical conflict copies.
