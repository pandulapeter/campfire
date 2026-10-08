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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * Values of how the song is played on one line, the ones marked in the accent color: that is the color of the chords,
 * which are what the key and the capo decide, while the tempo and the time signature are the click's.
 */
@Composable
internal fun SongPlayingValues(
    modifier: Modifier = Modifier,
    values: List<Pair<String, Boolean>>,
    style: TextStyle,
) {
    val accentColor = LocalSecondAccentColor.current
    if (values.isNotEmpty()) {
        Text(
            modifier = modifier.padding(horizontal = 12.dp),
            text = buildAnnotatedString {
                values.forEachIndexed { index, (value, isAccented) ->
                    if (index > 0) append(PLAYING_VALUE_SEPARATOR)
                    if (isAccented) withStyle(SpanStyle(color = accentColor)) { append(value) } else append(value)
                }
            },
            style = style,
        )
    }
}

internal const val PLAYING_VALUE_SEPARATOR = "  •  "
