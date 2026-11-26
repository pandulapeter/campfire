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

**A converter change is judged by the import corpus** (`ImportCorpusTest`, a private folder of other programs'
documents named by `campfire.importCorpus`): run it before and after, and read `report.txt`. A change lands only if
no document's overall score falls, unless the commit's author names the document and the owner accepts it. A document
that shows a new problem goes into the corpus with its corrected `.cho` before the fix; a test pins the rule the fix
adds. No rule names a producer or Campfire's export.

`ChordSheetConverter` is one deterministic conversion for plain text, PDF, and Word; it knows no file format. Its
heuristics and near misses are pinned in `ChordSheetConverterTest` and its siblings.

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
- **A chord row that opens with a bar line is a grid**, over a lyric too: its chords are counted by bars (and beats),
  so it stays a one-row grid above its lyric, which loses the indent that lined it up under the
  row. Its cells are written a space apart, chords spelled as a chord row's, and `||:` / `:||` as ChordPro's `|:` /
  `:|`, which it would read as chords. Its margins may hold text, as ChordPro's do: a label before the first bar line
  opens it as a bar would, and a word or two may follow the last (`x2`); `|.` and voltas (`|1`, `:|2`) are bar lines.
- Over a lyric, the bar lines and repeat counts of any other chord row are dropped, but `N.C.` stays where it stands. A
  `\` among a row's chords is a strum mark, dropped. A row of chord names (or playing marks, `PM`) against a tab staff
  is a line of the tab, kept as it stands in its columns against the strings (a margin all its lines share goes); a
  German or Hungarian staff names its B string `H`, and a string may carry a short note in parentheses or in its
  margin (`-0(let ring)|`, `|(repeat`), kept where it stands.
- Over a tab, a heading naming no section (`Intro`, `Solo`) opens one of its kind, until the next heading or lyric.
  A note in parentheses on a line of its own right over a staff (`(let ring)`) is a comment there, not a lyric
  ending that section; one over a lyric is a backing vocal.

## Words any sheet may print

- The playing details row (read from the same labels as `strings.xml`, in every language the app prints), a sounding
  capo (`print_capo_sounding`: imported as the capo chart, chords moved down, tab frets kept) and a capo's shapes' key
  (`song_details_capo_shapes`, written as `{key}`) are read from any sheet by the words Campfire's export prints them
  in; change both sides together. The importer's `ChordSheetText.clean` drops the word joiner from any text.
- A row holding right-to-left text arrives as one span in reading order over its glyphs (`ChordSheet.Span.pieces`), and
  its chords are set by the glyphs' edges, within an em of the lyric (`ChordSheetBidiAnchors`).

## Other producers

- **A plain text whose letters mostly read from the right is laid out as a right-to-left display shows it**
  (`ChordSheetPlainText`): every line flush right, its chord rows aligned visually, not logically, so read by index
  their chords would land in the opposite order. A chord row (starred chords too) and a line with no right-to-left letter (a tab's string) are one
  left-to-right run; a chord row exactly as long as the lyric under it was padded to the lyric's width by a page that
  sets its rows left to right, so its spaces stay where they were typed. In such character columns a chord hangs
  from its letter's edge to within a quarter of a column, so one over the spaces between two words stays between them.
- A document is cut into songs by `ChordSheetSongSplit` (by a title type that stands out, else by a contents page,
  `ChordSheetIndexTitles`); a cover in a type of its own does not stop it. Song-counting numbers are left out of titles,
  and a chordless part, half of whose lines start or end with another song's title, naming two at least, is the
  contents, no song.
- **Then each song is cropped** by the page model's scores of the whole document's lines (`pageScores`, read through
  `ChordSheetPageLines`, the one mapping the model was trained on: change it only with a retrain), held to the model's
  crop rule on its own lines (`ChordSheetSongCrop`), so the contents page still splits. Kept besides: every line
  labelled the title or the artist, and above the song every line with a 2% chance of being either (the header the
  converter reads; below it such a line is a printed page's running header), a blank line between kept ones, and a chord
  row (in a song written in Roman numerals, a row of them, above its first line too) inside the song or straight after it, past only blank lines and other such rows (the model reads a lone closing
  chord as a page number; a chord diagram block under the song, past its heading or first mark, goes). A song the model
  sees none in, or reaching past the lines it read, is converted whole. Its `METADATA` lines are kept, so the rules
  below find a key or capo the clutter around it hid.
- A section set apart by look (`ChordSheetLook`: bold, italic, or inset) ends where the lyrics return to the verses'
  look, not only at the next heading; with no heading, a run of bold or italic lyrics is a chorus. An inset alone is
  not one (a second column or a bridge starts further in too), and a run right under the title is the header's (a
  credit, an album).
- Chord shapes listed one a line become `{define}` lines (`ChordSheetDefinitions`). Of a chord listed both plain and
  starred (`G/B`, `G/B*`) only the plain line is: the star is dropped where the chord is played and the app reads no
  starred name, so no name defines the starred shape without mislabelling one. It goes, with the list's label
  (`Chords used:`), where every other line of the list became a definition; otherwise it stays text.
- The ChordPro program's plain text (`ChordProTextExport`): `-- Start of verse` / `-- End of verse` markers, a `-- Title:`
  line, and a literal `{chorus}` recall, which would otherwise make it pass for ChordPro, are read as headings, the title,
  and a recall.
- What the ChordPro app prints is read back too: margin section labels (`ChordSheetMarginLabels`, only where every
  other line of their column starts right of them, so a lyric against the left edge keeps its first word) and
  header lines printed in left, centre, and right parts (`ChordSheetHeaderParts`).
- A page whose chords lose their places on copy, leaving each on a row of its own and its spoken name in the lyric
  (`(C sharp minor)These`), is set back into chord rows over the lyric by `ChordSheetSpelledChords`, only where every
  spoken name's root is the next chord's. A page whose names are a line each, the lyric cut into pieces between them
  (an offscreen web view's `innerText`), has its pieces joined first, up to the row's last chord and the piece after it.
- Genius's injected `You might also like` and closing `12Embed` are left out of a song (never of prose).
- **A chord site's details and chrome** (`ChordSheetSiteDetails`, by labels and words in every app language, never a
  site's name): a details block whose labels the copy glued onto the values before them (`Tuning: E A D G B EKey: G`)
  is cut back into a row, and one whose last label's value fell onto the next line (`…Capo:` over `2nd fret`) gets it
  back where that line is only a value of the field's shape (a capo, key, tempo, or time); a tuning is
  `{meta: tuning …}` unless standard, a CCLI song number `{meta: ccli …}`, a capo of none nothing. Values that are the site's (difficulty, a transcriber's credit by its label, `Tabbed:` or `Transcribed by:`, or
  by counting its contributors) and whole
  lines that are its controls or calls to action (`Autoscroll`, `1,234 views`) are left out only before the first chord
  row, after the last, or alone between blank lines: between two lyrics they are as likely sung. `Author:` alone stays,
  and goes only glued into a row beside a site's value (`52Difficulty:`). A list of the chords the song plays, one a
  line (`ChordSheetChordList`), goes only corroborated: three or more, each once, all played in the song, outside it
  and under no heading, with a short label right above it naming chords in an app language. A line before the song
  that is just its title and artist (`Artist - Title`, the header's or a caller's `knownTitle`, a page's header; one
  with no artist, `Artist: Title`, by its two names) goes too.
- **Documented limits**: `Artist - Title` is read artist first, since both orders are common on the web; a title
  ending in a page word with no credit after it (`Three Chords`) loses that word.

## Entry points

- `convertFragment` converts what is pasted into the editor: the body only, no title or credit guessed and metadata
  lines kept as text, since the song it goes into has its own header. Only a paste with a chord row or a bracketed
  heading (`[Verse]`, which ChordPro would read as a chord) is converted.
- `setChordRows` sets the rows a format marks itself as they are marked, guessing nothing (OpenSong); `guessesTitle`
  off is for a caller that has the title from another app's field.
