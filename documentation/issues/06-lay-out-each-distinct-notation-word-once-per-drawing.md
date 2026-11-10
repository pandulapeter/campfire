<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Lay out each distinct word of a tune's drawing once, and count the cache's budget in distinct layouts

**Challenged:** sound

**Kind:** performance  ·  **Severity:** medium (measured)  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationPainter.kt`,
`…/ui/notation/NotationCache.kt` (`notationWordsOf` only), `…/ui/notation/CLAUDE.md` (the cache's "4,000 laid-out
words"), `presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationPaintingTest.kt`,
`…/NotationCacheTest.kt`

Lane D (first).

## Problem

`NotationPainter.paint` lays out every word run of every text of every system on its own:

```kotlin
private fun layout(text: String, font: NotationFont, unitSize: Float): TextLayoutResult = textMeasurer.measure(
    text = text,
    style = textStyle.copy(fontSize = font.size.toFontSize(unitSize), …),
    softWrap = false,
    maxLines = 1,
)
```

through a measurer made with no cache (`SongNotations.kt`, `NotationPainting.newPainter()`:
`TextMeasurer(fontResolver, density, LayoutDirection.Ltr, cacheSize = 0)`). Fret numbers, chord names, repeated
syllables, and bar numbers repeat across a tune. **Painting outweighs the engine**: measured on desktop, paint (text
layouts) took 4.2 ms for a medium tune (257 texts) and 3.8 ms for a tab tune (306 texts); in the app `render` (engine +
parse + paint) took 14–17 ms and 13–16 ms against the engine's 6 and 4–5 ms.

## Fix

1. In `paint(drawing, unitSize)`, keep a map local to that call from (text, `NotationFont` as passed to `layout` — the
   lyric font already scaled) to `TextLayoutResult` (`NotationFont` is a data class; `unitSize` is fixed for the call),
   and look words up in it. Local to one drawing, so a painter used for several drawings (the PDF's long-lived one) holds
   nothing past the call, and the cache's count per rendering below stays exact.
   (If plan 13 has not landed yet, `paintOf` calls `paint(drawing, unitSize)` and gets the sharing too; plan 13 later
   paints system by system and keeps a map per call of its own.)
2. `notationWordsOf(systems)` counts **distinct** layouts (by identity) rather than `texts.size`, since the cache's word
   budget stands for memory; update its KDoc and the `NotationCache` KDoc's "words laid out" wording, and the
   `notation/CLAUDE.md` line "16 tunes or 4,000 laid-out words" if it needs it (it still reads right if "words" means
   layouts kept). Edit only `notationWordsOf` in `NotationCache.kt`: lane N edits `keyOf` in the same file.

Must not break: every word drawn where and how it was (a shared layout is drawn with its own position and colour by
`drawNotationSystem`); a chord drawn in the chord colour, a lyric in the text colour, from the same layout if their
text and font match; boxes and knockouts sized from `firstWords`; painting stays off the main thread on screen.

## Tests

- `NotationPaintingTest`: a drawing whose two systems hold the same chord name in the same font paints them with
  `assertSame` layouts; the same text in a bold and a regular font gets two.
- `NotationCacheTest`: `notationWordsOf` counts a layout shared by three texts once.
- Confirm the gain with the sweep's measurement method (untracked `:tools:screenshots` test timing paint for the medium
  and tab tunes, deleted afterwards): paint should fall well under the measured 4.2 / 3.8 ms. Lane report only.
