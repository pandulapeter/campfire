/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chords
import com.pandulapeter.campfire.presentation.resources.ic_settings
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.settings_general
import com.pandulapeter.campfire.presentation.resources.preferences_page_song
import com.pandulapeter.campfire.presentation.ui.chords.ChordsIcon
import com.pandulapeter.campfire.presentation.ui.components.CampfireTooltip
import com.pandulapeter.campfire.presentation.ui.components.list.LIST_ITEM_KEYLINE
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIcon
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIconBeat
import org.jetbrains.compose.resources.painterResource

/**
 * The Preferences' pages as a connected button group, the way the tools behind the Tools item are switched: it reads as
 * switching what is shown under it, where a row of tabs under Settings' own nested tabs in tabs, and a row of chips
 * looked like one more setting. As in the tools' group each button carries an icon, and only the picked one also shows
 * its name, widening as it does, since four names side by side would cut "Metronome" short on a 360dp phone; the others
 * are named for accessibility services and by a tooltip.
 *
 * @param isKeyboard Whether the Chords page's icon is the keyboard's, as the Chords tab's is.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PreferencesPageButtons(
    modifier: Modifier = Modifier,
    pages: List<PreferencesPage>,
    selectedPage: PreferencesPage,
    isKeyboard: Boolean,
    onPageSelected: (PreferencesPage) -> Unit,
) = Row(
    modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = LIST_ITEM_KEYLINE, vertical = 8.dp)
        .selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    verticalAlignment = Alignment.CenterVertically,
) {
    pages.forEachIndexed { index, page ->
        val isSelected = page == selectedPage
        val label = page.label()
        val weight by animateFloatAsState(
            targetValue = if (isSelected) SELECTED_BUTTON_WEIGHT else 1f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        )
        // The tooltip's anchor stands between the row and the button, so it takes the weight and the button fills it.
        CampfireTooltip(modifier = Modifier.weight(weight), label = label.takeUnless { isSelected }) {
            ToggleButton(
                modifier = Modifier
                    .fillMaxWidth()
                    // One choice of several, as Material's single-select group reads, not a checkbox each.
                    .semantics {
                        role = Role.RadioButton
                        if (!isSelected) contentDescription = label
                    },
                checked = isSelected,
                onCheckedChange = { onPageSelected(page) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    pages.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = BUTTON_CONTENT_PADDING,
            ) {
                PageIcon(page = page, isKeyboard = isKeyboard)
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                ) {
                    Text(
                        modifier = Modifier.padding(start = ToggleButtonDefaults.IconSpacing),
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The icon of the screen a page is about, the one its navigation item has; General has Settings' own. */
@Composable
private fun PageIcon(
    page: PreferencesPage,
    isKeyboard: Boolean,
) = when (page) {
    PreferencesPage.GENERAL -> Icon(painter = painterResource(Res.drawable.ic_settings), contentDescription = null)
    PreferencesPage.SONG -> Icon(painter = painterResource(Res.drawable.ic_songs), contentDescription = null)
    PreferencesPage.CHORDS -> ChordsIcon(isKeyboard = isKeyboard)
    // At rest: a page button is no click to follow.
    PreferencesPage.METRONOME -> MetronomeIcon(
        beat = remember { MetronomeIconBeat(swingState = Animatable(0f), pulseState = Animatable(0f)) },
        contentDescription = null,
    )
}

@Composable
private fun PreferencesPage.label() = stringResource(
    when (this) {
        PreferencesPage.GENERAL -> Res.string.settings_general
        PreferencesPage.SONG -> Res.string.preferences_page_song
        PreferencesPage.CHORDS -> Res.string.chords
        PreferencesPage.METRONOME -> Res.string.metronome
    },
)

/** How much wider than each of the others the picked button is: enough for "Metronome" beside its icon at 360dp. */
private const val SELECTED_BUTTON_WEIGHT = 2.5f

/** Material's 16dp at each side of a small button's content left the picked one's name too little room. */
private val BUTTON_CONTENT_PADDING = PaddingValues(horizontal = 12.dp, vertical = ToggleButtonDefaults.ContentPadding.calculateTopPadding())
