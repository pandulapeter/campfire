<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 49th review: the ABC staff notation feature

Reviewed at `cfcf08150` on `master` (`af9940af8..cfcf08150`: the `:abc` module running abc2svg in QuickJS,
JavaScriptCore, and the browser, staff notation on screen and in the PDF, the editor's Sheet music button, the demo
melody, parallel screenshot rendering, and the regex brace check). Lean budget: three Opus area reviewers (engine and
platforms; notation UI, print, and editor; chordpro, tools, and CI) and a Sonnet check of the changed `CLAUDE.md` files.
The user's own commits (`44c1ca1f3`, `2cc4ff351..acc572580`) landed during the review; the plans name code, not lines.

## Headlines

- **A tune could run its own JavaScript** (`%%beginjs`): on the web in the page, beside OPFS and the sync token; on the
  other platforms it could wedge or poison the engine for the session. Fixed during the review (`8a507d1f8`).
- **A native library that fails to load crashed the app** on opening the demo song instead of showing the text. Fixed
  (`fd69abf1c`).
- **Opening a song with a tune animates its sections during the enter transition** (reported by the user): the staff
  arrives after the first layout and the page springs everything below it (06).
- **The editor's Transpose leaves the staff in the old key** beside chords in the new one (03).
- A hostile tune can still cost seconds to minutes and unbounded memory (01); notation is painted in composition on the
  main thread (05); a tune flips between text and staff on every typing pause in the Split preview (07).

## Fixed during the review

`cfcf08150..2fbe5b369` (14, the user's own commits in between excluded):

- `9f02e9e35` Name an ABC run's toggle Sheet music in the song view model docs.
- `263195bc0` Say in the print docs that only an ABC tune that did not render prints as text.
- `9aa62d3a9` Accept the braces of a \p{…}, \N{…}, or \x{…} escape in the regular expression check.
- `392fdc034` Check a pattern made with .toPattern() or with .toRegex() after .trimIndent() for braces Android refuses.
- `7f4c67589` End a screenshot run that hits an error instead of leaving its process, and a parallel run waiting on it,
  hanging.
- `55837654c` Stop a parallel screenshot run's processes when the run is stopped.
- `115befd65` Start a parallel screenshot run's processes without the debugger's agent.
- `242f607a2` Say Sheet music, not Melody, in the demo song's note on its tune.
- `8a507d1f8` Ignore the script a %%beginjs block of a tune carries instead of running it.
- `fd69abf1c` Show a tune as its text instead of crashing when the notation engine's native library cannot be loaded.
- `b475b8834` Remove the unused fallback notation engine.
- `0e61ac1d6` Say in the notation renderer's contract that the web renders on the main thread.
- `e08ac68f6` Keep a character outside the Basic Multilingual Plane whole in the notation scripts bundled for iOS and
  the web.
- `2fbe5b369` Stop a parallel screenshot run quietly when it is stopped while waiting for its processes.

A fresh read of the range found no fix breaking a caller (it checked the `%%beginjs` guard against every abc2svg module
hook in node); its one loose end is the last commit.

## Index

| # | Plan | Severity | Lane |
|---|------|----------|------|
| 01 | Bound what one tune may cost the notation engine | medium | E |
| 02 | Ship the desktop's QuickJS library in the app image | medium | E |
| 03 | Move an `abc` block's tunes with the editor's transposition | medium | N |
| 04 | Start an empty Sheet music block with a tune header | low | C |
| 05 | Paint notation off the main thread, once per rendering | medium | N |
| 06 | Cache renderings, never narrate a first rendering, warm the engine | medium | N |
| 07 | Keep an edited tune's staff until its new one arrives | medium | N |
| 08 | Engrave a repeated tune in the key it stands in | low | N |
| 09 | Describe sheet music to screen readers | medium | N |
| 10 | Time sheet music by its bars in auto-scroll | medium | N |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|------|------|-----------------|-------------|
| E | engine and desktop packaging | 01, 02 | `abc/implementation/**`, `app/desktop/**` |
| C | editor toolbar | 04 | `ui/screens/songEditor/toolbar/**`, `ui/screens/songEditor/LoadedSongEditor.kt` |
| N | notation UI, print, chordpro | 05, 06, 07, 08, 09, 10, 03 | `ui/notation/**`, `ui/songView/**`, `ui/print/**`, `ui/screens/songDetails/**`, `ui/screens/songEditor/SongPreview.kt` and `CLAUDE.md`, `ui/CampfireApp.kt`, the seven `strings.xml`, `chordpro/**`, `data/model/**/Song.kt`, `data/source/local/implementation/**/SongMappers*`, `tools/screenshots/src/test/**` |

(`ui/` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, tests beside it.)

**Merge order E, C, N**: N is the longest and touches the most shared files, so it goes last; E and C are independent.

**Shared files**: `abc/implementation/CLAUDE.md` (01 and 06's warm-up line) is merged word by word, both sentences
kept. If 03's optional stepper step touches `LoadedSongEditor.kt`, its cherry-pick keeps 04's Sheet music header too.

## Challenge

Every plan was challenged by a fresh agent that did not write it: 05 sound; 01, 02, 03, 04, 06, 07, 08, 09, and 10
amended, none dropped. The amendments that mattered: 01 also caps voices and systems (time grows with voices times
systems, not text) and treats a null result as a failure; 02 hooks Compose's tasks with `configureEach`; 03 writes a
plain sum (`rem 12` dropped a staff by an octave); 04 reads keys as `:chordpro` does (`a-moll`, modes); 06's cache is
bounded by words too; 08 keeps the offset out of a chorus recall; 09 reads verses in order; 10 counts bars, not bar
lines. After the challenge, 06 was extended with the user's report (sections springing on a cold open), and that
extension was challenged again and amended (the width at rest as `maxOf(settledWidth, maxWidth)`, no arrival silencing
a reader's own change, a 2 s warm-up delay instead of an idle-callback expect/actual).

## Decisions

Answered by the user on 2026-10-28, every one as recommended.

- **01** — the engine's limits: 50,000 characters, 32 voices, 2,000 systems (all platforms), and in QuickJS 128 MB
  and 10 s, restarting the engine after any failed render. *Recommended: as written.*
- **02** — the library in Compose's app resources (`common/`), loaded through `com.dokar.quickjs.library.path`, and the
  jar's `jni/` stripped from release builds. *Recommended: as written.*
- **03** — write each tune's own `%%transpose` (vs rewriting the ABC, or leaving the staff in the old key), as a plain
  sum folded only past ±36; and enable the stepper for a song whose only music is an `abc` block. *Recommended: yes,
  plain sum, yes.*
- **04** — the header `X:1`, the song's meter, `L:1/8`, `K:` from its key (else `K:C`), caret below; the keyboard stays
  down as after Tab. *Recommended: both.*
- **06** — warm the engine in the background: when the library holds a tune (web at idle) / at every launch / never.
  2 s after the library is read. *Recommended: when the library holds a tune, the web included.*
- **07** — a stand-in pairs tunes that left with tunes that came in order, only when their counts match. *Recommended.*
- **08** — key each tune by its text and its `{transpose}` offset (vs keep "a tune written twice is moved as it first
  stands"). *Recommended: key by offset.*
- **09** — lead with "Sheet music" only when the tune names itself nothing. *Recommended.*

## Checked and found solid

Engine threading (QuickJS's single daemon thread, JavaScriptCore's own context, the web's synchronous render), a
cancelled queued render, start-up failure and retry, module loading only by abc2svg's names, the tune passed as a value
never interpolated into code, hostile tunes without a script (huge scales, 20,000 bars, 3,000 slurs: all finished),
Android ABIs and 16 KB alignment, ProGuard keeps, credits (abc2svg, its font, QuickJS, quickjs-kt), the strings in all
seven languages, notation never wider than a column, lyrics-only and hidden chords dropping notation, transposition and
capo in the chord names, PDF rows per staff system, the sharding arithmetic of the parallel screenshot run, and
`check_regexes.py` on its own corpus.

## Dropped after verification

- Cap the parallel screenshot run's default by memory: the tool runs on the 24 GB development Mac, and
  `--parallel` overrides it.
- `check_regexes.py` missing a pattern built by concatenation in parentheses or held in a `val`: documented limits of
  the script, and none occurs in the repository.
- The What's new text still says sheet music "is not drawn yet": rewritten by prepare-release for the next version.
