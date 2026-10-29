<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
# Pad a right-to-left lyric so no chord over it is shifted off its letter, and import back every chord where it was

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLyricRows.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLyricLayout.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songLayout/LyricsPadding.kt` and its tests, `chordpro/src/desktopTest/resources/corpus/`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`

## Problem

In a right-to-left song, the export places each chord by `printChordLeft`, then moves it aside where it would run into
the previous chord or past the right margin, whereas a left-to-right lyric is padded (`padLyricsToFitChords`) so every
chord stands exactly at its letter. The moved chord no longer hangs from any letter's edge, so the import of the PDF
(`ChordSheetBidiAnchors.indexOf`, exact tolerance for Campfire's export) finds no anchor and falls back to the
left-to-right position search, which sets it a letter or a word off, at the line's end, or between a letter and its
points. Seen in generated songs of the corpus (seed 2026 of the generator the hunt used, below):

- chords on neighbouring short words or a mid-word chord in Arabic: `[F#m]يا قلب[C]ي يا` reads back `يا قلبي يا[C]`;
  `[G/B]and [Bm7]ילדה` reads back `and י[Bm7]לדה`;
- a chord hanging rightwards from a Latin letter or a digit at a line's right end runs past the margin and is pulled
  back: `tonig[F#m]ht …` reads back with the chord at the line's end, `[E7]2 נעים` as `2 [E7]נעים`;
- a line whose first word is a number in an RTL song (`[D]2 2 tonight`) is drawn in an order its glyph positions do
  not follow, and has no right-to-left letter to anchor on, so its chord reads back at the end.

Users who print a Hebrew or Arabic songbook and import it again (or share the PDF with another Campfire user) get
these chords in the wrong place; pure Hebrew lines with chords on words wide enough read back exactly.

## Fix

Pad right-to-left rows as left-to-right ones are: have `padLyricsToFitChords` reserve the space a chord needs on the
side it hangs to (leftwards from a right-to-left letter, rightwards from a left-to-right one), measured in the drawn
order of the row (`BidiOrder`), so `printChordLeft` never has to move a chord; keep the padding marked (U+2060 with
no-break spaces) so the import drops it. For a chord that would cross the right margin from a left-to-right letter at
the line's right end, pad the line's right end instead. For an all-left-to-right line in a right-to-left song whose
drawn order differs from its reading order (it opens with a number), write it with pieces like a row holding Hebrew
(`PrintPdfWriter`'s `rtlRuns`) so its glyphs can be found. Drop this plan's last point if the fix of the first two
already covers it.

## Tests

Add these songs to `chordpro/src/desktopTest/resources/corpus/`; `PdfRoundTripTest` must pass with them, and so
must `:chordpro`'s corpus tests:

```
--- 60-generated-rtl-00.cho
{title: הַמַּעֲלוֹת ילדה}
{key: Am}
{start_of_verse}
[Em]ומה 1984 נעים בְּשׁוּב [Am]ומה
[D]2 2 tonight
{end_of_verse}
--- 60-generated-rtl-01.cho
{title: עכשיו ומה}
{key: Am}
{start_of_verse}
[Dm]הַמַּעֲלוֹת ומה [D]100 לילה ומה
tonig[F#m]ht הַמַּעֲלוֹת בְּשׁוּב 2 ומה
נעי[Em]ם ומה [F#m]שלום [D]ילדה שלום
{end_of_verse}
--- 60-generated-rtl-02.cho
{title: سَلَامٌ دائما}
{key: Am}
{start_of_verse}
[Am]سَلَامٌ عَلَيْكُمْ دائما قلبي عين يا
[D]عين [Dm]قلبي دائما [F#m]دائما سَلَامٌ
[F]دائما [F#m]يا قلب[C]ي يا
{end_of_verse}
--- 60-generated-rtl-05.cho
{title: سَلَامٌ دائما}
{key: Am}
{start_of_verse}
lo[Em]ve دائما and [Bm7]قلبي
[Dm]قلبي قلبي [Dm]قلبي 2
قل[Dm]بي [Am]عين [F#m]قلبي يا دائما دائما
[G/B]rock [Cmaj7]عين عَلَيْكُمْ دائما
{end_of_verse}
--- 60-generated-rtl-07.cho
{title: ומה בְּשׁוּב}
{key: Am}
{start_of_verse}
ro[F]ck שִׁ[Cmaj7]יר [G]2 הַמַּעֲלוֹת שלום הַמַּעֲלוֹת
[D]and [Bm7]טוב, בְּשׁוּ[G/B]ב בְּשׁוּב בְּשׁוּב
{end_of_verse}
--- 60-generated-rtl-08.cho
{title: ليل قلبي}
{key: Am}
{start_of_verse}
[E7]يا ro[D]ck قلبي [D]عين
[Am]عَلَيْكُمْ دائما يا [Em]love قلبي
[Bm7]سَلَامٌ عين عين قلبي [G]سَلَامٌ
{end_of_verse}
--- 60-generated-rtl-15.cho
{title: שלום שִׁיר}
{key: Am}
{start_of_verse}
[Bb]ומה לילה ילדה [G/B]and [Bm7]ילדה
[Em]ומה [Em]הַמַּעֲלוֹת [Bm7]ומה ומה טוב, [Bm7]ומה
{end_of_verse}
```
