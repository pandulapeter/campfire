<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan 03: repeat marks on a chord row

**Status:** first draft, 2026-10-26. Nothing implemented. From a user's feedback (`TO_DO.md`: "Support (play this X
times), right now it looks like it treats x4 as a lyric and throws it underneath").

**Goal:** a line of chords written with a repeat count or bar lines, `[G] [C] [D] x4` or `[Am] | [F] | x2`, is drawn
as one row of chords with the count at its end, as the player reads it on paper. Not a row of chords over a lyric row
saying `x4`.

No file format change and no text edit: the file keeps `[G] [C] [D] x4` byte for byte. Nothing is unrolled. Campfire
plays nothing back, and writing a line out four times would cost pages (pedal reading wants the fewest).

## What exists today

- ChordPro has no repeat syntax outside grids. Its answer is an annotation, `[*x4]`, which Campfire already draws on
  the chord row in the annotation style and drops with the chords in lyrics-only mode. That works today as a
  workaround, but nobody types it.
- The parser reads `[G] [C] [D] x4` as `ChordProLine.Lyrics(text = "   x4", chords = G, C, D)`. So:
  - the viewer gives it a lyric row under the chords (`SongLineWithChords`: a line takes two rows unless
    `text.isBlank()`);
  - lyrics-only mode keeps a lyric line saying `x4` (`prepareForDisplay` in `SongRenderSections.kt` keeps every
    `Lyrics` whose text is not blank), so an intro of chord rows ending in `x4` shows a bare "x4" under its heading;
  - the PDF prints the same two rows (`PrintRows`);
  - `firstLyric`, which names an unnamed section's pill for screen readers, can come out as `x4`.
- The converter writes these lines itself. `am H7 | % x2 N.C.` becomes `[am] [H7] | % x2 N.C.`
  (`ChordSheetConverterTest`), and `G D (let ring)` with no lyric under it becomes `[G] [D] (let ring)`. So every
  pasted or imported chart with a repeat count has this bug, not only hand-typed ones.
- Repeat marks are already understood in four places, each with its own regex: `ChordSheetHeadings.repeat`
  (converter: chord-row tokens and `Chorus x2` headings), grid margins (`ChordProTokens.isMarginCell`), and two in
  tabs (`ChordProTabTransposer.repeatCountRegex`, `ChordProTabWrapper.repeatCountRegex`). The tab ones accept bare
  digits and one-sided forms on purpose (a fret number context) and stay as they are.
- Section-level repeats already read right: `{soc: Chorus x2}`, `{chorus: x2}`, `{c: x2}`, and `Chorus (x2)` headings
  on import are labels and comments. Grids and tabs keep `x2` in their margins.

## Decisions

1. **Recognized in the parser, as a kind of annotation.** `ChordProLine.Lyrics.Chord` gets `isMark: Boolean = false`.
   A mark is always `isAnnotation = true` too, so every reader that already skips annotations (the song's chord
   list, diagrams, degrees, the inferred key, scales, transposition of the model) skips marks without a change. Only
   what differs from an annotation reads `isMark`: it is shown with the chords, not with the comments, and it is
   written back as bare text.
2. **Only on a chord row.** A line counts if it has at least one bracket and every word of its text outside the
   brackets is a mark. Then those words become mark chords at their offsets, and the text becomes blank. A sung line
   that ends in `x2` (`[G]Row, row, [C]row x2`) is left alone: the count belongs to the words, and lyrics-only mode
   needs it.
3. **A line with nothing but a mark (`x4` alone) is left alone.** It is drawn as a lyric line saying `x4`, which reads
   correctly ("the line above, four times") and must survive lyrics-only mode.
4. **What a mark is**, defined once in `syntax/` (a new `ChordProRowMarks`, the converter's `ChordSheetHeadings.repeat`
   moved there and reused):
   - repeat counts: `x4`, `X4`, `4x`, `×4`, `4×`, each optionally in parentheses (`(x4)`, `(4x)`);
   - bar lines and repeat signs: `|`, `||`, `|:`, `:|`, `:|:`, `%`, `%%`, `/`, `-`;
   - `N.C.` and `NC` written as text. A bracketed `[N.C.]` stays a chord, as it is today.
   Word lists (`twice`, `repeat`, `kétszer`, `(let ring)`) are **not** marks: `[G] [C] (oh yeah)` is as likely a
   sung backing line. The converter writes its playing notes as annotations instead (step 4), and a user can always
   write `[*twice]`.
5. **No migration, no setting.** The model changes, the file doesn't. A chord row that took two rows now takes one,
   so some songs' page breaks move. That's the point.

## Steps

### 1. `ChordProRowMarks` and the parser

- `syntax/ChordProRowMarks.kt`: `isMark(word)` and `repeatCount` (the regex moved from `ChordSheetHeadings`, which
  then imports it; its heading pattern embeds `repeat.pattern`, so keep the pattern text identical).
- Words are split with `Char.isWhitespace` (pasted charts carry non-breaking spaces), never `\s`.
- Where `ChordProSectionBuilder` builds a `ChordProLine.Lyrics`, after markup is taken out (`ChordProMarkup`): if the
  line qualifies (decision 2), replace each mark word with
  `Chord(position = wordStart, name = word, isAnnotation = true, isMark = true)`, merged into `chords` in position
  order. A chord and a mark at the same offset (`[D]x4`) keep chord first, then mark. Set `text` to the blank
  remainder, keeping its length, so the chords' offsets still mean the same columns, and drop `formatting` (a mark is
  never formatted).
- Not inside delegated environments, tabs, or grids (they never build `Lyrics` chords anyway), and not in comments.
  Chorus recalls copy the lines, marks included.
- The parser stays total: no new failure path.
- Tests (`ChordProParserTest`): `[G] [C] [D] x4`; every count form; `(x4)` and `4×`; bars and `%` between chords;
  `[D]x4` (shared offset); `x2 [G] [C]` (mark before the chords); non-breaking spaces; `<i>x4</i>` on a chord row;
  `[*softly] x2` (only annotations plus a mark: still a row); not marks: `[G]Row [C]row x2`, a lone `x4`,
  `[G] [C] (oh yeah)`, `[G] [C] x4 la`, `[G]x[C]4`, `x2000` stays a mark only if the shared regex says so (pin it
  either way); a recalled chorus keeps its marks.
- Round trip: the test-only `ChordProSerializer` writes a mark as its bare text at its offset (never `[*x4]`), so
  `parse(serialize(parse(x))) == parse(x)` holds and the file's form is what comes back.

### 2. The viewer

- `SongLineWithChords` already draws annotations on the chord row in `annotationColor` with the annotation style
  (`SongTextMeasurements.chordLayout`). Marks go through the same path, and with `text` blank the line is one row.
- Check that two chords at one offset (`[D]x4`) don't overlap. If chord-only lines already push a chord past the one
  before (`[G][C]`), nothing is needed; if not, fix it there for both cases.
- A padded line may break only between two chords at a word boundary (`padLyricsToFitChords`): a mark must never wrap
  onto a row of its own away from the chord before it. Treat the gap before a mark as unbreakable.
- RTL lines: marks are anchored by offset like chords, so they hang the same way. Add an RTL case to the existing RTL
  test if there is one.
- Content description: the line's ChordPro form must say `x4`, not `[*x4]`. Write a mark as its bare text where the
  description is built.
- Lyrics-only mode: nothing to change. `prepareForDisplay` drops a `Lyrics` whose text is blank, so a chord row with
  a mark goes with the other chord rows.
- **The section rule (checked, no change):** the rule that drops a whole section, heading included, in lyrics-only
  mode (`toRenderSections`, line 79 of `SongRenderSections.kt`: `needsChords() || isBlank()`) does not apply to chord
  rows. `isBlank()` is false for a line with chords, so a section of chord rows keeps its heading. That rule exists for
  tab and grid sections only, and the bare heading over chord rows is intended (a singer must see where the
  instrumental parts are). Marks don't change that: an intro of `[G] [C] [D] x4` keeps its "Intro" pill and loses its
  stray "x4". Don't touch the rule. Add a test pinning that such a section keeps its heading and shows no line.
- `firstLyric` skips a line whose text is blank, so a pill is no longer named `x4`.

### 3. The PDF

- `PrintRows` filters annotations by `options.showComments` (line 165). A mark follows `options.showChords` instead:
  hiding comments must not strip `x4` off a printed chord row. With chords off, the line has no text and goes like
  any chord row.
- Test in the print tests: a chord row with a mark prints on one row, keeps the mark with comments off, and is gone
  with chords off.

### 4. The converter

- Nothing to change for counts and bars: `[am] [H7] | % x2 N.C.` now parses as one chord row.
- A chord row's playing note with no lyric under it (`G D (let ring)`) is written as an annotation, `[G] [D] [*let
  ring]`, so it lands on the chord row too (decision 4 keeps words out of `isMark`). A note that goes after a lyric
  line stays as it is (`ChordSheetConverterTest` line 533). Escape or drop a `]` inside the note.
- Tests: the `(let ring)` row over nothing; the existing heading and `x2` tests still pass with the moved regex.

### 5. The editor's highlighter

- On a line the parser would turn into a chord row, colour the mark words as `TokenType.ANNOTATION`, so the editor
  shows what the viewer will. Use `ChordProRowMarks`, not a copy of the rule. The line scanner is resumable per line,
  and the rule only reads its own line, so incremental highlighting is unaffected.
- Test in `ChordProHighlighterTest`: `[G] [C] x4` gives ANNOTATION for `x4`; `[G]Row [C]row x2` keeps it as lyrics.

### 6. Docs

- `chordpro/CLAUDE.md`, "Model and parser": one bullet. "A chord row's repeat counts and bar lines are marks
  (`ChordProRowMarks`): annotations shown with the chords, written back as text; a sung line keeps them as lyrics."
- `ui/songView/CLAUDE.md` only if step 2 needed a layout rule (an unbreakable gap before a mark).
- Remove the line from `TO_DO.md` when this lands.

## Corner cases, all decided above

| Written | Read as |
| --- | --- |
| `[G] [C] [D] x4` | one row: G C D x4 |
| `[G] [C] (x4)` / `4x` / `×4` / `X4` | one row, the mark as written |
| `[Am] \| [F] \| x2` | one row, bars and count in their columns |
| `[am] [H7] \| % x2 N.C.` (converter output) | one row |
| `[D]x4` | one row: D, then x4 beside it |
| `x2 [G] [C]` | one row, mark first |
| `[G]Row, row, [C]row x2` | unchanged: sung line ending in x2 |
| `x4` alone | unchanged: a lyric line, kept in lyrics-only mode |
| `[G] [C] (oh yeah)` | unchanged: may be sung |
| `[G] [C] x4 la` | unchanged: a lyric is present |
| `[G]x[C]4` | unchanged: a chord inside the word |
| `G D (let ring)` imported, no lyric below | `[G] [D] [*let ring]`: one row |
| `{c: x2}`, `{soc: Chorus x2}`, grid `:\| x2`, tab `x3` | unchanged: already right |
| lyrics-only mode | the whole chord row goes, mark included |
| PDF, comments off | mark stays with its chords |
| transpose, notation, Nashville | mark untouched (never a chord) |

## Out of scope

- Unrolling repeats, or a "play N times" counter while playing.
- Word repeat notes (`twice`, `repeat`, other languages) as marks. `[*twice]` is the way.
- Unifying the tab regexes with `ChordProRowMarks`: their contexts differ.
