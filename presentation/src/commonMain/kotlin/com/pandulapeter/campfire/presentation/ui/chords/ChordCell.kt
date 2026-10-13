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
 * [SongChord.letterName] gives it after that.
 */
@Immutable
internal data class ChordCell(
    val name: String,
    val letterName: String? = null,
    val tuning: ChordTuning,
    val root: Int,
    val selection: SelectedShape,
)
