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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_metronome_body
import com.pandulapeter.campfire.presentation.resources.ic_metronome_pendulum
import com.pandulapeter.campfire.presentation.resources.ic_scales
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_tuner
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_chords_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_metronome_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_scales_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_tuner_description
import com.pandulapeter.campfire.presentation.resources.onboarding_features_message
import com.pandulapeter.campfire.presentation.resources.onboarding_features_title
import com.pandulapeter.campfire.presentation.resources.scales
import com.pandulapeter.campfire.presentation.resources.settings_chords
import com.pandulapeter.campfire.presentation.resources.tuner
import org.jetbrains.compose.resources.painterResource

/**
 * "What do you need?", the features a newcomer decides on as cards any of which can be kept, each the Settings → Features
 * switch it stands for and in that list's order, so they start as the app is set (every one on, on a first run). It asks
 * about the features rather than about the person, since a role would only be a guess at them: a guitarist sings too,
 * and anyone may play from a list. The chords card says that leaving it off is how a singer reads lyrics only, the one
 * hint at a role the page gives. The notation is only asked about the chords left on here.
 *
 * Only the features that change what the app is for are asked, so that the page stays one short list: the chords card
 * switches the chord diagrams with it and the scales card the scale suggestions, as their descriptions say, and the
 * setlists are left to Settings, where each of those has a switch of its own.
 */
@Composable
internal fun FeaturesStep(
    modifier: Modifier = Modifier,
    markModifier: Modifier,
    features: OnboardingFeatures,
    isCompact: Boolean,
    onChordsEnabledChange: (Boolean) -> Unit,
    onMetronomeEnabledChange: (Boolean) -> Unit,
    onTunerEnabledChange: (Boolean) -> Unit,
    onScalesEnabledChange: (Boolean) -> Unit,
) = OnboardingPage(
    modifier = modifier,
    markModifier = markModifier,
    title = stringResource(Res.string.onboarding_features_title),
    message = stringResource(Res.string.onboarding_features_message),
    isCompact = isCompact,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        FeatureCard(
            title = stringResource(Res.string.settings_chords),
            description = stringResource(Res.string.onboarding_feature_chords_description),
            icon = { Icon(painter = painterResource(Res.drawable.ic_songs), contentDescription = null) },
            isEnabled = features.areChordsEnabled,
            onEnabledChange = onChordsEnabledChange,
        )
        FeatureCard(
            modifier = Modifier.padding(top = FEATURE_CARD_GAP),
            title = stringResource(Res.string.scales),
            description = stringResource(Res.string.onboarding_feature_scales_description),
            icon = { Icon(painter = painterResource(Res.drawable.ic_scales), contentDescription = null) },
            isEnabled = features.areScalesEnabled,
            onEnabledChange = onScalesEnabledChange,
        )
        FeatureCard(
            modifier = Modifier.padding(top = FEATURE_CARD_GAP),
            title = stringResource(Res.string.metronome),
            description = stringResource(Res.string.onboarding_feature_metronome_description),
            // The metronome's mark is drawn in two parts, so that the navigation item can swing the pendulum alone.
            icon = {
                Box {
                    Icon(painter = painterResource(Res.drawable.ic_metronome_body), contentDescription = null)
                    Icon(painter = painterResource(Res.drawable.ic_metronome_pendulum), contentDescription = null)
                }
            },
            isEnabled = features.isMetronomeEnabled,
            onEnabledChange = onMetronomeEnabledChange,
        )
        FeatureCard(
            modifier = Modifier.padding(top = FEATURE_CARD_GAP),
            title = stringResource(Res.string.tuner),
            description = stringResource(Res.string.onboarding_feature_tuner_description),
            icon = { Icon(painter = painterResource(Res.drawable.ic_tuner), contentDescription = null) },
            isEnabled = features.isTunerEnabled,
            onEnabledChange = onTunerEnabledChange,
        )
    }
}

/** One feature's card. */
@Composable
private fun FeatureCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) = OnboardingChoiceCard(
    modifier = modifier.fillMaxWidth(),
    title = title,
    description = description,
    icon = icon,
    isSelected = isEnabled,
    role = Role.Checkbox,
    onClick = { onEnabledChange(!isEnabled) },
)

private val FEATURE_CARD_GAP = 8.dp
