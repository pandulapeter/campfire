<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# 52nd review

Reviewed at `22d0af745` on `master` (2026-11-01); plans checked against `1230567a2`. Angle: regression since the 51st
review, plus the features new since the `5.2.1` tag judged as a player would (capo and chord names, the chord tables,
random picks, TAB/ABC highlighting, the PDF round trip, shared links, the release scripts). Thorough: four Opus area
reviewers, a live run (2,016 PDF round trips over 8 languages × 1–4 columns × both capo modes; random picks with 2,002
songs), and a Sonnet docs check.

## Headlines

- A long song printed in two columns comes back from its PDF with verses scrambled (01): a centred page number pulls
  the gutter onto a lyric.
- A capo chart can still be read in its sounding key, and Campfire's own default PDF of one comes back a whole step high
  (02a, 02b).
- The PDF round trip loses words, titles, artists, capos, and chords in narrow columns and other languages (03–07,
  09–14, 19). With every plan applied, the challenge's probe turned 349 failing round trips into passes and broke none.
- The random picks swap up to five times on screen during a cold launch of a library over 64 songs (15).
- A transposed song leaves some real tab chord rows (`G / / / | D`, `G D 2x`) untransposed while drawing them as chords
  (18).

## Fixed during the review

- `0dc6645b1` Leave the store badges out of the stores' what's new text when a release's notes are empty.
- `e7c1c3d87` Keep the question mark and colon of the French random picks and chord names questions on the line of their last word.
- `5f88b5c2e` Import a chord sheet whose capo, marked as sounding, names a number too long for a fret, instead of failing the whole import.
- `60c446517` Remove a stray copy of a comment in the chord sheet converter.
- `97a59c574` Say in the release docs that the stand-in notes leave out the store badges too (found by the regression read).

## Index

| # | Plan | Severity | Lane |
|---|---|---|---|
| 01 | Pick the gutter a short page number crosses over one a lyric crosses | high | F |
| 02a | Import the shapes key a printed capo names as the song's key | medium | R |
| 02b | Read a capo chart in its shapes key where the shapes' relative ranks first | medium | K |
| 03 | Read a Campfire export without splitting its lines into header parts | medium | R |
| 04 | Keep a Campfire export's title whole | low | R |
| 05 | Read every row of an export's heading under the title as its artist | low | R |
| 06 | Mark a hard cut in the PDF and join it back without a space | medium | R |
| 07 | Mark an overflowing chord row and join it back on import | medium | R |
| 09 | Keep a wrapped right-to-left row's chord on its letter | low | R |
| 10 | Judge an export's side by the edge most of its lines share | low | R |
| 11 | Take an export's lyric size from the lines under its chords | low | R |
| 12 | Read a printed key change in place, spell each part of a sounding print by its key | low | R |
| 13 | Leave a heading or footer crossing the gutter out of the fair share | low | F |
| 14 | Read only chords printed in the chords' type as chords in an export | low | R |
| 15 | Draw the random picks only from the whole library | medium | S |
| 16 | Keep the random picks across a restored process | low | S |
| 18 | Colour a tab line in the editor as the transposition reads it | medium | E |
| 19 | Drop a page count at the foot of a page above the margin band | low | F |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|---|---|---|---|
| F | PDF text extraction (`data/formats`) | 01 → 13 → 19 | `PdfLineLayout.kt`, `PdfTextExtractor.kt`, `PdfLineLayoutTest.kt`, `data/formats/CLAUDE.md` |
| K | Song keys (`chordpro/scales`) | 02b | `Harmony.kt`, `SongKeys.kt`, `SongScaleGroups.kt`, their tests, `scales/CLAUDE.md` |
| E | Tab chord rows (`chordpro/tabs`, `chordpro/edit`) | 18 | `ChordProTabTransposer.kt`, `ChordProHighlighter.kt`, their tests, `edit/CLAUDE.md`, `tabs/CLAUDE.md` |
| S | Songs list (`presentation/.../ui/state/songs`, `ui/launch`) | 15 → 16 | `SongListState.kt`, `SongGroups.kt`, `LaunchState.kt`, `RandomPicksDraw.kt`, `CampfireViewModel.kt`, `SavedStateStore.kt`, their tests, `ui/launch/CLAUDE.md`, `ui/screens/songs/CLAUDE.md` |
| R | PDF print and import (`chordpro/convert`, `presentation/.../ui/print`) | 02a → 06 → 11 → 14 → 07 → 04 → 05 → 03 → 09 → 10 → 12 | `ChordSheetConverter.kt`, `ChordSheetCampfireLayout.kt`, `ChordSheetConverterTest.kt`, `ChordSheetCampfireLayoutTest.kt`, `convert/CLAUDE.md`, `PrintTextWrapping.kt`, `PrintLyricRows.kt`, `PrintRows.kt`, `PrintLayout.kt`, `PrintDocument.kt`, `PrintRenderer.kt`, `PrintLayoutTest.kt`, `PrintLayoutRightToLeftTest.kt`, `PdfRoundTripTest.kt`, `ui/print/CLAUDE.md` |

Lane R's order is load-bearing: 02a before 06 and 12 (`extractHeader`); 06 carries 09(b)'s print fix and changes
`addLyric`'s signature, which 07 and 09 build on; 11 exposes `knownBodySize`, which 14 needs; 14 before 07 (a marked
chord row is safe only once chords must be in the chords' type); 06 before 04 and 05 (their title and artist joins are
hard-cut aware); 12 relies on 06's `joinWrappedDetails` and `detailRows`. Lane F: 01 before 19 (01 makes a centred
footer its own line, which 19 drops).

**Merge order:** F, K, E, S, R — the small independent lanes first, R last since it is the longest and touches the
most shared files. No file is shared between lanes; every shared `CLAUDE.md`, test, and `strings.xml` is inside one
lane. Start R first (it is the longest), with at most one other lane building beside it.

## Decisions (taken 2026-11-01, all as recommended)

- **02a:** the printed shapes marker sets `{key}` wherever it is read (pasted text and other sheets too, as the
  sounding marker does), not only in Campfire PDFs.
- **02b:** the wider rule reaches the song's key (and degrees, the whole-song fit) only, not each section's scale
  suggestions.
- **15:** option A as amended: the launch screen waits for the whole read only while the picks would lead a visible
  songs list.
- **18:** option 1 with the transposer widened: viewer, transposition, chord list, and editor agree; the
  `chordpro/edit/CLAUDE.md` rule changes. Option 2 (the viewer refusing count lines outside numberings) stays a
  follow-up.

## Challenge

Every plan was challenged by a fresh agent: 6 sound (01, 04, 11, 13, 16, 19), 12 amended (02a, 02b, 03, 05, 06, 07,
09, 10, 12, 14, 15, 18), none dropped. The biggest amendments: 02b limited to the song's key (applied to sections it
misread correctly keyed songs); 06 looks past every invisible layout mark and absorbs 09(b); 07 marks only rows that
are chords alone; 12 export-only and reads a wrapped key change; 14 splits `knownBodySize` from 11's fallback; 15 no
hold with Songs off or not on top, unread preferences count as awaited; 18 widens the transposer's markers.

## Checked and found solid

- ABC highlighting and `%%begin…` blocks share the import's walk; CRLF and UTF-16 offsets; highlighting cost linear.
- Chord-table search orders rooted shapes first with a total comparator; picks and `{define}` stored as frets, never
  positions; none of the 194 replaced rows was released.
- Microsoft Store bare languages consistent across manifest, scripts, and tests.
- Capo naming arithmetic across wrap-around; every surface names by the same transposition; no file writes from capo
  or name changes; the ask-once dialog never shows twice.
- Random picks: from the filtered sections only; hidden in search and selection; per-file ranks; 5-minute and 1-hour
  rules; seed not synced; strings in all eight languages; Shuffle fully removed; `CardDetails` move a pure refactor.
- Shared-link rows open only the in-app export screen.
- PDF labels of every language match `strings.xml`; Hungarian handled; `joinWrappedDetails` acts only on export rows.
- `isCampfireExport` is an exact `/Producer (Campfire)` match, written by every release since 5.0.0.
- The docs check found no false claim in the 26 `CLAUDE.md` files near the changed paths.

## Dropped after verification

- **08** (a Hebrew song's bare tab swallowed by the verse above): not right-to-left specific; an unnamed paragraph is
  printed unlabelled by design and rejoins the section above, losing nothing.
- **17** (ask-once dialog on an untouched capo): the setlist's capo override is computed on the main thread in the
  same step that delivers the song, on every platform, so the opened capo already includes it.
- The live run's "every card reads 120 BPM": unverified, the generated songs were deleted before it could be checked.
- Tab systems re-stacked rather than rejoined: documented in `convert/CLAUDE.md`.

## Measurements

- PDF round trip at `22d0af745`: 2,016 round trips, 277 failures, 36 s (en/he 5–13 per column count, it up to 15).
- Random picks, 2,002 songs, desktop UI harness: ready 428 ms warm JVM, ~1.6 s cold (2 songs: 62 ms); Refresh 48–65 ms.
- 02b: on 349 songs × 6 capos, shapes-keyed charts misread 19 → the same 19; sounding-keyed read right 1,403 → 1,449
  of 2,094; labelled set 66 → 69 of 70.

## Not planned, noted

- In 4 columns a chord row opening with an annotation (`Chorus: [D7]Home…`) loses the annotation (the row overflows).
- A chord-only line over a chordless lyric (`[G] [C]` / `Hello world`) imports as `[G]H[C]ello world` (old ambiguity).
- The existing continuation mark on right-to-left chord rows of two or more chords is not read back.
- `{chorus: Last chorus, slowing down}` recall comes back as a verse where it starts a page (the documented label rule).
