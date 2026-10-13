/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.settings_group_display
import com.pandulapeter.campfire.presentation.resources.settings_number_sections
import com.pandulapeter.campfire.presentation.resources.settings_number_sections_description
import com.pandulapeter.campfire.presentation.resources.settings_song_cards
import com.pandulapeter.campfire.presentation.resources.settings_song_cards_description
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.CardDetailsChoice
import com.pandulapeter.campfire.presentation.ui.components.LIST_ITEM_KEYLINE
import com.pandulapeter.campfire.presentation.ui.components.LabeledControlRow
import com.pandulapeter.campfire.presentation.ui.components.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem

/**
 * The second section of the Songs tab: what the app adds to a song's text and to its cards.
 */
@Composable
internal fun SongDisplaySection(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
) = SettingsSection(title = stringResource(Res.string.settings_group_display)) {
    SwitchListItem(
        title = stringResource(Res.string.settings_number_sections),
        description = stringResource(Res.string.settings_number_sections_description),
        isChecked = userPreferences?.shouldNumberSections == true,
        onCheckedChange = viewModel::setSectionNumberingEnabled,
    )
    // One row of chips per list, since a setlist's cards are where a set is timed and the library's where a song is found.
    // The key's chip is gone with the chords, which take it off the cards whatever it says; the tempo's stays with the
    // metronome off, which only decides it until it is picked. The song details screen is not theirs: the top of a song
    // is where its key and tempo are changed.
    val areChordsEnabled = userPreferences?.areChordsEnabled != false
    val isMetronomeEnabled = userPreferences?.isMetronomeEnabled != false
    val areSetlistsEnabled = userPreferences?.areSetlistsEnabled != false
    SettingsSubsection(
        title = stringResource(Res.string.settings_song_cards),
        description = stringResource(Res.string.settings_song_cards_description),
    ) {
        CardDetailsRow(label = stringResource(Res.string.songs)) {
            CardDetailsChoice(
                details = userPreferences?.songCardDetails ?: UserPreferences.CardDetails.SongsDefault,
                areChordsEnabled = areChordsEnabled,
                isMetronomeEnabled = isMetronomeEnabled,
                onDetailsChanged = viewModel::setSongCardDetails,
            )
        }
        CardDetailsRow(
            modifier = Modifier.padding(top = CARD_DETAILS_ROW_GAP),
            label = stringResource(Res.string.setlists),
            isEnabled = areSetlistsEnabled,
        ) {
            CardDetailsChoice(
                details = userPreferences?.setlistCardDetails ?: UserPreferences.CardDetails.SetlistsDefault,
                areChordsEnabled = areChordsEnabled,
                isMetronomeEnabled = isMetronomeEnabled,
                isEnabled = areSetlistsEnabled,
                onDetailsChanged = viewModel::setSetlistCardDetails,
            )
        }
    }
}

/**
 * One list's card details, named by the list's own name in the navigation, with the chips at the end of the row while
 * the name fits beside them. Dimmed like a disabled row with [isEnabled] false, the chips dimming themselves.
 */
@Composable
private fun CardDetailsRow(
    modifier: Modifier = Modifier,
    label: String,
    isEnabled: Boolean = true,
    chips: @Composable () -> Unit,
) = LabeledControlRow(
    modifier = modifier.fillMaxWidth().padding(horizontal = LIST_ITEM_KEYLINE),
    label = {
        Text(
            modifier = Modifier.alpha(if (isEnabled) 1f else 0.5f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
    controls = chips,
)

private val CARD_DETAILS_ROW_GAP = 8.dp
