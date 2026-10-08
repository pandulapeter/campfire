/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.metronome

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_volume
import kotlin.math.roundToInt

@Composable
internal fun VolumeSlider(
    volume: Float,
    onVolumeChanged: (Float) -> Unit,
) = Row(
    modifier = Modifier.padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    val volumeDescription = stringResource(Res.string.metronome_volume)
    Slider(
        modifier = Modifier.weight(1f).semantics { contentDescription = volumeDescription },
        value = volume,
        onValueChange = onVolumeChanged,
    )
    Text(
        modifier = Modifier.padding(start = 16.dp),
        text = "${(volume * 100).roundToInt()}%",
        style = MaterialTheme.typography.labelLarge,
    )
}
