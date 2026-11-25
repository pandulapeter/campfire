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

import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_time
import com.pandulapeter.campfire.presentation.resources.song_details_timing
import com.pandulapeter.campfire.presentation.localization.textResource

/**
 * How the song is played from a change of tempo or time signature on, one line in the style of the interface's own
 * sentences: read only, as the click plays it.
 */
@Composable
internal fun SongTimingLine(
    modifier: Modifier = Modifier,
    timing: RenderSection.Timing,
    style: TextStyle,
) {
    val text = listOfNotNull(
        timing.tempo?.let { textResource(Res.string.song_details_tempo, it) },
        textResource(Res.string.song_details_time, timing.time),
    ).joinToString(TIMING_VALUE_SEPARATOR)
    val description = textResource(Res.string.song_details_timing, text)
    Text(
        modifier = modifier
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp),
        text = text,
        style = style,
    )
}

private const val TIMING_VALUE_SEPARATOR = "  •  "
