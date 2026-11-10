<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 56th review sweep: the song details screen's rendering pipeline

**Reviewed at** `b5d5315b1`; plans written and checked against `8605618f3` (HEAD after the review's fixes), branch
`master`. **Angle:** performance first, then bugs and improvements, in the song details screen's rendering — ABC staff
notation and tabs above all — and the PDF's engraving.

**Challenge:** run against `8605618f3` on all 16 plans by two fresh agents: 04, 06, 07, 13, and 21 sound; 01, 02, 03, 05,
08, 09, 10, 11, 12, 14, and 20 amended (each plan's `**Challenged:**` line says what changed); none dropped. 09 and 10 were
proven with probe tests on HEAD.

## Headlines

- **A sheet-music tune turned by column is engraved for half the column it is given** (plan 20; the owner reported it
  with a screenshot: a 1512 × 938 dp macOS window at 135%, a guitar score over tablature). A tune answers the widest
  column as its least width, so the strip widens its column to it (642 px against the strip's own 360 px in the
  reproduction); the text before the first engraving asks for a page as wide as the screen (1,263 units at 135%, 1,705 at
  100%, wider than any column); and the swing guard settles on the narrowest column (264 units: 224 px of systems in
  456 px columns at 100%). Ten page dots where eight do with the fix, a third column cut by the window's edge; at 100%
  six stops where three do. A strip column wider than a slot also pushes every later column off the slot grid (plan 21),
  which is what cut the third column off.
- **The grid re-measures nearly every line at every candidate width** (plan 01): 11,353 intrinsic measurements on a
  first open where 3,907 are distinct; 189 ms of measure passes, worst 109 ms.
- **A rendering arriving re-measures the whole song** (plan 02): 17–33 ms extra grid decide on Main per arrival, twice
  on a first open.
- **An instrument or tuning change (and a capo that moves neither pitch nor chord names) re-engraves and cross-fades
  every plain staff into the same drawing** (plan 03), and the same tune in another song misses the cache by its song title.
- **Painting outweighs the engine** (plans 04, 06): every fret and notation word is laid out on its own, though few are
  distinct.
- **A pinch or resize evicts other songs' placed widths** (plan 05), so a song opened again can show its tune's text
  first — a written invariant broken.

## Fixed during the review

- `0d4ea1ce9` Keep the numbers that name a tab's strings when the song editor transposes its frets.
- `50ab6dcd1` Stop the song screen from being composed again on every frame of a song scrolling by itself.
- `a808b63c4` Stop rebuilding the song layout's glide bookkeeping on every layout pass of a resize or pinch.
- `8605618f3` Wrap a tab once for every window width that fits the same number of columns, instead of once per width.
- `8f4ff555a` Keep a tab's repeat count unchanged when the song editor transposes a staff with no closing bar. (Found by
  the regression read of the four fixes above, which were otherwise clean.)

## Index

| # | Plan | Kind | Severity | Lane |
|---|------|------|----------|------|
| 01 | [Measure a unit once for every width past its widest line](01-measure-a-unit-once-at-every-width-past-its-widest-line.md) | performance | high | N |
| 02 | [Measure again only the chunks a rendering changes](02-measure-again-only-the-units-a-rendering-changes.md) | performance | medium-high | N |
| 03 | [Key a tune's engraving by only what it reads](03-key-a-tunes-engraving-by-only-what-it-reads.md) | performance, stray animation | medium-high | N |
| 04 | [Lay out each distinct tab word once](04-lay-out-each-distinct-tab-word-once.md) | performance | medium-high | T |
| 05 | [Remember a song's placed width once its rendering lands](05-remember-a-songs-placed-width-once-its-rendering-lands.md) | bug, performance | medium | N |
| 06 | [Lay out each distinct notation word once per drawing](06-lay-out-each-distinct-notation-word-once-per-drawing.md) | performance | medium | D |
| 07 | [Draw a system's strokes as few paths](07-draw-a-systems-strokes-as-few-paths.md) | performance | medium | D |
| 08 | [Keep the PDF's engravings across option steps](08-keep-pdf-engravings-across-option-steps.md) | performance | medium | P |
| 09 | [A tab slot for every row its systems can wrap into](09-give-a-tab-run-a-slot-for-every-row-its-systems-can-wrap-into.md) | bug (test first) | medium | T |
| 10 | [Cut a tab row at a bar no word crosses](10-cut-a-tab-row-at-a-bar-no-word-crosses.md) | bug (test first) | low-medium | T |
| 11 | [Start no fade for a line that does not fade in](11-start-no-fade-for-a-line-that-does-not-fade-in.md) | performance | low-medium | G |
| 12 | [Build a tune's engraved text once](12-build-a-tunes-engraved-text-once.md) | performance | low-medium | N |
| 13 | [Paint a PDF page's notation system by system](13-paint-a-pdf-pages-notation-system-by-system.md) | performance | low | D |
| 14 | [Do an engraving's string work off the main thread](14-do-an-engravings-string-work-off-the-main-thread.md) | performance | low | N |
| 20 | [Engrave a tune for the strip's own column and settle a swing on its narrowest width](20-engrave-a-tune-for-the-strips-own-column-and-settle-a-swing-on-its-narrowest-width.md) | bug | medium | N |
| 21 | [Give a strip column wider than a slot a whole number of slots](21-give-a-strip-column-wider-than-a-slot-a-whole-number-of-slots.md) | bug (test first) | low | N |

## Lanes

| Lane | Area | Plans, in execution order | Files owned |
|------|------|---------------------------|-------------|
| N | Song page notation requests, placement, and measurement | 12, 03, 05, 20, 21, 14, 02, 01 | `ui/notation/SongNotations.kt`, `NotationPlacement.kt`, `NotationChordNames.kt`, `AbcTitles.kt`; `NotationCache.kt` (`keyOf` and the placed widths only); `libs/abc/api/…/AbcTablature.kt`, `libs/abc/implementation/…/AbcRendererImpl.kt`; `ui/songView/SongNotationBlock.kt`, `SongSectionContent.kt`, `SongLyrics.kt`, `SongSectionsLayout.kt`, `model/SongUnits.kt`, new `model/UnitContents.kt`, `grid/SectionMeasurements.kt`, `grid/StripPlacement.kt`; their tests, `NotationPlacementTest.kt`, `StripPlacementTest.kt`, new `tools/screenshots/…/ui/songView/SheetMusicColumnTest.kt` and `songs/campfire_tests-a_long_tune.cho`; `songView/CLAUDE.md`, `grid/CLAUDE.md`, `libs/abc/api/CLAUDE.md`; `notation/CLAUDE.md` (shared) |
| D | Notation drawing (screen and PDF) | 06, 07, 13 | `ui/notation/NotationPainter.kt`, `DrawNotationSystem.kt`; `NotationCache.kt` (`notationWordsOf` only); `ui/print/PrintRenderer.kt`; their tests; `notation/CLAUDE.md`, `print/CLAUDE.md` (both shared) |
| T | Tabs | 04, 09, 10 | `ui/songView/TabStaffLines.kt`, `ui/songLayout/LineRuns.kt`, `libs/chordpro/…/tabs/ChordProTabWrapper.kt`, `tabs/CLAUDE.md`; their tests |
| P | PDF engraving | 08 | `ui/screens/export/LaidOutDocumentProducer.kt`, `ui/print/PrintNotations.kt`, new `ui/print/PrintEngravings.kt`; `PrintNotationsTest.kt`; `print/CLAUDE.md` (shared) |
| G | Fades | 11 | `ui/songView/FadingIn.kt`, `ui/songView/SongSectionChildren.kt`; `ui/songView/SongSectionContent.kt` (the folded run's `fadingIn` call only; N edits other hunks and re-locates by snippet), `SongScalesSection.kt`, `SongChordsSection.kt` as plan 11 names them |

Paths under `ui/` are in `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.

**Merge order: T, G, P, D, N.** No lane builds on another. T, G, and P touch nothing shared with the others but
`print/CLAUDE.md` (P); D and N share `NotationCache.kt` and `notation/CLAUDE.md`; N is the largest and touches the most
shared song view files, so it goes last. Plans 20 and 21 (the owner's reported defect) are in N because every file they
touch is N's or no other lane's (`SongNotationBlock.kt`, `NotationPlacement.kt`, `StripPlacement.kt`), and 20 edits
`rememberSongNotations` and the `widthFor` call after 12, 03, and 05 have; they come before 14, 02, and 01, which are
pure performance work that must reproduce 20's layout exactly (02 measures a tune's chunks by 20's `minWidth`).

**Order inside N:** 12 (the per-tune memo) → 03 (stores its facts there) → 05 (removes the placed-width write from
composition) → 20 (adds `widestWidth` to the same `widthFor` call, `minWidth` to `NotationRun`) → 21 (shares
`grid/CLAUDE.md`'s strip bullet with 20) → 14 (moves the engraving off Main) → 02 (token of what a chunk draws of a
tune, `minWidth` included) → 01 (builds its flags where 02 moved the unit list).

**Shared files:**
- `NotationCache.kt`: N edits only `NotationRequest.keyOf` and the placed widths' KDoc; D edits only
  `notationWordsOf` and its KDoc. Different hunks; a conflict is merged keeping both.
- `notation/CLAUDE.md`: N edits the "Engraved again when…" sentence (plan 03) and the placement sentences of the same
  bullet (plan 20); D the cache's "4,000 laid-out words" (plan 06). Word-level merge, every sentence of both kept.
- `print/CLAUDE.md`: P edits the Layout bullet on `abc` runs; D the Renderer bullet on caching.
- Size limits (code-style): `SongLyrics` (~300 lines), `SongSectionContent` (~160), and `SongSectionsLayout` (190) are
  past the 120-line function limit and `SectionGrid.kt` (709) the file limit; a plan touching them moves out as much as
  it adds. `SongNotations.kt` (387 lines) gains from 12, 03, 05, 14, and 20: it stays under 500, and
  `rememberSongNotations` (86 lines) under 120 (plan 20 moves the effect's body out if it would pass).

## Decisions

- **(D1) Plan 03's new public "has a tab voice" function in `:libs:abc:api`**, so the page and the engine share one
  regex. Recommended: **add it**. *Taken as recommended by the orchestrator (an internal API choice); the owner may overturn it before execution.*
- **(D2) The web engine in a Web Worker** (an idea, not a finding): the web build blocks its only thread for every
  engraving (1.3–28 ms a tune on desktop JVM, a first render 133–364 ms). Recommended: **not now — measure the web
  first.** *Taken as recommended: no plan; revisit after measuring the web.*
- **(D3) Plan 20's trade**: a tune takes the strip's own narrow columns, filled (8 page dots at 135% on the owner's song
  instead of 10 today; a lucky wide engraving gave 6, but with 2.3 columns visible and one cut off). Recommended: **the
  strip's own columns.** *Decided by the owner, 2026-11-10: the strip's own columns.*
- **(D4) Plan 21**: a column slightly wider than a slot takes two slots, centered, leaving space around it.
  Recommended: **as written.** *Decided by the owner, 2026-11-10: whole slots, centered.*

## Measurements (desktop JVM, warm)

- Engine: 8-bar tune 1.3–1.7 ms; 32 bars with chords and lyrics 6 ms; 4 voices × 64 bars 28 ms; tab staff 4 ms; a
  second width costs a full run; first render in a process 133–364 ms; a new engine 16.5 ms. Parse 0.6–1.2 ms. Paint
  (text layouts) 4.2 ms medium (257 texts), 3.8 ms tab (306 texts); in the app `SongNotations.render` (engine + parse +
  paint) took 14–17 ms medium and 13–16 ms tab against 6 and 4–5 ms in the engine, so painting outweighs the engine.
  Draw of 33 systems onto a CPU bitmap 13.5 ms.
- Tabs' text work is negligible and linear (300 systems: parse 2 ms, transpose + retune 5 ms, wrap < 1 ms).
- Whole screen, song C (6 chorded sections, 3 tunes, a 40-system tab; desktop window): first open 6 engine calls (3 at
  width 536, then 3 at 344), 3 grid decides, 16 measure passes totalling 189 ms (worst 109 ms), 11,353 intrinsic
  measurements (3,907 distinct); open again 0 engine calls, 42 ms, 3,723 intrinsics; a text size step 3 engine calls,
  105 ms (worst 51 ms), 9,040 intrinsics; a transposition step 3 engine calls, 61 ms, 8,050 intrinsics; a width change
  from 3 to 2 columns 69 ms, 8,829 intrinsics; back with every engraving cached still 82 ms and 11,175 intrinsics. Each
  arriving rendering cost an extra 17–33 ms grid decide on Main. A plain song's warm open: 8 ms, 171 intrinsics.
- Method: an untracked `:tools:screenshots` test with temporary log lines counting engine renders, `search.decide`
  calls, measure passes, and intrinsic callbacks. The plans that cite a gain confirm it the same way, in the lane
  report, never committed.

## Checked and found solid

- The tab wrapper's termination and linearity; the retune / transposition ordering; multi-digit frets.
- Paged stepping causes no composition; the metronome's beats are collected only in its button.
- `SongLineWithChords`' draw path.
- Notation cancellation and the failure memory; the single QuickJS engine and its interrupt.
- The docs check found no false claims.

## Seen, not planned

- The first fret of a staff without a string name is drawn in the name's weight (`TabEngraving.kt`'s
  `indexOfFirst { it == '-' || it == '|' }`).
- `TabRows.extentAt` walks every row on every draw in right-to-left songs.
- The whole song is transposed twice when chords are named by the capo's shapes.
- One spring coroutine per gliding unit (idea: one shared progress).
- Tab slots undercount at 12 columns or fewer even for a single system (24 rows against 19 slots): outside plan 09, which
  covers 16 columns and up.
- `SectionSizesPool` hashes a whole section per chunk.
- The notation cross-fade writes state it read in the same composition.
- About 29 KB of embedded `@font-face` in every SVG (the parse is ~1 ms).
- A song's first open engraves each tune twice when its staves change the column count: the documented design; the
  engine is cheap, and the extra grid decide is what plans 01 and 02 shrink.
- A song whose tunes settled after a swing swings again when it is opened once more (the cache keeps the width last
  engraved, not that the placement had settled), each step cached and placed at once: a few layouts in a row on the
  open. Worth a plan if it shows after plan 20.
- `SectionCutting.kt`'s `flowAt(k)` builds a whole-song `IntArray` of heights per column count tried; after plan 01
  each entry is a cache lookup.

## Dropped after verification

- **"`SongNotationBlock`'s `drawBehind` is recorded again on every glide frame because `SongSectionsLayout` places
  without a layer"**: every chunk carries a `graphicsLayer` through `fadingIn` (`SongSectionChildren`), so a moved chunk
  redraws its recorded layer; plan 11 keeps that outer layer on purpose.
- Plan 08's premise was narrowed, not dropped: margins, page size, orientation, columns, and text size **do** change
  the width a PDF tune is engraved for, so those steps still engrave; every other option step, and a step back to a
  width seen before, no longer does.
