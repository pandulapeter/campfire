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
heuristics and near misses are pinned in `ChordSheetConverterTest`: a change belongs in a test first.

- **No recognized song structure means escaped prose, not a guessed song** (`ChordProLiteralText`), and a line that is
  as likely sung (a single chord, `La La La`, "Time after time") is a chord or a field only with corroborating evidence.
- It reads back Campfire's own PDF export (the heading row, wrapped chord lines, narrow-column titles). A sheet flagged
  as one (`ChordSheet.isCampfireExport`) is read by the type each line is printed in (`ChordSheetCampfireLayout`; a
  chord is one only in the chords' type, bold at the lyrics' size, or a tab's monospaced line, so a lyric `a b c d`
  and a title `A` stay what they are):
  labels open sections (a point under the body's size in the label's own font, `ChordSheetCampfireLayout.Sizes`: the
  chords' for Latin text, the lyrics' under chord rows for right-to-left letters, which some platforms draw in a
  fallback font of a taller line box, so no size is compared with one in another font; a label over a tab is the
  tab's), grids, italic comments, and boxed comments come back as
  written (an italic lyric is told from a comment by its distance under the chord row above,
  `ChordSheet.Line.baseline`; a right-to-left box's text is found up to a point further out than its inset, where 5.2
  drew it), the padding under wide chords is left out, the lyric-type rows right under the title,
  before the heading's gap, are the artist, however many, a chord is set on the character it stands
  over, an italic run among a row's chords is an annotation (`[*twice]`) where it stands (a right-to-left one over a
  left-to-right lyric where its leftmost glyph is), a row whose text opens with
  a word joiner (`ChordSheetCampfireLayout.CONTINUATION`) is the rest of the lyric above, wrapped by a narrow column,
  and joined back to it (its chord row too is marked, and may hold notes alone; a marked chord row with no marked
  lyric under it is the rest of the line above; a row of notes alone is a chord row only over a marked row in the
  chords' or the lyrics' type, so an annotation printed alone over its padding opens the line, and a comment's cut
  rest stays the comment's); two word joiners open a row that
  continues a word the column cut (a lyric's, a title's, a comment's, or a labelled value's), joined back with no
  space, and the importer's `clean` drops the word joiner from any text; and staff lines are told by the test
  the export engraves by. A row holding right-to-left text arrives as one span in reading order
  over its glyphs (`ChordSheet.Span.pieces`), and its chords are set by the glyphs' edges (`ChordSheetBidiAnchors`): a
  Hebrew letter's chord ends at its right edge, a Latin one's starts at its left, a space only where no letter fits, and
  one left of the row's last letter, over the spaces and padding drawn past it, is after the line's end, chords sharing
  a place written in the order a row set from the right reads them; a
  row of no right-to-left letter whose glyphs are drawn out of its order (`2 2 tonight` in a Hebrew song) is read the
  same way. A row of chords alone in a song set from the right (`ChordSheetCampfireLayout.isSetFromRight`, the song's
  side) is read from its right end, where the export starts it, and its word joiner opens that end's chord, which is not
  the row's first span on the page. A wrapped tab system comes back as the system it was
  printed as.
- An export's label opens the kind its leading heading word names, in every language the app prints labels in,
  whatever follows it (`Chorus (twice)`); a pre-chorus is the editor's `pre-chorus`, never a site's bridge. The
  playing details row (key, transposition, capo, tempo, time) is read in every language the app prints it in, from the
  same labels as `strings.xml`; a label that is an ordinary word too (`Metro`, `Mesure`) takes only a value of its
  field's shape, and a row of it marked as a continuation is the rest of the value above, wrapped by a narrow column
  (`joinWrappedDetails`). A capo marked as sounding (`print_capo_sounding`) is imported as the capo chart: the chords
  and key moved down by the capo in the standard notation, tab frets kept. A capo marked with its shapes' key
  (`song_details_capo_shapes`, in any of the app's languages, from any sheet) writes that key as the song's `{key}`.
  In an export, a key printed further down is the key change it stands for, written where it stands (nothing where it
  names the key in force), and a sounding capo chart is moved part by part, each spelled by its own key.
- Over a lyric, a chord row's bar lines and repeat counts are dropped, but `N.C.` stays where it stands.
- Any row with `pieces` is matched by those edges (another producer's PDF, put into reading order by `:data:formats`),
  within an em of the lyric rather than the export's exact point.
- **A plain text whose letters mostly read from the right is laid out as a right-to-left display shows it**
  (`ChordSheetPlainText`, chord rows left out of the vote): every line flush right, a line not shown in reading order
  written over its columns as `pieces`, so its chords are matched like a PDF's. Its chord rows align visually, not
  logically: a row of two chords or more is one left-to-right run in that display, drawn from its left end, so read by
  index its chords would land in the opposite order. `chrome-hebrew` in `:data:source:local:implementation`'s document
  fixtures shows it: Chrome's print of the sheet in a `<pre dir="rtl">` and the plain text give the same song.
- A document is cut into songs by `ChordSheetSongSplit`. The titles' type is the first one opening two songs of
  different titles, else the document's first title's, so a cover in a type of its own does not stop the split. A
  page opens a song with a title in that type (a bold title of the body's size not followed by more bold lyrics, which
  is a chorus); a type larger than the body opens one on any line of a page, a line under one in its type being the
  title wrapped. A page repeating the current song's title continues it, the repetition left out where it is marked
  (`(cont.)`, a page number) or set in the title's type.
- A contents page (`ChordSheetIndexTitles`: most of its lines, at least three, found again on three other pages or
  more) also opens a song at each line it lists, where it stands under no chord row and, in a book with a title type,
  in that type (an index of artists lists the credits too) and not wrapped; a book in one type is split by its
  contents alone. Numbers counting the songs (the openings after the front matter, rising mostly by one) are left out
  of the titles.
- A section whose lyrics are set apart from the verses before it (`ChordSheetLook`: bold or italic, or inset) ends
  where the lyrics return to the verses' look, not only at the next heading; with no verse before it, only a return
  from bold or italic to plain type ends it (a centred sheet's lines each start elsewhere, and a line in another type
  inside it is a backing vocal or an aside). An inset counts only where two of the section's lyrics or more, all of them so far, start more than a
  character and a half to one side of the edge the verses share; a sheet whose verses share none (centred,
  right-aligned) sets a section apart by type alone. A chorus heading
  over lines neither set in the look the song set its chorus apart in nor singing its words recalls it (`{chorus}`).
- With no heading, a stanza's run of two lyrics or more set in bold or italic at the size of the song's plain verses is
  a chorus, ended where the lyrics return to plain type; an inset alone is not one (a second column or a bridge starts further in too),
  and a line with no word (a tab's string, a stray accidental) neither belongs to such a run nor ends it.
- Chord shapes listed one a line (`Bb(#11)/A = x03330`) become `{define}` lines in the header
  (`ChordSheetDefinitions`), except a chord listed both plain and starred (`Bb`, `Bb*`): the star is dropped where the
  chord is played, so one definition would stand for both.
- A lyric indented under its first chord starts at that chord; two chords or more in the indent keep it.
- A header labelled with a colon or a semicolon names title (`Song;`), album, and year; a credit ending in a year
  gives both, and a credit printed twice is written once; an Artist label's value is a name, kept whole unless a comma
  sets a year off (`The 1975`).
- `convertFragment` converts what is pasted into the editor: the body as `convert` writes it (chords set, headings as
  sections, prose escaped), but no title or credit guessed and metadata lines kept as text, since the song it goes into
  has its own header. Only a paste with a chord row or a bracketed heading
  (`[Verse]`, which ChordPro would read as a chord) is converted.
- `setChordRows` sets the rows a format marks itself as they are marked, guessing nothing, for OpenSong.
- `convert` can be told not to guess a title (`guessesTitle`), for a caller that has the song's title from another
  app's field.
- A row of chord names right over a tab staff is a line of the tab, kept as it stands: its columns name the staff's
  bars, and it was never written over a lyric.
- The heading vocabulary lives in `ChordSheetHeadings`. A heading word that is also sung (`Hook`, `Tag`, `Coro`, `R`)
  is one only in brackets, with a colon, or with a chord row or a blank line after it.
- Section labels printed in a margin column (the ChordPro app's `label`, `V1`, `Ch1`, `A1`) open the sections they
  label (`ChordSheetMarginLabels`): only where every other line between a page's first and last label starts right of
  all of them, so a chord row or a lyric set against the left edge keeps its first word. A label no heading word or
  abbreviation names is a verse under its own name.
- The rest of what the ChordPro app prints is read back too: a header line printed in left, centre, and right parts
  (`ChordSheetHeaderParts`, a gap of three ems, above the first chord row only; never in Campfire's own export, which
  prints none) is one line per part, the part beside
  the title or a line centred under a centred title being its subtitle; a note among a row's chords (`(x)`,
  `(to v:)`) is an annotation where it stands, one ending the row staying text after the lyric; the dashes it fills
  a chord's width with at a lyric's end are left out; and a `©` line is the copyright wherever it stands.
- The title shapes a copied page opens with come from `ChordSheetTitles`. `Artist - Title` is read artist first, a
  documented limit, since both orders are common on the web; a title ending in a page word with no credit after it
  (`Three Chords`) loses that word, the same documented limit, which Campfire's own export, its title printed alone, is
  not read by.
- A row's columns are read only in text laid out in characters: a row of three or more chords typed one space apart
  over a much longer lyric lost them on copy and is written above its lyric, not into it.
- Genius's injected `You might also like` and closing `12Embed` are left out of a song (never of prose). Ultimate
  Guitar's page furniture (`Difficulty:`, `Author:`, a rating) is many lines in no fixed shape and is not filtered.
- `ChordSheetStructure.hasSongStructure` is the one answer to "is this text a chord sheet at all", stricter than the
  converter (a single-token row, a Markdown link, a lone `(a)` never count): the import's rule for text under an
  unknown name, where a README or a licence must stay skipped rather than become prose.
