<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# 32nd review: concurrency, cancellation, and low-end smoothness

Reviewed commit `6c3043996` (master). Angle: races and cancellation across sync, imports, edits, and engines, and jank on
low-end devices (main-thread work, recomposition per frame). Lean budget: four Opus area reviewers (storage and sync;
view model and domain; Compose rendering; engines and platform shells) plus the CLAUDE.md reviewer; no live run.

## Headlines

- **A stopped sync run can leave a file on disk the lists never hear about** (01): the next edit of that cached song
  writes the old text back over the downloaded one; on the web Delete library can be survived by an in-flight download.
- **The iOS tuner can go deaf when it opens over a playing click** (06): the click's stop deactivates the shared audio
  session the tuner has just started.
- **The first song of a session parses the chord table on the main thread** (05): 23 ms JIT / 302 ms interpreted on a
  Mac, frames dropped on a low-end phone and the web.

## Fixed during the review

`6c3043996..d356e8f25`:

- `dd76d5b97` Keep typing in the import review's search smooth on a large backup by folding each name once.
- `cdfa717d6` Never leave an empty setlist behind when the app closes while a new setlist with songs or a duplicate is written.
- `79569afbc` Point the CLAUDE.md files at the chrome package for the Tools item and the navigation chrome.
- `aab7d906e` Fix the stale names and pointers in the editor, first run, song details, platform, and metronome CLAUDE.md files.
- `3824d9b87` Use the serial comma in the chrome and dialogs CLAUDE.md files.
- `c87a32d90` Drop the hand check list from the print CLAUDE.md file.
- `c0691a138` Let a review plan raise a file's size allowance by its net growth in the code-style and codebase-review skills.
- `d9b63f5b2` Trim narration from the ui and setlists CLAUDE.md files.
- `d356e8f25` Restore the metronome settings, tabs package, and first section notes the docs trim lost, and align the skills
  on allowances (found by the regression read of the fixes).

## Index

| # | Plan | Severity | Lane |
|---|------|----------|------|
| 01 | Finish a sync run's local write and its library notice together, even when the run is stopped | medium | S |
| 02 | Keep the completed index when a sync run is stopped after it was saved | low | S |
| 03 | Drop a Files export whose export screen closed before its file was ready | low | P |
| 04 | Recompose only the moved rows while a setlist row is dragged | low–medium | P |
| 05 | Read the player's chord table off the main thread before the first song needs it | low–medium | P |
| 06 | Reopen the iOS tuner's microphone when the click's stop deactivated the shared audio session | medium | E |

## Lanes

| Lane | Area | Plans | Files owned |
|------|------|-------|-------------|
| S | sync | 01, 02 | `data/sync/implementation/**` |
| E | engines | 06 | `tuner/implementation/**` (and `metronome/implementation/src/iosMain/**` only under option B) |
| P | presentation, chordpro | 03, 04, 05 | `presentation/.../ui/screens/export/`, `ui/screens/setlists/`, `ui/chords/`, `ui/state/PreferencesController.kt` (the warm-up), `chordpro/.../voicings/`, `chordpro/CLAUDE.md` |

**Merge order: S, E, P.** No two lanes share a file; P goes last since it touches the most UI files and `:chordpro`.
Shared files: none. `size_allowances.json` may be touched by any lane (each raise is one line, a cherry-pick conflict is
resolved by keeping both raises).

## Challenge

Ran on every plan (two fresh read-only agents). 01 and 03 sound. 02 amended: the preferences step reports the folder's
base (`effectiveRemote`) once the local write is settled, never the merged one, which would revert a local-only edit
everywhere if the upload was cut short. 04 amended: the row's order provider is built inside the item content so it
keeps its identity, and the builder keeps reading the dragged order. 05 amended: the warm-up lives in
`PreferencesController`, gated on chords and chord diagrams, with the web trade-off stated. 06 amended: a stall is
detected as "no new tap for 1 s" rather than `!engine.running`, which a phone call's interruption would also trip
before its own notice and so spend the one reopen; the engine's existing reopen tests cover the rest.

## Decisions

- **06, how to keep the click from silencing the tuner** — recommended option A (the tuner notices its stopped engine and
  reopens it); option B is a shared session counter across modules. **User chose option A** (2026-10-14).

## Checked and found solid

- Repositories: re-read bookkeeping, `NonCancellable` write + cache update, lock order; import writes `NonCancellable`;
  `SongContentRepository` generation counter; `SyncRunScheduler` CAS; `CancellationException` rethrown in every broad catch.
- View model: lookup/cover jobs cancelled with their sheet; list states on `Dispatchers.Default`; editor draft and save
  ordering; PDF export cancellation; first-run ordering; selection edits.
- Rendering: song card fade, section headers, fast scroller, edge fades, tuner needle, metronome flash, transitions,
  pinch batching, song pages, lazy grid keys and content types, stability config.
- Engines: metronome queue and session ids; Android/desktop/iOS/web click outputs release blocked writes; pitch detection
  0.2 ms per window with no allocation; microphone inputs; no `runBlocking`, no `Dispatchers.IO` in wasm.

## Dropped after verification

- **Done button padding animation recomposes `SongList` per frame** (`SongList.kt`, `animateDpAsState`): the rows skip
  and the grid is remeasured either way, so the cost is a few function calls per frame for half a second; not worth a plan.
- **Web: a new file is empty between create and close, so sync could hash it empty**: transient, the next run heals it.
- **Cover prune racing a fresh cover save**: costs one re-download.
- **Baseline Profile names `ChordVoicingSearch` under its old package**: prepare-release regenerates it.
