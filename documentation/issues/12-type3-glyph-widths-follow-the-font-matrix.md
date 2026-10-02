# Scale Type 3 font widths by /FontMatrix instead of assuming 1/1000 text space
**Challenged:** amended — `widthScale` must be declared before `defaultWidth` (Kotlin initialises properties in order, and a later declaration reads 0.0 there, zeroing every missing width), only an explicit `/MissingWidth` is scaled, not the 500/600 guess, the test text is made consistent, and Campfire's own exports are named as the regression: they are Type 3 fonts with `[0.001 0 0 0.001 0 0]`.

**Kind:** bug (spacing)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfFonts.kt`, `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`

## Problem
`PdfFont.simpleWidths()` stores `/Widths` as they are and `show()` advances by `glyph.width * size / 1000`. For a Type 3 font
(`/Subtype /Type3`) the widths are in glyph space and the font's `/FontMatrix` maps them to text space; `[0.001 0 0 0.001 0 0]`
makes them identical to the 1/1000 assumption, which is the common case (dvips/pdfTeX bitmap fonts use `[0.001 ...]` or
other scales). A producer with another matrix such as `[0.01 0 0 0.01 0 0]` gets glyphs ten times too narrow or wide, so
every inter-glyph gap is misjudged and words fall apart or run together. Not measured with a real file (no Type 3 fixture
ships); verified by reading `simpleWidths`, which never looks at `FontMatrix`.

## Fix
In `PdfFont`, add
`private val widthScale = if (dictionary["Subtype"].name() == "Type3") file.array(dictionary["FontMatrix"]).firstOrNull()?.let { file.number(it) }?.takeIf { it.isFinite() && it > 0.0 }?.times(1000.0) ?: 1.0 else 1.0`
**above `defaultWidth`** (property initialisers run in declaration order, and `init` runs after both). Apply it in
`simpleWidths()` to each `/Widths` value before the `coerceIn(0.0, 10_000.0)` (the clamp then bounds the advance in
1/1000 em whatever the matrix, which is what it is for), and to an explicit `/MissingWidth` in `defaultWidth`; the
fallback 600/500 when there is none is already a guess in 1/1000 em and stays unscaled. Leave CID fonts alone (`Type3`
is always a simple font). Campfire's own PDF export writes Type 3 fonts with `/FontMatrix [0.001 0 0 0.001 0 0]`
(`PrintPdfWriter.writeTextFonts`), for which the scale is exactly 1.0 (`0.001 * 1000.0 == 1.0` in IEEE doubles), so
the `campfire*.pdf` goldens in `DocumentGoldenTest` must be byte-for-byte unchanged; they are this plan's regression
test. Drop this plan if no Type 3 font in the wild with a non-0.001 matrix can be found and the executor prefers not to
carry the extra code; it is cheap, so the default is to land it.

## Tests
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`: `type3WidthsUseTheFontMatrix`: `PdfFont(file, dictionary)` (any `PdfFile`, e.g. of
`PdfTestWriter.song(...)`, with a directly built dictionary) for `/Subtype /Type3 /FontMatrix [0.01 0 0 0.01 0 0]
/FirstChar 65 /Widths [100] /Encoding << /Differences [65 /A] >>`: `font.decode(byteArrayOf(65)).first().width` is
1000 (100 x 0.01 x 1000); with `/FontMatrix [0.001 0 0 0.001 0 0]` it is 100; and a code outside `/Widths` with no
`/FontDescriptor` gets the unscaled 500.

## Manual check
None.
