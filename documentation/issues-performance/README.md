<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# The 59th review: UI performance

Reviewed at `6ddedf2d4` on `main` (plans checked against `200f394e6`). Angle (the owner's): the app feels slow; debug
transitions skip frames, and the 5.5.0 release drops frames on a high-end Windows laptop, worst when opening song
details. Three Opus area reviewers (song details open path; app-wide recomposition and transitions; lists, sheets, and
the desktop and Android build), an Opus measurement run, two plan writers, two challengers. Kept apart from
`documentation/issues/`, which another sweep's execution was using.

## Headlines

- **Every warm transition is one long frame, then nothing.** Measured on an M-series Mac (desktop JVM, whole app
  offscreen, JIT warm): opening song details 25 ms (a plain song), 42 ms (223 lines), 45–77 ms (an ABC tune, 65 MB
  allocated, a GC inside the frame); back to the list 33–39 ms; a tab switch 29–37 ms; a bottom sheet 14 ms (132 ms
  the first time). Every other frame of each animation is under 1 ms. At 120–165 Hz one such frame is 4–6 lost frames.
- **Opening a song** builds its model and measures and column-searches the whole song in the push's first frame,
  every open, nothing kept from the last (03, 04), and a setlist's neighbour pages and the searched chord shapes land
  in the middle of the slide (05). An ABC tune's whole text is joined and described once per slot (06).
- **Going back and switching tabs** composes the whole list from nothing, since Navigation 3 drops the covered entry:
  ~17,400 composable bodies for 22 cards, a tooltip box per icon button and ~10 animation objects per card (07, 08).
- **Windows itself is unmeasured**: which GPU ANGLE runs on, the panel's refresh rate, and GC pauses are only visible
  on the owner's laptop (02).
- The shared transition layout added today (`23ec5e35d`, after 5.5.0) makes every screen a lookahead subtree; not the
  cause of the release's drops, measured before anything is replaced (14).

## Fixed during the review

| Commit | Message |
|---|---|
| `1c154bc92` | Stop recomposing the whole app for a push or pop between two screens that cover the songs list. |
| `f5d632d62` | Stop recomposing the navigation host and every screen's frame on each transition by remembering what they are handed. |
| `2c37d6024` | Stop recomposing the setlists screen on every push and pop that leaves it covered. |
| `25efc33bd` | Let a song's page skip recomposition by handing it the same fold callback each time. |
| `200f394e6` | Let the song pager and step buttons skip recomposition by handing them the same step callbacks each time. |

The last one first read a `rememberUpdatedState` in the composition that wrote it, which recomposed song details
forever (a UI test hung in `waitForIdle`); the landed form reads it only at the press.

## Index

| # | Plan | Severity | Lane | Run on |
|---|---|---|---|---|
| 01 | Time every frame of the transitions in `:tools:screenshots:benchmark` | high (tooling) | M | any |
| 02 | Find why the Windows release drops frames, and fix what that shows | high | W | **Windows** |
| 03 | Keep a few built song models across opens | high | S | any |
| 04 | Keep the lyric and chord name measurements across opens | medium | S | any |
| 05 | Hold models built in the background until the push and the pager settle | high (setlists) | S | any |
| 06 | Work out each ABC tune's text and description once per run | medium | S | any |
| 07 | Compose a tooltip box only once its tooltip is first asked for | high | L | any |
| 08 | Make a song card cheap to compose | high | L | any |
| 09 | Draw the shared chrome into covered screens from a recorded layer | medium | N | any |
| 10 | Keep the song-open count, the transition report, and root reads off transition frames | medium | N | any |
| 11 | Make opening a bottom sheet a cheaper first frame | medium | L | any |
| 12 | Draw the cards' edge fades without an offscreen layer | medium | L | any; gain shows on Windows |
| 13 | Stop recomposing song details for what a corner reads | medium | S | any |
| 14 | Measure the shared transition layout, and replace it only if it costs | medium | N | any |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|---|---|---|---|
| M | measuring | 01 | `tools/screenshots` (benchmark), `gradle.properties` (`campfire.benchmark.jvmArgs`) |
| S | song details | 06, 04, 03, 05, 13 | `ui/songView/**`, `ui/screens/songDetails/**`, `ui/notation/SongNotations.kt`, `ui/tabs/TabReading.kt`, `TabPlayers.kt`, `ui/components/WindowSize.kt` |
| L | lists and sheets | 07, 08, 12, 11 | `ui/components/` (tooltip, song card, labels, list, chip flow), `ui/screens/songs/**`, `ui/screens/setlists/SetlistSongRow.kt`, the filters sheet, `app/desktop` training run |
| N | navigation and root | 10, 09, 14 | `ui/CampfireScreens.kt`, `CampfireContent.kt`, `CampfireOverlays.kt`, `CampfireApp.kt`, `ui/chrome/**`, `ui/components/tabChrome/**`, `ui/spreadTheWord/**`, `ui/navigation/MidTransitionBackHandler.kt` |
| W | Windows | 02 | `app/desktop` (by outcome), the plan's own results table |

**Merge order: M, then S, L, N; W last, by the owner on the Windows laptop.** M first, alone, because every other
plan measures itself with its benchmark before and after; S, L, and N are cut from `HEAD` once M has merged. N last
of the three because it touches the root files every screen sits in. W last so its numbers show what is left after the
app-side fixes.

Shared files: package `CLAUDE.md` files are each one lane's, except `ui/components/CLAUDE.md` (L, and N's 09 for its
recording helper only if it lands there: keep both sides' sentences on a merge). `app/desktop/CLAUDE.md` and
`CampfireDesktopApplication.kt`: L's 11 edits the training run, W's 02 comes after it and rebases on it. 05 and 10(a)
share no file but both move work onto the settle frame: measure them together once S and N have merged.

## Decisions

- **D1: keep the covered list composed under song details, so going back composes nothing** (08 names it, not part of
  any plan: a `SceneStrategy` keeping the list as an overlaid entry, or the list screens outside `NavDisplay`; costs a
  hidden list recomposing on library changes and its memory, and the card transitions assume the covered screen
  leaves). Recommended: **not now**; measure the pop with 07 and 08 landed, and decide from that. **Decided: not now, measure first** (the owner, 2026-11-23).
- **D2: how to prefer the discrete GPU on Windows**, if 02 finds ANGLE on the integrated one: recommended no silent
  override (an upstream skiko request for ANGLE's power preference, and the adapter named in the log); decided with
  02's numbers in hand.

## Challenge

Both challengers read every plan against the documented invariants, every caller, and the other plans. Sound: 06, 13.
Amended: 01 (setlist and first-open scenarios, GC pauses only, Windows timer resolution), 02 (the Store MSIX is the
only Windows release; `JAVA_TOOL_OPTIONS`), 03 (no growth of over-long functions; one holder parameter 05 extends),
04 (the locale in the key), 05 (quiet needs `currentState == targetState`; a held result is taken, not rebuilt), 07
(no frame wait needed), 08 (resting content moved, not recomposed, so a closing menu still fades; the star's first
change still turns), 09 (covered copies placed off window, no semantics, no focus, or they eat touches), 10 (the test
must fail on today's code), 11 (the chip cache scoped to one measure; the training run skips the onboarding), 12 (the
paint is the card's own shape, not its cell), 14 (overlay coordinates, the export slide). Dropped: none.

## Checked and found solid

- Android release: R8 with `proguard-android-optimize.txt`; the Baseline Profile now has rules for every walked screen
  (the 46th's gap is closed). Debug builds are slow by nature: judge the `benchmarkRelease` variant, not debug.
- Desktop: no transparent or undecorated window, no `SwingPanel`, popups on the same canvas; ProGuard optimizing.
- Compose compiler: strong skipping on, no restartable composable left non-skippable; `:data:model` types unstable but
  their flows deduplicate by equality, so identity comparison is equivalent. Not a cause.
- The view model between frames: 0.3–1.2 ms of main thread; search and song groups run on `Default`.
- Slides use `placeWithLayer`; the fast scroller, setlist drag, bottom sheet keyboard handling, cover decoding.

## Dropped after verification

- `animateSheetContentHeight` as a cost of its own: its share includes measuring the whole sheet inside it
  (`dropped-app.md`).
- Fading cards under the list's top in the card colour: they fade to the screen's background (`dropped-app.md`).

## Execution (2026-11-24)

Landed from `b5a80fe33`: 01 (`6dea075b4`); S: 06, 04, 03, 05; L: 07, 08, 12, and 11 in part; N: 10, 09, 14; and
`2c6fee29c` (the fade test's edge tolerance on Linux), `5b5dd4c84` (a leftover opt-in). Left in this folder:

- **02**, rewritten for an agent on the owner's Windows laptop (`c721eeb54`).
- **11**, its step 2 (no profile showed `animateBounds` reaching `boundingRectInRoot`) and step 4 (a) (`app:desktop`
  cannot open a sheet: it needs a public view-model entry or to be a friend of `:presentation`).
- **13**, reverted by its lane: composing during the `push:setlist` slide grew (10.5 → 14–17 ms) with no fewer
  recompositions.

D1's figures (after 07 and 08, Mac): `pop:plain` warm 1.1–2.9 / 26.6–38.0 ms, `pop:long` 1.2–3.9 / 25.6–39.2 ms,
layout still 19–30 ms of it, against 2.3 / 38.9 and 2.9 / 41.8 at `6dea075b4`; allocation 15.1 → 11.9 MB.

After every lane, `5b5dd4c84` against `6dea075b4`, back to back on a loaded Mac (both slower than the baseline above):
warm frame 1 of every push fell from 14–25 ms to 4–7 ms and `sheet:close` from 17 to 7 ms; pops' worst frame fell
3–17 ms; allocation per transition roughly halved (pops 15.1 → 9.5 MB, `push:long` 34 → 14 MB, `push:fresh` unchanged).
The pushes' **frame 2 grew** 5–9 ms (`push:plain` 29 → 34, `push:long` 47 → 56, `push:tabs` 30 → 41), part of
frame 1's work arriving a frame later; lane N saw the same on `push:long` and `push:setlist` after 09. Worth one
quiet-machine A/B of 09 before the next performance work.

## Measurements

The probe that produced the numbers above stays out of the repo; 01 makes it the benchmark's transition suite. Its
frame-1 split (warm, Mac): compose 3–4 ms, layout 19–36 ms, draw 2.5–4.5 ms; 75–85 % of each long frame is layout, the
incoming screen composed inside the pager's or the grid's measure.
