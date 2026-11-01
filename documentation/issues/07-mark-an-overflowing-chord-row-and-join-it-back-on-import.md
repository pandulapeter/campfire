<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Mark the chord rows a line's chords overflow onto in the PDF, and join them back to their line on import

**Challenged:** amended — only rows of chords alone are marked (a chord line stacked over a lyric stays unmarked: joined
to the line before, it would take the chords off the lyric the print hangs them over, which HEAD merges them into); the
import's append comes before the merge with an unmarked lyric under it; the reliance on plan 14 for "a marked row is a
chord row whatever follows" and the right-to-left limit are stated; tests adjusted.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLyricRows.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`

## Problem

Where a line's chords do not fit the column, `PrintLyricRows.layOut` moves them onto more chord rows, and none of
those rows is marked as part of the line, so the import reads each as a line of its own. Two forms, both probed at HEAD
with `PdfRoundTripTest`'s pipeline:

1. **A chord-only fragment after the lyric.** The padded lyric wraps, and its last fragment is nothing but padding with
   the chords that hang past the words over it (`isChordsOnly`). Its chord part is marked only through
   `isContinuation`, which is false for a blank fragment:

   ```kotlin
   val isContinuation = fragmentIndex > 0 && !fragment.isPrintBlank() && ( … )
   ```

   `I [Cmaj7(#11)] [Bbmaj9/D] [Gm7b5]` in 3 columns comes back as `I [Cmaj7(#11)] [Bbmaj9/D]` and a lyric line
   `Gm7♭5` (one chord alone is no chord row without corroboration, so it stays text, flat sign and all); in 4 columns
   as `I [Cmaj7(#11)]` and `[Bbmaj9/D] [Gm7b5]`.
2. **Chord lines stacked within one row.** A chord that does not fit beside the one before it starts the next chord
   line (`chordY += chordLineHeight`, the `?: run { … }` branch, and each further line of a chord name wider than the
   column); the parts on those lines are never marked. The chord-only line
   `[Cmaj7(#11)] [Bbmaj9/D] [Gm7b5] [Am7add11]` followed by the line `Hello world`, in 4 columns, comes back as a lyric
   `Cmaj7(♯11)`, a line `[Bbmaj9/D] [Gm7b5]`, and `[Am7add11]Hello world`: the last chord set into the next line's
   first word.

(The finding's other example, `[Am7add11]d` → `[Am]Am7ad[Bm]d11 [Cm]d`, is finding 14's single-letter lyric `a b c d`
read as chords; with real words, `[Cmaj7]la [G/B]lo [Dsus4]li [Am7add11]lu` round-trips in 3 columns.)

## Fix

Print (`PrintLyricRows.layOut`), for a row that is chords alone (`val isChordsOnly = chords.isNotEmpty() &&
fragment.isPrintBlank()`, worked out before the chords are placed, the same test the row's `isChordsOnly` makes after):

- A chord-only fragment after the first (`isChordsOnly && fragmentIndex > 0`) marks its first chord part as a
  continuation, as the chord row of a wrapped lyric is.
- In a chords-only row, every chord line after the first (each time `chordY` grows, in the `?: run` branch and for
  `nameIndex > 0`) marks its first part as a continuation. The parts keep their coordinates; only the selectable text
  gains the word joiner.

A row with a lyric under it keeps its stacked chord lines unmarked, as today. There the lyric is printed under the last
chord line, and HEAD merges that line into it by its chords' places, which hang over the lyric's letters (only the one
that overflowed is moved to the starting edge); marked, the line would be appended to the line written before and take
those chords off the lyric. (Its first chord line still comes back on a line of its own above the lyric, as at HEAD; not
this plan's.)

Import (`ChordSheetConverter`):

- `classify`: a candidate of one chord or more is `Kind.CHORD` where the next line is a chord row marked as a
  continuation (`lines.getOrNull(index + 1)?.let { it.isContinuation && candidates[index + 1] != null } == true`), and a
  marked row whose candidate is all chords and notes is a chord row whatever follows (`allowsNotesOnly` already lets it
  hold notes alone). This rule is safe only because plan 14 (landed before this) takes an export's words for chords only
  in the chords' type: without it a wrapped lyric's marked rest that happens to read as chords (`a b`, `Am I`… `A`)
  would become a chord row.
- `writeBody`'s `Kind.CHORD` branch, **first**, before the merge with a lyric under it: a chord row marked as a
  continuation whose next line is not a lyric marked as one (which would make it the chord row of a wrapped lyric's
  rest, merged as today) is appended to the line written before it,
  `addLyric(bracketed(candidates[index]!!, chordRow(line)), continues = true, isHardCut = false)` (plan 06's
  signature), so `I [Cmaj7(#11)] [Bbmaj9/D]` + `[Gm7b5]` is one line, and the stacked rows of the chord-only line are one
  line again. Checked first, the last of them is never merged into the unmarked `Hello world` under it.

A chord put on an overflow line lost its column in the print already; joined back it stands after the chords before it,
at the end of the line, which is where the print hung it. Out of scope, and not this review's: a chord-only line
followed by a line with no chords (`[G] [C]` then `Hello world`) is merged into `[G]H[C]ello world` in any column count,
since nothing tells the two rows apart; this plan does not change that.

Right to left: the import reads a row's mark off its first span by position (`render`, spans in x order from
`:data:formats`), while the print puts it on the first part placed, which in a right-to-left row is the rightmost; a
right-to-left row of two chords or more therefore reads unmarked, as the chord rows of its wrapped lyrics already do,
and comes back as at HEAD. Not fixed here.

Docs: `ui/print/CLAUDE.md`, the sentence "An overflowing chord starts the next chord line at the row's starting edge"
gains "in a row of chords alone marked as a continuation, as is a chord-only row a wrapped lyric ends in";
`convert/CLAUDE.md`, the continuation sentence gains "a marked chord row with no marked lyric under it is the rest of
the line above".

Plan 06 changes `addLyric`'s signature and the same `isContinuation` expression in `PrintLyricRows`, and plan 14 the
chord test this relies on; land both first.

## Tests

- `PrintLayoutTest`: with the arithmetic measure and a narrow column, a chord-only line whose chords overflow onto a
  second chord line, and a line ending in a chord-only fragment; assert the first part of each further chord line and
  of the chord-only row has `isContinuation`; and a lyric line whose chords overflow onto a second chord line keeps no
  chord part marked.
- `ChordSheetConverterTest`: a Campfire export sheet holding the bold chord row `Cmaj7(♯11)`, then the marked rows
  `⁠Bbmaj9/D Gm7♭5` and `⁠Am7add11`, then the lyric `Hello world`; assert
  `[Cmaj7(#11)] [Bbmaj9/D] [Gm7b5] [Am7add11]` on one line and `Hello world` unchanged on the next; and a wrapped
  lyric whose marked rest is regular-weight `a b` (no chord row over it) stays the lyric `… a b`.
- `PdfRoundTripTest`: `I [Cmaj7(#11)] [Bbmaj9/D] [Gm7b5]` and `[Cmaj7(#11)] [Bbmaj9/D] [Gm7b5] [Am7add11]` /
  `Hello world` printed in 1 to 4 columns, compared through `summary` as the corpus test does. Fails before the fix
  in 3 and 4 columns.
