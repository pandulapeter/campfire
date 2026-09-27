<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Performance review — 2026-09-27

A read-only review of the whole codebase at `ea950dc6`, aimed at rendering smoothness and snappiness. Six reviewers
covered six areas: the song details screen, the list screens, the app shell and view model, the editor and
`:chordpro`, the data layer, and the build and platform shells. Each finding was re-checked against the code when
its plan was written. Some numbers are measured on the desktop JVM, and the plans say which ones. The rest are
estimates, and each plan's verification section says how to confirm them.

Every file here is one plan and lands as **one commit**. Its commit message is in the plan's table: one line, no
body, no attribution. `EXECUTION.md` is the brief for an orchestrating agent that resolves them in parallel.

## Headlines

- **Pinch-to-zoom on a song re-lays out the whole song on every frame**, on three pager pages at once, and twice
  per frame while `animateBounds` springs. This is plans 01–04; 10 is deferred. It is the biggest rendering
  cost in the app.
- **The web build copies every file across the Wasm boundary one byte per call, and makes about 4 storage round
  trips per file** while scanning. That is 33, 34 and 41, the dominant web cold-start cost.
- **Opening or closing search rebuilds the whole song grid on every frame of the app bar spring** (15). Scrolling
  re-records every visible card (16), and every row with tags pays for an offscreen layer (17).
- **Desktop rescans the whole library on every alt-tab** (23), and a sync run that changed one file rescans
  everything (37).
- **Android cold start** waits about 170 ms on a fade nobody can see (32) and ships no profile of the app's own
  code (44).
- **One correctness bug turned up along the way:** after the first pinch of a session, a text size changed
  elsewhere (Settings, sync) is ignored until restart. Plan 02 fixes it.

## Plans

| # | Lane | Impact | Plan |
|---|---|---|---|
| 01 | A | high | [Neighbour pages keep the settled font scale](01-neighbour-pages-settled-font-scale.md) |
| 02 | A | medium | [Live font scale as snapshot state, read deep](02-live-font-scale-snapshot-state.md) (also fixes the stale-preference bug) |
| 03 | A | low–medium | [Stepper label doesn't cross-fade per percent](03-stepper-label-no-crossfade-per-percent.md) |
| 04 | A | medium–high | [animateBounds off during continuous changes and in the editor preview](04-animate-bounds-off-during-continuous-changes.md) |
| 05 | A | low | [Header height in layout; FoldedRuns remembered](05-header-height-in-layout-and-remembered-folded-runs.md) |
| 06 | A | medium | [No lyrics swap mid-swipe](06-no-lyrics-swap-mid-swipe.md) |
| 07 | A | medium | [Parse/transpose/sections off the main thread](07-render-song-off-main-thread.md) |
| 08 | A | medium | [Section measurements keyed by content](08-section-measurements-by-content.md) |
| 09 | A | low | [Transposition labels keyed by key pairs](09-transposition-labels-by-key-pairs.md) |
| 10 | A | high | **Deferred** — [Visual scale during pinch, re-wrap on release](10-visual-pinch-relayout-on-release.md) |
| 11 | A* | medium | [Stability configuration for the :chordpro model](11-chordpro-model-stability-configuration.md) |
| 12 | B | low–medium | [Summary cache fast path on chorded lines](12-summary-cache-chord-line-fast-path.md) |
| 13 | B | low | [Toolbar's declared metadata computed incrementally](13-declared-metadata-incremental-cache.md) |
| 14 | B | medium | [German-notation probe only inside brackets](14-german-notation-probe-in-brackets.md) |
| 15 | C | medium–high | [App bar overlap read in layout](15-app-bar-overlap-as-lambda.md) |
| 16 | C | medium | [List top fade clamp](16-list-top-fade-clamp.md) |
| 17 | C | medium | [Tag strip offscreen only when it overflows](17-tag-row-offscreen-only-when-scrollable.md) |
| 18 | C | medium | [Library-derived states off Main](18-library-derived-state-off-main.md) |
| 19 | C | medium–low | [Pushed header in its own scope](19-pushed-header-own-scope.md) |
| 20 | C | medium–low | [Cheap row recomposition at scroll start/end](20-row-recomposition-at-scroll-start.md) |
| 21 | C | low | [Fast scroller conflation](21-fast-scroller-conflation.md) |
| 22 | C | low | [Setlist star captures a Boolean](22-setlist-star-captures-boolean.md) |
| 23 | D | medium–high | [Rescan on start, throttled, no loading flicker](23-rescan-on-start-throttled.md) (default throttle) |
| 24 | D | medium | [Prune and batch song texts](24-prune-and-batch-song-texts.md) |
| 25 | D | medium | [Snap theme on first resolution](25-snap-theme-on-first-resolution.md) |
| 26 | D | low–medium | Option A only — [Theme change fade](26-theme-change-snapshot-fade.md) |
| 27 | C* | medium | [Song picker order precomputed](27-song-picker-precomputed-order.md) |
| 28 | — | low–medium | [Resize hands screens decisions, not widths](28-resize-discrete-layout-decisions.md) (phase 2) |
| 29 | D | low | [Sync notification effect per run](29-sync-notification-effect-per-run.md) |
| 30 | D | low | [Desktop dark-mode poll](30-desktop-dark-mode-poll.md) |
| 31 | D | low | [Colour choice ring in draw phase](31-color-choice-ring-draw-phase.md) |
| 32 | D | medium | [Skip the hidden launch fade on Android/web](32-skip-hidden-launch-fade.md) |
| 33 | E | high | [Bulk web byte/text transfer](33-bulk-web-byte-transfer.md) |
| 34 | E | medium–high | [One-pass web scan](34-one-pass-web-scan.md) |
| 35 | E | medium | [Sync waits for the first library load](35-sync-waits-for-first-library-load.md) |
| 36 | E | medium | **Rejected** — [Sync hash reuse by size and mtime](36-sync-hash-reuse.md) |
| 37 | E | medium | [Per-file refresh after sync](37-per-file-refresh-after-sync.md) |
| 38 | E | medium | [Import without read-back or rescan](38-import-without-rescan.md) (without the optional fsync step) |
| 39 | E | medium | [Sync index off Main](39-sync-index-off-main.md) |
| 40 | E | low–medium | [Preferences JSON off Main](40-preferences-json-off-main.md) |
| 41 | E | medium | [Persistent OPFS writer worker](41-persistent-opfs-writer-worker.md) |
| 42 | E | low–medium | [iOS/JVM storage stats](42-ios-jvm-storage-stats.md) |
| 43 | E | low–medium | [Parallel library export](43-parallel-library-export.md) |
| 44 | — | medium | [Android Baseline Profile](44-android-baseline-profile.md) (phase 2, last) |
| 45 | F | low | [Drop AppCompat and Material Components](45-android-drop-appcompat-and-material-components.md) |
| 46 | F | medium | [Keep the wasm Response for the code cache](46-web-keep-wasm-response-for-code-cache.md) |
| 47 | F | low–medium | [Per-file web versions](47-web-per-file-versions.md) |
| 48 | F | medium | Approved — [Android handles configuration changes](48-android-handle-configuration-changes.md) |
| 49 | F | low | [AppIconSwitcher early return](49-android-app-icon-switcher-early-return.md) |

Plans marked `*` were written by one lane's reviewer but are executed in another lane because of the files they
touch: 11 in lane A (it edits `SongLyrics.kt`), 27 in lane C (it builds on 18).

## Decisions (answered by the user on 2026-09-27)

| Plan | Question | Decision |
|---|---|---|
| 10 | During a pinch, scale the page like a picture and re-wrap the text only once the fingers lift? | **Deferred.** Not executed in this run; the user will judge after 01–04 land. |
| 23 | How often to rescan, and whether ON_START bypasses the throttle | **The plan's default:** ON_START always rescans; desktop ON_RESUME only when the last rescan is stale. |
| 26 | User-initiated theme changes: (A) stepped lerp, (B) pixel-snapshot fade, (C) leave as is | **(A) stepped lerp.** Execute only option A of the plan. |
| 36 | Reuse the sync hash when size and mtime match the index | **No.** Not executed; content keeps deciding what changed. |
| 38 | Optional step: skip the fsync for files that are new during an import | **No.** Execute the rest of 38 without that step. |
| 48 | Keep the Android activity through size, orientation and density changes | **Yes**, verified on the emulator against all 13 recreation-dependent sites the plan lists. Language and dark mode stay as recreation. |

## Not planned

These were looked at and left out, with the reason for each:

- **Incremental highlighter tokenizing and merging chord spans in the editor.** The field's own relayout of 800
  spans may be the real per-keystroke cost, so profile before changing it.
- **`kotlin.collections.*` in the stability file.** It would change skipping for every collection parameter in
  `:presentation` without an audit.
- **The web file picker's per-byte copy** (`FilePicker.wasmJs.kt`). It has the same mechanism as 33 but runs only
  on an explicit import. Worth doing with 33's helper afterwards.
- **A second, unplaced copy of the navigation chrome during pushes**, and **back-stack JSON on every navigation**.
  Both are small; do them only if a profile shows them.
- **The editor Split preview and details screen items that overlapped** were merged into 04, 07 and 08.
