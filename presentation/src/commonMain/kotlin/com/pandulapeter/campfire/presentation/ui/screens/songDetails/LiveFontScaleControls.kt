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
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/**
 * The text size stepper, reading the live font scale in a scope of its own so that a pinch recomposes the
 * stepper rather than whatever holds it.
 */
@Composable
internal fun LiveFontScaleControls(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
) = FontScaleControls(
    modifier = modifier,
    fontScale = viewModel.fontScale,
    onFontScaleAdjusted = viewModel::adjustFontScale,
    onFontScaleReset = { viewModel.setFontScale(CampfireViewModel.DEFAULT_FONT_SCALE) },
)
