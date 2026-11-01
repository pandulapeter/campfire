<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# In a Campfire export, read a word as a chord only where it is printed in the chords' type, so `a b c d` stays a lyric and a title `A` a title

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — plan 11's `?: 1.0` fallback is split into a nullable `knownBodySize`, so an unknown size reaches `Rendered.bodySize` as 0.0 (no test) rather than 1.0 (every chord refused).  
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetCampfireLayoutTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`

## Problem

`{title: A}` over a verse `[C]Hello a b c d` / `a b c d`, printed and imported, comes back with no title (a paragraph
`A`) and the second line as `[a] [b] [c] [d]` (the live run's corpus case, read through `PdfRoundTripTest`'s summary,
showed `[Am][Bm][Cm][Dm]`). The converter's chord test is textual: `chord()` takes a lowercase `a`–`h` as a chord
(`name.firstOrNull() in 'a'..'h' && ChordProChordNames.isChordName(name.replaceFirstChar { it.uppercase() })`), and
`chordRow(line: Rendered, isCampfire)` adds only the export's italic notes, never its type. `extractTitle` returns
before reading a title that is a chord by its text:

```kotlin
if (first < 0 || kinds[first] != Kind.LYRIC || isChordLine(lines[first].text)) return
```

Campfire's export prints every chord of a lyric's chord row in bold at the body size (`chordStyle`), a tab's chord line
in the monospaced face, its title larger, and a lyric regular: the type says which is which. The other writer's plan 07
relies on this plan for the `[Am7add11]d` → `[Am]Am7ad[Bm]d11 [Cm]d` symptom of its finding.

## Fix

1. `Rendered` gains `bodySize: Double = 0.0`, set on every line of an export where `convert` sets `role` and
   `isCampfire` (`line.copy(role = …, isCampfire = true, bodySize = …)`), with the size plan 11 computes, or 0.0 where
   the document has no plain text to read it from (then no size test is made). Plan 11's expression ends in `?: 1.0`,
   which the roles need but this test must not see (a document of italic lyrics alone would then refuse every chord as
   larger than 1.3), so split it:

   ```kotlin
   val knownBodySize = lyricSizes.getOrNull(lyricSizes.size / 2) ?: plainSizes.getOrNull(plainSizes.size / 2)
   val bodySize = knownBodySize ?: 1.0                     // the roles, as plan 11 has it
   … line.copy(role = roles[index], isCampfire = true, bodySize = knownBodySize ?: 0.0)
   ```
2. `chordRow(text, isNote, allowsNotesOnly)` gains `isChord: ((Token) -> Boolean)? = null`; after its existing
   rejection, `if (isChord != null && row.any { rowChord(it.text) && !isChord(it) }) return null`.
3. `chordRow(line: Rendered, isCampfire)` passes, for an export line with a known `bodySize`:

   ```kotlin
   isChord = if (!isCampfire || line.bodySize <= 0.0) null else { word ->
       word.text.indices.all { offset ->
           val x = line.positions.getOrNull(word.index + offset)
           x != null && line.source.spans.any {
               (it.isMonospace || it.isBold && it.size < line.bodySize * TITLE_SIZE_RATIO) && x >= it.start - 0.01 && x < it.end
           }
       }
   },
   ```

   (monospaced for a tab's chord line, which the export writes regular; `TITLE_SIZE_RATIO`, 1.3, as
   `ChordSheetCampfireLayout` reads the title, made shared or repeated with a KDoc).
4. `extractTitle`: for an export, ask the export's own test,
   `if (… || (if (lines[first].isCampfire) chordRow(lines[first]) != null else isChordLine(lines[first].text))) return`.

The `roles` call and plan 11's `isChordRowLine` run before `bodySize` is set, so they keep the textual test as today.
Another producer's sheet and pasted text read as now.

Checked with a patched copy: every `ChordSheetConverterTest` and `ChordSheetCampfireLayoutTest` case passes (the tab
test needed the monospaced exception, the italic-lyric test the unknown-size exception), the case above comes back as
`{title: A}` and `a b c d`, and nothing else in the corpus round trip changes. Re-checked by the challenge (with plans
03, 04, 05, 10, and 11, every `values*` folder, 1–4 columns): goldens, `PdfRoundTripTest` (its capo test prints German
and Latin chord names in every column count), and every `:chordpro` test pass. Older exports read the same: every
release since 5.0.0 writes `/Producer (Campfire)`, prints chords in `chordStyle` (bold, the lyrics' size), and marks
bold fonts (`/FontWeight 700`, `Bold` in the name).

Land after plan 11 (the size) and plan 06 (another writer's, `extractTitle`), before 04 and 05; plan 07 after this.

Docs: `convert/CLAUDE.md`, in the export sentence after "read by the type each line is printed in": "a chord is one
only in the chords' type (bold at the lyrics' size, or a tab's monospaced line), so a lyric `a b c d` and a title `A`
stay what they are".

## Tests

`ChordSheetCampfireLayoutTest` (verified: at HEAD `A` / `[a] [b] [c] [d]`, with the fix `{title: A}` / `a b c d`):

```kotlin
val song = ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(listOf(
    text("A", size = 19.0, bold = true), text("Verse", size = LABEL, bold = true), chords("C" to 0), text("Hello a b c d"), text("a b c d"),
))), isCampfireExport = true)).single()
assertTrue(song.startsWith("{title: A}\n"))
assertTrue("\na b c d\n" in song)
```

`PdfRoundTripTest`: the song above in 1 column, compared by `summary`. Fails at HEAD.
