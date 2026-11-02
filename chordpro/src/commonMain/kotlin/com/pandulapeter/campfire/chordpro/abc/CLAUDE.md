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

[ABC notation](https://abcnotation.com/wiki/abc:standard:v2.1) read into ChordPro: a file format, so it is
read here rather than by the chord-sheet converter (`:chordsheet`, which knows none). Pure and total: whatever text
comes in, a song comes out.

- **The ABC block is the source, the grid and verses a one-off translation**: each tune is kept whole in
  `{start_of_abc}`, and editing it later regenerates nothing.
- **`AbcBars` reads only what a chord grid needs**. Notation is a renderer's job, never a reason to grow it. Only the
  first voice is read (or the first holding chord symbols, where it has none).
- Grid cells are chords in order, not on beats: placing them needs note lengths, which nothing here reads.
- A chord symbol is written as spelled, so a mid-tune `K:` changes no name and `{key}` stays the opening key; a mode
  becomes the key of its chords (`Edor` → `Em`), since `{key}` knows only major and minor.
- `AbcTunes` writes the file header into every tune, but leaves out a field the tune's own header has (a file `C:` must
  not become the artist of a tune with its own), as the standard says.
- `AbcTranspositions`: a tune's own `%%transpose` (or `I:transpose`) lines in its header add to the reader's
  transposition, which is how the editor's transposition moves a tune; lines before `X:` replace it and are not read.
