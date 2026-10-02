# Mark right-to-left runs in the exported PDF's text layer with their logical text
**Challenged:** amended — grouping by `getBidiRunDirection` splits RTL text at its digits and embedded Latin (both resolve to LTR runs) and the importer would then put the pieces back in visual order, so a whole run that contains any RTL glyph now carries one `ActualText`; the importer must refuse an `ActualText` whose glyphs are on more than one baseline (Word and InDesign tag hyphenated words that way), must charge the replacement to the text budget (one 64 KiB `ActualText` per glyph would otherwise multiply it by 65,000), treats an empty one as removing its glyphs, and filters it like a glyph (plan 11); the writer test has to look inside the Flate-compressed content stream.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (the export and the importer are shared)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintPdfText.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRenderer.kt` (`selectableText`),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintPdfWriter.kt` (`addPage`),
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractor.kt`
(this plan runs last in lane A, after 02, 07, 09, 10 and 11 have changed this file; build on their code), tests in `presentation/src/commonTest/.../print/` and
`data/source/local/implementation/src/commonTest` (or `desktopTest` for a golden), `presentation/CLAUDE.md` (the PDF
paragraph), `data/source/local/implementation/CLAUDE.md` (the `document/` paragraph), root `CLAUDE.md` (Printing: "New exports can be searched, copied and reimported without OCR").

## Problem
`PrintRenderer.selectableText` emits one `PrintPdfText` per shaped cluster in *logical* offset order, using
`layout.getBoundingBox(offset)`; in a Hebrew/Arabic run the x of successive clusters decreases. `PrintPdfWriter.addPage`
writes them in that order, so the TJ adjustments move leftwards (`val adjustment = (nextX - glyph.x) * 1000 / glyph.height`
is positive). Viewers that order text by position read the run reversed (the finding reports PDFKit extracting it that
way), and Campfire's own `PdfTextExtractor.buildLines` sorts each line's spans by `span.start` (x), so re-importing an
exported RTL song yields each RTL run backwards. Copy/search in position-based viewers is wrong for the same reason.
Whether this matters is a matter of audience: the app is in English and Hungarian and only a user with Hebrew or Arabic
songs hits it, so low.

## Fix
1. **Writer — which glyphs.** Give `PrintPdfText` an `isRtl: Boolean = false`, set in `selectableText` from
   `layout.getBidiRunDirection(start) == ResolvedTextDirection.Rtl`. Do **not** wrap only the maximal sequences of RTL
   glyphs: in Hebrew or Arabic text a digit, a number or a Latin word resolves to an LTR bidi run inside the RTL one
   (and `text()` measures with the default content direction, so a run that starts with Hebrew is an RTL paragraph
   with its Latin embedded), so "שיר 2 שלום" would become two `ActualText` pieces around a bare "2", and an importer
   sorting by x would put the pieces back in visual order. Instead, wrap **every glyph of a `run`** (one `PrintPage`
   text item, which is one unwrapped line segment) that contains at least one RTL glyph, with the run's text in logical
   order: the joined `glyph.text` of that run in emission order, which is `item.text` without the `\u200B`s and with
   NBSP as space, exactly what the glyphs map to. Extract this as
   `internal fun rtlRuns(glyphs: List<PrintPdfText>): List<IntRange>` (index ranges of the consecutive glyphs of one
   `run` index whose run has any `isRtl`).
2. **Writer — the operators.** In `addPage`, for each such range: if a `[` is open, close it with `] TJ`; write
   `/Span << /ActualText <FEFF${printUtf16Hex(text)}> >> BDC`; write the glyphs as today (a `Tf`/`Tm` switch inside the
   range, e.g. when a font fills its 255 codes, stays inside the marked content); after the range's last glyph close
   with `] TJ` and `EMC`, and reopen `[` before the next glyph **without** a `Tm`: `TJ` has already advanced the text
   matrix exactly as `nextX` tracks it. Marked content may sit inside the page's single `BT`/`ET` as long as it is
   balanced within it. Keep the glyph order and positions as they are; a page without RTL runs writes byte-for-byte
   the content it writes today.
3. **Importer (`PdfTextExtractor.interpret`).** Track marked content with a stack local to each `interpret` call:
   `BDC` and `BMC` push (the `glyphs.size` at that point and the `ActualText` string when the `BDC`'s second operand is
   an inline dictionary holding one; a property *name* naming `/Properties` in the resources is ordinary marked content),
   `EMC` pops (an `EMC` with an empty stack is ignored; entries still open when the call ends are dropped, leaving their
   glyphs as they are). On popping an entry with `ActualText`, look at `glyphs` from the recorded index to the end
   (this includes glyphs from forms run inside it, and an inner `ActualText` already replaced):
   - none kept (all filtered out, e.g. rotated): do nothing — there is no position to give the text;
   - their `y` values differ by more than `0.22 x` the smallest size: leave them alone. Word and InDesign put an
     `ActualText` of the whole word over a word hyphenated across a line break, and one span at the first line's
     baseline stretching from the second line's margin would garble both lines;
   - the decoded text (UTF-16BE after a `FEFF` BOM, otherwise PDFDocEncoding read as Latin-1) is empty: remove the
     glyphs (that is how a producer marks a hyphen or a decoration as not text);
   - it is over 64 KiB, contains `\u0000` or `\ufffd`, or `hasLoneSurrogate` (from
     `11-lone-surrogates-from-font-maps-do-not-reach-the-text.md`) is true: leave the glyphs alone;
   - otherwise replace them by one `Positioned` span with that text, `start` = min start, `end` = max end, and `y`, size,
     bold, monospace and raised of the first, **charging** `text.length * 3` to `textBytes` and one to `glyphCount` under
     the same `PdfLimitException` limits as `show()` (the replaced glyphs stay charged; at most double). Without the
     charge a `BDC` with a 64 KiB `ActualText` around each of a million one-byte glyphs is 64 GB of text.
4. Document in `presentation/CLAUDE.md` and `data/source/local/implementation/CLAUDE.md` that RTL runs carry `ActualText` and that the importer honours single-line
   `ActualText`. Note that a PDF from another producer with RTL text in visual order and no `ActualText` is still read
   in visual order (unchanged; out of scope).
5. If execution finds the importer cannot honour marked content cleanly, reduce this plan to steps 1–2 plus the doc
   line and say plainly that Campfire's own re-import of RTL exports stays reversed.

The golden corpus has no `ActualText` (checked: `chrome.pdf` and `libreoffice.pdf` carry `BDC`/`BMC` without it, the
others none), so `DocumentGoldenTest` must be unchanged and proves that plain marked content is a no-op.

## Tests
Pure parts: (a) `rtlRuns` with a hand-built list: an LTR run (no range), an RTL run, a mixed run with a digit and a
Latin word inside Hebrew (one range covering the whole run), two adjacent RTL runs (two ranges). (b) A writer test
that writes one RTL run between two LTR glyphs: the content stream is Flate-compressed, so compare it against
`deflated(expected)` as `aReusedScratchBufferWritesIdenticalPagesIdentically` does (or extract the text-object writing
into an internal function and assert on its uncompressed output) — it holds `/Span << /ActualText <FEFF05D005D1> >> BDC`,
balanced `BDC`/`EMC`, and no `Tm` after the `EMC`; a Latin-only page's content is identical to before. (c) Importer
tests in `data:source:local:implementation` with hand-written content streams: `/Span << /ActualText <FEFF05D005D1> >> BDC … EMC`
around two glyphs reads the logical string; the same around glyphs on two baselines reads the glyphs; an empty
`ActualText` removes its glyphs; a `/P << /MCID 0 >> BDC … EMC` and a stray `EMC` change nothing. The shaping itself
(which glyphs are RTL) needs the platform text engine and is checked by hand.

## Manual check
Export a song with a Hebrew verse to PDF, open it in Preview/Acrobat, select and copy the verse: it pastes in logical
order. Import the PDF back into Campfire: the verse reads correctly. A song with only Latin text produces a byte-for-byte
identical content stream to before.
