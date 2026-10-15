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
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_text_decrease
import com.pandulapeter.campfire.presentation.resources.ic_text_increase
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_decrease
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_increase
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_reset
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.controls.STEPPER_BUTTON_LENGTH
import com.pandulapeter.campfire.presentation.ui.components.controls.STEPPER_RESET_MARK_WIDTH
import com.pandulapeter.campfire.presentation.ui.components.controls.STEPPER_VALUE_MIN_WIDTH
import com.pandulapeter.campfire.presentation.ui.components.controls.Stepper
import com.pandulapeter.campfire.presentation.ui.fontScale.FONT_SCALE_STEP
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
        widestValues = listOf(fontScaleLabel(UserPreferences.MIN_FONT_SCALE), fontScaleLabel(UserPreferences.MAX_FONT_SCALE)),
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

/**
 * How wide `250%`, the widest text size, is written in the stepper's value style, measured in the bundled Inter. The
 * stepper exactly fits the read only app bar of the smallest supported phone, so a change of the font or the style
 * measures it again rather than guessing.
 */
private val WIDEST_FONT_SCALE_LABEL_WIDTH = 42.dp

/**
 * How wide [FontScaleControls] is drawn, for the app bar that has to leave its title room beside it: its buttons and the
 * room its value keeps for the widest text size with the reset icon.
 */
internal val FONT_SCALE_STEPPER_WIDTH = STEPPER_BUTTON_LENGTH * 2 + maxOf(STEPPER_VALUE_MIN_WIDTH, WIDEST_FONT_SCALE_LABEL_WIDTH + STEPPER_RESET_MARK_WIDTH)
