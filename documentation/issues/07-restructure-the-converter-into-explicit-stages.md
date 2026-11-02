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

**Replanned:** 2026-11-02 — steps 1–2 landed; step 3 stopped because the writer decides by text already written. Steps
3–9 rewritten: a pure move of the body's helpers first (3), then the writer split with each output-reading decision
mapped to a structural one and proven exact by a probe (4), header, title, and kinds as stages (5), one behaviour step
for neighbour evidence and one median (6), then 7–9 as before; the Campfire exports enter the corpus (`campfire/`)
before step 3; batches of one agent each; the new files are named in each step, superseding **Files** for the
converter's own package; counts corrected (120 converter tests, six Campfire golden tests).

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
  ChordSheetProfile.of(sheet)              once per document: sizes, medians, tolerances            (landed, step 1)
  pages → lines → songs                    as today (header parts, margin labels, ChordSheetSongSplit with the profile)
  per song:
    LineFeatures                           once per line: heading, metadata, labelled rows, look, isSung   (landed, step 2)
    LineKinds.of(…)                        pass 1: named rules in today's order, each giving Evidence(kind, isStrong);
                                           pass 2: a WEAK kind is kept or dropped by its neighbours' kinds
    SongHeader.of(…)                       header, title, credit, definitions, tempo/time/key changes, sounding capo
    SongStructure.of(…)                    every body decision: sections and where they open and close, chorus by look,
                                           recalls, tab and grid runs, changes, blank lines, wrapped-row joins
    SongWriter.write(song)                 ChordPro text; no decision, no state beyond the output
  collapse, asCapoChart                    as today, after the writer
```

- `SongStructure` (decided by this replan; the types are step 4's):
  `Song(header: List<String>, parts: List<Part>)`; `Part = Loose(lines) | Section(type, label: String?, lines)`;
  `Line = Blank | Lyric(text) | Chords(text) | Comment(text, isBoxed) | Raw(text) | TabRun(label: String?, lines) |
  GridRun(lines) | Recall | Change(directive)`. A `Lyric`'s `text` is the lyric with its chords already set in
  (`[G]Hello`), not a list of placed chords: `merge` shifts the indent, carries a chord past a surrogate pair, and
  converts parenthesized chords on the merged string, so a chord list would need those decisions moved into the
  writer. The writer writes a line's text as it stands.
- `BodyWriter`'s decisions by text already written become decisions on the structure built so far: "the last output
  line is not blank" is "the song so far ends in something other than a `Blank`", "join onto the previous output line"
  is "replace the open part's last `Lyric` or `Chords`", and "move the trailing blank lines out of a section a look
  closes" is "move the open section's trailing `Blank`s into the loose part after it". Checked on 2026-11-02 (below)
  that this is exact.

### How a step is checked

Every corpus run uses a copy, so that two lanes never write one `.runs/` and the owner's report shows only landed
steps (`$S` is the agent's scratchpad, the build is in the agent's worktree):

```sh
rsync -a --delete --exclude .runs /Users/pandulapeter/Projects/CampfireImportCorpus/ $S/corpus/
./gradlew :data:source:local:implementation:desktopTest --tests '*ImportCorpusTest*' --rerun -Pcampfire.importCorpus=$S/corpus
```

- **Byte-identical** (steps 3–5): run at the parent commit, `cp -R $S/corpus/.runs/out $S/before`, run with the change,
  and `diff -r $S/before $S/corpus/.runs/out` prints nothing. Scores are not enough: a score can hold while text
  moves. The `campfire/` members (batch A) make this cover the export's paths as well; `./gradlew desktopTest` (the
  pre-commit hook runs it) covers the rest, `PdfRoundTripTest`'s 21 tests among them.
- **Behaviour** (steps 6–8): the same before and after, then `report.txt` with the after run's `scores.tsv` against
  the before run's: no document's overall score falls, unless the step names it and the owner accepts it. After
  committing, one run on the real corpus (`-Pcampfire.importCorpus=/Users/pandulapeter/Projects/CampfireImportCorpus`)
  leaves its report for the owner. A run printing "Import corpus not configured" judged nothing.
- `check_sizes.py` (pre-commit): `ChordSheetConverter.kt` (1,522 lines now) must shrink or hold in every commit; a new
  file stays under 500 lines and a new top-level function under 120 (members of an `object` or a class are not
  counted, but keep them under 120 too); a new non-data class takes at most 12 constructor parameters.
- Snippets below are quoted from `8e9598e08`; re-locate each by its text, not its line.

### Steps (one commit each; every step leaves all platforms compiling and every test green)

Steps 1–5 change no output: every test passes unchanged and the corpus output is byte-identical. **Step 6 is the
first that changes output on purpose**; from there, a step lands only if no corpus document's overall score falls
(plan 05's rule), `campfire/` members included. The commit message names the step; reports are not committed.

1. **Profile and thresholds.** *Landed in `326a4388e`.* Add `ChordSheetProfile`; replace every `1.2`, `0.05`, and
   `15` above with it in the converter, `ChordSheetSongSplit`, and `ChordSheetLook`; compute the document median once
   in `convert` and pass the profile to the split. The two "every other line" medians stay as they are, behind one
   named function on the profile (`medianWithout(lines)`), since replacing them is behaviour (step 6).
2. **Line features.** *Landed in `3f97477c9`* (corpus unchanged at 0.778). Add `LineFeatures`, computed once per song
   from `Rendered`; `classify`, `extractHeader`, `extractTitle`, and `writeBody` read its fields instead of re-parsing.
   Move export state into `CampfireMarks?`. `LineFeatures` holds `heading`, `metadata`, `headerRow`, `bodyRow`,
   `look`, and `isSung`.

#### Batch A (one agent): the Campfire exports into the corpus, then step 3

**A0. Campfire exports in the corpus** (no commit; before step 3, so that every later byte-identical check and every
behaviour check also reads Campfire's export). Owner's decision below on who writes the folder.

- In the worktree, add a throwaway test (never committed; keep its source in the scratchpad and copy it in) to
  `presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/`, built like
  `PdfRoundTripTest`: the same `PrintRenderer(measurer, monospaceFontFamily, textFontFamily)` over the bundled fonts,
  the same English `PrintLabels`, and `testSongRenderer()`. For each song:
  `songRenderer.transposedSong(text, transposition = 0, spelling = UserPreferences.ChordSpelling.Default)`, then
  `PrintSong(fileName, title, artist, song = songRenderer.notatedSong(song, spelling))`,
  `layoutPrintDocument(PrintSource(title, songs = listOf(entry)), settings, labels, renderer::width, measureLyric = renderer::lyricLayout)`,
  and `renderer.pdf(document, title)` written to `$S/export/<song>_<settings>.pdf`. Proven on 2026-11-02 with three
  songs: the PDFs carry `/Producer (Campfire)` and score 0.79–1.00 with the source file as their expected `.cho`,
  every other document unchanged.
- Songs: about six of the corpus's own expected files with different shapes (`chordpro/because-of-jesus-b29`,
  `guitartabs-cc/starman-david-bowie`, `mysongbook/ymca`, `generated-libreoffice/amazing_grace_capo`,
  `doctoruke/clementine_the_whole_story`, `generated-chrome/house_of_the_rising_sun_pre`); and, if the owner agrees,
  the songs of `chordpro/src/desktopTest/resources/corpus` (in git already) that hold what no corpus song does:
  `04-tab`, `05-grid`, `09-capo-transpose`, `15-hebrew`, `21-modulation`, and `22-recalls-comments`. Each at two
  settings: `PrintSettings()` (one column, 12 points) and `PrintSettings(columns = 3, fontSize = 11)` (narrow columns,
  where rows wrap).
- Each member gets the `.cho` a careful person would type from its PDF: the source with what the page does not show
  taken out (its `{define}`s, which print as nothing without diagrams) and unnamed sections given the labels the page
  prints. Never the converter's output. A `notes.md` beside it says what was taken out, like the other members'.
- Copy into `/Users/pandulapeter/Projects/CampfireImportCorpus/campfire/`, adding files only, and run the corpus once
  on the real folder: its report is the baseline (`campfire/` members new, every other document unchanged at 0.778
  overall). Delete the throwaway test. The PDFs are today's export, word joiners included; they stay as they are
  after step 9, an older Campfire's file being a real one.

**3. Make the body's helpers shareable** (byte-identical; a move, no logic touched). `writeBody` cannot leave the
converter while everything it calls is private to it. Move, unchanged but for visibility and qualification:

- `RenderedLine.kt`: `private data class Rendered(` (with its `joint`), `private data class Token(val text: String,
  val index: Int)`, and `private class ChordRow(val tokens: List<Token>, val annotation: String?, val notes:
  List<Token>)`, each made `internal`. `private enum class Kind { BLANK, TAB, GRID, COMMENT, SECTION, METADATA, CHORD,
  LYRIC }` becomes `internal enum class LineKind` in `LineKinds.kt` (a rename; step 5 fills the file).
- `ChordSheetChordWords.kt`, `internal object ChordSheetChordWords`: `token`, `tokens`, `ascii`, `chord`,
  `isChordOrLatinName`, `isBareLatinNote`, `isLatinChordBeyondNote`, `isPositioned`, `furniture`, `noChord`,
  `rowChord`, `rowChordName`, `note`, both `chordRow` overloads and `chordTokens`, `isCharacterColumns`,
  `isBareLetter`, and `parentheses`. The public `isChordLine` and internal `isChordRow` stay on `ChordSheetConverter`
  and delegate, since `ChordSheetHeaderParts`, `ChordSheetMarginLabels`, `ChordSheetStructure`, `ChordSheetPlainText`,
  `DocxTextExtractor`, `OnSongFile`, and `SharedTextName` call them there.
- `ChordSheetChordPlacement.kt`, `internal object ChordSheetChordPlacement`: `merge`, `inline`, `bracketed`,
  `withoutChordFill`, `chordEnd`, `isUnaligned`, `splitsSurrogate`, `firstIndex`, and `EXACT_POSITION`.
- `ChordSheetText.kt`, `internal object ChordSheetText`: `clean`, `isExportFiller`, `headerValue`, `headingLabel`, and
  `joinRows`.

Callers use the objects' names or import their members (`import com.pandulapeter.campfire.chordpro.convert.
ChordSheetChordWords.chord` is legal Kotlin for an object's member). `ChordSheetConverter.kt` falls to about 1,200
lines. Byte-identical, every test unchanged.

#### Batch B (one agent): step 4

**4. Structure out of the writer** (byte-identical; the largest step). Replace `writeBody` and `BodyWriter` with three
files; `convertSong`, `convertFragment`, and `setChordRows` call `SongWriter.write(SongStructure.of(…))` with
`writeBody`'s parameters and get the same `List<String>`.

- `SongStructure.kt`: the types of the target shape, `internal`. `Part.Section.label` is null only for the unlabelled
  chorus; the writer writes `{start_of_$type}` for null and `{start_of_$type: $label}` for any other value, an empty
  one included, as today. `TabRun.lines` and `GridRun.lines` hold `Raw` rows, and `Comment`s and `Change`s, since a
  comment over a staff and a pending change are written inside an open run today.
- `SongSections.kt`: `internal class SongSections`, the look-following half of `BodyWriter` moved verbatim
  (`verseLooks`, `isVerseInterrupted`, `sectionLooks`, `sectionVerses`, `setApart`, `sectionWords`, `isRecall`,
  `words`, `isSetApart`, `returnsToVerses`), with no output: `opened()` (today's `open` without `environment`),
  `openedUnlabelled()` (also `sectionVerses = emptyList()`, as `openChorus` does), `endsAt(look): Boolean` (today's
  `ends`, true only while a section with lyrics is open), `record(look, text, isInSection)` (the `if (environment ==
  null)` half of `follow`), and `closed(type, isReturn)` (the `setApart.getOrPut` half of `closeSection`).
- `SongStructure.of(lines, features, kinds, candidates, omitted, changes, convertParentheses)`: `writeBody`'s loop,
  its lookaheads (`nextContent`, the recall's first lyric, `ChordSheetLook.unlabelled`) unchanged, writing into a
  private builder that holds the closed parts, the open loose lines, the open section, the open tab or grid run, the
  pending changes, and a `SongSections`. Each output-reading line of today becomes a structural one:

  | Today (`BodyWriter`, `writeBody`) | Builder |
  |---|---|
  | `if (output.lastOrNull()?.isNotBlank() == true) output += ""` (4×) | `if (endsInText()) add(Line.Blank)`: false for an empty song; true where a run or section is open (its start is written), else where the open loose lines end in anything but `Blank`, or are empty after a closed section |
  | `addLyric`: `if (continues && previous != null && previous.isNotBlank() && !previous.startsWith('{')) output[output.lastIndex] = "$previous$joint${line.trimStart()}"` | if `continues` and the open container's last line is a `Lyric` or `Chords` whose text does not start with `{` (an escaped lyric may, and today refuses the join), replace it with `Lyric(last.text + joint + line.trimStart())`; else add `Lyric(line)` |
  | `follow`'s `val blanks = output.takeLastWhile { it.isBlank() }.size` … `closeSection(isReturn = true)` … `repeat(blanks) { output += "" }` | `closeByReturn()`: take the open section's trailing `Blank`s, close it, and start the loose lines with them |
  | `closeTab()`, `closeGrid()` | the open run becomes a `TabRun`/`GridRun` line of the open section, or of the loose lines |
  | `closeSection()` writing `{end_of_$it}` | the open section becomes a `Part.Section`; the loose lines before it became a `Part.Loose` when it opened (none when empty) |
  | `flushChanges()` | `Change(directive)` lines into the innermost open container |
  | `output += "{chorus}"`, `"{comment: $label}"`, `"{$directive: …}"`, `"{start_of_tab: $label}"` | `Recall`, `Comment(label, isBoxed = false)`, `Comment(text, isBoxed)`, a `TabRun` opened with its label |
  | `output += line.text` (tab), `line.text.trimEnd()` (grid), `bracketed(…)`, `merge(…)`, `inline(…)` | `Raw`, `Raw`, `Chords`, `Lyric`, `Lyric` |

- `SongWriter.kt`: `internal object SongWriter { fun write(parts: List<Part>): List<String> }`, a fold:
  `Blank` → `""`, `Lyric`/`Chords`/`Raw` → its text, `Comment` → `{comment: …}` or `{comment_box: …}`, a run →
  `{start_of_tab[: label]}` or `{start_of_grid}`, its lines, `{end_of_tab}` or `{end_of_grid}`, `Recall` →
  `{chorus}`, `Change` → its directive, a section → its start, its lines, `{end_of_$type}`.
- **Proven exact on 2026-11-02**: assertions added to `BodyWriter` held over `:chordsheet:desktopTest` (167 tests),
  `PdfRoundTripTest` (21), and the corpus with six Campfire exports: `{start_of_…}`/`{end_of_…}` always nest, no
  section opens or closes inside a tab or grid, no lyric or bracketed row is blank, and the blanks a look-return moves
  never reach the section's start. No lyric or chord row opened with `{`. Re-add these four `check`s at the start of
  the step if anything upstream changed since `8e9598e08`, then drop them.
- `convertSong` keeps counting `{key: ` lines on the written output, and `collapse` and `asCapoChart` run after the
  writer as today. `ChordSheetConverter.kt` falls by about 240 lines.

#### Batch C (one agent): step 5

**5. Header, title, and kinds as stages** (byte-identical).

- `SongHeader.kt`: `extractHeader`, `extractTitle`, `extractPlainTitle`, `isTitle` (which `splitLines` keeps
  calling), `isTitleContinuation`, `isCenteredUnder`, `addTitle`, `isChordSummary`, `HEADING_ROW`, and the regexes
  only they read (`credit`, `creditYear`, `commaYear`, `pageWords`) move into `internal object SongHeader`; what
  `metadata` shares with them (`capo`, `shapesKeyOf`, `timingNames`, and the `TRANSPOSITION`, `NO_CAPO`,
  `CAPO_SOUNDING`, `CAPO_SHAPES`, and `CREDIT` names) becomes `internal` where it is. They return values instead of filling the lists they are handed (`header:
  MutableList<String>, omitted: MutableSet<Int>`). The order today's insertions make is kept by the shape:
  `HeaderFields(entries, omitted, changes, soundingCapo)` from `extractHeader`, its `shapesKey` replacement applied;
  `TitleLines(leading, trailing, omitted)` from the title, where `leading` is what `addTitle` and
  `header.add(if (named == null) 1 else 2, "{subtitle: …}")` put first (title, a split-off artist, a subtitle) and
  `trailing` what `header += "{artist: …}"` appends (a credit, the export's artist rows). `convertSong` assembles
  `leading + entries + trailing + definitions.values`, then `distinct()`, into `Song.header`, and `SongWriter`
  writes the header and the body as `convertSong` does today.
- `LineKinds.kt`: `classify` becomes `internal object LineKinds` with `fun of(lines, features, candidates):
  List<LineKind>`, its `headings` and `isStaff` arrays computed as today, and a list of named rules each
  `(context, index) -> Evidence?`, `internal data class Evidence(val kind: LineKind, val isStrong: Boolean)`, taken
  first-match in today's branch order: `blank`, `exportLabel`, `exportGrid`, `exportComment`, `staff`, `heading`,
  `metadata`, then the chord branch split into the clauses it ORs (`notesOnly`, `exportContinuation`,
  `exportOverflow`, `severalChords`, `singleChordUnderHeading`, `chordOverLyric`), then `LYRIC`. Every rule is
  `STRONG`; pass 2 does not exist yet. `isStyledChordLine`, `isKeyChange`, `isBracketedHeading`, and `tab` move with
  it; `hasUnambiguousChords` and `hasStructure`, which `withoutInjections` reads too, become `internal`. The `export*` names keep the export's rules visible for step 8.
- `ChordSheetConverter.kt` falls to roughly 600–700 lines.

#### Batch D (one agent): step 6

**6. Evidence by neighbours and one median** (changes output; corpus must not fall). One commit, measured as two
changes (run the corpus after each, so the report says which one moved a document):

- **WEAK evidence.** The rules that guess from a neighbour today give `isStrong = false`: an ambiguous heading
  (`features[index].heading?.let { !it.isAmbiguous || candidates.getOrNull(index + 1) != null ||
  lines.getOrNull(index + 1)?.text?.isBlank() != false }`), `singleChordUnderHeading`, and `chordOverLyric` (the
  clause ending `lines.getOrNull(index + 1)?.let { it.text.isNotBlank() && chordTokens(it) == null &&
  !headings[index + 1] && features[index + 1].metadata == null } == true`). In pass 1 they give their kind without
  the neighbour test. Pass 2 walks from the last line to the first, so a line's next neighbour is already resolved and
  its previous one still has its pass-1 kind: an ambiguous heading stays `SECTION` before a `CHORD` or a `BLANK` (or
  at the end); a single chord stays `CHORD` under a `SECTION` and before a `BLANK`, a `SECTION`, or the end; a chord
  row over a lyric stays `CHORD` before a `LYRIC`. A dropped kind falls to the next rule that gives evidence, in
  order. The export rules stay `STRONG` (step 8 removes them).
- **One median.** `isTitle`'s `ChordSheetProfile.medianWithout(lines.filter { it !== line }.map { it.source }) ?:
  line.source.spans.maxOfOrNull { it.size } ?: 1.0` and `isTitleContinuation`'s
  `ChordSheetProfile.medianWithout(lines.filter { it !== title && it !== line }.map { it.source }) ?: return false`
  become `profile.bodySize`: `convert` keeps the profile it builds for the split in a `val` and hands it to
  `splitLines` and `convertSong`. Also ends the quadratic recomputation. If any document falls with it and not
  without it, keep `medianWithout` and say so in the commit.
- Tests that change say which corpus documents gained. A new test pins each kept or dropped case on a plain-text
  sheet.

#### Batch E (one agent): step 7

**7. General rules the export needs** (changes output; corpus must not fall). For each export rule, switch it off
locally (not committed) and read the `campfire/` members' scores; for each loss, find the rule that reads the same
thing from any producer, add it with a test on a sheet no producer is named for, and keep it if no document falls.
The export's rules, all reached through `Rendered.marks`, `sheet.isCampfireExport`, or `ChordSheetCampfireLayout`:

- types: `roles` (labels, grids, comments, boxed comments by type and place), chords only in the chords' type
  (`chordRow`'s `isChord`), italic notes among chords (`isNote`, `allowsNotesOnly`), and the staff test by
  `ChordProTokens.isStaffLine`;
- geometry: the padding under wide chords (`dropsPadding`), the artist rows under the title (`HEADING_ROW`,
  `isCampfireArtist`), the title read whole (no `ChordSheetTitles.split`), the exact chord position
  (`EXACT_POSITION`, `lyricsEnd`), a right-to-left song's rows (`isSetFromRight`), and the header-parts split it
  skips;
- invisible marks: the word joiners (`isContinuation`, `isHardCut`, `isContinuedBelow`, `markRowsContinuedBelow`,
  `joinWrappedDetails`, and the builder's join), and the key change read anywhere (`isKeyChange`, the `lines[index].marks
  != null` branch of `extractHeader`).

Candidates, as the old step said: a label is a heading word in a smaller bold type; an italic line among lyrics is a
comment when the song's lyrics are not italic; a row wrapped by a narrow column continues the line above when it
starts at the column's edge and the line above reaches the other edge. The capo phrases and the details row stay
(owner's ruling: vocabulary, not export rules). A rule that finds no general form is recorded in this plan with the
members it costs, for the owner to accept in step 8.

*Done* in two rounds. First round (general rules: a smaller bold label, a grid row of bars and beats, a lone italic line
before a chord row or a heading as a comment, a row wrapped by a column filled to its gutter; corpus 0.8299 → 0.8352,
`campfire/` unchanged at 0.9595). Second round (general rules: a title row that is one word cut at its column's edge
joins the next with no space; a column's wrap measured in the lyrics' type, a chord wider than the wrapped row's first
word counted; a capitalized row joined where the row above ends in the one space a soft wrap leaves; no-break spaces
padding a lyric under a wide chord dropped; every column of a layout as wide as its widest lyric column; no-break spaces
beside a word travelling with it onto a wrapped row; a "Words: Someone" credit and the line between the title and the
key, tempo, or time row as the artist; corpus 0.8352 → 0.8387, `campfire/` unchanged at 0.9595). With every export rule
off on top of both, `campfire/` reads 0.9562 (0.8906 before the second round, 0.8175 before the first). What the general
reader still loses, overall score with the export on → with every export rule off:

- `because-of-jesus-b29-one-column-12pt` 0.79 → 0.78 and `-three-columns-11pt` 0.81 → 0.79: the exact chord position
  (`EXACT_POSITION`, `lyricsEnd`): a chord set mid-word moves to the word's start, or a character early. No general
  form: a tolerance of 0.05 em, or even 0.02 em, on where a chord meets a character moves chords in
  `doctoruke/jingle_bells_page3`, `generated-libreoffice/songbook_times`, and `generated-textutil/red_river_valley_helvetica`,
  whose chords are hung by eye, and the corpus scores chords by word, so the export gains nothing by it. Also the bar
  lines and notes among chords set in italics (`isNote`), `||:`/`:||` lost or kept as text, and a wrapped comment
  (`Order: …`) read as a lyric; a no-break space padding after a note (`[*to v:]been   [Em]`), which the padding rule
  reads only after a chord.
- `starman-david-bowie-three-columns-11pt` 0.88 → 0.81: comments wrapped onto rows (the italic credit, which the header
  reads row by row, giving `{artist: David}`, and the italic paragraphs; the word joiners' `joinWrappedDetails` and
  comment join), and chord rows wrapped onto a row of their own (`[F] [Ab]` / `B♭`), which the wrap rule does not join
  (the joiners' `isContinuedBelow`).
- `house_of_the_rising_sun_pre-three-columns-11pt` 1.00 → 0.98: one row (`ruin of` / `[D]many a poor [F]boy`) the export
  wrapped because its chord needed room after the chord before it, which the wrap rule does not measure.
- No loss left: the title cut inside a word, the capitalized wrapped rows, the padding under wide chords in `ymca` and the
  one-column `because-of-jesus-b29`, and the artist rows (`HEADING_ROW`, `isCampfireArtist`) of `ymca` and `starman`
  (`starman-david-bowie-one-column-12pt` reads 0.89 with every export rule off, 0.84 with them on).
- No cost alone (first round): `isChord`, `allowsNotesOnly`, the staff test, boxed comments, the title read whole and its
  chord test, `isSetFromRight`, the header-parts skip, the key change anywhere, and `isStyledChordLine`'s export
  exemption. `isNote` off raises `campfire/` to 0.9632 while `because-of-jesus-b29` loses 0.002 (both).

#### Batch F (one agent; shares files with other lanes, see **Files**): steps 8 and 9

**8. Switch the export off, with the tests that pinned it** (one commit, so no commit is red). `convert` treats
`sheet.isCampfireExport` as false (the field stays until step 9), so no line has `marks`. In the same commit:
`ChordSheetCampfireLayoutTest` (29) and the 9 converter tests that build `isCampfireExport = true` are deleted; the
six Campfire tests of `DocumentGoldenTest` (eight `campfire*.pdf`, two `.cho` goldens, four expectations written in
the test) get the general reader's output as their expectation after checking it by hand against each PDF;
`PdfRoundTripTest` is cut to one test (below). Land when every `campfire/` member scores at least what it did after
step 6, or the owner accepts the loss per document. If the kept single-column round trip fails, step 7 is not done:
find the general rule it misses first.

**9. Delete what only the export used** (dead code after step 8). `ChordSheetCampfireLayout.kt`, `CampfireMarks.kt`
and `Rendered.marks`, every `export*` rule, the builder's join (`Rendered.joint`, `joinRows`' joint), `dropsPadding`
and `isExportFiller`, `ChordSheet.isCampfireExport`, `PdfTextExtractor.CAMPFIRE_PRODUCER` and its
`PdfTextExtractorTest` assertions, the word joiners `PrintRenderer` writes (`CONTINUATION_MARK`,
`isContinuationMarked`, the hard-cut pair) and the print tests that look for them (`PrintRendererTest`,
`PrintLayoutTest`, `ChordPaddingTest` name them; keep what they test besides), the padding marks
(`padded(marksPadding = …)`, read only by `dropsPadding`) if nothing else reads them, the export paragraphs of
`convert/CLAUDE.md`, of `ui/print/CLAUDE.md` (the word joiner sentences under Layout), and of `data/formats/CLAUDE.md`
("A PDF whose `/Producer` is Campfire is flagged…"; the gutter's "since Campfire's own export leaves only 18 points"
becomes "since a dense layout leaves less than a word's gap", the rule unchanged). The importer's `clean` keeps
dropping word joiners from any text: other producers write them too. `PrintPdfWriter` keeps `/Producer (Campfire)`:
it is the PDF's honest producer, only no longer read. `ActualText` over right-to-left runs stays: it serves every
reader.

### What becomes of the tests

- The 120 converter tests without `isCampfireExport` are behaviour tests of the public `convert` on text; they pass
  through steps 3–5 unchanged and through 6–8 unless the corpus shows the old behaviour was wrong, in which case the test
  changes in the same commit, saying which corpus documents gained.
- `ChordSheetCampfireLayoutTest` (29) and the 9 export converter tests are deleted in step 8, the commit that stops
  applying their rules.
- `PdfRoundTripTest` (21 tests, every column count at four sizes; the slowest test, about 14 s) is exactly the
  Campfire round-trip metric the owner set aside: it pins each export rule. Decided: in step 8 it is cut to one
  test of what any good reader must get from a clean single-column PDF of the presentation corpus at the default size —
  every lyric in reading order, every chord on its syllable, the title — and the multi-column, narrow-column, and
  every-language variants go. The export's place as a measure moves to the corpus's `campfire/` members.

## Tests

Steps 3–5: the existing tests and a byte-identical corpus output. Step 6 onwards: each new general rule gets a
converter test on a sheet no producer is named for; the corpus report decides landing. Step 8 deletes tests only
together with switching off the rules they pinned; step 9 deletes only code nothing runs.

## Decisions

Taken by the owner:

- **Evidence, not order** (steps 5–6): two-level evidence resolved by neighbours; step 5 keeps today's order exactly.
- **What counts as export-specific** (step 7): the capo phrases (`(chords as they sound)`, the shapes' key) and the
  details row read in the app's languages are words on the page and stay as vocabulary any sheet may use; only the
  type, geometry, and invisible-mark rules go.
- **The Campfire goldens and `PdfRoundTripTest`** (step 8): goldens regenerated from the general reader;
  `PdfRoundTripTest` reduced to one single-column test, so CI keeps an end-to-end PDF import of a realistic layout.

Taken by the replan (2026-11-02):

- A `Lyric` holds its text with the chords set in, not a list of placed chords (see Target shape).
- Step 3 is a pure move, so that step 4 changes only the writer; old step 4 (header and title) joins classification in
  step 5; old step 5's second half and old step 6 are one behaviour step, measured as two changes.
- The `campfire/` members come before step 3, so the byte-identical checks cover the export's paths too.

Taken by the owner on 2026-11-02:

- The batch A agent writes the `campfire/` members into the corpus itself, only into a new `campfire/` folder, each
  with its `notes.md`; the owner skims the notes afterwards.
- The members are the six corpus songs plus the six presentation-corpus songs (tab, grid, capo, Hebrew, key change,
  recalls), each at one column 12 pt and three columns 11 pt.
- The step 4 design (Target shape, batch B) is approved.
- Still the owner's, at step 8: a score loss on a document where step 7 found no general replacement for an export rule.
- **Step 8**: every export reading rule is switched off and deleted, accepting the losses in step 7's list (`campfire/`
  0.9595 → 0.9562: `starman-david-bowie-three-columns-11pt` 0.88 → 0.81, `house_of_the_rising_sun_pre-three-columns-11pt`
  1.00 → 0.98, `because-of-jesus-b29` 0.79 → 0.78 and 0.81 → 0.79).

Taken in step 8 (2026-11-02):

- The kept round trip compares the title, and the lyric lines in their order each with its chords by word, as the corpus
  scores them: a chord moved within its word is the loss accepted above, and a comment or a section label read as a
  lyric is no lyric lost. Three general rules it found missing landed first: an italic word among upright chords is an
  annotation, a single bold chord over larger lyrics is a chord, and a right-to-left word's no-break padding is dropped.
- The six export converter tests that pass with the export off (an artist longer than the title, a key's accidental
  sign, and four right-to-left placements) stay as tests of the general reader; the three that fail are deleted.
