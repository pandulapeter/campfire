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
