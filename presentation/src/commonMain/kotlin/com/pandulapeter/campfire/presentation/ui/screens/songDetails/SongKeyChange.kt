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

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_key_change
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * The key a `{transpose}` further down the song takes it to, set as the chords are: it is what they are in from there on,
 * and the line the song's first section reads its opening key in is set the same way.
 */
@Composable
internal fun SongKeyChange(
    modifier: Modifier = Modifier,
    keyChange: RenderSection.KeyChange,
    chordStyle: TextStyle,
) = Text(
    modifier = modifier,
    text = textResource(Res.string.song_details_key_change, keyChange.key),
    style = chordStyle,
    color = LocalSecondAccentColor.current,
)
