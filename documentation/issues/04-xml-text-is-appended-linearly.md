# Accumulate an XML element's text in a StringBuilder instead of re-concatenating it per event
**Challenged:** amended — the test input is spelled out (as written, `<a>` could be read as part of the repeated unit, which nests 120,000 levels and fails as "XML too deep"); the fix is unchanged.

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/XmlPullParser.kt`, `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/DocxTextExtractorTest.kt`

## Problem
`parseXml` (bottom of `XmlPullParser.kt`) does `stack.last().text += event.value` for every `Text` event. A comment or CDATA
section ends one text run and starts another, so `<a>` followed by many `<![CDATA[...]]>` sections yields one event each, and
each `+=` copies the element's text so far: quadratic in the text length. The 8 MiB XML limit (`MAX_TEXT_FILE_SIZE` for
`word/document.xml`) and the 200,000-event cap allow 8 MB of text in 200,000 pieces.

Measured on HEAD (probe, JVM): `<a>` + 120,000 x `<![CDATA[<54 chars>]]>` + `</a>` (6.9 MB of text) took **9.3 s**; 190,000
one-character CDATA events (cap territory) 0.58 s. A Wasm string concatenation is slower still, and `DocxTextExtractor` is
reached by a drop of any `.docx`.

## Fix
In `parseXml`, keep a parallel stack of `StringBuilder`s (`val texts = mutableListOf<StringBuilder>()`, pushed on `Start`),
append `event.value` to `texts.last()` on `Text`, and assign `element.text = texts.removeLast().toString()` on `End` (only
when the builder is non-empty, to avoid allocating empty strings for the thousands of structural elements). `XmlElement.text`
stays a `var String`, so no caller changes. Keep the blank-text-outside-root check.

## Tests
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/DocxTextExtractorTest.kt` (or a new `XmlPullParserTest.kt`): `manyCdataSectionsInOneElementParseInLinearTime`: parse
`"<a>" + ("<![CDATA[" + "x".repeat(54) + "]]>").repeat(120_000) + "</a>"` (7.9 M characters, under the 8 MiB parser
limit; 120,002 events, under the event cap), assert `root.text.length == 120_000 * 54` and `measureTime < 2.seconds`
(9.3 s today, tens of milliseconds after). Also assert mixed content still concatenates in document order:
`parseXml("<a>x<b/>y<![CDATA[z]]></a>").text == "xyz"` and the `b` child's text is `""`.

## Manual check
None; the Word goldens in `DocumentGoldenTest` cover the ordinary path.
