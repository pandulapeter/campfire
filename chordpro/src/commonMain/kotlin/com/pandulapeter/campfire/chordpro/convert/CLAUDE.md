<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# chordpro/convert

`ChordSheetConverter` is one deterministic conversion for plain text, PDF, and Word; it knows no file format. Its
heuristics and near misses are pinned in `ChordSheetConverterTest` and its siblings: a change belongs in a test first.
What follows is what the tests cannot say: the principles, the contracts, and the limits kept on purpose.

## Principles

- **No recognized song structure means escaped prose, not a guessed song** (`ChordProLiteralText`), and a line that is
  as likely sung (a single chord, `La La La`, "Time after time") is a chord or a field only with corroborating evidence.
- `ChordSheetStructure.hasSongStructure` is the one answer to "is this text a chord sheet at all", stricter than the
  converter (a single-token row, a Markdown link, a lone `(a)` never count): the import's rule for text under an
  unknown name, where a README or a licence must stay skipped rather than become prose.
- A heading word that is also sung (`Hook`, `Tag`, `Coro`, `R`) is one only in brackets, with a colon, or with a chord
  row or a blank line after it. A pre-chorus is the editor's `pre-chorus`, never a site's bridge.
- A row's columns are read only in text laid out in characters: a row of three or more chords typed one space apart
  over a much longer lyric lost them on copy and is written above its lyric, not into it.
- Over a lyric, a chord row's bar lines and repeat counts are dropped, but `N.C.` stays where it stands. A row of chord
  names right over a tab staff is a line of the tab, kept as it stands.

## Campfire's own PDF export

- It is read back exactly (`ChordSheet.isCampfireExport`), by the type each line is printed in
  (`ChordSheetCampfireLayout`): a chord is one only in the chords' type, so a lyric `a b c d` and a title `A` stay what
  they are. **No size is compared with one in another font**: right-to-left letters are drawn in a fallback font of a
  taller line box on some platforms.
- **The export and this reader share a contract**: a row opening with a word joiner
  (`ChordSheetCampfireLayout.CONTINUATION`) is the rest of the line above, wrapped by a narrow column; two word joiners
  continue a word the column cut. The importer's `clean` drops the word joiner from any text. Labels, the playing
  details row (read from the same labels as `strings.xml`, in every language the app prints), a sounding capo
  (`print_capo_sounding`: imported as the capo chart, chords moved down, tab frets kept) and a capo's shapes' key
  (`song_details_capo_shapes`, from any sheet, written as `{key}`) are read by what the export prints; change both
  sides together.
- A row holding right-to-left text arrives as one span in reading order over its glyphs (`ChordSheet.Span.pieces`), and
  its chords are set by the glyphs' edges (`ChordSheetBidiAnchors`). A song set from the right
  (`ChordSheetCampfireLayout.isSetFromRight`) has its chord rows read from their right end. Another producer's rows
  with `pieces` are matched the same way, within an em of the lyric.

## Other producers

- **A plain text whose letters mostly read from the right is laid out as a right-to-left display shows it**
  (`ChordSheetPlainText`): every line flush right, its chord rows aligned visually, not logically, so read by index
  their chords would land in the opposite order. `chrome-hebrew` in `:data:source:local:implementation`'s document
  fixtures shows it.
- A document is cut into songs by `ChordSheetSongSplit` (by a title type that stands out, else by a contents page,
  `ChordSheetIndexTitles`), so a cover in a type of its own does not stop the split. Song-counting numbers are left out
  of the titles.
- A section set apart by look (`ChordSheetLook`: bold, italic, or inset) ends where the lyrics return to the verses'
  look, not only at the next heading; with no heading, a run of bold or italic lyrics is a chorus. An inset alone is
  not one (a second column or a bridge starts further in too), and a run right under the title is the header's (a
  credit, an album).
- Chord shapes listed one a line become `{define}` lines (`ChordSheetDefinitions`), except a chord listed both plain
  and starred (`Bb`, `Bb*`): the star is dropped where the chord is played, so one definition would stand for both.
- What the ChordPro app prints is read back too: margin section labels (`ChordSheetMarginLabels`, only where every
  other line starts right of them, so a lyric against the left edge keeps its first word) and header lines printed in
  left, centre, and right parts (`ChordSheetHeaderParts`; never in Campfire's export, which prints none).
- Genius's injected `You might also like` and closing `12Embed` are left out of a song (never of prose). Ultimate
  Guitar's page furniture is many lines in no fixed shape and is deliberately not filtered.
- **Documented limits**: `Artist - Title` is read artist first, since both orders are common on the web; a title
  ending in a page word with no credit after it (`Three Chords`) loses that word. Campfire's own export, its title
  printed alone, is not read by either.

## Entry points

- `convertFragment` converts what is pasted into the editor: the body only, no title or credit guessed and metadata
  lines kept as text, since the song it goes into has its own header. Only a paste with a chord row or a bracketed
  heading (`[Verse]`, which ChordPro would read as a chord) is converted.
- `setChordRows` sets the rows a format marks itself as they are marked, guessing nothing (OpenSong); `guessesTitle`
  off is for a caller that has the title from another app's field.
