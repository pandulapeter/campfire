/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_scales_and_unnamed
import com.pandulapeter.campfire.presentation.resources.song_details_scales_and_unnamed_several
import com.pandulapeter.campfire.presentation.resources.song_details_scales_from
import com.pandulapeter.campfire.presentation.resources.song_details_scales_over
import com.pandulapeter.campfire.presentation.resources.song_details_scales_over_several
import com.pandulapeter.campfire.presentation.resources.song_details_scales_over_two
import com.pandulapeter.campfire.presentation.ui.songView.model.SongScaleException
import com.pandulapeter.campfire.presentation.ui.songView.model.SongScaleGroup

/**
 * What a row of chips over chords is titled: "Over E7", "Over F♯7 and F♯", or "Over A7, D7, and G7", the list said as
 * the language says one.
 */
@Composable
internal fun SongScaleException.title(): String = when (chordNames.size) {
    1 -> textResource(Res.string.song_details_scales_over, chordNames.single())
    2 -> textResource(Res.string.song_details_scales_over_two, chordNames[0], chordNames[1])
    else -> textResource(Res.string.song_details_scales_over_several, chordNames.dropLast(1).joinToString(", "), chordNames.last())
}

/**
 * What a group's row of chips is titled where the song has several: its sections' names, saying so where it also holds
 * parts without a heading, else its first words where several groups have no name, else the rest of the song, [other].
 */
@Composable
internal fun SongScaleGroup.title(other: String): String {
    val label = label ?: return opening?.let { textResource(Res.string.song_details_scales_from, it) } ?: other
    return when {
        !hasUnnamed -> label
        joinsSeveral -> textResource(Res.string.song_details_scales_and_unnamed_several, label)
        else -> textResource(Res.string.song_details_scales_and_unnamed, label)
    }
}
