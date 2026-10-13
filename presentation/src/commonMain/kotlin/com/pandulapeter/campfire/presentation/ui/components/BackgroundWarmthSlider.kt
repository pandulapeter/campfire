/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_background_warmth
import kotlin.math.roundToInt

/**
 * How far the palette's neutrals are turned towards sepia. A slider of a few steps rather than a picker of tints, since
 * every step keeps every contrast ratio of the palette (see `withBackgroundWarmth`), so no step can cost legibility,
 * and a step is a whole theme change that recomposes the app: a continuous slider would do that on every frame of a
 * drag. Each step reaches the theme as it is crossed, so the app behind the slider shows where the thumb is.
 *
 * @param description What the slider does, where the screen it is on explains its settings; the onboarding shows the
 *   page change color instead.
 */
@Composable
internal fun BackgroundWarmthSlider(
    modifier: Modifier = Modifier,
    warmth: Int,
    description: String? = null,
    shouldApplyPadding: Boolean = true,
    onWarmthChanged: (Int) -> Unit,
) = SettingsSubsection(
    modifier = modifier,
    title = stringResource(Res.string.settings_background_warmth),
    description = description,
    shouldApplyPadding = shouldApplyPadding,
) {
    val sliderDescription = stringResource(Res.string.settings_background_warmth)
    Slider(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { contentDescription = sliderDescription },
        value = warmth.toFloat(),
        onValueChange = { value -> value.roundToInt().let { if (it != warmth) onWarmthChanged(it) } },
        valueRange = 0f..UserPreferences.MAX_BACKGROUND_WARMTH.toFloat(),
        steps = UserPreferences.MAX_BACKGROUND_WARMTH - 1,
    )
}
