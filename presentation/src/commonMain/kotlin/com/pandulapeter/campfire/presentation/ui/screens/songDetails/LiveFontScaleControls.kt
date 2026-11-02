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
import com.pandulapeter.campfire.presentation.ui.fontScale.FontScaleController

/**
 * The text size stepper, reading the live font scale in a scope of its own so that a pinch recomposes the
 * stepper rather than whatever holds it.
 */
@Composable
internal fun LiveFontScaleControls(
    modifier: Modifier = Modifier,
    fontScaleController: FontScaleController,
) = FontScaleControls(
    modifier = modifier,
    fontScale = fontScaleController.fontScale,
    onFontScaleAdjusted = fontScaleController::adjustFontScale,
    onFontScaleReset = { fontScaleController.setFontScale(UserPreferences.DEFAULT_FONT_SCALE) },
)
