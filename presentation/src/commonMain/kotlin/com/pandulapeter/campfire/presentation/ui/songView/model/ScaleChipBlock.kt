/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.chordpro.scales.Scale

/**
 * One row of chips of a [RenderSection.Scales]: the group at [groupIndex]'s own, or, where [exception] is set, the one
 * for a chord that group's scales leave out, drawn after it.
 */
@Immutable
internal data class ScaleChipBlock(
    val groupIndex: Int,
    val exception: SongScaleException?,
) {

    /** The note this row's chips are played on, the group's own root or the exception's. */
    fun rootNameOf(groups: List<SongScaleGroup>) = exception?.rootName ?: groups[groupIndex].rootName

    /** The scales this row offers. */
    fun scalesOf(groups: List<SongScaleGroup>): List<Scale> = exception?.scales ?: groups[groupIndex].scales
}
