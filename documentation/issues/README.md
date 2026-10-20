<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# 40th review

Reviewed at `0164081ff` on `master` (2026-10-20). Angle: what the 25 commits since the 39th review
(`175121032..0164081ff`) added, each feature on its own terms: the change-feed long poll, sync on return, and
reconnection; turning Songs off; predictive back and Esc; the tools' button group; scale playback and the tuner's
Strings / Chromatic mode; the compact song details and resize transitions. Six Opus area reviewers, a Sonnet docs
check, an Opus regression read of the fixes, three Opus plan writers, and two Opus challengers. The live Dropbox run
ran on the third try, once the user clicked the two consent pages (see **Measurements**).

## Headlines

- **Desktop sync stopped running on its own after one offline moment** (fixed, `c853adb09`): the desktop's
  reconnection flow is empty, `first()` threw, and the scheduler's loop and the change-feed watcher died with it.
- **Another device's transpositions and tempos were lost to one failed download** (fixed, `74eaccebb`, `6136b5999`).
- With Songs off, a shared chord sheet is written into the library unreviewed (plan 08, decision).
- Scale playback: notes overlap and can clip on desktop (03), Android flips another app's audio focus on every note
  (04), a refused note crawls 14–18 s in silence (05).
- The instrument pill can squeeze its tuning out of sight on the Tuner tab (10).
- Live: a long poll the service held and lost is backed off as if offline, so changes arrive 54–96 s late on a healthy
  link (13).
- CI was red since `cf6c277f4`: a UI test still expected the short-window rail rule `1cb8069af` removed (fixed, `ca02170c3`).

## Fixed during the review

`f3c873935..0876be233`, not pushed (`52c0e8337` in the range is the user's own golden update):

- `f3c873935` Fix the instrument notes' claim that the tuner has no Chromatic switch.
- `398d6b5fb` Name the tools' button group, not tabs, in the metronome screen notes.
- `ec219d6fb` Reword the Songs switch's description in Settings, and name the scales among what the app is without songs.
- `c853adb09` Keep the desktop syncing on its own after it was offline once, instead of only on returning to the app.
- `74eaccebb` Bring over another device's transpositions and tempos even when the first try to download them fails.
- `8ce9f74ad` Put the sync tests' run counter KDoc back on the run counter.
- `244291259` Ignore a lost Android network that is no longer the default when telling sync the connection is back.
- `094cf8266` Note that sync's waits run their full length on an Android network that never validates.
- `f75c0b5ff` Open the Preferences tab from a web bookmark of the old Songs settings tab.
- `dca2367f7` Visit the Tuner, Chords, and Scales through the tools' button group in the Baseline Profile journey.
- `bc2a13c3b` Name the tools' button group, not tabs, in the comments of the tool screens and the chrome.
- `7ea30b9e5` Read the tools' buttons to screen readers as one choice of several, not as checkboxes.
- `ca02170c3` Test the rail's tools grouping by whether every item fits, as the rail now decides it.
- `f6a166c3c` Keep the settings sections inside the page while they slide between one column and two.
- `396acf923` Test that the web build's offline and development server routes list every address the app writes.
- `6136b5999` Wait longer and longer between tries while another device's preferences cannot be downloaded (the
  regression read's follow-up to `74eaccebb`).
- `684f3258a` Rewrap two notes lines the review's fixes left overlong.
- `d45387630` Say that the rail's tools grouping follows Firefox for Android's keyboard, which shortens the page
  (was plan 12; its Chrome scenario did not hold, `overlaysContent` keeps the window's size there).
- `0876be233` Rewrap the rail capacity comment in the app's content.

## Index

| #  | Plan                                                              | Severity   | Lane |
|----|-------------------------------------------------------------------|------------|------|
| 01 | Match the change feed to the index ignoring case and Unicode form | low        | S    |
| 02 | Keep an open deletion question across returns to the app          | low        | S    |
| 03 | Feed desktop tones through one line and a tone stream             | medium     | T    |
| 04 | Keep Android audio focus across replaced tones                    | medium     | T    |
| 05 | End a scale run at the first refused note                         | low–medium | T    |
| 06 | Play iOS tones alone in the playback category                     | low        | T    |
| 07 | Stop a scale when the app leaves the front                        | low        | T    |
| 08 | Review what the system hands over while Songs is switched off     | medium     | N    |
| 09 | Measure a Forward address on the home screen the switches leave   | low        | N    |
| 10 | Keep a share of the instrument pill for its tuning                | medium     | U    |
| 11 | Reach the instrument pill halves with a 48 dp touch               | low        | U    |
| 12 | Move the Tuner's instrument pill to a line of its own when it cannot show its tuning | low | U |
| 13 | Ask again at once after a long poll the service held and lost     | low–medium | S    |
| 14 | Skip the preferences download when the listing shows the revision the last run left | low | S |

## Lanes

| Lane | Area                                   | Plans, in order    | Files owned                                                              |
|------|----------------------------------------|--------------------|--------------------------------------------------------------------------|
| S    | sync (`data/sync`, Dropbox feed)        | 01, 02, 13, 14     | `SyncWatchPoint`, `SyncRunScheduler`, `SyncRemoteWatcher`, `SyncedPreferencesSync`, the index document, `RemoteListing`, `DropboxChangeFeed`/entries, their tests, `scheduling/CLAUDE.md`, `data/source/remote/implementation/CLAUDE.md`, `SyncRepository`/`SyncFolder` KDoc |
| T    | tones (`tuner/*`, `ui/tuner`, scales)  | 03, 04, 05, 06, 07 | `tuner/api`, `tuner/implementation`, `TunerController`, `ScaleSheet`, `dialogs/scales/CLAUDE.md` |
| N    | imports and web navigation             | 08, 09             | `ImportController`, `ImportOutcome`, `BrowserRoutes`, `BrowserHistory`, their tests |
| U    | instrument pill                        | 10, 11, 12         | `InstrumentPill`, `ToolInstrumentSetting`, `ui/instrument/CLAUDE.md`, the new pill UI test |

**Merge order: S, T, N, U.** S and T touch nothing outside their modules but the root `CLAUDE.md` line (02); N
touches the shared `ui/CLAUDE.md` and `wasmJsMain/CLAUDE.md`; U is the UI lane and goes last. No plan touches
`strings.xml`.

**Shared files:** the root `CLAUDE.md` (02 rewords the Sync invariant "a run starts on every return to the front");
`presentation/.../ui/CLAUDE.md` (08 only); `presentation/src/wasmJsMain/CLAUDE.md` (09 only). `presentation/.../ui/tuner/CLAUDE.md`: 05 (lane T, its Lifecycle section) and 12 (lane U, its Layout
paragraph). Merge `CLAUDE.md`
paragraphs word by word, keeping both sides.

## Decisions

Taken by the user on 2026-10-20, all as recommended except 4:

1. **02**: a return to the app leaves an open deletion question alone (option a).
2. **06**: a tone heard alone on iOS uses the `playback` category.
3. **08**: with Songs off, a shared text and a lone `.cho` handed over go to the import review.
4. **10**: the 200% Tuner case gets option B too, as plan 12 (against the recommendation to accept it).
5. **11**: proven with a UI test, the non-drawing outset only if the test fails.

## Checked and found solid

- Long poll lifecycle (`repeatOnLifecycle(STARTED)`), no double runs across watcher, return and retry, cursor coverage,
  `reset`/`backoff`, 401s settled by a run, callback unregistration on every platform, the progress bar's animations.
- Songs off: preferences default and compatibility (not synced), back stack rooting, every way to open a song blocked,
  the offline route lists.
- Predictive back recession (cancel, complete, non-predictive), handler ordering, the Esc ladder, restored tool state.
- `ToneStream` math, scale tones, tap guards, iOS tone output teardown, the web gesture rule, the tuner mode preference.
- `GroupedRows` / `BalancedRows` math, no measure-time feedback loops, no first-frame animations, the scale suggestions
  keep the 38th review's decisions.
- Manifest permission, iOS Info.plist, desktop jlink modules, credits, privacy, strings, license headers, sizes.

## Measurements

Live run, desktop at `0876be233`, two test homes on the user's real folder, each through a local degrading proxy;
the folder was snapshotted (339 files) and restored with every `content_hash` equal, and test home B's token revoked.

| Scenario | Result |
|---|---|
| A third device uploads, both apps in front (×5) | run starts 1.26–1.49 s after the upload (median 1.39 s), one run per change |
| Another app saves (×5) | the watching app's run starts 4.2–4.9 s after the save, ~1.3 s after the upload; no double run |
| Minimised 61 s, a change meanwhile | poll cancelled in 0.3 s, no requests while away, one run 0.46 s after restore |
| Connections go silent while polling | the poll hangs to its 360 s bound, then waits 60 s: the change arrived after 389.6 s (13) |
| Link down 120 s | 3 failed polls, no storm, no error shown; the run started 90.8 s after the link returned (by design) |
| 500 ms per chunk, 1% 3 s stalls, 11 min | no loop, all runs succeed; runs 15–22 s instead of 2–3 s |
| Undegraded link | two polls never answered (closed at 313.9 s; the 360 s bound), each backed off 30 s+: changes 54–96 s late (13) |
| Idle in front 10 min | 3.9 s CPU, ~73 MB RSS, two long polls |
| Disconnect with a poll open | poll cancelled in 9 ms, one token revoke, nothing after |
| Every run | downloads `preferences.json`, 0.6–0.9 s of a ~2.4 s one-song run (14) |

## Dropped after verification

- A full run on every return from in-app system UI (file picker, share sheet): by design, the root invariant says a
  run starts on every return to the front.
- The Tools grouping flipping while typing in Chrome on Android: Chrome lays the keyboard over the page; only the
  comment was wrong (fixed, `d45387630`).
- A device's own upload waking its own poll once (live): judged its own, no second run.
- `ToolsItemTest`'s large-text KDoc "taller than a short window": still true, left alone.

## Challenge

Two Opus challengers ran on every plan: 01–05, 08–11 amended (01's deletion test would have stopped at the deletion
guard; 03 overlapped a stopped tone with the next and kept a dead stream; 04 raced a posted focus loss; 05 wrote one
change as two states; 08 opened the editor when Songs went off mid-read; 09's test passed on the old code and moved
into a pure `BrowserRoutes.resolveForward`; 10's UI test matched the merged node and its measure could throw; 11 read
Compose's hit testing and expects a test only), 06 and 07 sound, none dropped. Plan 12, written after the decisions, was challenged on its own and amended
(the layout's intrinsics would have run its measure unbounded, creating the slide's state at the wrong target and
throwing; a negative room mid-slide). Plans 13 and 14, from the live run, were challenged together and amended (13: a
late 408/429/5xx also counts as a held poll, bounded at ~60 polls an hour in front, a test that a failing listing still
backs off; 14: a test through the real `SyncRunner`, empty revisions refused, every released index reader named).
