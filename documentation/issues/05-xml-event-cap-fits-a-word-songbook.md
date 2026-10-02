# Raise the XML event cap so a large Word songbook is not rejected as unreadable
**Challenged:** amended — the proposed 300,000-element cap could never fire (every element costs a start and an end event, so 600,000 events already bound the tree at 300,000 elements) and its test was really testing the event cap; the element cap is dropped and the fallback lowers the event cap instead.

**Kind:** bug (silent "no readable text" on a legitimate document)  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/XmlPullParser.kt`, `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/DocxTextExtractorTest.kt`, `data/source/local/implementation/CLAUDE.md`

## Problem
`XmlPullParser.next()` starts with `require(++count <= 200_000) { "Too many XML events" }`. An event is every start tag,
end tag (self-closing tags count twice) and text run, so it is a measure of markup, not of risk. Counting the shipped
fixtures: python-docx's `word/document.xml` has 52 events for 5 paragraphs and LibreOffice's 98 for 5 (about 20 a
paragraph), and a Word file with revision attributes, `pPr/rPr` and run properties is about 28. Measured on HEAD: 8,000
paragraphs shaped like Word's (`<w:p w:rsidR><w:pPr><w:spacing/><w:rPr><w:rFonts/><w:sz/></w:rPr></w:pPr><w:r><w:rPr><w:rFonts/><w:sz/><w:lang/></w:rPr><w:t xml:space="preserve">hello </w:t></w:r></w:p>`)
throw "Too many XML events"; 7,000 of them pass (196k events). A songbook of 120 songs with chord lines, lyric lines and
blank paragraphs is 6,000 to 10,000 paragraphs, so a real Word songbook is at or past the cap, and the importer reports
"no readable text" (`DocumentLocalSourceImpl` swallows the exception).

The 8 MiB size limit already bounds the input; the cap's real job is bounding the tree `parseXml` builds, whose memory is
per element (an `XmlElement` with a map and a list is roughly 200 bytes), not per event.

## Fix
- `XmlPullParser`: `200_000` becomes `600_000` events (a 3x headroom: about 21,000 Word paragraphs of the shape above).
  Name it `MAX_EVENTS` in the parser's companion with a KDoc giving the reasoning, including that **the event cap is
  also the tree bound**: every element is a start and an end event (a self-closing one too, through `pendingEnd`), so
  `parseXml` can never build more than `MAX_EVENTS / 2` = 300,000 elements (roughly 60–75 MB at about 200–250 bytes an
  `XmlElement` with its map, list and name). Do **not** add a separate element counter in `parseXml`: at any value
  of 300,000 or more it is dead code, and a lower one would just be a second, stricter event cap.
- Update the `document/` paragraph of `data/source/local/implementation/CLAUDE.md` ("Object, recursion, stream, page, operator, glyph and output limits")
  to name the XML event limit. The root CLAUDE.md does not state it.

If the executor measures a Word-shaped 20,000-paragraph document and the parse (`parseXml` + `DocxTextExtractor.fromXml`)
exceeds about 150 MB retained on the desktop test JVM, use `400_000` events instead (14,000 such paragraphs, still
above the 10,000 of the test).

## Tests
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/DocxTextExtractorTest.kt`: `aWordShapedDocumentOfTenThousandParagraphsIsRead` builds `DocxTextExtractor.fromXml` input
from the paragraph above repeated 10,000 times inside `<w:document><w:body>...</w:body></w:document>` (about 2.8 MB,
about 280,000 events, so it fails today), asserts 10,000 lines come out; and `pathologicallyManyEventsAreStillRejected`:
`"<r>" + "<a/>".repeat(350_000) + "</r>"` (700,002 events) through `parseXml` expects `IllegalArgumentException`. Both
well under 2 s.

## Manual check
Export a long songbook from Word (or generate it with python-docx: 10,000 paragraphs) and import it: it converts rather
than reporting no readable text.
