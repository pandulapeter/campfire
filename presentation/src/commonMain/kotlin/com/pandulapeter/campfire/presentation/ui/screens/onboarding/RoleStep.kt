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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_sound
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_chords_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_metronome_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_setlists_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_tuner_description
import com.pandulapeter.campfire.presentation.resources.onboarding_role_features
import com.pandulapeter.campfire.presentation.resources.onboarding_role_leader
import com.pandulapeter.campfire.presentation.resources.onboarding_role_leader_description
import com.pandulapeter.campfire.presentation.resources.onboarding_role_message
import com.pandulapeter.campfire.presentation.resources.onboarding_role_player
import com.pandulapeter.campfire.presentation.resources.onboarding_role_player_description
import com.pandulapeter.campfire.presentation.resources.onboarding_role_singer
import com.pandulapeter.campfire.presentation.resources.onboarding_role_singer_description
import com.pandulapeter.campfire.presentation.resources.onboarding_role_title
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.settings_chords
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import org.jetbrains.compose.resources.painterResource

/**
 * "How will you use Campfire?", asked about the person rather than about the settings: each answer switches on the
 * features somebody like that needs ([OnboardingRole]), and the four switches under the answers show what was switched
 * on and stay there to be changed one by one. This is the page every later question depends on: the instrument, the
 * notation and what a song shows at a glance are only asked about the features left on here.
 *
 * @param role The answer picked since the onboarding was opened, null until one is: it is not stored, since the switches
 *   under it are what the app goes by.
 */
@Composable
internal fun RoleStep(
    modifier: Modifier = Modifier,
    role: OnboardingRole?,
    features: OnboardingFeatures,
    isCompact: Boolean,
    onRoleSelected: (OnboardingRole) -> Unit,
    onChordsEnabledChange: (Boolean) -> Unit,
    onSetlistsEnabledChange: (Boolean) -> Unit,
    onMetronomeEnabledChange: (Boolean) -> Unit,
    onTunerEnabledChange: (Boolean) -> Unit,
) = OnboardingPage(
    modifier = modifier,
    icon = painterResource(Res.drawable.ic_tune),
    title = stringResource(Res.string.onboarding_role_title),
    message = stringResource(Res.string.onboarding_role_message),
    isCompact = isCompact,
) {
    val roles = OnboardingRole.entries
    OnboardingChoiceGrid(
        modifier = Modifier.selectableGroup(),
        columns = 1,
        count = roles.size,
    ) { index, itemModifier ->
        val shownRole = roles[index]
        OnboardingChoiceCard(
            modifier = itemModifier,
            title = shownRole.title(),
            description = shownRole.description(),
            icon = { Icon(painter = shownRole.icon(), contentDescription = null) },
            isSelected = shownRole == role,
            role = Role.RadioButton,
            onClick = { onRoleSelected(shownRole) },
        )
    }
    Column {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
            text = stringResource(Res.string.onboarding_role_features),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        SwitchListItem(
            title = stringResource(Res.string.settings_chords),
            description = stringResource(Res.string.onboarding_feature_chords_description),
            isChecked = features.areChordsEnabled,
            onCheckedChange = onChordsEnabledChange,
        )
        SwitchListItem(
            title = stringResource(Res.string.setlists),
            description = stringResource(Res.string.onboarding_feature_setlists_description),
            isChecked = features.areSetlistsEnabled,
            onCheckedChange = onSetlistsEnabledChange,
        )
        SwitchListItem(
            title = stringResource(Res.string.metronome),
            description = stringResource(Res.string.onboarding_feature_metronome_description),
            isChecked = features.isMetronomeEnabled,
            onCheckedChange = onMetronomeEnabledChange,
        )
        SwitchListItem(
            title = stringResource(Res.string.tuner),
            description = stringResource(Res.string.onboarding_feature_tuner_description),
            isChecked = features.isTunerEnabled,
            onCheckedChange = onTunerEnabledChange,
        )
    }
}

@Composable
private fun OnboardingRole.title() = stringResource(
    when (this) {
        OnboardingRole.PLAYER -> Res.string.onboarding_role_player
        OnboardingRole.SINGER -> Res.string.onboarding_role_singer
        OnboardingRole.LEADER -> Res.string.onboarding_role_leader
    }
)

@Composable
private fun OnboardingRole.description() = stringResource(
    when (this) {
        OnboardingRole.PLAYER -> Res.string.onboarding_role_player_description
        OnboardingRole.SINGER -> Res.string.onboarding_role_singer_description
        OnboardingRole.LEADER -> Res.string.onboarding_role_leader_description
    }
)

@Composable
private fun OnboardingRole.icon() = painterResource(
    when (this) {
        OnboardingRole.PLAYER -> Res.drawable.ic_songs
        OnboardingRole.SINGER -> Res.drawable.ic_sound
        OnboardingRole.LEADER -> Res.drawable.ic_setlists
    }
)
