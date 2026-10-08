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
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_text_decrease
import com.pandulapeter.campfire.presentation.resources.ic_text_increase
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_decrease
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_increase
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_reset
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun FontScaleControls(
    modifier: Modifier = Modifier,
    fontScale: Float,
    onFontScaleAdjusted: (steps: Int) -> Unit,
    onFontScaleReset: () -> Unit,
) {
    val value = fontScaleLabel(fontScale)
    Stepper(
        modifier = modifier,
        value = value,
        // The step the value is nearest to, so that a tap, a shortcut or a reset still cross-fades while a pinch, which
        // changes the percentage on almost every frame, counts it up in place and cross-fades once per step at most.
        valueKey = (fontScale / FONT_SCALE_STEP).roundToInt(),
        isDefault = value == fontScaleLabel(UserPreferences.DEFAULT_FONT_SCALE),
        decreaseIcon = painterResource(Res.drawable.ic_text_decrease),
        decreaseLabel = stringResource(Res.string.song_details_text_size_decrease),
        canDecrease = fontScale > UserPreferences.MIN_FONT_SCALE,
        onDecrease = { onFontScaleAdjusted(-1) },
        increaseIcon = painterResource(Res.drawable.ic_text_increase),
        increaseLabel = stringResource(Res.string.song_details_text_size_increase),
        canIncrease = fontScale < UserPreferences.MAX_FONT_SCALE,
        onIncrease = { onFontScaleAdjusted(1) },
        resetLabel = stringResource(Res.string.song_details_text_size_reset),
        onReset = onFontScaleReset,
    )
}

private fun fontScaleLabel(fontScale: Float) = "${(fontScale * 100).roundToInt()}%"
