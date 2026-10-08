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
import com.pandulapeter.campfire.chordpro.model.ChordInstrument

/**
 * One diagram of the Chords section: the chord as the page names it and the shape it is drawn with, and the names
 * [SongChord.soundingName] and [SongChord.letterName] give it after that.
 */
@Immutable
internal data class ChordCell(
    val name: String,
    val soundingName: String?,
    val letterName: String? = null,
    val instrument: ChordInstrument,
    val root: Int,
    val selection: SelectedShape,
)
