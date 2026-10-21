<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# The 43rd review: abuse of shared links, and hand-edited Dropbox files

Reviewed at `5514fcf76` on `master` (plans re-checked by the challengers at `ea4b86a1e`), 2026-10-21. Angle: abuse of
the shared links feature — a hostile link or zip opened by a recipient, the sharer's Dropbox and privacy, and hostile
content in a shared song after import — plus, at the user's request, a minimal defence against files edited by hand
in the synced Dropbox folder (data loss is acceptable; the app must not crash and must recover to a usable state).
Three Opus reviewers, one Opus reviewer for the Dropbox folder, a Sonnet docs check (nothing false), Opus writers, and
two Opus challengers.

## Headlines

- A 24 KB zip behind a share link inflates to 23 MiB of `[0,0,…]` JSON that parses into a ~900 MB tree: Android runs
  out of memory before the review appears (01). A deeply nested setlist overflows the stack, at launch too (01).
- A huge or deeply nested `preferences.json` or `shared-links.json` put into the Dropbox folder by hand is downloaded
  whole and retried forever, or crashes Settings → Sharing (and likely every run on iOS) (08, 09).
- A shared song with alternating `{transpose}` sections freezes the song view for seconds to minutes (05); one with
  50,000 tags freezes every list showing it (06).
- A stranger's link opens its review on Replace for any name the recipient already has (02).
- A song's cover address is asked on every launch when it gives no image (a presence beacon), and on desktop may point
  into the local network (07).

## Fixed during the review

Nothing: every verified finding was complex.

## Index

| # | Plan | Severity | Lane |
| --- | --- | --- | --- |
| 01 | Count a JSON document's tokens before parsing it, setlists included | high | J |
| 02 | Preselect Replace in a link's review only for names that link wrote before | medium | I |
| 03 | Warn in the Disconnect confirmation while deletions have not stopped their links | low | S |
| 04 | Run Stop sharing under the journal's application lock | low | S |
| 05 | Skip scale suggestions over a played-chord budget | medium | K |
| 06 | Read at most a fixed number of tags and languages per song | medium | K |
| 07 | Fetch covers only from public `https` hosts, and remember a missing one | low | C |
| 08 | Cap every Dropbox download before its body is read | high | J |
| 09 | Bound the folder's JSON documents before parsing them | high | J |

## Lanes

| Lane | Area | Plans, in order | Owns |
| --- | --- | --- | --- |
| J | JSON bounds, Dropbox downloads | 01, 09, 08 | `:data:model` `JsonBounds`, `ShallowJson`, `SetlistDocumentFormat`, `SyncedPreferencesDocument`, `SharedLinksFormat`, `SharedLinksMirror`, `SharedLinksRegistry`, `DropboxSyncProvider`, `SyncFolder`, the new `SyncDocumentTooLargeException`, the preferences step and watcher's document reads, `FakeSyncProvider` |
| K | `:chordpro` limits | 05, 06 | `scales/SongScaleGroups`, `ChordProMetadataBuilder`, `ChordProMetadata`, `ChordProTags`, `ChordProLanguages`, `SongMetadataEditing`, `SelectionEditing`, `SongLanguagesDialog` |
| C | Covers | 07 | `CoverArtRemoteSourceImpl`, `HttpClientConfiguration`, `PublicAddress`, `CoverArtRepositoryImpl` |
| S | Stopping shares | 04, 03 | `SharedLinksRepositoryImpl`, `SharedLinksJournal`, `SharedLinksRepository`, `SyncConnectionManager` (KDoc), `ShareLinkController`, `CampfireDialogs` |
| I | Import review | 02 | `ImportController`, `ImportRequest`, `importReport/`, `UserPreferences` and its document and mappers, the new library use cases |

**Merge order: J, C, K, S, I.** J first: it carries the high-severity fixes and the shared `JsonBounds`. S and I last:
they touch the most shared UI files (`CampfireViewModel.kt`, `strings.xml`). All lanes are cut from `START`.

**Shared files:** `data/sync/.../shared/CLAUDE.md` (J, S), `data/source/remote/implementation/CLAUDE.md` (J, C),
`data/source/local/implementation/CLAUDE.md` (J, I), `presentation/.../ui/CLAUDE.md` (C, K), the root `CLAUDE.md`
(C), `CampfireViewModel.kt` and both `strings.xml` (S, I). Each lane edits only its own paragraphs, keys, and
parameters; the merge keeps every sentence and key from both sides.

## Decisions (taken by the user, 2026-10-21; all the recommended defaults)

- 02: option (a) — Replace only for names an earlier import of the same link (keyed by its Dropbox file id) wrote on
  this device; Keep both for every other name.
- 08: an oversized `preferences.json` or `shared-links.json` reads as unreadable and is overwritten, like a malformed
  one under 09 (the plan was amended to this after the challenge).
- 07: a 5xx or unreachable cover address is not remembered; it is asked again on the next launch.
- 05: `MAX_PLAYED_CHORDS = 1_000`. 06: 100 tags and 100 languages per song.
- Taken unasked: 03's warning shows after any deletion not yet applied in Dropbox (the journal does not tell shared
  items from others), worded so it does not claim a link exists; 04's stop may wait behind a run's shared-copy step.

## Challenge

Two Opus challengers read every plan against `ea4b86a1e`. Amended: 01 (the check is `JsonBounds` in `:data:model` from
the start), 02 (keyed by file id, not share id, which a crafted link can copy; the record merges), 03 (wording; the
view model wiring is required), 04 (disconnect claim corrected; test fixture), 05 (cost claims re-measured), 07
(test builds the production client; residuals stated), 08 (the first mechanism was broken: Ktor 3.6's
`skipSavingBody()` does nothing, a streamed error was read twice, the refusal became a retried network error), 09
(only calls 01's check; shared 600k budget; mirror margin). Sound: 06. Dropped: none.

## Checked and found solid

- Link grammar: anchored regex, 4096-character cap; the download is pinned to `dl.dropboxusercontent.com`; no
  arbitrary host, no URL injection.
- Link downloads: the 24 MiB cap streams; cancellation; a non-zip answer is "link gone".
- `ZipReader`: bounds-checked offsets, ZIP64 refused, overlapping-entry bomb check, nesting depth 3, shared budget.
- Path traversal: entry names cut at the last separator, `requireValidFileName` on every platform, a hand-planted
  share id refused by `sharedFilePath`.
- The published zip holds only the item's library bytes (and a setlist's named songs), fixed timestamps, no
  preferences, overrides, covers, or account data; links are public view-only file links, never folder links; logs
  name share ids, never `rlkey`.
- Revocation paths, 429 / `Retry-After` handling, re-upload only on a changed content hash.
- Link directives: `http(s)` only, no shell on desktop, `noopener,noreferrer` on the web.
- Cover downloads: `image/*` only, 5 MB cap, 60 s timeout, file name is the address's SHA-256.
- `:chordpro` on 1–4 MB pathological input (unclosed brackets, huge `{define}`s, wide tabs): linear, under 0.5 s.
- An import never writes preferences, chord-shape picks, or instrument picks.
- The Dropbox folder: folders and subfolders ignored, wrong extensions filtered, names a device cannot hold refused,
  non-UTF-8 read through the fallback, a deleted `preferences.json` or `shared-links.json` handled, library downloads
  over 8 MiB refused by listing size.

## Dropped after verification

- "5xx covers retried every minute while shown": nothing retries on a timer; a row redrawn after a minute asks again.
- Unconfirmed, not planned: an image decompression bomb under Coil on desktop/iOS/web (next to the settled
  `SizeResolver.ORIGINAL` item); Windows `rundll32` parsing of odd https strings; an iOS Local Network prompt for an
  https LAN cover address (07 refuses LAN hosts anyway); a jetsam crash loop through the iOS share inbox (01 removes
  the cause).

## Measurements

- `parseToJsonElement` on 23 MiB `{"songs":[0,…]}`: 882–918 MB of tree; 8 MiB `[0,…]`: 312 MB (OOM at `-Xmx192m`).
- `SongScaleSuggestions.of`: 50 alternating-`{transpose}` sections × 500 chord lines (1.25 MB) 25 s, 100 → 94 s;
  200 sections with 45,000 chords (181 KB) 2.3–2.8 s; under the 1,000-chord budget 0.17–0.42 s at 200 sections.
- kotlinx-serialization-json 1.11.0 stack overflow: depth 1,994 on a 1 MB stack, 864 on 512 KB; pretty-printing at
  732 and 312.
