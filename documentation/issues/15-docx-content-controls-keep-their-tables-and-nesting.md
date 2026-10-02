# Read tables and nested content controls inside a Word content control
**Challenged:** amended — Word also wraps table **rows** (repeating-section content controls) and **cells** in `w:sdt`, which `table()` drops just the same through its `tr`/`tc` filters, and the chord-grid branch reads only bare `p` cell children; the fix now unwraps `sdt` at all three places with one helper.

**Kind:** bug (dropped text)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/DocxTextExtractor.kt`, `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/DocxTextExtractorTest.kt`

## Problem
The body loop in `DocxTextExtractor.fromXml` reads an `sdt` (structured document tag / content control, which Word uses for cover
pages, tables of contents, "building blocks" and legacy form fields) like this:

```kotlin
"sdt" -> element.child("sdtContent")?.children?.filter { it.localName == "p" }?.forEach { paragraph(it, ::append, ::page) }
```

so a table inside the content control (a chord grid wrapped in a content control is plausible), a nested content control, and a
paragraph-level `sdt` inside a table cell are all dropped silently. The `table()` function handles only `p` and `tbl` cell children too.
Not measured on a real file (none ships); verified by reading.

## Fix
1. Add one helper in `DocxTextExtractor`: `fun XmlElement.unwrapped(depth: Int = 0): List<XmlElement>` returning the
   children with every `sdt` child replaced by the (recursively unwrapped) children of its `sdtContent`, at most 16
   levels deep (deeper content controls are left out; XML depth is already capped at 64, so this only keeps the
   recursion obviously bounded). `sdtPr`/`sdtEndPr` are never returned.
2. Body: `for (element in body.unwrapped())` with the existing `p` / `tbl` branches; the `sdt` branch goes away.
3. `table()`: rows are `table.unwrapped().filter { it.localName == "tr" }` (Word's repeating-section control is a
   `w:sdt` around `w:tr`s), cells `row.unwrapped().filter { it.localName == "tc" }` (a cell-level control wraps
   `w:tc`), and a cell's content `cell.unwrapped()` in **both** branches: the grid test and the grid lines read
   `p` from `cell.unwrapped()`, and the non-grid branch handles `p` and `tbl` from it as today. Use the same helper in
   the second `for (row in table.children.filter …)` loop of the non-grid branch, or reuse the `rows` already built.
Run-level `sdt` (inside a paragraph) already works, because `walk` falls through to `element.children.forEach`.
`DocxTextExtractor`'s other choice, hard-coding `word/document.xml` instead of following the package relationship, is kept: Word, LibreOffice,
Pages, python-docx and Google Docs all write that name, and a relationship parser would add a part to the reader for no
observed file.

## Tests
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/DocxTextExtractorTest.kt`: `aTableInsideAContentControlIsRead`: `fromXml` of `<w:body><w:sdt><w:sdtPr/><w:sdtContent><w:tbl>...</w:tbl></w:sdtContent></w:sdt></w:body>`
(a two-by-one table of "C" / "lyric" cells in the existing test style) produces its lines; the same table with its two
rows wrapped in one `<w:sdt><w:sdtContent>…</w:sdtContent></w:sdt>` inside `w:tbl`, and with each `w:tc` wrapped,
reads the same; a content control nested inside another also reads; 17 levels of nesting is ignored without an
exception. All of these lose their text today.

## Manual check
None.
