# PDF export polish — review plans

Reviewed commit: `130daa3b0` ("Implement PDF export."), `master`, clean tree. Date: 2026-10-01.
Angle: the one feature, from every side — layout correctness, output quality on paper, performance and memory,
the sheet's UI and usability, per-platform behaviour. Three reviewers (engine; sheet and wiring; a live run that
rendered 21 PDFs from the demo songs, an 11-song hard-case setlist and a 60-song stress setlist under 15 option
sets and looked at every kind of page), every finding re-read against HEAD, then a challenge of every fix.

Nothing here has been executed. Start with `EXECUTION.md` once the decisions below are answered.

## Headlines

1. **A song title is stranded at the bottom of a column or page** whenever its first section does not fit the room
   left (plan 01) — the most visible defect on paper.
2. **The page does not look like a lead sheet**: one monospace face for everything where the viewer uses the text
   font, annotations drawn like chords, all comment styles like lyrics, choruses unmarked, lone labels, blank rows
   under chord-only lines, grids cut mid-bar, tab systems touching (plans 03–09, 13–15).
3. **Files are 2.4–2.9× larger than they need to be and the export holds them in memory several times over**: 207 KB a
   page with RunLength (29 MB for a 141-page setlist) against roughly 68–82 KB with Flate on 16 grays through a small pure-Kotlin encoder (55 KB with a full zlib); an 18 MB bitmap is
   allocated per page and never released (plans 17–19).
4. **On the web every option change freezes the page** for the whole layout — it runs on the one thread and never
   yields (plan 12) — and on every platform each change blanks the preview to a spinner (plan 21).
5. **The export has no progress, cannot be cancelled, and an out-of-memory error in it crashes the app** rather than
   reporting a failed export (plan 27).
6. The sheet is built from controls the rest of the app does not use, and on a phone the preview and the options
   cannot be seen together (plans 23–25).

## Index

| # | Plan | Severity | Lane |
|---|------|----------|------|
| 01 | Keep a song's heading with its first block | high | A |
| 02 | Draw the page number inside its reserved band | low | A |
| 03 | No empty lyric row under a chord-only line | medium | A |
| 04 | Chord or annotation wider than the column | medium | A |
| 05 | Leave out a section label with nothing under it | medium | A |
| 06 | Chorus recall under its own label | medium | A |
| 07 | Label sections as the viewer does (no "Verse") — D4 | low | A |
| 08 | Break a grid between bars | medium | A |
| 09 | Gap between wrapped tab systems | medium | A |
| 10 | Multi-line setlist description | low | A |
| 11 | Wrap at the last word boundary, keep grapheme clusters | low | A |
| 12 | Measure less and yield while laying out | high (web) | A |
| 13 | Text font for lyrics, monospace for tabs, a hierarchy — D1 | high | A |
| 14 | Annotations and comments in their own styles | medium | A |
| 15 | Mark choruses with a rule | low | A |
| 16 | Gray by luminance | medium | B |
| 17 | Reuse one bitmap for every page | high | B |
| 18 | Write streams straight into the output | medium | B |
| 19 | Flate on sixteen grays — D2 | high | B |
| 20 | PDF title and binary header | low | B |
| 21 | Keep the last preview while a new one is laid out | high | C |
| 22 | Save print options once they settle | low | C |
| 23 | Build the options from the app's own controls | medium | C |
| 24 | Preview and options together on a phone — D3 | medium | C |
| 25 | Page by swipe and keys, zoom | medium | C |
| 26 | Keep selection and export state across recreation | medium | C |
| 27 | Progress, cancel, close on save, report every failure | high | C |
| 28 | Share the PDF where the platform can | medium | C |
| 29 | Name the PDF after the song file | low | C |
| 30 | Retry for a failed layout; empty setlist message | low | C |
| 31 | Describe the preview by its page | low | C |
| 32 | Transpositions as the viewer reads them | low | C |
| 33 | Setlist date in the app's language | low | C |
| 34 | Hungarian terms and existing strings | low | C |
| 35 | Module docs and house code style | medium | D |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|------|------|-----------------|-------------|
| A | Layout engine and styles | 01–15 | `ui/print/PrintLayout.kt`, `PrintLayoutTest.kt`; in `PrintRenderer.kt` only `text`, `width`, `draw`; `SongLyrics.kt` only to make `bars()` internal (08) |
| B | Rasterizing and the PDF file | 16, 17, 18, 19, 20 | `ui/print/PrintPdfWriter.kt`, new `PrintDeflater.kt`, `PrintPdfWriterTest.kt`, `PrintRendererTest.kt`; in `PrintRenderer.kt` only `pdf` |
| C | Sheet and view model | 21–34 | `ui/dialogs/PrintExportSheet.kt`, `CampfireViewModel.kt`, `SongDisplayControls.kt`, both `strings.xml` |
| D | Docs and style | 35 | everything above plus the `CLAUDE.md` files — runs alone, after A, B and C are merged |

**Merge order: B, A, C, then D runs on the merged result.** B is self-contained; A changes the model the sheet
consumes; C rewrites the sheet and so resolves whatever A and B touched in it; D reformats everything and would
conflict with any lane still open.

Shared files and how each lane may touch them:

- `PrintRenderer.kt` — A edits `text`/`width`/`draw` (styles, rules), B edits `pdf` (buffers, gray, title), C's plan
  27 adds the `onPage` parameter to `pdf`. Keep each edit inside its function.
- `PrintExportSheet.kt` — owned by C. A touches only the `PrintLabels(…)` call (07), the `layoutPrintDocument { … }`
  call (12, 13) and, for 13, the `newRenderer()` line and the preview renderer's `remember` keys (the text font
  family); B only the `pdf(snapshot)` call (plan 20). On conflict keep C's structure and re-apply those changes.
- `pdf()`'s parameters end up as `pdf(document, title, onPage = {})`: plan 20 adds `title`, plan 27 adds `onPage`
  after it and moves the call into the view model's `exportPdf` lambda. Plan 19 makes `addPage` suspend.
- `PrintRendererTest.kt` — B owns it; A's plan 07 edits its one `PrintLabels` call and plan 13 adds one test function.
- `CampfireViewModel.setVisibleDialog` — plans 22 (flush the options) and 27 (cancel the rendering) hook the same
  place: one block for a `PrintExport` dialog being replaced or dismissed.
- The `PrintLabels(` call and the `print_*` keys — 07 removes `print_verse`; 34 replaces seven duplicate keys in the
  same lines. Resolve by key name, not by hunk.
- `strings.xml` (both) — A removes `print_verse` (07); C adds and removes `print_*` keys. Word-level merge, every key
  from both sides.
- Root `CLAUDE.md` — B's plan 19 edits the Printing paragraph's sentence on image encoding; D rewrites the rest.

## Decisions (taken by the user on 2026-10-01 — every recommended default)

| | Question | Decision |
|---|----------|---------------------|
| D1 | Lyrics font in the PDF (plan 13) | The app's proportional text font, as in the viewer; monospace only for tabs and grids |
| D2 | Page encoding (plan 19) | 16 gray levels + Flate through a small pure-Kotlin fixed-Huffman deflater: ≈68–82 KB/page (2.4–2.9× smaller than today; a full dynamic-code zlib would reach 55 KB) at an estimated 30–80 ms/page on the web |
| D3 | Phone layout (plan 24) | Preview above the options, both visible; no Preview/Options switch |
| D4 | Headings for unnamed verses (plan 07) | None, as in the viewer |

Taken without asking, as the smaller choices: the sheet closes after a successful save (27); margins step by 5 mm
(23); no creation date in the PDF (20).

## Checked and found solid

- The PDFs are valid: PDFKit/Quartz opens all 21 with the right page counts and boxes; every xref entry is 20 bytes
  and points at its object; every `/Length` is exact; `encodePrintRuns` is correct at the 128 boundaries and linear.
- No ink outside the margins or in the gutter on any page under any option set; fonts 8 and 20 and both margin
  extremes do not overflow; the narrowest column (A4, 25 mm, two columns, 20 pt) is 218 pt — no degenerate width.
- `wrapPrintText` always advances (no infinite loop); `place`/`nextColumn`/`newPage` create no empty page; chords at
  a fragment boundary go to the right fragment; tab strings stay together.
- Chord placement over Latin, Cyrillic, accented Hungarian and Japanese, also after wrapping.
- The key, transposition and chord spelling of all three entry points match the viewer in ordinary libraries.
- `Density(1f, 1f)` keeps the system font scale out; measuring at 1× and drawing under a 3× scale stay proportional.
- Menu wiring, dismissal, web Back and Escape, Android's save picker keeping `.pdf`, both string files complete.

## Dropped after verification

- **`{new_page}` treated as `{column_break}`** — `:chordpro` folds both into one `ChordProBlock.Break` and the viewer
  ignores both; telling them apart is a parser change for a directive the demo library does not use.
- **Right-to-left lines' chords** — the renderer is fixed to LTR; real, but needs bidi-aware anchoring that the
  layout's character-index model does not have. Deferred, not planned.
- **Emoji/fallback lines taller than the row** — rare, and a measured row height would cost the fixed rhythm of every
  other line.
- **Thread-safety of the shared font resolver off the main thread** — not confirmed by any failure in 21 exports.
- **Android writing the bytes to cache before the picker; iOS/web copies in the save path** — existing `FilePicker`
  behaviour shared with every export; plans 17–19 shrink what goes through it.
- **A recalled chorus printed in full each time** (a "reference only" option) and **page numbers in the overview**,
  **"(cont.)" on continuation pages** — features, not polish; ask separately if wanted.
- **Exporting the editor's unsaved text** — the editor's menu has no entry, so nothing wrong is exported.
- **A hyphen where adjacent chords split a word** — the viewer shows the same gap.

## Measurements (desktop JVM, Apple silicon)

| Case | Pages | Layout | `measure` calls | `pdf()` | Size |
|------|-------|--------|-----------------|---------|------|
| Demo song | 2 | 91 ms (cold) | 873 | 26 ms/page | 227 KB/page |
| Hard cases, 11 songs | 18 | 82 ms | 4 696 | 8.2 ms/page | 3.3 MB |
| Stress, 60 songs | 141 | 295 ms | 55 383 | 1.0 s | 29.2 MB |
| Stress, two columns | 115 | 298 ms | 59 488 | 0.85 s | 27.4 MB |

Per page (raw gray 4.51 MB): RunLength 207 KB · Flate 8-bit 77 KB · 16 levels + Flate 55 KB · 1-bit + Flate 24 KB.
Heap growth during `pdf()`: 180–234 MB. Rendered pages and PDFs of the run were kept only in the session scratchpad.

## Challenge

Ran on every plan (two challengers that did not write them): 11 sound, 24 amended in place (each carries a
`**Challenged:**` line), none dropped. What it changed in substance:

- 01 — the first block is placed with `keepWhole = false` when heading + block exceeds a column, which the first fix
  still stranded; a missing song's row counts as the first block.
- 06 — the recall's header goes on the first recalled piece that yields rows (plan 05 could empty the first).
- 08 — reuses the viewer's `bars()`; the first grouping broke on `|:` / `:|`, voltas and margin labels.
- 09 — the gap goes between tablature systems only, not in preformatted runs.
- 11 — the cluster step-back could end a fragment on a lone high surrogate; fragments still concatenate to the input.
- 13 — `FontFamily.Default` is not the viewer's lyric face on the web (Inter is preloaded there), so the renderer takes
  the theme's text family as it takes the monospace one.
- 14, 15 — a frame or bar is built per row so it survives a column break; `rowsFor(block, width)` carries the
  narrower width through every wrap.
- 19 — sizes corrected for fixed Huffman codes (see D2); nibble order, row padding, rounding quantization, zlib
  framing and yielding specified.
- 21 — Save is enabled only for a document laid out from the current options (a held preview could otherwise be
  exported stale).
- 22, 27 — flushing and cancelling hook `setVisibleDialog`, since Escape, web Back and a replacing dialog never reach
  `dismissSheet`; Cancel exists only while pages render (cancelling under Android's live picker would orphan it).
- 23 — the bottom inset belongs to the action row; `Stepper` needs its own labels; margins snap from stored values.
- 24 — the height breakpoint subtracts the sheet's uncovered top inset so the layout does not flip while sliding in.
- 25 — keys live on the preview pane and ignore modifiers (Alt+Left is the browser's Back); a zoom button is added.
- 26 — the transfer flag is cleared only by the job that set it.
- 28 — iOS can share too; a `print_share` string is needed.
- 29 — the name is built from artist and title as `ExportFileNames.kt` does, not from the library file name.
- 32 — uses the viewer's own `transpositions[song, setlist]` lookup on all three branches.
- 33 — the sheet formats the date and copies it into the source; the layout and its labels are untouched.
- 34 — two claimed duplicate strings were not duplicates; seven are.
- 35 — the state holder must keep `rememberSaveable` field by field.

## Manual checks owed after execution

- Print one exported page per platform family and compare with a pre-change print (13, 19).
- Web: a 40+ song setlist — option changes stay responsive (12, 21); memory flat during export (17).
- iPhone: 100+ page export without a memory warning (17–19).
- Android: rotation keeps the selection and progress (26), Share reaches a messaging app and the print service (28),
  gesture and three-button navigation insets (23).
- Phone portrait/landscape and window resize across the breakpoints (24, 25); TalkBack on the preview (31).
- Open an exported PDF in Preview, Chrome, Adobe Reader, Android and iOS Files (19, 20).
