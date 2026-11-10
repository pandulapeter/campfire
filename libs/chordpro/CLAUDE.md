<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :libs:chordpro

The ChordPro format and the reading of ABC tunes into it (chord sheets are converted by `:libs:chordsheet`). **No
dependencies at all**, not even Koin: stateless `object`s called directly. Everything is pure and tested in
`commonTest`; rules pinned by a test are not repeated here.

## Rules that span the module

- **The dialect is defined once, in `syntax/`.** `ChordProLineScanner` is the one walk over lines; only
  `ChordProSummaryCache.safeLineAt` and `ChordProPrettifier` keep their own. `MetadataKind` describes every metadata
  directive once, in header order, for every reader.
- **Whitespace is `Char.isWhitespace`, never regex `\s`**: the platforms' regex engines disagree, and pasted charts are
  full of non-breaking spaces.
- **Text edits keep every other byte.** Tags, languages, links, cover, metadata fields, transposition, and notation
  conversion edit the text, not the model, since the result is written straight back to the user's file.
  `ChordProLines.joinLines` keeps the file's line separator and trailing line break.
- **A song is read with its first `MAX_TAGS` tags and `MAX_LANGUAGES` languages**; the file keeps the rest, and no edit
  drops them: `ChordProTags.addTag` scans the lines, and a language edit starts from `ChordProLanguages.declaredIn`,
  never from the parsed list.
- **A directive is added after the last of its kind, else into the header in `metadataOrder`**
  (`ChordProHeaderLayout`); adding a line never reorders the others, only Prettify reorders the header. A body
  `{key}`/`{tempo}`/`{time}` is a mid-song change, never the header's.
- **Delegated environments** (`abc`, `ly`, `svg`, `textblock`) are kept verbatim, with no chords, markup, or
  definitions: only their closing `{end_of_…}` and a directive written with a colon are directives inside them. The
  one exception: a transposition writes each `abc` tune's own `%%transpose` (`AbcTranspositions`).
- **The model is immutable**, and the Compose compiler is told so (`gradle/compose-stability.conf`): never hand it a
  collection mutated afterwards. It says what a thing is, never how it looks. `ComposeStabilityListTest` fails until a
  new public class of `model/` or `scales/` is listed there, and on a listed name no class has.
- The caller supplies NFC normalization and tag folding; this module has none.
- **`language/` is the one answer about languages and scripts** for every module: `LanguageCodes.current` (`iw` read as
  `he`), `isStrongRtl`, `BidiOrder`, and `writingDirectionOf`. `ChordProSong.textDirection` is a song's one direction:
  right to left where every language it declares is in `RightToLeftLanguages` (which the `add-language` skill's
  `language.py` reads), else by a vote of its lyrics' strong letters, a tie left to right.

## Model and parser

- `ChordProMetadata.displayTitle` is the one rule for naming a song.
- **`SectionType` has no `Tab` or `Grid`**: those environments switch the kind of line inside whatever section runs, so
  a solo of chords over tablature is one section. A *run* of tab or grid lines is the unit everywhere.
- **`abc`, `ly`, and `svg` lines are `ChordProLine.Verbatim`**; `textblock` is prose and stays `Lyrics`. Inside an
  explicit section a delegated environment is a run of its lines, as a tab is, and the section goes on after it;
  anywhere else it opens a section of its own, so the run is the section.
- **Recalls are capped** (`ChorusRecall`): past an allowance proportional to the song, a recall draws as its heading,
  since every later step expands each recall and a tiny file could otherwise become millions of lines.
- **The parser is total: it never throws or rejects** — the file is the user's and half of it may be under the caret.
- **Say-once fields**: the first readable line wins. **Key, tempo, time** come from the header; a body line counts only
  where the header has *no* line of that field, an empty one included, which is how a cleared value stays cleared.
- `{transpose}` before the first line transposes the whole song; later ones, and body `{tempo}`/`{time}` that differ
  from the one in force, are mid-song changes that cut the section. Recalled choruses leave them out.
- **Markup** (`ChordProMarkup`): `<b>`, `<i>`, and `<u>` (and their HTML and Pango spellings, a `<span>` included) in a
  lyrics line move into `Lyrics.formatting`; the other inline tags are dropped from the text, the file keeping them;
  tags are read inside one line only, and any other angle bracket (`<intro>`) is lyrics. A chord renamed inside markup
  keeps the tags around it. A comment keeps its markup in its text, since its chords are rewritten there after parsing:
  whoever draws it calls `ChordProMarkup.strip`.
- **`{chordcolour}`** (`ChordProColors`: `#RRGGBB`, `#RGB`, or a CSS name) colours every real chord after it, across
  sections, until the next one; an empty or unreadable value, and `foreground`, give back the reader's colour. It is
  the one look the model carries (`Lyrics.Chord.color`), since the file asks for it; annotations and marks never take it.
  `{textcolour}` is read the same way into `Lyrics.color`, for the words of every lyrics line after it (never a comment,
  tab, grid, text block, or delegated line).
- **A chord row's repeat counts, bar lines, and beat dots are marks** (`ChordProRowMarks`), written back as their bare
  text; a sung line, and a line of a mark alone, keep them as lyrics. A word is never one: `twice` is written
  `[*twice]`.
- `ChordProSplitter` splits at `{new_song}` outside delegated environments. `comparable` (what an import compares
  against the file on disk) also folds line endings, notation, and directive spellings, which Prettify must not do.
  Both drop a BOM anywhere, or a `{title}` behind it would read as lyrics.

## Tests

`ChordProSerializer` lives only in `commonTest`: the round-trip oracle `parse(serialize(parse(x))) == parse(x)` every
model change is held to (the app never writes a model back).
