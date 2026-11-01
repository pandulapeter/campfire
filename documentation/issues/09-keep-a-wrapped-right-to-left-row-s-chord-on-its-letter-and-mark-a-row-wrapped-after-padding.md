<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Measure a marked chord's width without its word joiner, and mark a lyric row wrapped right after a padded piece's direction mark

**Challenged:** amended — (b)'s print fix moved into plan 06, whose cut-at-a-space helper must look past these marks
anyway (and past the marked padding a wrapped right-to-left row may open with); this plan keeps (a) and (b)'s tests.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetCampfireLayoutTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutRightToLeftTest.kt`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`

## Problem

Finding 09 is three failures of right-to-left songs in 4 columns (corpus `chordpro/src/desktopTest/resources/corpus/`).
Each has its own cause:

**(a) `60-generated-rtl-10`, every language: `טו[G]ב` comes back as `ה[G]ַמַּעֲלוֹת`** (the chord between a letter and
its points). The wrapped row's chord row is one span `"⁠G"` (x 486.19–495.19, the word joiner marking a
continuation) and the import sets a right-to-left row's chords by the glyphs' edges, a Hebrew letter's chord ending at
its right edge (`ChordSheetBidiAnchors.indexOf`). The chord's right edge is computed by `chordEnd`, which divides the
span's width by its text length with the word joiner counted:

```kotlin
private fun chordEnd(chords: Rendered, word: Token): Double {
    …
    return position + (span?.let { (it.end - it.start) / clean(it.text).length.coerceAtLeast(1) } ?: 0.0)
}
```

so `G` "ends" at 490.70 instead of 495.19, no letter edge matches (`ב` ends at 495.19), the bidi lookup returns null,
and the exact-position fallback lands on index 1. `render` itself measures the same span without its fillers
(`span.text.filterNot(::isExportFiller)`), so only `chordEnd` disagrees. Traced with a debug copy of the converter:
`insert 'G' x=486.194 end=490.6952 … bidi=null`; with the fix `end=495.196 … bidi=14` and the line reads
`הַמַּעֲלוֹת טו[G]ב,`.

**(b) `60-generated-rtl-15`: a wrapped row is not joined back** (`… [G/B]and` / `[Bm7]ילדה`). The export did not mark
the row: neither it nor its chord row opens with the word joiner. `PrintLyricRows.layOut` marks a row only where the
previous fragment, trimmed of break opportunities, ends in whitespace:

```kotlin
val isContinuation = fragmentIndex > 0 && !fragment.isPrintBlank() &&
    (fragments[fragmentIndex - 1].trimEnd('​').lastOrNull()?.isWhitespace() == true || fragment.firstOrNull()?.isWhitespace() == true)
```

but `padLyricsToFitChords` closes a padded piece whose direction is against the line's with a direction mark
(`bidiMarkFor`: U+200E or U+200F) after its padding, so the fragment `…and‎ ⁠ …‎​` ends in
a mark, not a space, though the cut is at the piece's end.

**(c) `17-hebrew-mixed`, Romanian only: the first lyric comes back as `{comment_box: …}`, its chords on a line of their
own.** This is plan 10's cause (the flush-right test reads the edge from one overshooting line) and is fixed there.

## Fix

(a) In `ChordSheetConverter.chordEnd`, count the characters `render` keeps:

```kotlin
return position + (span?.let { (it.end - it.start) / (if (chords.isCampfire) it.text.filterNot(::isExportFiller) else it.text).let(::clean).length.coerceAtLeast(1) } ?: 0.0)
```

(Optionally also let `ChordSheetBidiAnchors.starts` skip the export's fillers, so the lyric's `positions` follow its
glyphs instead of the linear fallback; not needed for this finding, since the bidi anchors decide first.)

(b) Landed with plan 06: its `isCutAtSpace(previous, fragment)` (`PrintTextWrapping.kt`) reads both ends past the
chord layout's invisible marks (`'​'`, `'‎'`, `'‏'`, `PADDING_MARK`) and decides `isContinuation` and `isHardCut`
alike, so the fragment ending `…and‎ ⁠  ‎​` is a cut at a space, marked as a continuation, and joined with a space.
Nothing to change here; check that 06's helper is in place and add (b)'s tests below. If the rtl-15 round trip still
fails, look at the fragment's last characters before anything else.

Checked: (a) with a patched copy of the converter (all `ChordSheetConverterTest` and `ChordSheetCampfireLayoutTest`
cases pass; over the corpus in 1–4 columns and five languages only rtl-10's chord moves, onto `ב`). (b) is read from the
code and the PDF (the first row's text ends `and ·` with no mark, the second `ילדה··` with none) and was not run
patched; if the rtl-15 round trip below still fails with (b) in place, look at the fragment's last characters before
anything else.

## Tests

- `ChordSheetCampfireLayoutTest`: `a wrapped right-to-left row's chord hangs from the letter its right edge meets`
  (verified: at HEAD `[Am]שלום [G]טוב`, with the fix `[Am]שלום טו[G]ב`):

  ```kotlin
  val sheet = ChordSheet(listOf(ChordSheet.Page(listOf(
      ChordSheet.Line(listOf(ChordSheet.Span("Am", RIGHT - 12.0, RIGHT, BODY, isBold = true))),
      ChordSheet.Line(listOf(ChordSheet.Span("שלום", RIGHT - 24.0, RIGHT, BODY, pieces = listOf(
          ChordSheet.Span("ש", RIGHT - 6.0, RIGHT, BODY), ChordSheet.Span("ל", RIGHT - 12.0, RIGHT - 6.0, BODY),
          ChordSheet.Span("ו", RIGHT - 18.0, RIGHT - 12.0, BODY), ChordSheet.Span("ם", RIGHT - 24.0, RIGHT - 18.0, BODY))))),
      ChordSheet.Line(listOf(ChordSheet.Span("⁠G", RIGHT - 18.0, RIGHT - 12.0, BODY, isBold = true))),
      ChordSheet.Line(listOf(ChordSheet.Span("⁠טוב", RIGHT - 18.0, RIGHT, BODY, pieces = listOf(
          ChordSheet.Span("⁠ט", RIGHT - 6.0, RIGHT, BODY), ChordSheet.Span("ו", RIGHT - 12.0, RIGHT - 6.0, BODY),
          ChordSheet.Span("ב", RIGHT - 18.0, RIGHT - 12.0, BODY))))),
  ))), isCampfireExport = true)
  assertEquals("[Am]שלום טו[G]ב", ChordSheetConverter.convert(sheet).single().trim())
  ```
- `PrintLayoutRightToLeftTest`: lay out (with the test's arithmetic measure) a right-to-left line whose Latin word is
  padded under a wide chord and that wraps right after it, `[Bb]ומה לילה ילדה [G/B]and [Bm7]ילדה` in a column that cuts
  after `and`; assert the second row's lyric part and first chord part have `isContinuation`, and neither has
  `isHardCut`. Fails at HEAD (passes once 06 is in).
- `PdfRoundTripTest`: print `60-generated-rtl-10.cho` and `60-generated-rtl-15.cho` from the corpus in 4 columns and
  compare `summary`s as the corpus test does. Fails at HEAD.
