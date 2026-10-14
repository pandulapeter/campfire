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
`:data:source:local:implementation`. Explicit API mode.

## Rules that span the module

- **The dialect is defined once, in `syntax/`.** `ChordProLineScanner` is the one walk over lines (environment, `#`
  comment, directive, any `{end_of_…}` closing whatever is open). Two readers deliberately keep their own walk:
  `ChordProSummaryCache.safeLineAt` (works on offsets so it does not split the text per keystroke) and
  `ChordProPrettifier` (a stack: an end closes only the innermost environment it names). `MetadataKind` describes every
  metadata directive once (names, spellings, `{meta}` alias, repeatable, timing change, kept in body, readable values)
  in header order; `metadataOrder`, aliases, parser, highlighter, prettifier, and `ChordProMetadataFields` all read it.
- **Whitespace is `Char.isWhitespace`, never regex `\s`** (`ChordProTokens.words`): the platforms' regex engines
  disagree, and pasted charts are full of non-breaking spaces.
- **Text edits keep every other byte.** Tags, languages, links, cover, metadata fields, transposition, and notation
  conversion edit the text, not the model, since the result is written straight back to the user's file.
  `ChordProLines.joinLines` detects the separator per file (CRLF if any line has it, else bare CR if used, else LF) and
  restores a trailing line break; a file mixing endings comes out with the one picked.
- **Where a directive is added**: `ChordProHeaderLayout.metadataInsertionIndex` puts it after the last of its kind,
  otherwise into the header in `metadataOrder`. It only decides where a line is *added*, never reorders. It counts only
  header lines of a changeable kind (`key`, `tempo`, `time`), since a body one is a mid-song change.
- **Delegated environments** (`abc`, `ly`, `svg`, `textblock`) are kept verbatim as chordless lyrics; inside them only
  their closing `{end_of_…}` and a directive written with a colon are directives
  (`ChordProDirectives.matchDelegatedDirective`). Parser, summary, transposition, splitter, and highlighter all agree;
  definitions and `{new_song}` inside one are its text.
- **Directive values may be separated by a colon or whitespace** (the spec allows both); a braced line with a name the
  app does not know stays lyrics.
- **The model is immutable**, and the Compose compiler is told so (`gradle/compose-stability.conf`): never hand it a
  collection mutated afterwards, and add a model class there only once that holds.
- **The model says what a thing is, never how it looks**: no Compose, colours, or measurements here.
- The caller supplies NFC normalization and tag folding; this module has none.

## Model

- `ChordProMetadata.displayTitle(fallback)` is the one rule for naming a song: `{title}` with `{subtitle}` in
  parentheses (what the scan writes into `Song.title` and the editor's app bar shows).
- **`SectionType` has no `Tab` or `Grid`**: tab and grid environments switch the kind of `ChordProLine` inside whatever
  section is running, so a solo of chords over tablature is one section. Outside every section they open an implicit
  paragraph headed by their label (no paragraph if they hold no line); inside one, the section heading wins and the
  label is carried per line (`Tab.label`, `Grid.label`), written back where the environment opens.
- A *run* of tab or grid lines is the unit everywhere: the serializer wraps each run, the transposer moves each as its
  own fingerboard, the viewer wraps each. A blank line inside an environment does not end a run; a second environment
  does, even directly adjacent (`Tab.continuesEnvironment` tells them apart).
- A `{comment}`, break, or `{chorus}` inside an environment cuts the section; the rest is `isContinuation` (not headed
  again; serialized back into one environment). A comment there is never a Campfire 3 heading.
- **Comments record where they were written** (`placement`: `START_OF_SECTION`, `IN_SECTION`, `BETWEEN_SECTIONS`;
  `isInTabOrGrid` for a note inside a tab or grid that has a line, hidden with it).
- `ChorusRecall.blocks` holds the chorus it repeats, resolved by the parser. **Recalls are capped** (a fixed allowance
  plus twice the song's size, in lines, characters, and chords); past it a recall carries nothing and draws as its
  heading, since every later step expands each recall and a tiny file could otherwise become millions of lines.
- Grid lines keep their margins as text; a cell may hold several chords joined with `~`.

## Parser (`ChordProParser`)

`parse`, `summarize` (directives plus "has chords", one walk, run on every file at startup; a tab staff or chord row
counts), `parseMetadata`. `ChordProSummaryCache` reuses the last summary for a keystroke inside a lyric line that
touches no syntax.

- **Total: never throws, never rejects** — the file is the user's and half of it may be under the caret.
- Selectors: `{title-guitar}` is dropped, `{title-guitar!}` is read; an environment with a selector is that environment;
  a tab's selector (negated or not) is kept on its lines and comments for `ChordProTabs`. A `{define}` selector naming
  an instrument is read.
- **Say-once fields** (title, subtitle, artist, composer, lyricist, album, year, capo, duration, cover): the first line
  that says something readable wins wherever it stands; empty or unreadable lines yield to the next; later ones are read
  past (the highlighter marks them `DUPLICATE`).
- **Key, tempo, time**: the song's value is the first readable one in the header (header ends at
  `ChordProHeaderLayout.bodyStartIndex`); a body line counts only if the header has *no* line of that field, an empty
  one included, which is how a cleared value stays cleared. An empty `{key}`/`{capo}`/`{tempo}`/`{time}` declares
  nothing. Invalid tempo/time and a negative capo count as missing.
- `{transpose}` before the first line transposes the whole song (last wins); later ones are modulations
  (`ChordProBlock.Transpose`, offset from the whole-song value, cutting the section; valueless reverts to the previous).
- A readable body `{tempo}`/`{time}` that differs from the one in force is a `ChordProBlock.Timing` (both values in
  force, cutting the section); adjacent ones merge; a group that returns to what was in force is no change. Recalled
  choruses leave timing changes and `{transpose}` out.
- `{meta: title …}` and the other spec-standard names read as their directive. `{define}`/`{chord}` go into
  `ChordProMetadata.definitions` (`{chord}` only where there is no `{define}` of that chord). Fonts, colours, images,
  and page directives are dropped.
- Campfire 3 dialect: `{comment: Verse 1}` outside an environment is a section heading, unless nothing follows before a
  blank line, another section, or the end.

## Text editors (`edit/`)

- `ChordProTags`: `{tag: …}`, one per directive, value taken whole (commas included); `{meta: tag …}` read, never
  written. A line break in a value becomes a space (it would end the directive). Matching ignores case and the caller's
  `fold`. New tag goes after the last tag or at the end of the opening directives.
- `ChordProLanguages`: no ChordPro directive exists; writes `{meta: language en}`, reads also `{meta: lang}`,
  `{language}`, `{lang}`. Values are normalized (`ChordProMetaItems.languageCode`): primary subtag, lowercase,
  two-letter code where one exists (a region or a three-letter code would split a filter group, and the JVM cannot name
  `eng`). Unlisted three-letter codes are kept as written. `und` and `zxx` mean *no* language. `code` is the same rule
  for typed search text.
- `ChordProCoverArt` / `ChordProLinks`: `{meta: cover URL}`, `{meta: link URL Optional name}`. A file is read strictly
  (`http`/`https`, no whitespace); `usableUrl` (`ChordProMetaItems.typedWebUrl`) completes a typed address to `https`
  only where it starts with a host, so `mailto:`, `me@…`, or `https:/…` is refused. Cover sits between album and year in
  `metadataOrder`; links after languages. `setLinks` fills existing link lines in order and keeps unchanged lines'
  spelling; names cannot inject braces or line breaks.
- `ChordProMetadataFields.set`: rewrites the line the parser reads the value from, in its own spelling; drops other
  lines of the field, **but never a body `{key}`/`{tempo}`/`{time}`** (`MetadataKind.isKeptInBody`). A blank value
  removes the field, except a header key/tempo/time the body still changes, which is kept as an empty line so the song
  does not start in the body's first change.
- `ChordProHeader`: `insert` returns the header offset a directive belongs at (a directive at the caret would be valid
  but invisible mid-verse). `declaredMetadata` counts by directive, not value (an empty `{title: }` is a title);
  `DeclaredMetadataCache` is its incremental form and must always equal it. `insertChangeable` writes a mid-song change
  at the caret's line only when the header names a value and the caret is in the body; otherwise into the header.
  `insertDefinition` goes after the last definition or at the header's end.
- `ChordProPrettifier`: formats raw text, never re-serializes a model (unsupported directives and delegated notation
  survive). Used by the editor and every import. **No blank line is added around a tab or grid, or before a comment or
  break inside a legacy heading section or implicit paragraph** — a blank line would split or close it. The first
  readable body `{tempo}`/`{time}` of a song whose header has none moves into the header (never from inside a delegated
  environment); other changes are grouped, tempo first, right before the line they head. `define` is deliberately not in
  `metadataOrder`. `prettifiedOffset` keeps caret and selection on their lines.
- `ChordProSplitter`: splits at `{new_song}`/`{ns}`; one inside a delegated environment is its text, and an unclosed
  environment takes the rest. `comparable` is what an import compares against the file on disk: it also folds line
  endings, notation, and directive spellings (`canonicalDirective`), which Prettify must not do since it would rewrite
  the user's file. Both drop a BOM anywhere (U+FEFF is not whitespace to `trim`, so a `{title}` behind it would be read
  as lyrics).
- `ChordProDuration`: seconds, `m:ss`, or `h:mm:ss`; anything else, zero included, is no duration rather than a guess.

## Chord sheet conversion (`convert/`)

`ChordSheet` / `ChordSheetConverter`: one deterministic conversion for plain text, PDF, and Word; positions may be
columns, PDF points, or Word estimates, and it knows no file format. Rules and near misses are pinned in
`ChordSheetConverterTest`; a change belongs in a test first.

- Passed through as ChordPro when any line is a known directive (`ChordProDirectives.isKnownDirective`) or a chord
  bracketed against a syllable (`[G]Hello`), otherwise only when most lines hold bracketed chords.
- **No recognized song structure means escaped prose, not a guessed song.** `ChordProLiteralText` escapes brackets,
  braces, and a leading `#` so extracted text cannot become markup.
- Single-chord lines and bare Latin note rows (`La La La`) are chord lines only with corroborating evidence (they are as
  likely sung). Labels without a colon become metadata only with a value of the field's shape (`Capo 2`), since "Time
  after time" is a lyric.
- The first `Tempo:`/`Time:` is the header's; later ones (with a colon) are changes before the section they head. The
  row Campfire's own PDF heads a song with (`Key: … Transposition: … Capo: … Tempo: 96 BPM …`, English or Hungarian) is
  read field by field, the transposition dropped since the chords are already in that key. The converter also reads back
  Campfire's PDF export's wrapped chord lines and narrow-column titles.

## Transposition

- `ChordProTransposer`, on the model (viewer) or the text (editor). Sharps or flats follow the key; `preferFlats` forces
  a spelling, so only `semitones == 0` *and* no forced spelling short-circuits. A key in words (`G major`, `Bb-Dur`)
  moves by its note and keeps the words. Comment and label brackets (`ChordProDirectives.hasChordsInValue`) are moved
  but do not vote on spelling or notation. **A bracket moves only if `ChordProChordNames.isChordName` accepts all of
  it** (`[Chorus 2x]` stays). A modulation's stretch moves by its offset too; the text transposition leaves
  `{transpose}` directives alone, since each is relative to the song as written. `transposedOffset` maps the caret.
- `ChordProTabTransposer` moves fret numbers, not the tuning. A tab run moves as a whole and **by octaves if a fret
  would leave the neck**; one that fits no octave (over 24 frets) is left alone rather than half moved. Only staff lines
  are fretted; a line of only chord names is renamed; anything else (`Tuning: D A D G A D`) is byte for byte, though a
  bare `E A D G B E` is six chords. Dashes absorb or pad width changes so columns stay aligned.

## Tabs for one reader (`ChordProTabs`, `ChordProTabRetuner`)

`TabPlayer` comes from `:presentation`, which knows the instruments (selectors, strings, assumed standard tunings,
string names).

- `select`: of each group of adjacent selector tabs, keep the reader's selector, else `guitar`, else the first; `!`
  negates. Comments inside a tab go with it.
- Retuning happens in the same move as the transposition, so one octave decision covers both. **A staff's string names
  are its tuning** (`Eb|` on standard guitar moves that string down a fret); an unnamed staff is in the standard tuning
  `TabPlayer.assumedTuning` picks (`A E C G` is a ukulele's). Same instrument: each string moves by its own distance.
  Other instrument: strings line up as one block, moved by pitch class, no further than two semitones apart.
- Every staff line is renamed for the reader's string in their notation (lowercase for the higher of two same-named
  strings). A run that cannot stay on the neck or be lined up is shown as written, transposition only, names respelled
  (`ChordProStaffNames.respelled`). A staff with a string count no instrument has is untouched. **Text transposition and
  notation conversion never retune.**
- `TabNotice` on the run's first line records octave moves, another instrument, or failure; a plain move says nothing.
  `withNotices` writes them as comments; a `hidesTabs` instrument (keyboard) gets a notice in place of every tab.

## `ChordProTabWrapper`

Cuts a run into rows of a character width (the viewer's only sideways overflow; **nothing scrolls sideways**, since
songs are read with an up/down pedal). Every line is cut at the same columns, system by system: after the last bar line
that fits, else a column every string has a dash on, else the edge. Later rows repeat the string names unless fewer than
eight columns would remain. Never split a surrogate pair or combining mark. A run with no staff line is preformatted
text (`wrapPreformatted`): a chord line travels with the line under it. A run that would wrap into absurdly many rows
(crafted files) is returned whole.

## Notation (`ChordNotation`, `ChordProNotation`)

- Every file and the model are in `STANDARD` (see the last section); other notations are converted only at two
  boundaries: `toNotation` (last rendering step, after transposition) and `convertText` (the editor's field, every other
  character kept; tab chord rows renamed in their columns).
- **A text is read in the notation it is declared in.** A text declared standard is still read as German where a chord
  (or key) uses `H`/`h`; a German chart in a flat key is indistinguishable and reads as standard. German only swaps
  `B`→`H` and `Bb`→`B`. `convertText(text, STANDARD, STANDARD)` normalizes such files (and ASCII accidentals); every
  import does it.
- Lowercase root = minor (`a`, `h`), kept lowercase through conversions. A lowercase bass (`D/f#`) is a note, kept in
  its case. A lowercase root is never a note.
- **Latin needs no declaring**: no Latin name is a standard chord (`ChordProChordNames.latinExpanded`, its test sweeps
  every quality), so it is read wherever it arrives. `ChordProNotation.read` is the one reading path (Latin, then
  lowercase minor, then German where declared).
- Tab string names go with the notation, read in the declared notation, never guessed; the way back must be exact, and a
  system that could not come back exactly is left as written.
- **Numberings are display only** (`isNumbering`, `ChordProNashville`): counted from the song's key (minor from its own
  tonic), keys and definitions stay in letters, each modulated stretch counted from its key, no key = letters. Nothing
  is ever converted into or read from numbers (`[1]` is as often an ending). `shownNames` maps played chords to shown
  names (a step cannot be read back without its stretch). The viewer recognizes displayed names via
  `ChordProChordNames.isDisplayedChordName`.
- Definitions are converted and transposed with the chords but do not vote on notation.

## `ChordProHighlighter`

Typed spans for the editor; lives here so "what is a chord" is decided once. Reads lines through `ChordProLines` and
trims brackets like the parser. A directive the parser reads and drops (bad tempo, time, capo, duration, transpose,
cover, link, language, or an unreadable `{define}`) is one `INVALID` token, decided by the parser's own functions
(`MetadataKind.isReadable`, …), which a table test holds against the parser; an *empty* value is not invalid (the editor
writes those). A repeated say-once directive is `DUPLICATE` from exactly the lines the parser reads past; nothing counts
inside a delegated environment.

## Chords and shapes

- `ChordProChords.parse` reads a name into `Chord`, walking it through `ChordProChordNames.read` — the same walk as
  `isChordName`, so a name is read exactly where it is recognized. **`Chord.id` (`C#:0.3.7.10`) is what player shape
  choices are stored under**; do not change its format. Ambiguous readings (`11`, `13`, `dim`, `C2`, `+5`, `-5`) are
  decided once here. `namesIn` and `ChordProChordRewriter.writtenChordNames` share the walk `visitChordNames`.
- `ChordVoicings` (cache, stored format, entry points), `ChordVoicingSearch`, `ChordShapeGeometry`, and the tables
  (`*ChordTable(s)`, from chords-db, Tabsy's fork, and ChordPro's configurations; notices in each file's KDoc). Every
  table shape must sound exactly the chord it is filed under, stay below the twelfth fret, and be holdable
  (`ChordVoicingTablesTest`). **Table order is written, not computed** (most comfortable first); keep it. Lookup: filed
  shapes, then the quality's open-string-free shapes moved from other roots, then the search (cached per session, thread
  safe; `needsSearch` lets frame-built pages skip it). `playsChordOver` instruments (re-entrant ukulele, mandolin,
  banjo) do not require the bass lowest. The stored choice format (`x 3 2 0 1 0`, `4 7 12 / 0`) arrives through sync, so
  `read` is tolerant. `resetCache` exists for test isolation.
- `ChordTuning`: its `id` must match `:tuner:api`'s `InstrumentTuning` ids (`:presentation` tests it). **A chord name
  names the chord that sounds.** A tuning either has its own table or is one with every string shifted by the same
  interval (`tableTuning`, `shift`, enforced by `ChordVoicingTablesTest`). Seven- and eight-string guitars, bass, and
  violin have no tuning here and get no diagrams. `drawsDefinitions` (standard and ukulele low G only) is where a
  `{define}`, which has no tuning, is drawn. `voicingKey` stores choices by instrument id in standard and tuning id
  otherwise.
- **The banjo's fifth string is a drone** (`droneString`): only open (if its note is in the chord) or muted; never
  fretted, never moved with a shape. Every banjo tuning has its own table.
- `ChordProDefinitions`: `read` parses a definition (frets, base fret, fingers, keys); frets stay within 24 and keys
  wrap past four octaves so no file can make a diagram draw without end. The instrument is the selector's, else by
  string count (four frets a ukulele, a mandolin only with `{define-mandolin}`). A value given twice is `Invalid`;
  `copy` or an unknown string count is `Other`. Last `{define}` per chord and instrument wins, in the first one's place.
  `transposed` moves a shape along the neck (open strings up an octave; fingering dropped where it can't follow);
  `movedBy` adds up, so there and back is zero. `sounding` mutes a banjo drone outside the chord, so reader and editor
  draw one shape. In text, `rewrittenLine` rewrites only the numbers; an unreadable or unmovable line is left byte for
  byte.

## Tests

Everything is pure and tested in `commonTest` (`./gradlew :chordpro:desktopTest`). `ChordProSerializer` lives only in
`commonTest`: the round-trip oracle `parse(serialize(parse(x))) == parse(x)` every model change is held to (the app
never writes a model back). The `desktopTest` contact sheet draws every table shape into `CAMPFIRE_CHORD_QA_DIR` when
set, for a player to review.

## Chord notation in the library

**Every file is in the standard chord notation** (`C D E F G A B`, `#`, `b`), whatever notation the reader prefers
(Settings → Songs): German and Latin are ways of showing and typing, Nashville and Roman of showing only. An import
writes every song in standard notation; a file arriving otherwise (sync, hand edit) is read the same way and normalized
the next time the editor saves it. The editor's field is in the reader's notation (letters for the numberings), so what
they type is never ambiguous.
