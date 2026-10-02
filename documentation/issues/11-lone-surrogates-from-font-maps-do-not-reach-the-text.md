# Skip glyphs whose mapped text is a lone surrogate
**Challenged:** amended — `hasLoneSurrogate` must be `internal`, not private, for the direct unit test the plan asks for; plan 16 reuses it for `ActualText`.

**Kind:** robustness  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractor.kt`, `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`

## Problem
A glyph's text comes from a ToUnicode CMap (`PdfFont.utf16`, any UTF-16 code unit) or a glyph name (`glyphName`'s `uni` branch
does `it.toChar()` on every 4-hex group with no range check; `uniD800` is a lone high surrogate; only the `u` branch excludes
`0xd800..0xdfff`). `show()` filters only `'\u0000'` and `'\ufffd'`, so a lone surrogate becomes a span, then a character of the
converted song, and is turned into `?` when the file is written as UTF-8 (`encodeToByteArray`). Rare in practice; it needs a
broken font map.

C0 control characters (the other half of the original finding) are handled where they belong, for every document
format at once, in `23-strip-control-and-bidi-characters-from-extracted-text.md`; do not duplicate that here.

## Fix
In the `show()` filter next to `value.none { it == '\u0000' || it == '\ufffd' }` add "and every surrogate in `value` is half of a valid
pair": a small `internal fun hasLoneSurrogate(text: String)` in `PdfTextExtractor` (internal so the test can call it, and so
`16-keep-right-to-left-lyrics-in-reading-order-in-the-pdf-text-layer.md` can apply it to `ActualText` strings) (a high surrogate must be followed by a low one
inside the same string; a low one must follow a high one). Such a glyph is skipped the way a U+FFFD glyph is: not recorded and, as
today for those, not counted as unreadable (keep the existing rule; do not change the readability maths in this plan).

## Tests
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`: a Type1 font with `/Encoding << /Differences [65 /uniD800 /B] >>` and content `(AB) Tj` yields only
"B"; a valid pair (`/uni1F600` is not 4 hex, use `/u1F600`) still reads. Unit-test `hasLoneSurrogate` directly for "a", "\ud83d\ude00",
"\ud83d", "\ude00".

## Manual check
None.
