/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_metronome_body
import com.pandulapeter.campfire.presentation.resources.ic_metronome_pendulum
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.ic_tuner
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_chords_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_metronome_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_setlists_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_tuner_description
import com.pandulapeter.campfire.presentation.resources.onboarding_features_message
import com.pandulapeter.campfire.presentation.resources.onboarding_features_title
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.settings_chords
import com.pandulapeter.campfire.presentation.resources.tuner
import org.jetbrains.compose.resources.painterResource

/**
 * "What do you need?", the four features a newcomer decides on as cards any of which can be kept, each the Settings →
 * Features switch it stands for, so they start as the app is set (every one on, on a first run). It asks about the
 * features rather than about the person, since a role would only be a guess at them: a guitarist sings too, and anyone
 * may play from a list. This is the page every later question depends on: the instrument, the notation and the key on a
 * card are only asked about the features left on here.
 */
@Composable
internal fun FeaturesStep(
    modifier: Modifier = Modifier,
    features: OnboardingFeatures,
    isCompact: Boolean,
    onChordsEnabledChange: (Boolean) -> Unit,
    onSetlistsEnabledChange: (Boolean) -> Unit,
    onMetronomeEnabledChange: (Boolean) -> Unit,
    onTunerEnabledChange: (Boolean) -> Unit,
) = OnboardingPage(
    modifier = modifier,
    icon = painterResource(Res.drawable.ic_tune),
    title = stringResource(Res.string.onboarding_features_title),
    message = stringResource(Res.string.onboarding_features_message),
    isCompact = isCompact,
) {
    val options = listOf(
        FeatureOption(
            title = stringResource(Res.string.settings_chords),
            description = stringResource(Res.string.onboarding_feature_chords_description),
            icons = listOf(painterResource(Res.drawable.ic_songs)),
            isEnabled = features.areChordsEnabled,
            onEnabledChange = onChordsEnabledChange,
        ),
        FeatureOption(
            title = stringResource(Res.string.setlists),
            description = stringResource(Res.string.onboarding_feature_setlists_description),
            icons = listOf(painterResource(Res.drawable.ic_setlists)),
            isEnabled = features.areSetlistsEnabled,
            onEnabledChange = onSetlistsEnabledChange,
        ),
        FeatureOption(
            title = stringResource(Res.string.metronome),
            description = stringResource(Res.string.onboarding_feature_metronome_description),
            // The metronome's mark is drawn in two parts, so that the navigation item can swing the pendulum alone.
            icons = listOf(painterResource(Res.drawable.ic_metronome_body), painterResource(Res.drawable.ic_metronome_pendulum)),
            isEnabled = features.isMetronomeEnabled,
            onEnabledChange = onMetronomeEnabledChange,
        ),
        FeatureOption(
            title = stringResource(Res.string.tuner),
            description = stringResource(Res.string.onboarding_feature_tuner_description),
            icons = listOf(painterResource(Res.drawable.ic_tuner)),
            isEnabled = features.isTunerEnabled,
            onEnabledChange = onTunerEnabledChange,
        ),
    )
    OnboardingChoiceGrid(
        columns = 1,
        count = options.size,
    ) { index, itemModifier ->
        val option = options[index]
        OnboardingChoiceCard(
            modifier = itemModifier,
            title = option.title,
            description = option.description,
            icon = { Box { option.icons.forEach { Icon(painter = it, contentDescription = null) } } },
            isSelected = option.isEnabled,
            role = Role.Checkbox,
            onClick = { option.onEnabledChange(!option.isEnabled) },
        )
    }
}

private class FeatureOption(
    val title: String,
    val description: String,
    val icons: List<Painter>,
    val isEnabled: Boolean,
    val onEnabledChange: (Boolean) -> Unit,
)
