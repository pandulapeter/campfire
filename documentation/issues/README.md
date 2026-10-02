# Twelfth review — hostile input in the new import, export and reading code

Reviewed commit `8c267e01a` on `master` (clean tree), covering the 15 commits since the eleventh review
(`9f95b7e73..8c267e01a`): the PDF/Word/TXT import and its pure-Kotlin readers, the PDF export's invisible text layer
and up to four columns, standard-notation saving, Prettify, the import/export flow rework, tap-to-step, What's new.
**Angle:** hostile and messy input, monkey tapping. **Budget:** lean — four Sonnet area reviewers (readers;
`:chordpro`; import/export flow; viewer and text layer), one Opus fuzz run (its PDF/DOCX mutation half was stopped by
a safety classifier, so the readers' figures come from the verification probes instead), Sonnet writers per lane,
Opus/Sonnet challengers.

## Headlines

No data loss was found in the planner, the notation round trip (30 000 random documents) or the export subset. What
was found is mostly a hostile or merely huge file freezing or crashing the app — worst on the web's one thread and
Android's ~192 MB heap — and a few silent changes to what a song looks like:

- **01, 02 (high):** a PDF with no usable `endstream` makes the recovery scan quadratic (5.7 s at 20k objects,
  hours at the cap, no yield); a monospace PDF at 0.1 pt pads gaps to 30 M characters from 600 KB of content — an
  `OutOfMemoryError`, which the importer's `catch (Exception)` does not catch.
- **03–10 (medium):** more unbounded work in the readers — object-stream headers re-parsed per lookup, fonts deep-
  hashed on every `Tf`, forms and shared contents re-read without a budget, CID `/W` ranges expanded to billions of
  puts, XML text concatenated quadratically; and one bad page, font or stray `)` throws away a whole 300-page
  songbook (09), while a real Word songbook past ~7 000 paragraphs is rejected as unreadable (05).
- **18 (medium):** Prettify — run on every import — puts blank lines around a tab or grid inside an unlabelled
  paragraph, splitting one section into three (829 of 20 000 random documents).
- **17, 20 (medium):** converted `[Verse 1]` headings keep their brackets; a line of `[` freezes the converter
  (20 s at 40k).
- **27 (medium):** the import progress dialog cannot be left; a slow songbook can only be escaped by killing the app.
- **32 (medium):** 500 songs that all fall back to `untitled` cost ~125 000 `exists` calls (OPFS round trips on the web).

## Index

| # | Plan | Severity | Lane | Challenge |
|---|---|---|---|---|
| 01 | Make the PDF recovery scan linear by finding every `endstream` once | high | A | amended |
| 02 | Charge monospace gap padding to the text budget | high | A | sound |
| 03 | Parse each object stream's header once; newest definition wins in recovery | medium | A | amended |
| 04 | Accumulate XML element text in a StringBuilder | medium | A | amended |
| 05 | Raise the XML event cap so a large Word songbook is read | medium | A | amended |
| 06 | Memoize the hash of PDF dictionaries, arrays and streams | medium | A | sound |
| 07 | One work budget per PDF for interpreted content and stream input | medium | A | amended |
| 08 | Bound CID `/W` range expansion (per font and per document) | medium | A | amended |
| 09 | A malformed page, font, form or stream costs only itself | medium | A | amended |
| 10 | Cut a page at its full-width rows in one sorted pass | low | A | amended |
| 11 | Skip glyphs whose mapped text is a lone surrogate | low | A | amended |
| 12 | Scale Type 3 widths by `/FontMatrix` | low | A | amended |
| 13 | Accept junk before `%PDF-` in the first kilobyte | low | A | amended |
| 14 | Read Flate streams missing their Adler-32 trailer | low | A | sound |
| 15 | Read tables and nested content controls inside a Word content control | low | A | amended |
| 16 | Mark RTL runs in the exported PDF's text layer with `ActualText`, and honour it on import | low | A | amended |
| 17 | Write converted section headings without brackets, parentheses and colon | medium | B | sound |
| 18 | No Prettify blank lines around tab/grid inside an implicit paragraph | medium | B | amended |
| 19 | Keep the editor's caret next to its text through Prettify | low | B | amended |
| 20 | Linear chord-bracket detection in the converter | medium | B | amended |
| 21 | Linear chord-over-lyric merge and inline replacement | low | B | amended |
| 22 | Never place a chord between the halves of a surrogate pair | low | B | amended |
| 23 | Drop C0 controls and bidi overrides from imported text (keep LRM/RLM/ALM) | low | B | amended |
| 24 | Read a single capitalised chord under a heading as a chord | low | B | amended |
| 25 | Convert `{define}`/`{chord}` names with the notation (not when transposing) | low | B | amended |
| 26 | Ignore `{new_song}` inside verbatim environments when splitting | low | B | sound |
| 27 | Let the user cancel an import while it is being prepared | medium | C | amended |
| 28 | What's new waits for import questions and queued batches | low | C | amended |
| 29 | Release the picked files' bytes once the import is planned | low | C | amended |
| 30 | Report the repeat of a skipped conflict as skipped | low | C | sound |
| 31 | Write an "identical" song after all when its library file is gone | low | C | amended |
| 32 | List names once when numbering a colliding file | medium | C | amended |
| 33 | Correct the docs about a partially ticked setlist export's manifest | low | C | sound |
| 34 | Step on a tap only for touch or the mouse's primary button | low | C | amended |

## Lanes

| Lane | Area | Plans (in order) | Files owned |
|---|---|---|---|
| A | PDF/Word readers; the PDF export's text layer | 01–16 numeric (16 last) | `data/source/local/implementation/.../document/*`, its tests, `presentation/.../ui/print/*` and its tests |
| B | `:chordpro` converter, Prettify, splitter, notation; editor caret | 17–26 numeric | `chordpro/**`, `presentation/.../songEditor/SongEditorScreen.kt` |
| C | Import/export flow; tap-to-step | 27–34 numeric | `presentation/.../CampfireViewModel.kt`, `ImportProgressDialog.kt`, `Dialogs.kt`, new `WhatsNewGate.kt`, `StepTaps.kt`; `domain/implementation/.../ImportFilesUseCaseImpl.kt`; `data/source/local/implementation/.../FileNames.kt` |

Ordering inside lanes (numeric order satisfies all of it): A 01→13, 02→07, 02→10, 06→07→08→09, 11→16; B 17→24,
18→19, 20→21→22; C 27→28→29 (29's clearing sits in 27's `finally`), 30→31.

**Merge order: B, A, C.** B is pure `:chordpro` and touches nothing the others do; A is self-contained apart from
CLAUDE.md files; C goes last because it touches the most shared UI and doc files.

**Shared files:** `data/source/local/implementation/CLAUDE.md` (A; C's 32), `presentation/CLAUDE.md` (A's 16, B's 19,
C's 27 and 34), root `CLAUDE.md` (A's 16, C's 27 and 33), `domain/implementation/CLAUDE.md` (C only),
`chordpro/CLAUDE.md` (B only). Each lane edits only its own sentences; at merge, resolve word by word keeping both
sides. **No plan changes `strings.xml`** (27 reuses the existing `cancel` string).

## Decisions

- **D1 — answered 2026-10-02: accept.** Plans 17 and 18 change what an import writes and what Prettify produces. A song
  imported before them no longer compares equal to a re-import of the same source file (the old brackets / blank
  lines stay in the library copy), so that re-import asks the conflict question instead of saying "already in
  library". **Recommended: accept** (no migrations in this repo; the old output was a different structure).
- **D2 — answered 2026-10-02: keep.** Plan 31 only matters when a sync run (or the user elsewhere) deletes the matched
  library file while an import question is open. **Recommended: keep it** (cheap, and the alternative reports a song
  as "already in library" that is not).
- **Default, not asked:** plan 34's optional step 2 (an edge-inset guard against a cancelled back-swipe stepping the
  song) stays out unless reproduced on a device; plan 25 converts definition names rather than documenting that they
  are left alone.

## Challenge

Ran on all 34 plans (fresh agents per lane): 8 sound, 26 amended, none dropped. Substantive amendments: 01 also bounds
the whitespace walk after a declared `/Length` (a second quadratic, 2.1 s at 2 000 objects); 07 charges each byte once,
yields every 4 MiB, converts 02's check to `PdfLimitException`, and (added after the challenge) charges stream input
for streams sharing one far `endstream`; 08 adds a document-wide font-table budget; 09 rounds rotation to the nearest
90°, keeps encryption fatal and fixes the cyclic-form bookkeeping; 16 tags whole runs containing RTL rather than bidi
runs, ignores multi-baseline `ActualText` and charges it to the text budget; 18's rule rewritten (0 of 20 000 differ
vs 8 275); 19's line matching made linear and CRLF-safe; 21 falls back to the old code where positions are not
monotone; 23 keeps LRM/RLM/ALM; 25 renames definitions only on notation conversion, never on transposition; 27
catches a Cancel landing after preparation finished; 31 writes under the header-derived name, not the library's; 32
lists only once `_2` is taken; 34's type error (`PointerEvent.type`) fixed to `down.type == PointerType.Mouse`.

## Checked and found solid

Notation round trips (30 000 random documents, both directions, no loss); "an `H` anywhere marks it German" never
misfired on tabs, grids, annotations or words; Prettify idempotent over 50 000 fuzz cases and never drops a line; the
PDF text layer against parentheses, backslashes, lone surrogates, NUL, emoji, Zalgo, ligatures and titles; ToUnicode
CMaps and the 255-code font split; magazine layout over 20 000 random cases (2–5 ms at 20 000 units); row-snapping
termination; filter output caps, recursion caps, reference cycles, zip bombs and XML entity expansion; the import
planner's collisions (case/NFC, numbered siblings, setlists following renamed songs), double taps, Back during writing,
process death and the web `import` address; `seenWhatsNewVersions` decoding; the Android open-with alias.

## Dropped after verification

- Retained glyph memory (≈48 B/glyph, ~100 MB peak at the 1 M cap): not an OOM by itself; coalescing would change the
  positional spans the converter relies on; the real OOM is 02.
- Rejecting a PDF over 2 000 pages whole rather than reading its head: kept; silently dropping a book's tail is worse.
- `scan` calling `compressed` with generation 0: objects in an object stream always have generation 0.
- Docx `word/document.xml` hard-coded: every producer writes that name.
- Recovery scan with unterminated arrays: measured linear (depth cap 64).
- Prettify blank lines around `{comment}` in a paragraph: only flips `isContinuation`, pinned by an existing test.
- A comment inside a tab moving between sections: not reproduced.
- Setlist zip subset per file name vs per slot: setlists cannot hold one song twice (`distinctBy` on read and write).
- `intent.component` vs the Android activity alias: nothing compares it; shared ChordPro text passes the converter unchanged.
- A short flick stepping by the half it landed in: intended (622f6dfdd's KDoc).
- A link name hiding its host: documented feature; the library is the user's.

## Measurements

Recovery scan 0.48 / 1.2 / 5.7 s at 5k / 10k / 20k markers; padding 30.0 M chars from 30 000 glyphs (+181 MB), ~900 MB
at 150 000; XML 120 000 CDATA events 9.3 s; 8 000 Word paragraphs exceed the event cap; font hashing 0.1 ms per `Tf`
with 90k widths; 5 MB form 12 ms per `Do`; object-stream headers 2.1 / 8.2 s at 5k / 10k entries; CID `/W` 33k ranges
12.5 s; spanning rows 6.6 s at 14 000; converter `[` × 20k 3.6 s; merge 10k chords 1.37 s. Test JVM heap 512 MB–4 GB.

## Manual checks owed

- Import a real large Word songbook (> 8 000 paragraphs) and a 300-page PDF songbook on an Android phone and in the web
  build: it completes, stays responsive, and Cancel (27) works during reading.
- Import a PDF songbook exported by Campfire with Hebrew or Arabic lyrics and check the copy in Preview, Chrome and
  Acrobat (16).
- Re-import a `.txt` with `[Verse 1]` headings that was imported before 17: the conflict question appears (D1).
- Song details on desktop and the web: right-click and middle-click do not step (34); on Android 14+, an aborted
  edge back-swipe over the song (34's optional step 2).
- Open the app with a conflicting file on a version with What's new: the question comes first, then What's new (28).
- Prettify in the editor with the caret mid-song: the caret stays on its line (19).
