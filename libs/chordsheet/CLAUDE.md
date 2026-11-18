<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :libs:chordsheet

Chord sheets (plain text, and the positioned lines `:data:formats` reads from a PDF or a Word file) converted into
ChordPro: the import's heuristics, kept out of `:libs:chordpro`'s dependency-free core. It depends on `:libs:chordpro`
alone, has no Koin, and is called directly. Its rules are in the package's own `CLAUDE.md`
(`src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`).

`ChordSheetHtml` reads the HTML a browser copies of a page whose plain text loses the chords' places, in two shapes. A
page that draws its chords with CSS (`content: attr(data-…)`): an element with nothing in it whose first chord-valued
`data-*` attribute names a chord is that chord, over the character after it. A page that sets each chord over its
syllable in a box of its own (an inline box holding a row of chord names, then a row under it, `ChordSheetHtmlCells`):
the boxes flow on one line, a margin or whitespace between two of them a word break, none a syllable of the same word,
flex items in their `order`; the inline style says it (Chrome and Safari copy it; Firefox copies none, so its copy of
such a page stays plain text), and what it hides (`opacity: 0`) is left out. It writes chord rows over lyrics, not inline
ChordPro, which the converter would take as finished, a right-to-left song as `ChordSheetPlainText` reads one
(`ChordSheetHtmlRightToLeft`), and answers null where no chord came either way, so the caller keeps the plain text. No
class name of any site is read, and nothing the fragment points at is fetched.
