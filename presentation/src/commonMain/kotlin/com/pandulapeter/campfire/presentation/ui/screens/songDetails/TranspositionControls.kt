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

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.song_details_transpose_down
import com.pandulapeter.campfire.presentation.resources.song_details_transpose_reset
import com.pandulapeter.campfire.presentation.resources.song_details_transpose_up
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun TranspositionControls(
    modifier: Modifier = Modifier,
    transposition: Int,
    /** The key the song sounds in after transposing, shown next to the amount when the file declares one. */
    key: String? = null,
    fontScale: Float = 1f,
    height: Dp = STEPPER_HEIGHT,
    onStep: (semitones: Int) -> Unit,
    onReset: () -> Unit,
) = Stepper(
    modifier = modifier,
    fontScale = fontScale,
    height = height,
    value = transpositionLabel(transposition, key),
    isDefault = transposition == 0,
    decreaseIcon = painterResource(Res.drawable.ic_subtract),
    decreaseLabel = stringResource(Res.string.song_details_transpose_down),
    canDecrease = true,
    onDecrease = { onStep(-1) },
    increaseIcon = painterResource(Res.drawable.ic_add),
    increaseLabel = stringResource(Res.string.song_details_transpose_up),
    canIncrease = true,
    onIncrease = { onStep(1) },
    resetLabel = stringResource(Res.string.song_details_transpose_reset),
    onReset = onReset,
)

/** What the transposition stepper reads for [transposition], with the [key] it takes the song to where there is one. */
internal fun transpositionLabel(transposition: Int, key: String?) = (if (transposition > 0) "+$transposition" else transposition.toString())
    .let { if (key.isNullOrBlank()) it else "$it $KEY_SEPARATOR $key" }

internal const val KEY_SEPARATOR = "\u00B7"
