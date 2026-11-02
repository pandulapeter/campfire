# Move the chord-sheet converter out of :chordpro into a module of its own, and carry the extraction's columns in ChordSheet instead of re-guessing them

**Kind:** structure · **Severity:** medium · **Platforms:** all · **Priority:** P2 · **Effort:** M (steps 1–2), step 4 gated on plan 05's corpus
**Files:** `settings.gradle.kts`, new `chordsheet/build.gradle.kts`, new `chordsheet/CLAUDE.md`, every file under
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/` (moved) and
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/` (moved),
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordProLatinNotationTest.kt`,
`chordpro/src/desktopTest/kotlin/com/pandulapeter/campfire/chordpro/corpus/PlainTextRoundTripTest.kt`, `chordpro/CLAUDE.md`,
the six `internal` objects the converter uses (`chordpro/.../chords/ChordProChordNames.kt`, `syntax/ChordProRowMarks.kt`,
`syntax/ChordProTokens.kt`, `syntax/ChordProVocabulary.kt`, `syntax/ChordProDirectives.kt`, `edit/ChordProLiteralText.kt`),
`data/formats/build.gradle.kts`, `data/source/local/implementation/build.gradle.kts`, `domain/implementation/build.gradle.kts`,
`presentation/build.gradle.kts`, root `CLAUDE.md` (architecture tree), `data/formats/CLAUDE.md`;
step 2: `data/model/.../importing/ExtractedDocument.kt` (deleted), `data/model/CLAUDE.md`, `data/source/local/api/.../DocumentLocalSource.kt`,
`data/repository/api/.../DocumentRepository.kt`, `data/repository/implementation` (the repository passing it on),
`data/source/local/implementation/.../source/DocumentLocalSourceImpl.kt`, every `data/formats/src/commonMain/.../document/*.kt`
naming `ExtractedDocument` (`PdfTextExtractor`, `PdfLineLayout`, `PdfBidi`, `PdfContentInterpreter`, `DocxTextExtractor`)
and their tests, `domain/implementation/.../mapper/DocumentMappers.kt` (deleted), `PrepareImportUseCaseImpl.kt` and its
tests (`PrepareImportUseCaseImplTest`, `ImportFilesUseCaseImplTest`), `DocumentGoldenTest.kt` / `DocumentSheets.kt`,
`presentation/src/desktopTest/.../ui/print/PdfRoundTripTest.kt`;
step 4: `ChordSheet.kt`, `ChordSheetPlainText.kt`, `ChordSheetLook.kt`, `ChordSheetMarginLabels.kt`, `PdfLineLayout.kt`, `PdfBidi.kt`.
The paste path (`presentation/.../ui/screens/songEditor/PastedLines.kt`, `presentation/.../ui/platform/SharedTextName.kt`)
and the backup readers (`data/source/local/implementation/.../backup/OnSongFile.kt`, `OpenSongSong.kt`, `JsonSongs.kt`) only
change imports if the package is renamed (it is not: decided).

**Challenged:** amended — step 3 (one shared `ChordSheetExportMarks` object for the word joiner) dropped: plan 07, now
decided, deletes the mark with the export rules, so the object would exist only to be deleted; the owner's decisions
folded in (one `ChordSheet` model replacing `ExtractedDocument`, package kept); `data/model/CLAUDE.md` added; step 2
ordered after plan 05 step 1 and plan 02 step 2 (`PdfRoundTripTest`).

## Problem

`convert/` (2,926 lines, 60 commits since 2026-10-26) is the fastest-churning code in the repository and lives in
`:chordpro`, the dependency-free, `explicitApi()` core every layer depends on, whose `CLAUDE.md` describes a format
("The ChordPro format, the conversion of positioned chord sheets into it…"). `ChordSheet` and `ChordSheet.Span` are
public API of that core. The converter's heuristics, its 2,232 lines of tests, and its Campfire-export knowledge share a
module with the parser and the transposer, so the core's rules ("rules pinned by a test are not repeated here") and
its review cost grow with every import fix.

The model is written twice: `ExtractedDocument` (`:data:model`) mirrors `ChordSheet` field for field, and
`DocumentMappers.kt` copies one into the other (a test copy of that mapper had already drifted, plan 05 step 1). Any
fact the extraction learns costs three edits, so the facts are dropped instead: `PdfLineLayout` finds each page's columns
and `PdfBidi.Column(left, right, direction)`, then hands on only spans, `isHeading`, and `baseline`:

```kotlin
// data/model/.../ExtractedDocument.kt
data class Line(val spans: List<Span>, val isHeading: Boolean = false, val baseline: Double? = null)
```

and the converter guesses them again from the spans: `ChordSheetCampfireLayout.isSetFromRight` re-derives a column's
shared left and right edges and its direction (`shared(column.map { it.second })`, `writingDirectionOf(...)`),
`ChordSheetLook` judges an inset against "the left edge [looks] share", and `ChordSheetMarginLabels` against "the
leftmost text of the lines without a label" — per page, so a two-column page mixes both columns' edges.

The export and the import also spell one protocol twice:

```kotlin
// presentation/.../ui/print/PrintRenderer.kt:303
private const val CONTINUATION_MARK = '⁠'
// chordpro/.../convert/ChordSheetCampfireLayout.kt:232
const val CONTINUATION = '⁠'
```

## Fix

### The module

`:chordsheet` (folder `chordsheet/`, beside `:chordpro` and `:abc`), `campfire-library`, `explicitApi()`, depending on
`api(project(":chordpro"))` and nothing else: still dependency-free, no Koin, called directly. Dependents:

| Module | Uses | Dependency |
| --- | --- | --- |
| `:data:formats` | `ChordSheetConverter.isChordLine` (`PdfLineLayout`, `DocxTextExtractor`); after step 2 it builds `ChordSheet` | `api` after step 2, `implementation` before |
| `:data:source:local:api`, `:data:repository:api` | after step 2, `extract` returns `ChordSheet?` | `api` |
| `:data:source:local:implementation` | backup readers, `DocumentLocalSourceImpl`, `DocumentGoldenTest` | `implementation` |
| `:domain:implementation` | `PrepareImportUseCaseImpl` (convert, `ofPlainText`, `ChordSheetStructure`) | `implementation` (it gets `:chordpro` through `:domain:api` today) |
| `:presentation` | paste (`PastedLines`, `SharedTextName`), `PdfRoundTripTest` | `implementation` |

The package stays `com.pandulapeter.campfire.chordpro.convert`, so the move changes no import and is checked
mechanically (`git diff -M --stat`, sorted declaration lists before and after). No `:app:di` change: the module has no
Koin definitions. Root `CLAUDE.md`'s tree gets one line under `chordpro`; `chordpro/CLAUDE.md` loses "the conversion of
positioned chord sheets into it", `convert` from its package list, and `:data:formats` from its callers; the
converter's `CLAUDE.md` moves with the code, and `chordsheet/CLAUDE.md` (MPL header) says what the module is in three
lines and points to it.

What `:chordpro` must make public (explicit API, with KDoc): `ChordProChordNames.isChordName`, `latinExpanded`,
`latinNoteLength`; `ChordProRowMarks.repeatCount`; `ChordProTokens.isStaffLine`; `ChordProVocabulary.ANNOTATION_MARKER`
and `KEY`; `ChordProDirectives.brackets` and `isKnownDirective`; `ChordProLiteralText.escape`. Only those members, each
a fact about the format other readers may ask for: a public member of an `internal` object is still invisible outside
the module, so each object becomes `public` and its other members are marked `internal`. Drop this plan at execution if the list grows past these ten members, and report why.

### Steps

1. **Move `convert/` and its tests into `:chordsheet`** (pure move). `settings.gradle.kts` includes `:chordsheet`; the
   members above become public; the four commonTest files under `convert/` move; `ChordProLatinNotationTest`'s
   converter case (`a text is converted into Latin and back`) and `PlainTextRoundTripTest` move to `:chordsheet`'s
   commonTest / desktopTest, the latter reading the shared corpus as `presentation` does
   (`resources.srcDir(rootProject.file("chordpro/src/desktopTest/resources"))`). Every dependent's build file as in the
   table. Grep the repository for `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert` and
   `chordpro/convert` (CLAUDE.md files, skills, `documentation/`) and fix each reference; `check_doc_names.py` must pass.
2. **One model.** `ExtractedDocument` is deleted; the extractors build `ChordSheet` directly, `DocumentLocalSource` and
   `DocumentRepository.extract` return `ChordSheet?`, and `DocumentMappers.kt` and the test's `DocumentSheets.kt` go.
   `ChordSheet.Span.size` keeps its default of 1.0 for plain text; the extractors always pass it. Pure refactor: every
   golden and test unchanged in substance, which holds only after plan 05 step 1 put the golden test on the app's
   mapper (with the baseline). In `PdfRoundTripTest` only the `ExtractedDocument.toChordSheet()` mapper goes (plan 02
   step 2 has already changed its renderer; plan 07 step 8 later cuts it to one case).
3. *(Dropped in the challenge: the word joiner is deleted by plan 07 step 9, not shared.)*
4. **Carry the columns** (gated on plan 05's populated corpus). `ChordSheet.Page` gains
   `columns: List<Column>` and `ChordSheet.Line` `column: Int?` (an index into its page's columns, null where the source
   has none: Word, a running line that crosses the gutter), `public data class Column(val left: Double, val right:
   Double, val direction: WritingDirection)`. `PdfLineLayout.columns` keeps the `PdfBidi.Column` it already builds and
   numbers the lines it returns; `ChordSheetPlainText` gives a text one column, its width and the direction it votes.
   No alignment field: a line's side is its spans' ends against its column's bounds, computed where needed.
   Then, one commit each and only where plan 05's report shows no document falling: `ChordSheetLook` measures an inset
   against the edge its own column's verses share, `ChordSheetMarginLabels` finds the body's edge per column.
   (`ChordSheetCampfireLayout.isSetFromRight` is not switched: plan 07 deletes it.) A field no consumer ends up using
   is not added.

## Tests

Steps 1–2 are refactors: every existing test, moved with its code, passes unchanged; the moves are checked with
`git diff -M --color-moved=dimmed-zebra`. Step 4 adds a test in `:data:formats`'s `PdfTextExtractorTest` that a page of
two columns gives each line its column and each column its bounds and direction (reusing the two-column fixture the
gutter tests build); its consumers each
get a converter test of a two-column page whose columns start at different edges, and are judged by the corpus delta
(plan 05).
