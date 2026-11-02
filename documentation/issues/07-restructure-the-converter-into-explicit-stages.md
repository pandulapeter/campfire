# Restructure the chord-sheet converter into explicit stages, and remove the Campfire-specific rules once the general reader scores as well on Campfire exports

**Kind:** structure · **Severity:** medium · **Platforms:** all · **Priority:** P2 · **Effort:** L (9 steps)
**Gated on:** plan 05 landed and its corpus populated (the owner's step); plan 06 steps 1–2 landed (paths below are
after the move to `chordsheet/`; before it, read `chordpro/` for `chordsheet/`).
**Files:** `chordsheet/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/` — `ChordSheetConverter.kt`,
`ChordSheetSongSplit.kt`, `ChordSheetLook.kt`, `ChordSheetCampfireLayout.kt` (deleted in step 9), `ChordSheet.kt`,
`CLAUDE.md`, and new `ChordSheetProfile.kt`, `LineFeatures.kt`, `LineKinds.kt`, `SongStructure.kt`, `SongWriter.kt`;
tests `ChordSheetConverterTest.kt`, `ChordSheetCampfireLayoutTest.kt` (deleted in step 8);
steps 8–9 also (**shared with other lanes**) `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRenderer.kt`,
`presentation/.../ui/print/PrintPdfWriter.kt`, `presentation/src/desktopTest/.../ui/print/PdfRoundTripTest.kt`,
`data/formats/src/commonMain/.../document/PdfTextExtractor.kt`, `PdfLineLayout.kt`, `PdfTextExtractorTest.kt`,
`data/formats/CLAUDE.md`, `data/source/local/implementation/src/desktopTest/.../document/DocumentGoldenTest.kt` and its
`campfire*.pdf`/`.cho` fixtures, `presentation/.../ui/print/CLAUDE.md` (it names the marks).

**Challenged:** amended — the owner's three decisions folded in (evidence resolved by neighbours; capo and details-row
wording kept as general vocabulary; goldens regenerated and `PdfRoundTripTest` cut to one single-column test); old
step 8 would have committed red tests (the export tests and goldens pin the rules it switches off), so switching off now
carries those test changes in the same commit and step 9 deletes only dead code; corpus runs in a worktree pass the
property; references to plan 06 step 3 removed.

## Problem

`ChordSheetConverter.kt` is 1,525 lines in which every decision is taken in the order the code happens to run:

- **Classification is one ordered first-match `when`** (`classify`), whose first branches are Campfire's roles and whose
  chord branch is a single boolean expression of eleven clauses peeking at raw neighbour text:

  ```kotlin
  line.role == ChordSheetCampfireLayout.Role.LABEL -> Kind.SECTION
  …
  index < 15 && (metadata(line.text)?.second?.isNotBlank() == true || labelledRow(line.text, isHeader = true) != null) ||
  …
  candidate != null && (candidate.isEmpty() || … ||
      lines.getOrNull(index + 1)?.let { it.text.isNotBlank() && chordTokens(it) == null && !headings[index + 1] && metadata(it.text) == null } == true) -> Kind.CHORD
  ```

  `metadata(text)`, `ChordSheetHeadings.parse`, and `chordRow` are re-run for the same line by `classify`,
  `extractHeader`, `extractTitle`, and `writeBody`.
- **The body is written by a state machine that re-decides structure while emitting**: `writeBody` and its mutable
  `BodyWriter` (≈246 lines) open and close choruses by look, recall choruses, follow continuations, and close tabs and
  grids as they print.
- **Thresholds are spelled where used**: `median * 1.2` twice in the converter and four times in
  `ChordSheetSongSplit`; `size * 0.05` in the converter (twice), `ChordSheetLook`, and `ChordSheetSongSplit`; "the
  header" is `index < 15` twice and `joined.size <= 15` once. There are three different medians: the document's
  (`convert`, blank spans out, passed to the split) and two "every other line" medians recomputed for each candidate in
  `isTitle` and `isTitleContinuation` (quadratic in a song's lines).
- **Campfire's export is a second reader inside the first**: `Rendered` has 14 fields, 7 of them export state (`role`,
  `isCampfire`, `bodySize`, `isContinuation`, `isHardCut`, `isContinuedBelow`, `isSetFromRight`); `isCampfire` and
  `ChordSheetCampfireLayout` appear 43 times in the converter, plus `ChordSheetCampfireLayout.kt` (277 lines), 9
  converter tests, 29 layout tests, 3 goldens, and the 21 tests of `PdfRoundTripTest`. The owner ruled these patches:
  the export should be read by the general reader, as one corpus member.

The 60 one-document commits since 2026-10-26 each added a clause to this order; nothing shows a clause's cost on the
other documents. Plan 05's corpus now does.

## Fix

### Target shape

```
convert(sheet)
  ChordSheetProfile.of(sheet)              once per document: sizes, medians, tolerances, character-columned or not
  pages → lines → songs                    as today (header parts, margin labels, ChordSheetSongSplit with the profile)
  per song:
    LineFeatures.of(lines, profile)        once per line: text, tokens, chord row, heading, metadata, labelled row,
                                           staff/tab, look, start against its column, isSung, isBlank
    LineKinds.classify(features)           pass 1: each rule gives a kind STRONG or WEAK evidence;
                                           pass 2: a WEAK kind is kept or dropped by its neighbours' resolved kinds
    SongStructure.of(features, kinds)      header, title, definitions, sections (type, label, lines), chorus by look,
                                           recalls, tab and grid runs, key and tempo changes: every decision
    SongWriter.write(structure)            ChordPro text; no decision, no state beyond the output
```

- `ChordSheetProfile` (`internal data class`): `bodySize` (the document median `convert` computes today),
  `TITLE_RATIO = 1.2`, `SAME_SIZE = 0.05`, `HEADER_LINES = 15` as named constants in its companion, and functions
  `isLarger(size)`, `isSameSize(a, b)`, `isInHeader(index)` that every caller uses.
- `LineFeatures` (`internal data class`, one per line): replaces `Rendered`'s derived parts; the line's source and
  positions stay, every parse is a field computed once. Export state, until step 9, is one nullable
  `CampfireMarks` field, so the general code reads no export field.
- `LineKinds`: the `Kind` enum as today. Each current `when` branch becomes a named rule
  `(features, index) -> Evidence?` with `Evidence(kind, isStrong)`. Pass 1 takes, per line, the strongest evidence
  in rule order (so step 5 reproduces today's order exactly); pass 2 revisits WEAK evidence with the neighbours' kinds
  (a chord row is kept only over a lyric, a sung-looking heading word only before a chord row or a blank line), which is
  what the eleven-clause branch does with raw text today.
- `SongStructure`: `Song(header: List<String>, parts: List<Part>)`, `Part = Section(type, label, lines) | Loose(lines)`,
  `Line = Lyric(text, chords: List<PlacedChord>) | Chords(row) | Comment(text, isBoxed) | TabRun(lines) |
  GridRun(lines) | Recall(type) | Change(directive)`. `BodyWriter`'s look-following (`follow`, `isSetApart`,
  `returnsToVerses`, `isRecall`) becomes a function here that returns where sections open and close; `merge`, `inline`,
  and the bidi anchoring set chords into `Lyric` here too.
- `SongWriter`: a fold over `Song` that writes directives, `[chords]`, and blank lines; `collapse` and `asCapoChart`
  stay after it as today.

### Steps (one commit each; every step leaves all platforms compiling and every test green)

Steps 1–4 are pure refactors: **every test passes unchanged and the corpus report shows no document changed** (a
stronger check than the tests, since the corpus is real). Steps 5–8 change behaviour only where they say so and land
only if no document's overall score falls (plan 05's rule). Every corpus run is plan 05's command with
`-Pcampfire.importCorpus=<the owner's path>` (a worktree has no `local.properties`); a run that prints the skip line
judged nothing. The commit message names the step; the report is not
committed.

1. **Profile and thresholds.** Add `ChordSheetProfile`; replace every `1.2`, `0.05`, and `15` above with it in the
   converter, `ChordSheetSongSplit`, and `ChordSheetLook`; compute the document median once in `convert` and pass the
   profile to the split. The two "every other line" medians stay as they are, behind one named function on the
   profile (`medianWithout(lines)`), since replacing them is behaviour (step 6).
2. **Line features.** Add `LineFeatures`, computed once per song from `Rendered`; `classify`, `extractHeader`,
   `extractTitle`, and `writeBody` read its fields instead of re-parsing. Move export state into `CampfireMarks?`.
   **Size note (`check_sizes.py`):** `ChordSheetConverter.kt` (1,525 lines) may not grow in any step, so steps 1–2 offset added plumbing with
   what they remove; and a `LineFeatures` of more than 12 constructor parameters fails as a new class (`Rendered` is
   14 already and may not gain one): group its fields into small classes.
3. **Structure out of the writer.** Split `writeBody` + `BodyWriter` into `SongStructure.of` (every decision, as
   today's order takes them) and `SongWriter.write`. Check with the corpus that the output is byte-identical. This is the
   largest step; if it cannot stay byte-identical, split it per part kind (tabs and grids first, then comments, then
   sections and looks), each still byte-identical.
4. **Header and title as structure.** `extractHeader`, `extractTitle`, and `extractPlainTitle` return values into
   `Song.header` instead of filling the `header` and `omitted` lists they are handed.
5. **Rules with evidence, same order.** Rewrite `classify` into `LineKinds` with named rules, pass 1 only, every
   branch `STRONG`: same output. Then, one rule per commit, mark as `WEAK` the clauses that are neighbour guesses today
   and move their neighbour test into pass 2 (behaviour may change: judged by the corpus).
6. **One median.** Replace `medianWithout` by the profile's median where the corpus does not fall; also removes the
   quadratic recomputation.
7. **General rules the export needs.** Run the corpus with the export's detection switched off for one rule at a time
   (a local edit, not committed) and read the `campfire/` members. For each rule whose loss lowers them, find the
   general rule that reads the same thing from any producer (a label is a heading word in a smaller bold type; an
   italic line among lyrics is a comment when the song's lyrics are not italic; a row wrapped by a narrow column
   continues the line above when it starts at the column's edge and the line above reaches the other edge), add it with
   a test on a non-Campfire sheet, and land it if no document falls. The rules to cover, from `convert/CLAUDE.md`: the
   roles by type (`ChordSheetCampfireLayout.roles`), chords only in the chords' type, the padding under wide chords
   (`dropsPadding`), the artist rows under the title (`HEADING_ROW`), the word-joiner continuations (`isContinuation`,
   `isHardCut`, `isContinuedBelow`, `markRowsContinuedBelow`, `joinWrappedDetails`), a right-to-left song's chord rows
   (`isSetFromRight`), the staff test, the exact chord position (`EXACT_POSITION`), and the header-parts split it skips.
8. **Switch the export off, with the tests that pinned it** (one commit, so no commit is red). The converter ignores
   `ChordSheet.isCampfireExport` (the field stays until step 9), and in the same commit: `ChordSheetCampfireLayoutTest`
   (29) and the 9 converter tests that build `isCampfireExport = true` are deleted; the three Campfire goldens in
   `DocumentGoldenTest` are regenerated from the general reader's output after checking it by hand against the PDF;
   `PdfRoundTripTest` is cut to one test (below), which drops the narrow-column right-to-left test and any exclusion
   plan 02 step 2 left in it. Land when the `campfire/` members' scores are at least what they were before step 7, or
   the owner accepts the loss per document. If the kept single-column round trip fails, step 7 is not done: find the
   general rule it misses first.
9. **Delete what only the export used** (dead code after step 8, every test green throughout).
   `ChordSheetCampfireLayout.kt`, `CampfireMarks`, `ChordSheet.isCampfireExport`, `PdfTextExtractor.CAMPFIRE_PRODUCER`
   and its test, the word joiners `PrintRenderer` writes (`CONTINUATION_MARK`, `isContinuationMarked`, the hard-cut
   pair) and the print tests that look for them, the export paragraphs of `convert/CLAUDE.md`, of `ui/print/CLAUDE.md`
   (the word joiner sentences under Layout), and of `data/formats/CLAUDE.md` ("A PDF whose `/Producer` is Campfire is
   flagged…"; the gutter's "since Campfire's own export leaves only 18 points" becomes "since a dense layout leaves less
   than a word's gap", the rule unchanged). The importer's `clean` keeps dropping word joiners from any text: other
   producers write them too. `PrintPdfWriter` keeps `/Producer (Campfire)`: it is the PDF's honest producer, only no
   longer read. `ActualText` over right-to-left runs stays: it serves every reader.

### What becomes of the tests

- The 118 converter tests without `isCampfireExport` are behaviour tests of the public `convert` on text; they pass
  through steps 1–4 unchanged and through 5–8 unless the corpus shows the old behaviour was wrong, in which case the test
  changes in the same commit, saying which corpus documents gained.
- `ChordSheetCampfireLayoutTest` (29) and the 9 export converter tests are deleted in step 8, the commit that stops
  applying their rules.
- `PdfRoundTripTest` (21 tests, every column count at four sizes; the slowest test, about 14 s) is exactly the
  Campfire round-trip metric the owner set aside: it pins each export rule. Decided: in step 8 it is cut to one
  test of what any good reader must get from a clean single-column PDF of the presentation corpus at the default size —
  every lyric in reading order, every chord on its syllable, the title — and the multi-column, narrow-column, and
  every-language variants go. The export's place as a measure moves to the corpus's `campfire/` members.

## Tests

Steps 1–4: the existing tests and an unchanged corpus report. Step 5 onwards: each new general rule gets a converter
test on a sheet no producer is named for; the corpus report decides landing. Step 8 deletes tests only together with switching
off the rules they pinned; step 9 deletes only code nothing runs.

## Decisions (taken by the owner)

- **Evidence, not order** (step 5): two-level evidence resolved by neighbours; step 5's first commit keeps today's
  order exactly.
- **What counts as export-specific** (step 7): the capo phrases (`(chords as they sound)`, the shapes' key) and the
  details row read in the app's languages are words on the page and stay as vocabulary any sheet may use; only the
  type, geometry, and invisible-mark rules go.
- **The Campfire goldens and `PdfRoundTripTest`** (step 8): goldens regenerated from the general reader;
  `PdfRoundTripTest` reduced to one single-column test, so CI keeps an end-to-end PDF import of a realistic layout.
