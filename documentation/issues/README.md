# The 35th review: imports from anywhere

**Reviewed commit:** `25c4ad1ab` (the tree was committed as `f94b1bc90` during the review; the fixes below start
there). **Angle:** the claim "Campfire will do its best to transform any data to ChordPro" — pasting or sharing song
text from any website or app, other apps' backups detected as zips whatever their extension, every file in every
subfolder given a chance, JSON backups. Four reviewers (a converter probe with 31 invented pastes, archives and
backups, a web research of other apps' export formats, the docs reviewer), then a regression read of the fixes and a
challenge of every plan.

## Headlines

- **Paste conversion misses the headings and title lines the biggest sites write.** Genius's `[Verse 1: Artist]`,
  Ultimate Guitar's `[Pre chorus]` / `[Chorus x2]` / `Riverlight Chords by …`, and every non-English heading word are
  read as lyrics, which also leaves a section open across the rest of the song (plans 01–03). A chord row ending in
  `(let ring)` or with a `C*` footnote is not a chord row (05). `Capo: II` is lost without a trace (04).
- **Archives:** a backup under an unknown name is unpacked only when picked loose; nested in a zip or inside a
  dropped folder it is skipped (10). Text under unknown names (`.onsong`, extensionless) never gets a chance (11).
- **JSON:** feasible as a generic, strictly gated extractor (12); modest payoff by the research. The formats worth
  readers are OpenSong XML / `.osb` (13) and OnSong `.onsong` (14). SQLite-only libraries (songs typed in OnSong,
  MobileSheets) are not feasible without a SQLite reader; see "Not planned".

## Fixed during the review (`f94b1bc90..a6f5c149f`)

- `413b214d6` Unpack a backup archive renamed to a text, song, or JSON file, picked loose or inside another archive.
- `885c8a882` Leave the files inside an archive's hidden folders out of an import.
- `3e2e2711c` Recognise a SongbookPro backup whose document is named in another case or starts with a blank line.
- `5a6041a83` Name the lone song files that skip the import review in the notes.
- `1f2eaafef` Tighten the presentation notes on text and document imports.
- `a2563cea7` Trim the import package notes that repeat the module's and the API's.
- `d2dd7b4e6` Update the Android notes on files offered without a file name.
- `4fe91473b` Bring the local implementation notes back under their word budget.
- `28cdc50b0` Correct the root notes' pointers for documents, backups, shares, and the iOS inbox.
- `a6f5c149f` Reword the import notes the review's fixes left ambiguous (from the regression read).

## Index

| # | Plan | Severity | Lane |
|---|------|----------|------|
| 01 | Recognize the section headings every chord and lyrics site writes, in every language | high | C |
| 02 | Keep the chord row written after a heading's colon on its own line | medium | C |
| 03 | Read the title and the artist from the first lines a chord page is copied with | high | C |
| 04 | Read the capo and key lines chord sites write; never drop a metadata line whose value could not be read | medium | C |
| 05 | Accept a chord row that ends in a playing note or whose chords carry a footnote star | medium | C |
| 06 | Read chord rows that lost their alignment or their line breaks when the page was copied | medium | C |
| 07 | Leave out the lines a lyrics site injects into a copied text | low | C |
| 08 | Title a shared or pasted song that declares no title by the page title it was shared with | medium | P |
| 09 | Let the web's file picker offer every file, as the other platforms' pickers do (decision) | low | A |
| 10 | Unpack an archive nested under an unknown name by peeking at its first bytes; folder drops too | medium | A |
| 11 | Read a text file under an unknown name as a chord sheet when it has the structure of one | medium | A |
| 12 | Read the songs out of any JSON document whose objects hold a title and a chord sheet (decision) | medium | A |
| 13 | Read OpenSong's XML songs, loose and inside OpenSongApp's `.osb` backups (decision) | medium | A |
| 14 | Read OnSong's `.onsong` files, with the title and artist lines their header starts with (decision) | medium | A |

## Lanes

| Lane | Area | Plans, in execution order | Files owned |
|------|------|---------------------------|-------------|
| C | the chord sheet converter | 01 → 02 → 03 → 04 → 05 → 06 → 07 | `chordpro/src/commonMain/.../convert/ChordSheetConverter.kt`, the new `ChordSheetHeadings.kt` and `ChordSheetTitles.kt`, `chordpro/src/commonTest/.../convert/ChordSheetConverterTest.kt`, `chordpro/.../convert/CLAUDE.md` |
| P | pasted and shared text in `:presentation` and the draft use case | 08 | `presentation/src/commonMain/.../ui/state/importing/*`, `.../ui/platform/SharedTextName.kt`, `.../ui/screens/songEditor/EditorSession.kt`, `.../ui/CampfireViewModel.kt`, `domain/api/.../songs/DraftSongFromTextUseCase.kt`, `domain/implementation/.../songs/DraftSongFromTextUseCaseImpl.kt` and its test, `presentation/src/commonTest/.../ui/state/importing/*`, `.../ui/platform/SharedTextNameTest.kt`, `.../ui/screens/songEditor/EditorSessionTest.kt`, `presentation/CLAUDE.md` |
| A | archives, unknown names, foreign formats | 09 → 10 → 11 → 12 → 13 → 14 | `data/formats/.../zip/*` and tests, `data/formats/.../document/XmlPullParser.kt`, `data/model/.../importing/*`, `data/model/.../library/LibraryFiles.kt`, `data/source/local/implementation/.../source/ArchiveLocalSourceImpl.kt`, `.../backup/*` and tests, `data/source/local/api/*`, `data/repository/api|implementation/.../Archive*`, `domain/implementation/.../importing/*` and tests, `domain/implementation/src/commonTest/.../RepositoryStubs.kt` (`FakeArchiveRepository`), `data/formats/.../document/DocxTextExtractor.kt` (its `limitOf`), the new `chordpro/.../convert/ChordSheetStructure.kt` and `ChordSheetLyrics.kt` and their tests, `presentation/src/desktopTest/.../ReadAsImportedFilesTest.kt`, `app/ios/iosApp/ShareExtension/ShareInboxWriter.swift`, `data/model/.../library/CLAUDE.md`, `presentation/src/desktopMain/.../FilePicker.desktop.kt`, `presentation/src/wasmJsMain/.../FilePicker.wasmJs.kt`, `app/android/src/main/AndroidManifest.xml`, `app/ios/iosApp/ShareExtension/Info.plist`, their `CLAUDE.md` files |

**Merge order: C, A, P.** C is a pure module every other lane's converted text goes through, and P's one plan is
the only one touching shared UI state. Lanes run in parallel from `START` (no lane builds on another's commits).

**Shared files:**
- `chordpro/src/commonMain/.../convert/CLAUDE.md` is lane C's. Lane A's plan 11 appends exactly one bullet at the
  end of the file and touches nothing else in it; the merge keeps both sides.
- `chordpro/src/commonMain/.../convert/ChordSheetConverter.kt` is lane C's only. Lane A's plan 11 builds
  `ChordSheetStructure` on the converter's public surface (`isChordLine`) and never edits the converter.
- `domain/implementation/CLAUDE.md`, `data/model/CLAUDE.md`, `presentation/CLAUDE.md`: edited by one lane each (A, A,
  P). The root `CLAUDE.md` is not edited by any plan.
- `strings.xml`: no plan adds a string. Plan 08 reuses the placeholder strings.
- Plans 09, 12, 13, 14 are decision-gated (below); a declined one is skipped by its lane, which moves on.

## Decisions (taken by the user on 2026-10-18, all as recommended: every plan is executed)

1. **Plan 09, the web picker's filter:** **drop it**, so the web offers any file as the other platforms do; the
   accepted cost is that phones' browsers then also offer the camera and photo library in the picker.
2. **Plan 12, the generic JSON extractor:** **build it** with the strict acceptance rule (one bounded file; any
   future JSON export becomes importable), although the research found few chord apps that export JSON.
3. **Plan 13, OpenSong XML and `.osb`:** **build it** (documented format, large church-musician user base on Android,
   cheapest of the readers); one real `.osb` is to be checked against before the release that ships it.
4. **Plan 14, OnSong `.onsong` files:** **build it** (the most-asked-for migration; the per-song files and the loose
   files of a `.backup` are covered; songs typed in OnSong stay in its SQLite and are not).
5. **A SQLite reader** for OnSong's and MobileSheets' databases: not planned (500–800 lines of pure Kotlin for a
   read-only table scan, an uncertain sample base, and OnSong backups are often over the 24 MiB import cap anyway).
   Ask again only if users ask.

## Checked and found solid

- Already-ChordPro text passes through untouched (BOM, CRLF, `{new_song}`); escaping lets no pasted prose become
  markup; tablature, bar rows, repeat marks, `N.C.`, Unicode accidentals, exotic chord names (`Dm7(b5)`, `E7#9`,
  `A7+`), Latin and German notation, parenthesized inline chords by majority, `Intro: G D` labels, `Capo: 2nd fret`,
  `Key: G`, `Tempo: 120 BPM`, `Artist - Title`, tabs expanded to columns, NBSP. Non-songs (an email, a list, a URL, a
  lone `A` line) never become structure. Conversion is linear: 50 k characters of any shape in 12–55 ms warm.
- Zips picked loose under unknown names unpack on Android, iOS, and the desktop; `.docx` stays a document; a zip
  named `.pdf` is an unreadable document; an OpenDocument or EPUB found by bytes is reported as one file; paths are
  flattened and same-named entries numbered, nothing lost; `__MACOSX`, `.DS_Store`, directory entries, and backslash
  paths handled; every unread entry but hidden ones is reported by name; `MAX_DEPTH = 3` suffices; damage is
  isolated per entry and per nested archive; the budget is one across readers, planner, and nested inflation; the
  zip-bomb guards hold.
- SongbookPro: `dataFile.txt` in a subfolder, a BOM, CRLF, and a missing version line all import.
- Docs: every claim the sweep set out to verify held except the ones fixed above; the dead `importFromAddress` chain
  (`DownloadImportFileUseCase` wired but unreachable) is kept on the user's earlier decision.

## Dropped after verification

- "`Artist – Title` is read in the wrong order" (converter reviewer, low): both orders are common on the web, so there
  is no rule to prefer; documented as a limit in plan 03's `CLAUDE.md` line instead.
- "Chord diagram blocks (`Am: x02210`) could become `{define}`": optional, not an error; not planned (ask if wanted).
- "Ultimate Guitar page furniture (`Difficulty:`, `View official tab`, ratings) survives a whole-page copy": many
  lines in no fixed shape; plan 07 says so and leaves it.
- "An unknown-name backup over the budget is reported unsupported, not too large" (archives reviewer, low): a wording
  difference on a file that is refused either way; not planned.
- "Two `dataFile.txt` entries in one archive are two raw songs": only a hand-built archive has two; not planned.
- "The 24 MiB SongbookPro document is held several times over in memory": not measured, and the limit was accepted in
  an earlier sweep (`ImportLimits` is sized for the Android heap); note for a performance sweep with a 20 MiB sample.
- Docs: "`DownloadImportFileUseCase` is unused yet" is accurate enough for the dead chain; left.

## Measurements

Converter probe, 50 k characters each, JVM warm: 2000 sections 29–40 ms; lone chord lines 55 ms; parentheses-dense
text 45 ms; one 50 k-character inline-chord line 20 ms; a 50 k-column chord row 12 ms; prose 13 ms.

## Challenge

Run on every plan after they were written (see each plan's `**Challenged:**` line where it was amended); results
recorded below by the challengers.

Lane C (plans 01–07, challenged against the converter, its pinned tests, `DocumentGoldenTest`, the editor paste, and
the import's notation and Prettify passes; a `{start_of_verse: Verse 1: Some Artist}` label, `{comment: Chorus (x2)}`,
`{capo: 0}`, and `{key: Sol}` → `{key: G}` were probed and survive them):

- 01 amended: the sung-word context rule has a home (`Heading.isAmbiguous`, resolved once in `classify`);
  `expandLabels` passes the colon; `[Chorus] (x2)` in the grammar; `Parte` untyped, `Pont`/`Ponte`/`Brug`/
  `Pré-Refrão`/`Pre-Coro` added; one repeat-token definition.
- 02 sound.
- 03 amended: no `by` split without a page kind ("Stand by Me"), no artist from a parenthesized tail ("(Live)");
  page kind stripped before the dash split; a credit may be followed by metadata (a copied Campfire PDF).
- 04 amended: the Roman-numeral pattern matched the empty string and a first word; now the whole value. `No capo`
  writes nothing; `Tom`/`Tono` need their colon.
- 05 amended: stars read only in chord rows (`chord()` and the public `isChordLine` untouched); the annotation kept
  out of the token list; dots decided.
- 06 amended: steps 1 and 3 would have broken five PDF fixtures of `DocumentGoldenTest` (a PDF row renders single
  spaced) and `a capitalized lone chord can end a headed section`; both now apply to character-column text only, and
  step 3 to rows of two or more tokens in a sheet with no heading. Lane C should run
  `:data:source:local:implementation:desktopTest` with `:chordpro:desktopTest`.
- 07 amended: reuses plan 01's structure test instead of a second one; cuts a share count glued to the last lyric.

Lanes P and A (plans 08–14, challenged against `ZipReader`, `Inflater`, `ImportBudget`, the archive source, the
planner's `sort` / `planSongs` / `isImportable`, both folder walks, the iOS share extension's Swift, and the editor's
new-song path; `isChordLine` was probed on `- A`, `(a)`, `a`, `Do`, and `La`, all chord rows):

- 08 amended: the suggestion now travels through `PastedSongController`'s `enqueueImport` and
  `ImportController.enqueueImport`, which the draft skipped; only `<title> <page kind> by <artist>` is split (a bare
  " by " made "Stand by Me Lyrics" title "Stand", artist "Me"); the broken test doubles (`ImportControllerQueueTest`,
  `EditorSessionTest`) are named; no use case default.
- 09 amended: states the decision's cost (phones' pickers add camera and photo choices), removes the `js` parameter,
  and the `LibraryFiles` import left unused with plan 10.
- 10 amended: compressed bytes charged once (the draft's double charge refused a nested backup over half its archive);
  the peek buffer capped at 512 bytes (1 MiB per peek otherwise); peek order and reasons pinned; the three `limitOf`
  callers the type change breaks named; the over-8 MB step rewritten as one rule using the declared size; folder walks
  peek before reading an unknown file.
- 11 amended: `hasSongStructure` tightened (one-token rows and `[C](url)` no longer count); `sort`'s size check fixed
  (every flagged file was oversized, `maxSizeOf` being 0); `planSongs`' branch and dotted-name title named;
  `ImportedFile.equals`/`hashCode` carry the flag.
- 12 amended: iOS needs `ShareInboxWriter.swift`'s `ShareLimits` too, or the extension is offered and drops the JSON;
  lyrics-only accepted only under `lyrics` (notes exports write `{title, content}`); `sort` made `suspend`, loose songs
  take the file's index as origin; the node cap bounds the walk, not the parse.
- 13 amended: a chordless section, where `convertFragment` returns null, goes through a new public
  `ChordSheetLyrics.escaped`; `parseXml`'s exceptions caught; the `data/formats` "used only by" sentence reworded;
  unmarked lyric lines read; `.osb` stated as not shareable on iOS; says what to do if 12 was declined.
- 14 amended: a loose `.onsong` reaches `songsIn` before `sort`'s unknown-extension skip; header rule made exact (a
  chord-named title kept by its file name, a third bare line ends it); the body converted between `{c:}` lines, since
  `convertFragment` refuses text holding a directive; iOS share left out explicitly; says what to do if 12 or 13 was
  declined.
