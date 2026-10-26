<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# chordpro/abc

[ABC notation](https://abcnotation.com/wiki/abc:standard:v2.1) read into ChordPro: a file format, so a sibling of
`convert/` (which knows none), not inside it. The caller sniffs (`AbcText`), splits (`AbcTunes`), and converts each
tune (`AbcToChordPro`); the library stays ChordPro.

- **The ABC block is the source, the grid and verses a one-off translation**: each tune is kept whole in
  `{start_of_abc}`, and editing it later regenerates nothing.
- **`AbcBars` reads only what a chord grid needs** (bar lines, voltas, chord symbols, inline fields, continuations,
  comments, voices). Notation is a renderer's job, never a reason to grow it. Only the first voice is read: the others
  are written under the same bars.
- Grid cells are chords in order, not on beats: placing them needs note lengths, which nothing here reads.
- A chord symbol is written as spelled, so a mid-tune `K:` changes no name and `{key}` stays the opening key; a mode
  becomes the key of its chords (`Edor` → `Em`), since `{key}` knows only major and minor.
- `AbcTunes` writes the file header into every tune, but leaves out a field the tune's own header has: the tune's
  overrides the file's (a file `C:` must not become the artist of a tune with its own), as the standard says.
- Pure and total: whatever text comes in, a song comes out. Callers: the import's archive reader
  (`data/source/local/implementation`, `.abc` files and sniffed texts) and its plain-text branch (pastes and shares).
