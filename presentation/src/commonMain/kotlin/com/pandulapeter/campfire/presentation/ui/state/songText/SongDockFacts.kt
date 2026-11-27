/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songText

import androidx.compose.ui.unit.Dp

/**
 * What a song read gave song details' dock ([SongTextStore.dockFacts]): whether it has a chord diagram to draw
 * ([hasChords]) and a scale to suggest ([hasScales]), and how wide its scale chips are in one line ([scalesWidth]), each
 * null until a page drawing it has said.
 */
internal data class SongDockFacts(
    val hasChords: Boolean? = null,
    val hasScales: Boolean? = null,
    val scalesWidth: Dp? = null,
)
