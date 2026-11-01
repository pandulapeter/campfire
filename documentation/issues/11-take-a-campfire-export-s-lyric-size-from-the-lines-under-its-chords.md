<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Take a Campfire export's lyric size from the lines under its chord rows, so a long details row cannot pass for the lyrics and hide every label

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetCampfireLayoutTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`

## Problem

Corpus `05-grid.cho` (`{key: A}{time: 4/4}`, a grid labelled `4x4`, one verse line), printed in German (also es, fr,
hu, it, ro) in any column count, comes back with the grid's label `4x4` as a stray lyric paragraph; the verse's label
`Strophe` is not read as a label either (it survives only because it is a heading word). With only the key or only the
time it passes, and in English and Hebrew.

`convert` reads labels by their type: bold, not monospaced, a point under the lyrics' size
(`ChordSheetCampfireLayout.roles`, `it.size < bodySize - LABEL_SIZE_STEP / 2`). The lyrics' size is the median over
every plain span of the document:

```kotlin
// The lyrics' size, which the labels are set a point under: the plain text, not the many glyphs of a tab.
val bodySize = rendered.flatten().flatMap { line ->
    line.source.spans.filter { it.text.isNotBlank() && !it.isBold && !it.isMonospace && !it.isItalic }.map { it.size }
}.sorted()
    .let { it.getOrNull(it.size / 2) ?: 1.0 }
```

The details row is plain too, set a point smaller (`detailStyle`, `fontSize - 1`), so it outvotes the lyrics wherever
it has more letters: `Tonart: A   Taktart: 4/4` (19) against `Woke up this morning` (17). `bodySize` becomes the
labels' own size, and no label is smaller than it. Right-to-left lyrics arrive as one span a line, so there a short
details row can outvote many lyric lines too.

Rejected: the largest plain size (tried: a right-to-left song's `ActualText` spans read up to 16.34 at a 14.52 body,
which turns its chord rows into labels — `60-generated-rtl-02`, `-05`, `-14` in 4 columns broke); the median of lines
(still outvoted by a details row wrapped onto two rows over a one-line song).

## Fix

Take the median of the plain size of the lines right under a chord row (a lyric under its chords is always at the
body's size), falling back to today's median where the document has none:

```kotlin
val plainSizes = rendered.flatten().flatMap { line ->
    line.source.spans.filter { it.text.isNotBlank() && !it.isBold && !it.isMonospace && !it.isItalic }.map { it.size }
}.sorted()
val isChordRowLine = rendered.map { page -> page.map { it.text.isNotBlank() && chordRow(it, isCampfire = true) != null } }
val lyricSizes = rendered.flatMapIndexed { pageIndex, page ->
    page.indices.filter { it > 0 && isChordRowLine[pageIndex][it - 1] }.mapNotNull { index ->
        page[index].source.spans.filter { it.text.isNotBlank() && !it.isBold && !it.isMonospace && !it.isItalic }.maxOfOrNull { it.size }
    }
}.sorted()
val bodySize = lyricSizes.getOrNull(lyricSizes.size / 2) ?: plainSizes.getOrNull(plainSizes.size / 2) ?: 1.0
```

and reuse `isChordRowLine[pageIndex]` for the `roles` call that today computes the same list. Only Campfire's export is
read this way. Plan 14 keeps the size for its own test: land this first.

Checked with a patched copy: every `ChordSheetConverterTest` and `ChordSheetCampfireLayoutTest` case passes, the grid
comes back as `{start_of_verse: 4x4}` around it in every language, and nothing else in the corpus round trip changes
for the worse.

Docs: `convert/CLAUDE.md`, after "labels open sections": "(a point under the lyrics' size, read from the lines under
its chord rows)".

## Tests

`ChordSheetCampfireLayoutTest`: `a details row longer than the lyrics leaves the labels labels` (verified: at HEAD
`4x4` comes back as a lyric before `{start_of_grid}`):

```kotlin
val song = convert(
    text("Tonart: A   Taktart: 4/4", size = LABEL),
    text("4x4", size = LABEL, bold = true),
    text("| A7 . . . | D7 . . . |", bold = true, mono = true),
    text("Strophe", size = LABEL, bold = true),
    chords("A7" to 0, "D7" to 13),
    text("Woke up this morning"),
)
assertTrue("{start_of_verse: 4x4}\n{start_of_grid}" in song)
```

`PdfRoundTripTest`: the corpus test's summary drops grid-only sections, so add a check that `05-grid.cho` printed with
the German labels (`printLabelsOf` of `values-de`) keeps the verse's lyrics as the only paragraph text: no section of
type paragraph holding `4x4`. Fails at HEAD.
