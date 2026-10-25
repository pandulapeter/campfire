<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Print each formatted lyric or comment fragment as one text carrying its styles, so right-to-left lines read in order again

**Kind:** bug (regression, unreleased)  ·  **Severity:** low  ·  **Platforms:** all (PDF export and print preview)
**Challenged:** sound
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRows.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintDocument.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRenderer.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLyricPieces.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/CLAUDE.md`

## Problem

Since 14fbf0152 (not in any release tag yet), a lyric row with markup is printed as one `Part` per change of style,
placed left to right in logical order (`PrintRows.kt`, lyric rows):

```kotlin
if (!isChordsOnly) {
    var x = 0f
    pieces.forEach { piece ->
        parts += Part(piece.text, x = x, y = lyricY, style = piece.style)
        x += measure(piece.text, piece.style)
    }
}
```

and the same in `formattedRows` for comments:

```kotlin
val parts = styledPieces(stripped.formatting, start, fragment, style).map { piece ->
    Part(piece.text, x = x, style = piece.style).also { x += measure(piece.text, piece.style) }
}
```

Each piece is laid out on its own (`PrintRenderer.text`, `measurer.measure(text = value, …)`), so the bidi algorithm
only ever sees one piece. A Hebrew or Arabic line `שלום <b>עולם</b>` prints `שלום` on the left and `עולם` on its right,
so a right-to-left reader reads them in reverse; the same happens to an opposite-direction run split by a style change
inside a left-to-right line (`hello שלום <b>עולם</b>`). Before that commit the row was one
`Part(fragment, y = lyricY, style = lyricStyle)`, laid out by one `TextMeasurer` call whose paragraph direction comes
from the content, so such lines printed in the right order (only chord anchoring, measured from the left, was and stays
left-to-right only). The screen draws one `AnnotatedString` and is fine. The PDF's text layer is also affected:
`rtlRuns` (`PrintPdfText.kt`) groups right-to-left clusters per text, and the pieces now split those runs.

## Fix

**Recommended: one text per fragment, carrying its formatting as spans.**

1. `Part` (`PrintRows.kt`) and `PrintText` (`PrintDocument.kt`) gain
   `val formatting: List<ChordProLine.Lyrics.Formatting> = emptyList()`, offsets into the part's own text;
   `PrintLayout.kt` copies it where it turns a `Part` into a `PrintText` (`PrintText(part.text, x + part.x, …)`).
2. Lyric rows: emit one `Part(fragment, y = lyricY, style = lyricStyle, formatting = …)` with the formatting that
   reaches the fragment, clipped and shifted by `start`. Keep `styledPieces` / `widthUntil` only for the chord anchors
   (`pieces.widthUntil(...)`), which need the width of the styled prefix. Comments (`formattedRows`): one `Part` per
   fragment the same way.
3. `PrintRenderer.text(value, style)` becomes `text(value, style, formatting)`: with formatting, measure an
   `AnnotatedString` whose `SpanStyle`s set `FontWeight.Bold`, `FontStyle.Italic`, and `TextDecoration.Underline`;
   without, the plain string as now. The page cache builds `text(it.text, it.style, it.formatting)`. `width(...)` is
   unchanged (the layout measures by pieces).
4. `selectableText` keeps `item.style` for every cluster of the text, so the text layer holds one run per fragment
   (restoring `rtlRuns`) and never marks markup's bold as bold: Campfire's own importer reads a bold word as a chord
   (see plan 06, which this makes a test-only change).
5. `PrintStyle.underline` and its line in `toTextStyle` lose their only writer (`styledPieces`); drop them if nothing
   else sets them, and keep `styledPieces` producing the bold and italic it needs for widths.

Alternative (smaller, not recommended): keep the pieces, and place them right to left from the fragment's total width
when the fragment's first strong character is right-to-left. It fixes pure right-to-left lines but still swaps an
opposite-direction run split by a style change (`hello שלום <b>עולם</b>`, a Latin name in bold inside Hebrew), and keeps
the split text-layer runs.

`CLAUDE.md` (ui/print): replace "prints as one text per change of style (`styledPieces`), side by side" with "prints
as one text carrying its markup as spans, laid out once so right-to-left lines keep their order; the text layer keeps the
text's own style".

## Tests

`./gradlew :presentation:desktopTest`, `PrintLayoutTest` (pure layout, arithmetic measure):
- rewrite `formatted lyrics print in their styles, one after the other, under their chords`: the lyric is one text
  `"Some bold and under"` in the unbolded lyric style, with formatting `[5, 9) BOLD` and `[14, 19) UNDERLINE`, and `Am`
  still anchored at the bold piece's x (`measure("Some ", …)`);
- rewrite `a comment prints in its markup's styles, plain and boxed`: one text `"Play slowly here"` with `[5, 11)
  UNDERLINE`;
- new: `שלום <b>עולם</b>` prints as one text `"שלום עולם"` with `[5, 9) BOLD`, which is what lets the renderer lay it
  out once (the bidi order itself needs a real `TextMeasurer`, which the pure layout tests do not have).
