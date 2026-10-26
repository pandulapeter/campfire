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

The [ChordPro](https://www.chordpro.org) format, the conversion of positioned chord sheets into it, and the reading of
ABC tunes into it (`abc`). **No dependencies at all**, not even Koin: stateless `object`s called directly by
`:presentation`, `:domain:*`, `:data:formats`, and `:data:source:local:implementation`. Explicit API mode. Everything is pure and tested in `commonTest`; rules pinned by a
test are not repeated here. Each package's rules are in its own `CLAUDE.md` (`chords`, `voicings`, `scales`, `tabs`, `edit`,
`convert`, `abc`).

## Rules that span the module

- **The dialect is defined once, in `syntax/`.** `ChordProLineScanner` is the one walk over lines (resumable from a
  line, for the highlighter's incremental reading); only `ChordProSummaryCache.safeLineAt` and `ChordProPrettifier` (a
  stack: an end closes only the innermost environment it names) keep their own. `MetadataKind` describes every metadata directive once, in header order, for every reader.
- **Whitespace is `Char.isWhitespace`, never regex `\s`**: the platforms' regex engines disagree, and pasted charts are
  full of non-breaking spaces.
- **Text edits keep every other byte.** Tags, languages, links, cover, metadata fields, transposition, and notation
  conversion edit the text, not the model, since the result is written straight back to the user's file.
  `ChordProLines.joinLines` keeps the file's line separator and trailing line break.
- **A song is read with its first `MAX_TAGS` tags and `MAX_LANGUAGES` languages** (`ChordProMetadata`, 100 each); the
  file keeps the rest, and no edit drops them: `ChordProTags.addTag` scans the lines, and a language edit starts from
  `ChordProLanguages.declaredIn`, never from the parsed list.
- **A directive is added after the last of its kind, else into the header in `metadataOrder`**
  (`ChordProHeaderLayout`); adding a line never reorders the others, only Prettify reorders the header. A body
  `{key}`/`{tempo}`/`{time}` is a mid-song change, never the header's.
- **Delegated environments** (`abc`, `ly`, `svg`, `textblock`) are kept verbatim, with no chords, markup, or
  definitions: only their closing `{end_of_…}` and a directive written with a colon are directives inside them.
- **The model is immutable**, and the Compose compiler is told so (`gradle/compose-stability.conf`): never hand it a
  collection mutated afterwards. It says what a thing is, never how it looks.
- The caller supplies NFC normalization and tag folding; this module has none.

## Model and parser

- `ChordProMetadata.displayTitle` is the one rule for naming a song (`{title}`, `{subtitle}` in parentheses).
- **`SectionType` has no `Tab` or `Grid`**: those environments switch the kind of line inside whatever section runs, so
  a solo of chords over tablature is one section. A *run* of tab or grid lines is the unit everywhere (serializer,
  transposer, viewer).
- **`abc`, `ly`, and `svg` lines are `ChordProLine.Verbatim`**, carrying their environment, which always opens a
  section of its own (`SectionType.Custom`), so the run is the section; `textblock` is prose and stays `Lyrics`.
- **Recalls are capped** (`ChorusRecall`): past an allowance proportional to the song, a recall draws as its heading,
  since every later step expands each recall and a tiny file could otherwise become millions of lines.
- **The parser is total: it never throws or rejects** — the file is the user's and half of it may be under the caret.
- **Say-once fields**: the first readable line wins; later ones are read past.
  **Key, tempo, time** come from the header; a body line counts only where the header has *no* line of that field, an
  empty one included, which is how a cleared value stays cleared.
- `{transpose}` before the first line transposes the whole song; later ones, and body `{tempo}`/`{time}` that differ
  from the one in force, are mid-song changes that cut the section. Recalled choruses leave them out.
- **Markup** (`ChordProMarkup`): `<b>`/`<strong>`, `<i>`/`<em>`, and `<u>` in a lyrics line are taken out of its text
  into `Lyrics.formatting`, chords moved with it; tags are read inside one line only, and any other angle bracket is
  lyrics. A comment keeps its markup in its text, since its chords are rewritten there after parsing: whoever draws
  it calls `ChordProMarkup.strip`. Delegated environments, tabs, grids, and other directives have no markup.
- **A chord row's repeat counts, bar lines, and beat dots are marks** (`ChordProRowMarks`): annotations shown with the
  chords, written back as their bare text, the text left blank where they stood; a sung line, and a line of a mark
  alone, keep them as lyrics. A word is never one: `twice` is written `[*twice]`.
- `ChordProSplitter` splits at `{new_song}` outside delegated environments. `comparable` (what an import compares
  against the file on disk) also folds line endings, notation, and directive spellings, which Prettify must not do.
  Both drop a BOM anywhere, or a `{title}` behind it would read as lyrics.

## Tests

`ChordProSerializer` lives only in `commonTest`: the round-trip oracle `parse(serialize(parse(x))) == parse(x)` every
model change is held to (the app never writes a model back).
`desktopTest` draws every table shape into `CAMPFIRE_CHORD_QA_DIR` when set, for a player to review.
