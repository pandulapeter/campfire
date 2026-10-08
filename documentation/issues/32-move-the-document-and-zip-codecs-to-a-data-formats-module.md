# Move the pure PDF/Word extractors and the zip reader/writer out of :data:source:local:implementation into a new :data:formats module

**Challenged:** amended — the codecs do use coroutines (`CancellationException`, `yield`), so the module needs the coroutines dependency (and coroutines-test); `DocumentGoldenTest` drives `DocumentLocalSourceImpl` and so stays, with its fixtures, in the local module; `ImportLimitsTest`'s `Inflater.MAX_ENTRY_SIZE` assertion moves to the new module since `Inflater` stays internal.

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** M  ·  **Risk:** low  ·  **Platforms:** all
**Files:** new module `data/formats/` (`build.gradle.kts`, `CLAUDE.md`, `src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/{document,zip}/…`, `src/commonTest/…`, `src/desktopTest/…`); `settings.gradle.kts` (`include(":data:formats")`); moved from `data/source/local/implementation/src/commonMain/.../implementation/document/` (9 files at 2940b0e0a, 3117 lines) and `.../implementation/zip/` (10 files, 859 lines); their tests `commonTest/.../document/{PdfFiltersTest,DocxTextExtractorTest,PdfTextExtractorTest,PdfTestWriter,PdfFileTest,XmlPullParserTest}.kt`, `commonTest/.../zip/{ZipRoundTripTest,ZipReaderTest}.kt`, `desktopTest/.../zip/{ZipReaderJvmTest,InflaterTest}.kt` (moved), `desktopTest/.../document/DocumentGoldenTest.kt` and `desktopTest/resources/document/**` (stay, imports only), `commonTest/.../ImportLimitsTest.kt` (one test moves), `desktopTest/.../source/ArchiveLocalSourceTest.kt` (imports); consumers `data/source/local/implementation/.../source/ArchiveLocalSourceImpl.kt`, `.../source/DocumentLocalSourceImpl.kt`; `data/source/local/implementation/build.gradle.kts`; `data/source/local/implementation/CLAUDE.md`; root `CLAUDE.md` (Architecture tree, `data:source:local:api -> :implementation` comment line, test command list)
**Depends on:** none. The concurrent lane splits `PdfTextExtractor` into several files: do this after it, moving whatever files `document/` then holds.

## Problem

`:data:source:local:implementation` is the library's storage — `FileStorage` on four platforms, the local sources, the
secret stores, the mappers — but two thirds of its common code (≈3976 of 6323 commonMain lines) is something else:
pure, dependency-free codecs.

- `document/` — `PdfFile`, `PdfSyntax`, `PdfFilters`, `PdfFont`, `PdfOutput`, `PdfTextExtractor`, `DocxTextExtractor`,
  `XmlPullParser`, … (3117 lines). Imports: `:data:model`'s `ImportLimits` and `ExtractedDocument`, the zip package,
  and `:chordpro`'s `ChordSheetConverter.isChordLine` (in `DocxTextExtractor`).
- `zip/` — `ZipReader`, `ZipWriter`, `Inflater`, `Crc32`, `DosTimestamp`, … (859 lines). Imports: `ImportLimits` only.

They use no `FileStorage` and no Koin; the only coroutine API they touch is `kotlinx.coroutines.CancellationException` and `yield()` (the extractors give way and rethrow cancellation on long documents). The only consumers are `ArchiveLocalSourceImpl` (`ZipReader`,
`ZipWriter`, `ZipEntry`, `UnreadZipEntry`, `DosTimestamp`) and `DocumentLocalSourceImpl` (`PdfTextExtractor`,
`DocxTextExtractor`). Living inside the storage module means: every edit to a PDF heuristic recompiles the storage
module and its dependents for all five targets; the module's CLAUDE.md is half about PDF internals; and the codecs' unit tests sit next to file-storage tests.

`backup/` (SongbookPro, 259 lines) is *not* part of this move: it produces Campfire's own setlist documents
(`SetlistDocument`, `SetlistSongDocument`, `SetlistDocumentFormat` are in this module's `model/`), so it belongs with the
library.

## Fix

1. Create `:data:formats` with the `campfire-library` convention plugin, `commonMain` depending on `:data:model` (`api`,
   for `ExtractedDocument`) and `:chordpro` (`implementation`), `implementation(libs.kotlin.coroutines)` (for `CancellationException` and `yield`); `commonTest` on `kotlin("test")` and
   `libs.kotlin.coroutines.test` (`PdfTextExtractorTest`, `DocxTextExtractorTest` and `PdfFileTest` use `runTest`). No
   Koin plugin.
   Package `com.pandulapeter.campfire.data.formats.document` / `.zip`.
2. `git mv` the two packages and their tests (keep history), rewrite the `package`/`import` lines. Make public only what
   the two local sources use: `ZipReader`, `ZipWriter`, `ZipEntry`, `UnreadZipEntry`, `DosTimestamp`, `ZipException`
   (if it escapes `ZipReader`'s API), `PdfTextExtractor`, `DocxTextExtractor`, and whatever their public functions take
   or return; everything else stays `internal` (the tests are in the same module, so they keep seeing internals).
   **`DocumentGoldenTest` and `desktopTest/resources/document/` stay in `:data:source:local:implementation`**: the
   test drives `DocumentLocalSourceImpl().extract(…)` (the size gate, the extension dispatch and the "no readable
   text" filter included), which `:data:formats` cannot see. It is the local source's end-to-end test of the
   extractors, and the root CLAUDE.md's "independent-producer document goldens in `desktopTest`" of that module stays
   true. (Alternatively rewrite it against `PdfTextExtractor.extract` and move it — a weaker test; not recommended.)
   **`ImportLimitsTest`** (local module's `commonTest`) asserts `Inflater.MAX_ENTRY_SIZE == ImportLimits.MAX_IMPORT_SIZE`;
   `Inflater` stays `internal`, so move that one test (`theInflaterAllowsWhatAnImportDoes`) into `:data:formats`'
   `commonTest` (`ImportLimits` is in `:data:model`, which it sees) and leave the rest of `ImportLimitsTest`, which uses
   only the public `ZipWriter`/`ZipEntry`, where it is. `ArchiveLocalSourceTest` uses `ZipWriter`/`ZipEntry` too —
   public, so it only changes its imports. `UnreadZipEntry` (with its nested `Reason`) is what `ZipReader` reports and
   `ArchiveLocalSourceImpl` reads, so it is public as listed.
3. `:data:source:local:implementation` gains `implementation(project(":data:formats"))` and updates the imports in
   `ArchiveLocalSourceImpl` and `DocumentLocalSourceImpl`.
4. Write `data/formats/CLAUDE.md` from the PDF/Word/zip paragraphs of `data/source/local/implementation/CLAUDE.md`
   (moved, not duplicated), add the module to the root CLAUDE.md tree (`data:formats — the pure-Kotlin zip
   reader/writer and PDF/Word text extractors; depends on :data:model and :chordpro`) and to the `desktopTest` command
   list. CI runs `./gradlew desktopTest`, which picks the new module up by itself.

One commit for 1–3 (the move must compile as a whole), one for 4.

## Tests

None new: every moved test moves with its code and must pass unchanged in the new module
(`./gradlew :data:formats:desktopTest :data:source:local:implementation:desktopTest`). `ArchiveLocalSourceTest`
(desktopTest of the local module) still guards the wiring.

## Manual check

Import one PDF, one .docx and one zip of songs on desktop and on the web build: same results as before.

## Decision

- **A — create `:data:formats` with `document/` and `zip/` (recommended).** The storage module shrinks to storage; the
  codecs become a leaf module that compiles in parallel with it and that `:presentation`'s own PDF writer could reuse
  pieces of later (`PrintDeflater` is a separate encoder today). Cost: one more module in `settings.gradle.kts`, one
  more klib per target.
- **B — also move `backup/`**, together with the setlist document model it writes (`model/SetlistDocument*`). Rejected
  for now: the setlist document format is the library's own on-disk shape and is used by `SetlistLocalSourceImpl`; it
  would have to move to a module both depend on.
- **C — leave the module as it is**, and only give the codecs their own CLAUDE.md section. No build or boundary gain;
  chosen if the user prefers fewer modules.
