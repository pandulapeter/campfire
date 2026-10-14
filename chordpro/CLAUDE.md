<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :chordpro

The [ChordPro](https://www.chordpro.org) format and the conversion of positioned chord sheets into it. **No dependencies
at all**, not even Koin: stateless `object`s called directly by `:presentation`, `:domain:*`, `:data:formats`, and
`:data:source:local:implementation`. Explicit API mode. Everything is pure and tested in `commonTest`; rules pinned by a
test are not repeated here.

## Rules that span the module

- **The dialect is defined once, in `syntax/`.** `ChordProLineScanner` is the one walk over lines; only
  `ChordProSummaryCache.safeLineAt` and `ChordProPrettifier` (a stack: an end closes only the innermost environment it
  names) keep their own. `MetadataKind` describes every metadata directive once, in header order, for every reader.
- **Whitespace is `Char.isWhitespace`, never regex `\s`**: the platforms' regex engines disagree, and pasted charts are
  full of non-breaking spaces.
- **Text edits keep every other byte.** Tags, languages, links, cover, metadata fields, transposition, and notation
  conversion edit the text, not the model, since the result is written straight back to the user's file.
  `ChordProLines.joinLines` keeps the file's line separator and trailing line break.
- **A directive is added after the last of its kind, else into the header in `metadataOrder`**
  (`ChordProHeaderLayout`); nothing is ever reordered. A body `{key}`/`{tempo}`/`{time}` is a mid-song change, never
  the header's.
- **Delegated environments** (`abc`, `ly`, `svg`, `textblock`) are verbatim chordless lyrics: only their closing
  `{end_of_…}` and a directive written with a colon are directives inside them.
- **The model is immutable**, and the Compose compiler is told so (`gradle/compose-stability.conf`): never hand it a
  collection mutated afterwards. It says what a thing is, never how it looks.
- The caller supplies NFC normalization and tag folding; this module has none.

## Model and parser

- `ChordProMetadata.displayTitle` is the one rule for naming a song (`{title}`, `{subtitle}` in parentheses).
- **`SectionType` has no `Tab` or `Grid`**: those environments switch the kind of line inside whatever section runs, so
  a solo of chords over tablature is one section. A *run* of tab or grid lines is the unit everywhere (serializer,
  transposer, viewer).
- **Recalls are capped** (`ChorusRecall`): past an allowance proportional to the song, a recall draws as its heading,
  since every later step expands each recall and a tiny file could otherwise become millions of lines.
- **The parser is total: it never throws or rejects** — the file is the user's and half of it may be under the caret.
- **Say-once fields**: the first readable line wins; later ones are read past.
  **Key, tempo, time** come from the header; a body line counts only where the header has *no* line of that field, an
  empty one included, which is how a cleared value stays cleared.
- `{transpose}` before the first line transposes the whole song; later ones, and body `{tempo}`/`{time}` that differ
  from the one in force, are mid-song changes that cut the section. Recalled choruses leave them out.

## Text editors (`edit/`)

- Tags, languages, cover, and links are directives: `{tag}` one per value; `{meta: language en}`, `{meta: cover URL}`,
  `{meta: link URL Optional name}`. **Language codes are normalized** to one primary subtag, two letters where one
  exists (a region or three-letter code would split a filter group); `und` and `zxx` mean no language. Values
  cannot inject braces or line breaks.
- `ChordProMetadataFields.set` rewrites the line the parser reads, in its own spelling, **but never a body
  `{key}`/`{tempo}`/`{time}`**; a cleared header value the body still changes is kept as an empty line, so the song
  does not start in its first change.
- `ChordProHeader.insert` puts a directive in the header, never at the caret (valid but invisible mid-verse).
- `ChordProPrettifier` formats raw text, never re-serializes a model, so unsupported directives survive. **No blank line
  is added around a tab or grid, or inside a legacy heading section or implicit paragraph** — it would split or close
  it. `define` is deliberately not in `metadataOrder`.
- `ChordProSplitter` splits at `{new_song}` outside delegated environments. `comparable` (what an import compares
  against the file on disk) also folds line endings, notation, and directive spellings, which Prettify must not do.
  Both drop a BOM anywhere, or a `{title}` behind it would read as lyrics.

## Chord sheet conversion (`convert/`)

`ChordSheetConverter` is one deterministic conversion for plain text, PDF, and Word; it knows no file format. Its
heuristics and near misses are pinned in `ChordSheetConverterTest`: a change belongs in a test first.

- **No recognized song structure means escaped prose, not a guessed song** (`ChordProLiteralText`), and a line that is
  as likely sung (a single chord, `La La La`, "Time after time") is a chord or a field only with corroborating evidence.
- It reads back Campfire's own PDF export (the heading row, wrapped chord lines, narrow-column titles).

## Transposition and tabs

- `ChordProTransposer`: **a bracket moves only if
  `ChordProChordNames.isChordName` accepts all of it** (`[Chorus 2x]` stays). The text transposition leaves
  `{transpose}` alone, since each is relative to the song as written.
- `ChordProTabTransposer` moves frets, not the tuning: a run moves as a whole, **by octaves if a fret would leave the
  neck**, and one that fits no octave is left alone rather than half moved. Only staff lines are fretted; columns stay
  aligned.
- `ChordProTabs.select` keeps, of adjacent selector tabs, the reader's, else `guitar`, else the first. `TabPlayer`
  comes from `:presentation`, which knows the instruments.
- **A staff's string names are its tuning**; an unnamed staff is in the standard tuning `TabPlayer.assumedTuning` picks.
  Retuning happens in the same move as the transposition, so one octave decision covers both; another instrument's
  strings line up as one block. A run that cannot be moved is shown as written, transposition only. Every staff line is
  renamed for the reader's string. **Text transposition and notation conversion never retune.**
- `TabNotice` records only judgment calls (octave move, another instrument, failure); a `hidesTabs` instrument
  (keyboard, violin) gets a notice in place of every tab.
- `ChordProTabWrapper` cuts a run into rows (**nothing scrolls sideways**: songs are read with an up/down pedal), every
  line at the same columns, never inside a surrogate pair or combining mark.

## Notation (`ChordNotation`, `ChordProNotation`)

- **Every file and the model are in `STANDARD`** (`C D E F G A B`, `#`, `b`); German and Latin are ways of showing and
  typing, Nashville and Roman of showing only. Conversion happens only at two boundaries: `toNotation` (last rendering
  step) and `convertText` (the editor's field). An import writes standard; a file arriving otherwise is normalized the
  next time the editor saves it.
- **A text is read in the notation it is declared in**, except that `H`/`h` makes a standard text German, and Latin
  needs no declaring (no Latin name is a standard chord). `ChordProNotation.read` is the one reading path. A lowercase
  root is minor, a lowercase bass a note.
- Tab string names follow the notation; a system that could not come back exactly is left as written.
- **Numberings are display only**: counted from the song's key per modulated stretch, no key = letters, and nothing is
  ever read from numbers (`[1]` is as often an ending). Definitions follow the chords but do not vote on notation.

## `ChordProHighlighter`

Lives here so "what is a chord" is decided once. A directive the parser reads and drops is one `INVALID` token, decided
by the parser's own functions; an *empty* value is not invalid.

## Chords and shapes

- `ChordProChords.parse` reads a name through the same walk as `isChordName`, so a name is read exactly where it is
  recognized. **`Chord.id` (`C#:0.3.7.10`) is what player shape choices are stored under**; never change its format.
- The tables (sources and notices in each file's KDoc): every shape
  must sound exactly the chord it is filed under, below the twelfth fret, holdable (`ChordVoicingTablesTest`). **Table
  order is written, not computed** (most comfortable first). The stored choice format arrives through sync, so `read`
  is tolerant.
- `ChordTuning`: its `id` must match `:tuner:api`'s `InstrumentTuning` ids. **A chord name names the chord that
  sounds.** A tuning has its own table or shifts one by a single interval. `drawsDefinitions` (standard and ukulele low
  G) is where a `{define}`, which has no tuning, is drawn.
- **The banjo's fifth string is a drone**: only open (if its note is in the chord) or muted, never fretted or moved.
- `ChordProDefinitions`: frets stay within 24 and keys wrap past four octaves, so no file can make a diagram draw
  without end. The instrument is the selector's, else by string count (four a ukulele, five a banjo, a mandolin only
  with `{define-mandolin}`). Last `{define}` per chord and instrument wins. `sounding` mutes a banjo drone outside the
  chord, so reader and editor draw one shape. A rewritten line changes only its numbers.

## Tests

`./gradlew :chordpro:desktopTest`. `ChordProSerializer` lives only in `commonTest`: the round-trip oracle
`parse(serialize(parse(x))) == parse(x)` every model change is held to (the app never writes a model back).
`desktopTest` draws every table shape into `CAMPFIRE_CHORD_QA_DIR` when set, for a player to review.
