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
 * What the layout of Campfire's own export says about a line of it, which a line of any other sheet has none of: kept
 * apart from what is read off every line, so that the general reading names no export state.
 *
 * @property role What the export's layout says the line is ([ChordSheetCampfireLayout.roles]).
 * @property bodySize The lyrics' size, which the export prints its chords at; 0 where it is unknown.
 * @property isContinuation Whether the row continues the line above ([ChordSheetCampfireLayout.CONTINUATION]).
 * @property isHardCut Whether it continues a word of it, opening with two marks, which is joined back with no space.
 * @property isContinuedBelow Whether the row under this one continues its line in the chords' or the lyrics' type, which
 *   makes a row of notes alone the chord row of that line.
 * @property isSetFromRight Whether the line belongs to a song the export printed from the right, a row of its chords alone too.
 */
internal data class CampfireMarks(
    val role: ChordSheetCampfireLayout.Role? = null,
    val bodySize: Double = 0.0,
    val isContinuation: Boolean = false,
    val isHardCut: Boolean = false,
    val isContinuedBelow: Boolean = false,
    val isSetFromRight: Boolean = false,
)
