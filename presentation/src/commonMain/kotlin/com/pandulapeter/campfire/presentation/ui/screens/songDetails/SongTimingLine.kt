/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_time
import com.pandulapeter.campfire.presentation.resources.song_details_timing
import com.pandulapeter.campfire.presentation.ui.components.textResource

/**
 * How the song is played from a change of tempo or time signature on, set as the line of the song's first section is
 * in read only mode, since it is that line again for the rest of the song: read only, as the click plays it.
 */
@Composable
internal fun SongTimingLine(
    modifier: Modifier = Modifier,
    timing: RenderSection.Timing,
    style: TextStyle,
) {
    val values = listOfNotNull(
        timing.tempo?.let { textResource(Res.string.song_details_tempo, it) to false },
        textResource(Res.string.song_details_time, timing.time) to false,
    )
    val description = textResource(Res.string.song_details_timing, values.joinToString(PLAYING_VALUE_SEPARATOR) { it.first })
    SongPlayingValues(
        modifier = modifier.semantics { contentDescription = description },
        values = values,
        style = style,
    )
}
