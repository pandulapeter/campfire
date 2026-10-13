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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.card_detail_duration
import com.pandulapeter.campfire.presentation.resources.card_detail_key
import com.pandulapeter.campfire.presentation.resources.card_detail_tempo
import org.jetbrains.compose.resources.StringResource

/**
 * What one list's song cards say after the artist ([UserPreferences.CardDetails]), as a row of chips any of which can be
 * on: the key, the tempo, and the duration. The key goes with the chords and the tempo with the metronome, so with its
 * feature off a chip is disabled, showing what it is set to for when the feature comes back, or left out altogether
 * where [isUnavailableHidden], for a page that only asks about what the app has.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardDetailsChoice(
    modifier: Modifier = Modifier,
    details: UserPreferences.CardDetails,
    areChordsEnabled: Boolean,
    isMetronomeEnabled: Boolean,
    isUnavailableHidden: Boolean = false,
    onDetailsChanged: (UserPreferences.CardDetails) -> Unit,
) = FlowRow(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
) {
    listOf(
        CardDetailOption(Res.string.card_detail_key, details.isKeyShown, areChordsEnabled) { details.copy(isKeyShown = it) },
        CardDetailOption(Res.string.card_detail_tempo, details.isTempoShown, isMetronomeEnabled) { details.copy(isTempoShown = it) },
        CardDetailOption(Res.string.card_detail_duration, details.isDurationShown, true) { details.copy(isDurationShown = it) },
    ).filter { it.isEnabled || !isUnavailableHidden }.forEach { option ->
        SelectableChip(
            isSelected = option.isShown,
            isEnabled = option.isEnabled,
            role = Role.Checkbox,
            onClick = { onDetailsChanged(option.withShown(!option.isShown)) },
        ) { contentColor ->
            Text(
                text = stringResource(option.label),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

private class CardDetailOption(
    val label: StringResource,
    val isShown: Boolean,
    val isEnabled: Boolean,
    val withShown: (Boolean) -> UserPreferences.CardDetails,
)
