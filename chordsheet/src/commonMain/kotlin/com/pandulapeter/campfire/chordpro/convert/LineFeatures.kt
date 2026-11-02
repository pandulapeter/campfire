/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

/**
 * What a line of a song says on its own, read once: every decision about the song (its kinds, its header, its sections)
 * reads these rather than parsing the line again.
 *
 * @property heading The heading the line reads as, ambiguous or not ([ChordSheetHeadings.parse]).
 * @property metadata The field and value the line names, where it is one labelled value.
 * @property headerRow The values the line names as a row of labelled values, read as the header reads them where the
 *   line is in its reach ([ChordSheetProfile.isInHeader]), else as the body does.
 * @property bodyRow The values the line names as a row of labelled values read anywhere in the song.
 * @property look How the line's lyric is set, null for a line with nothing printed.
 * @property isSung Whether the line holds a sung word, which a tab's string or a stray accidental does not, and which the
 *   rest of a line Campfire's export wrapped is not counted as.
 */
internal data class LineFeatures(
    val heading: ChordSheetHeadings.Heading?,
    val metadata: Pair<String, String>?,
    val headerRow: List<Pair<String, String>>?,
    val bodyRow: List<Pair<String, String>>?,
    val look: ChordSheetLook?,
    val isSung: Boolean,
)
