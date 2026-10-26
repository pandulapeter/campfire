/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.chordpro.model.ChordTuning

/**
 * One diagram of the Chords section: the chord as the page names it and the shape it is drawn with, and the letters
 * [SongChord.letterName] gives it after that, or else the step of the key [SongChord.degree] names.
 *
 * @property root The root of the chord that sounds.
 * @property capo The fret the shape is played above, see [SongChord.capo].
 */
@Immutable
internal data class ChordCell(
    val name: String,
    val letterName: String? = null,
    val degree: String? = null,
    val tuning: ChordTuning,
    val root: Int,
    val selection: SelectedShape,
    val capo: Int = 0,
)
