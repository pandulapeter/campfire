# Score the chord-sheet importer against a private corpus of other programs' documents before changing it further

**Kind:** testing / structure · **Severity:** high · **Platforms:** all (the test runs on desktop) · **Priority:** P1 · **Effort:** M
**Files:** `gradle.properties`, `data/source/local/implementation/build.gradle.kts`,
`data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/DocumentGoldenTest.kt`,
new `…/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/DocumentSheets.kt`,
new `…/document/corpus/ImportCorpusTest.kt`, `…/document/corpus/CorpusScore.kt`, `…/document/corpus/CorpusReport.kt`,
new `…/document/corpus/CorpusScoreTest.kt`, `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`data/formats/CLAUDE.md`, `data/source/local/implementation/CLAUDE.md`, root `CLAUDE.md` (Tests section)

**Challenged:** amended — a blank property is announced, not silent, and a worktree (no `local.properties`) must pass
the property for any step the corpus judges; a custom section keys on its label too; step 1 is ordered before plan 06
step 2, which deletes its mapper; the waiting plans are 06 step 4 and 07 (06 step 3 was dropped).

## Problem

The converter is judged one document at a time. `chordpro/.../convert/` has 79 commits, 60 of them since 2026-10-26,
nearly each fixing one document's case (`b49fec932 Keep an imported lyric sheet whole where its chorus is printed on
every page…`, `b7a688280 Keep an imported one-song document whole where its later pages open with bold part names…`).
Each fix is pinned by a synthetic test (`ChordSheetConverterTest`, 127 tests on hand-built sheets) or a golden of a
synthetic PDF (21 fixtures in `data/source/local/implementation/src/desktopTest/resources/document`, mostly made for the
test), so nothing says whether a fix for one document broke another real one. The owner's ruling: measure the importer
on a corpus of other programs' PDFs, fix general rules, and treat a Campfire export as one corpus member.

The golden test does not even run the app's path: its own mapper drops the baseline the app passes.

```kotlin
// DocumentGoldenTest.kt (end of file)
private fun ExtractedDocument.sheet() = ChordSheet(
    pages = pages.map { page ->
        ChordSheet.Page(page.lines.map { line -> ChordSheet.Line(line.spans.map { it.toChordSheetSpan() }, line.isHeading) })
    },
```

```kotlin
// domain/implementation/.../mapper/DocumentMappers.kt — what the app does
ChordSheet.Line(line.spans.map { it.toChordSheetSpan() }, line.isHeading, line.baseline)
```

## Fix (decided by the owner: a private corpus, outside git)

### The corpus folder

Anywhere outside the repository; named by `campfire.importCorpus` (absolute path) in `local.properties`.

```
<corpus>/
  ultimate-guitar/  word/  google-docs/  pages/  onsong/  songbookpro/  songselect/  genius/  chordpro-app/  campfire/ …
    <name>.pdf | .docx | .txt     the document as the program wrote it (a .txt is a copy from a web page)
    <name>.cho                    the expected result, hand-corrected; several songs are joined by `{new_song}`
    <name>.notes.md               optional: what is hard about it, a known disagreement accepted for now
  .runs/                          written by the test, never read as a document
    scores.tsv                    this run's scores
    scores.previous.tsv           the last run whose scores differed
    report.txt                    the delta table and the totals
    out/<program>/<name>.cho      what the converter wrote for each document
```

The program is the first folder's name; a document deeper down still counts under it. A document without a `.cho` is
not scored: its output lands in `.runs/out/` for the owner to correct.

**Producing an expected `.cho` cheaply:** drop the document in, run the test, copy `.runs/out/<program>/<name>.cho` beside
it as `<name>.cho`, and correct it with the document open beside it (Campfire's editor shows the result as a song).
Correct what a careful person would type, not what Campfire could reach: a structure the document does not show (a
chorus with no heading and no distinct look) stays unlabelled. A Campfire export's expected file is the song file it
was printed from, with what the print does not carry (`{define}`, hidden directives) taken out.

### The pipeline

Exactly the import's: `DocumentLocalSourceImpl(Logger.Standard).extract(ImportedFile(name, bytes))`, the same mapping to
`ChordSheet` the app uses, then `ChordSheetConverter.convert(sheet, String::normalizedToNfc)`; a `.txt` goes through
`ChordSheet.ofPlainText(text)` as `PrepareImportUseCaseImpl` sends it. Step 1 gives the golden test and the corpus test
one mapper that matches `DocumentMappers.kt` (plan 06 removes the copy for good).

### The scoring

Expected and actual are each split at `{new_song}` and parsed with `ChordProParser`; each song becomes a flat list of
**items**: `Section(type)` at each section's start, then one item per body line: `Lyric(text, chords)`,
`Chords(names)`, `Comment(text)`, `Tab(text)`, `Grid(text)`. Blank lines and recalls are skipped. Text is compared
normalized: NFC, lowercase, whitespace collapsed and trimmed, the markup taken out (`ChordProMarkup.strip`). A chord
is its name with `♯`/`♭` folded to `#`/`b`; its place is its character index in the normalized lyric.

1. **Lines** — the items of all songs of a document, concatenated (so a wrong song split costs only in 4), are aligned
   by a longest common subsequence on the key *kind + normalized text* (`Section` keys on its type, a custom section's normalized label
   included; `Chords` on the names). Score: F1 = 2·matched / (expected + actual).
2. **Chords** — over the matched `Lyric` pairs, each expected chord is found when the actual line has a chord of the
   same name within one character of its index, each actual chord used once; unmatched `Chords` rows count all their
   chords as missed. Score: F1 over chords (found / expected and found / actual).
3. **Sections** — over matched items, a boundary is a `Section(type)` immediately before an expected item; it is right
   when the actual list has a `Section` of the same type immediately before the matching item. F1 over boundaries.
4. **Songs** — the songs' normalized titles, expected against actual, aligned by LCS. F1.
5. **Header** — the fields title, subtitle, artist, key, capo, tempo, time of each matched song pair, compared
   normalized; F1 over the fields present on either side.

A document's **overall** is the mean of the five; a component with nothing on either side counts 1. A document that
throws or extracts as `null` scores 0 everywhere and its error goes into the report. The scores are four-decimal
numbers; nothing else is kept. The whole thing is a few small pure functions in `CorpusScore.kt`, with no fuzzy
heuristics of its own, so a score moves only when the output does.

### The report and the delta, with no committed baseline

`ImportCorpusTest` writes `.runs/scores.tsv` (`program/name  lines  chords  sections  songs  header  overall`). Before
writing, if the old `scores.tsv` differs from the new one, it becomes `scores.previous.tsv`; a rerun that changed
nothing leaves the previous file alone, so the delta always compares against the last run that scored differently.
`report.txt` holds, in order: the corpus totals (mean of each component, overall; per program too) with their delta,
then one row per document whose score changed, sorted by overall delta, worst first, then the unscored and the failed
documents. The test prints the report's path and the number of documents scored.

The test **never fails on a score**: there is no threshold and no baseline in git. It fails only when the property names
a folder that does not exist or holds no scored document, since that is a misconfiguration that would make every run
look clean.

### Where it lives and how it is switched on

- `ImportCorpusTest` in `:data:source:local:implementation`'s `desktopTest`, beside `DocumentGoldenTest`, which already
  reaches `DocumentLocalSourceImpl` (internal) and the converter. It reads the folder from the system property
  `campfire.importCorpus`; blank means it prints "Import corpus not configured (campfire.importCorpus)" and returns, a
  no-op, which is what CI and a fresh clone get.
- **A worktree has no `local.properties`**, so the property is blank there. An agent carrying out a step the corpus
  judges (plan 06 step 4, plan 07) runs `./gradlew :data:source:local:implementation:desktopTest --tests '*ImportCorpusTest' -Pcampfire.importCorpus=<the owner's path>`
  and reads "scored N documents" in the output; the skip line means the step was not judged.
- `gradle.properties`, a new section:

  ```properties
  # ---- Import corpus -----------------------------------------------------------------------------------------------
  # A folder of other programs' documents, each beside its hand-corrected .cho, that the importer is scored against
  # (data/source/local/implementation). Private: set it in local.properties; empty skips the scoring.
  campfire.importCorpus=
  ```
- `data/source/local/implementation/build.gradle.kts`, after the `kotlin { }` block, following the
  `campfire.updateGoldens` wiring in `tools/screenshots/build.gradle.kts`:

  ```kotlin
  tasks.named<Test>("desktopTest") {
      val corpus = project.property("campfire.importCorpus").toString()
      systemProperty("campfire.importCorpus", corpus)
      // The corpus lives outside the project, so Gradle is told to rerun when a document or its .cho changes.
      if (corpus.isNotBlank()) inputs.files(fileTree(corpus) { exclude(".runs/**") }).withPropertyName("importCorpus")
  }
  ```

  Configuration cache: only the local `corpus` string and a file tree are captured.
- Run alone: `./gradlew :data:source:local:implementation:desktopTest --tests '*ImportCorpusTest'`. With the property set,
  the pre-commit hook's `desktopTest` scores the corpus whenever this module's tests rerun; a corpus of a few hundred
  documents takes seconds.

### The rule, in `convert/CLAUDE.md` (top, replacing "a change belongs in a test first")

> **A converter change is judged by the import corpus** (`ImportCorpusTest`, a private folder of other programs'
> documents named by `campfire.importCorpus`): run it before and after, and read `report.txt`. A change lands only if
> no document's overall score falls, unless the commit's author names the document and the owner accepts it. A document
> that shows a new problem goes into the corpus with its corrected `.cho` before the fix; a test pins the rule the fix
> adds. No rule names a producer or Campfire's export.

`data/formats/CLAUDE.md` gets one line: an extraction change is judged by the same corpus. The module `CLAUDE.md` of
`:data:source:local:implementation` and the root `CLAUDE.md`'s Tests section name the test and the property in one line.

### Steps

1. **One mapper for the golden test** (before plan 06 step 2, which then deletes `DocumentSheets.kt` with the rest of
   the copy, its goldens already on the app's path). Move `sheet()` / `toChordSheetSpan()` out of `DocumentGoldenTest` into
   `DocumentSheets.kt` in the same test package, passing `line.baseline` as `DocumentMappers.kt` does. If a golden
   changes, the golden was not testing the app's path: update the expected `.cho` only after checking the new output
   is what the app imports today (a real import of that fixture on desktop gives the same text). Own commit.
2. **The scorer.** `CorpusScore.kt` (items, normalisation, LCS, the five F1s) and `CorpusScoreTest` (runs in CI): two
   identical songs score 1 everywhere; a chord moved by two characters, a missing section heading, a song split in
   two, and a missing artist each lower exactly their component, by the amount worked out by hand.
3. **The test and the wiring.** `ImportCorpusTest`, `CorpusReport.kt`, the property, the build file, the docs above.
4. **The owner populates the corpus** (not an agent's step): at least five documents for each program the import
   names, plus a few Campfire exports in `campfire/`. Plan 06 step 4 and plan 07 wait for this.

## Tests

`CorpusScoreTest` (step 2) tests the scorer on real ChordPro text through the real parser. `ImportCorpusTest` is the
measurement itself; with the property blank it does nothing, so CI and the hook are unaffected. Step 1 is checked by
the existing goldens passing (or knowingly updated).
