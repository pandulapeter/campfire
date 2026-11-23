# Score the page corpus through PageTextCrop itself, not a copy of it

**Challenged:** amended — a header counts as read where `converted` answers a title or an artist (an artist-only
header read by the import was scored as unread with `title != null`); the test skips a page whose lines `text.lines()`
would split differently; overlap with plan 09 on `PageCropperImpl.songLinesOf` named.

**Kind:** tests  ·  **Severity:** low  ·  **Platforms:** all (desktop test only)
**Files:** `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/corpus/PageCorpusTest.kt` (moved),
`domain/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/domain/implementation/importing/PageCorpusTest.kt` (new place),
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/importing/PageTextCrop.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/importing/PageTextCropTest.kt`,
`domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/importing/PageSongLines.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/PageCropperImpl.kt`,
`domain/implementation/build.gradle.kts`, `data/source/local/implementation/build.gradle.kts`,
`CLAUDE.md` (Tests section), `data/source/local/implementation/CLAUDE.md`, `tools/page-model/CLAUDE.md`,
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/importing/CLAUDE.md`
(only if it names where the corpus test lives)

Lane C, last of four (after 01, 02, and 03, whose crop changes it then scores). Shares no source file with them; the
numbers it prints change with 01 and 03. Plan 09 (next in this lane) memoizes `PageCropperImpl.songLinesOf`, the function
step 2 rewrites: whichever merges second keeps the other's change (the memo wraps the `PageSongLines.of(crop)` answer). Quotes are as at HEAD `79bb02af9`; re-locate by the quoted snippet.

## Problem

`PageCorpusTest` says it crops "each page … as the import crops a paste (`PageCropperImpl` and `PageTextCrop`…)", but it
lives in `:data:source:local:implementation`, which cannot see `PageTextCrop` (internal to `:domain:implementation`), so
it re-implements both:

```kotlin
// PageCorpusTest.kt:91-105 (data/source/local/implementation/src/desktopTest/.../document/corpus/)
val crop = pageSong.crop(lines.map(::PageLine), PageLineSource.PAGE)
val withHeader = if (crop.holdsSong) (crop.keptLines + listOfNotNull(crop.titleLine, crop.artistLine)).toSet() else lines.indices.toSet()
val heading = crop.headerLines.toSet()
// The header lines leave the song only where the converter reads no title from the crop with them, as the import does.
val isHeaderConverted = heading.isNotEmpty() && keptText(lines, withHeader).let { text ->
    val songs = ChordSheetConverter.convert(ChordSheet.ofPlainText(text)).flatMap(ChordProSplitter::split)
    songs.size != 1 || !ChordProParser.parseMetadata(songs.single()).title.isNullOrBlank()
}
...
// PageCorpusTest.kt:136-138
/** The kept lines' text with the blank lines between the first and the last, as `PageTextCrop` hands it on. */
private fun keptText(lines: List<String>, kept: Set<Int>) =
    lines.indices.filter { it in kept || (lines[it].isBlank() && it in kept.min()..kept.max()) }.joinToString("\n") { lines[it] }
```

The copy has already drifted: `PageTextCrop.cropped` filters indices to `lines.indices`, returns null (whole page) where
the crop keeps every line or nothing, and `converted` passes `String::normalizedToNfc` and converts the body again with
`guessesTitle = false`; the copy does none of these. The "blank lines between the first and last kept line" rule now
lives in three places: `PageTextCrop.keptText` (`PageTextCrop.kt:94-100`), `PageCorpusTest.keptText`, and
`ChordSheetSongCrop`'s `span` (`origin in kept || line.text.isBlank() && origin in span`). A change to the import's crop
(plans 01 and 03 are such changes) is scored by the copy, not by the code that ships.

`ImportCorpusTest` (same folder) copies much less: its document path is one line,
`pageSong?.crop(ChordSheetPageLines.of(document), PageLineSource.DOCUMENT)?.scores` (`ImportCorpusTest.kt:99`), standing
in for `PageCropperImpl.documentScoresOf` and `PageDocumentCrop.scoresOf`; the only difference is that
`PageDocumentCrop` does not ask about a document of no line, which changes nothing (the crop of no line keeps none).
It must stay where it is (it needs `DocumentLocalSourceImpl`), and needs no change.

## Fix

1. Give the page path a seam the test can score: split `PageTextCrop.cropped` so the kept line indices are a function of
   their own, e.g. `internal fun croppedLines(lines: List<String>, found: PageSongLines): CroppedLines?` (with-header
   indices, body indices, heading indices), which `cropped` turns into text; `cropped` behaves exactly as before.
2. Move the `PageCrop` → `PageSongLines` mapping out of `PageCropperImpl.songLinesOf` into `:domain:api`
   (`PageSongLines.of(crop: PageCrop): PageSongLines?`, null where `!crop.holdsSong`; `:domain:api` already depends on
   `:libs:pagesong` with `api`), used by `PageCropperImpl` and by the test, so the test's `PageCropper` over `PageSong`
   copies nothing.
3. Move `PageCorpusTest` to `domain/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/domain/implementation/importing/`
   (a new `desktopTest` source set for that module). It runs `PageTextCrop.cropped` and `PageTextCrop.converted` with a
   `PageCropper` whose `songLinesOf` is `PageSongLines.of(pageSong.crop(lines.map(::PageLine), PageLineSource.PAGE))`,
   scores the indices from step 1 (a header is "read" where `converted` took the body, i.e.
   `ConvertedPage.title != null || ConvertedPage.artist != null`: a header of the artist line alone gives a null
   `title`, and the old test counted it as read), and drops its own `keptText`. `cropped` takes the text and splits it
   with `lines()`, which also breaks at a lone `\r`: join the page's lines with `\n` and skip (and count in the
   printout) a page whose `text.lines().size` differs from its labelled lines, so labels never shift. Its printout and
   sanity bounds stay as they are.
4. Build files:
   - `domain/implementation/build.gradle.kts`: a `desktopTest` dependency on `libs.kotlin.serialization.json` (the test
     only parses, no plugin needed), and a `tasks.named<Test>("desktopTest")` block giving it `campfire.pageCorpus`
     (with `inputs.property` and the `pages/**`, `labels/**` file tree) and `pageModelWeights`
     (`rootProject.file("presentation/src/commonMain/composeResources/files/page-model.bin")`, with its `inputs.files`),
     read with `project.property`, as `data/source/local/implementation/build.gradle.kts:56-66` does now.
   - `data/source/local/implementation/build.gradle.kts`: keep `campfire.pageCorpus` (still read by
     `PageCorpusDocumentsDump`) and `pageModelWeights` (still read by `ImportCorpusTest`); drop the `pageCorpusPages`
     inputs (pages and labels were only `PageCorpusTest`'s; the dump reads documents) and fix the two comments naming
     `PageCorpusTest` (the `:libs:pagesong` test dependency's comment becomes `ImportCorpusTest`'s).
5. Docs that name its place:
   - Root `CLAUDE.md`, Tests: "`PageCorpusTest` beside it scores …" — it is no longer beside `ImportCorpusTest`; say it
     lives in `:domain:implementation` and runs the import's own crop.
   - `data/source/local/implementation/CLAUDE.md` (~157-159): drop the `PageCorpusTest` sentence.
   - `tools/page-model/CLAUDE.md` step 8 ("`PageCorpusTest`, beside the dump") and "How a model is judged": name the new
     module and the command, `./gradlew :domain:implementation:desktopTest --tests '*PageCorpusTest'`.
   - `importing/CLAUDE.md`: add one line under Preparing that `PageCorpusTest` scores this crop, if it fits there.

## Tests

The move is itself the test. Add to `PageTextCropTest` a case for the new `croppedLines` seam (indices with the blank
lines between, the heading lines left out of the body) so it is pinned apart from the text. Run
`./gradlew :domain:implementation:desktopTest --tests '*PageCorpusTest'` with `campfire.pageCorpus` set (in a worktree,
with `-P`) and report its summary next to the last numbers printed by the old test; differences are the drift the copy
had and are expected to be small (header reading and NFC only).
