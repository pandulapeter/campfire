/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIconBeat
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.settingsPage.SETTINGS_TAB_ROW_MAX_WIDTH

/**
 * The switch between the tools behind the Tools item of the navigation bar or of a short window's rail (see
 * [areToolsGrouped]): a connected button group rather than a row of tabs, so that it does not read as the pages of one
 * screen that a swipe would turn, as the settings screen's tabs are. The selected button fills and morphs in place
 * instead of an indicator travelling across the row, matching the tool screens fading through under it rather than
 * sliding. It is capped at the settings tabs' width and starts where the lists start.
 *
 * Each button carries its tool's icon, the one its navigation item would have ([DestinationIcon]), since the chrome's
 * own item shows only the wrench; only the selected one also shows its name, widening as it does, since four names
 * side by side would cut "Metronome" short on a 360dp phone. The others are named for accessibility services instead.
 * Each button is a top level screen of its own, selected as its item would be, so the one already open scrolls its
 * screen to the top.
 *
 * The group is laid on the background color rather than left transparent: the tool screens fade through under it, and
 * a transparent row would show both of them at once for the length of the fade.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ToolsButtonGroup(
    modifier: Modifier = Modifier,
    tools: List<CampfireDestination.TopLevel>,
    selected: CampfireDestination.TopLevel?,
    metronomeBeat: MetronomeIconBeat,
    isKeyboard: Boolean,
    onSelected: (CampfireDestination.TopLevel) -> Unit,
) = Row(
    modifier = modifier
        .widthIn(max = SETTINGS_TAB_ROW_MAX_WIDTH)
        .height(TOOLS_BUTTON_GROUP_HEIGHT)
        .background(MaterialTheme.colorScheme.background)
        // The tabs carried the gutter in their own padding; the buttons' containers reach their edges, so the group
        // keeps the one every list of the app keeps.
        .padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    verticalAlignment = Alignment.CenterVertically,
) {
    tools.forEachIndexed { index, tool ->
        val isSelected = tool == selected
        val label = stringResource(tool.label)
        val weight by animateFloatAsState(
            targetValue = if (isSelected) SELECTED_BUTTON_WEIGHT else 1f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        )
        ToggleButton(
            modifier = Modifier
                .weight(weight)
                .height(ToggleButtonDefaults.MinHeight)
                .semantics { if (!isSelected) contentDescription = label },
            checked = isSelected,
            onCheckedChange = { onSelected(tool) },
            shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                tools.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            },
            contentPadding = BUTTON_CONTENT_PADDING,
        ) {
            DestinationIcon(destination = tool, metronomeBeat = metronomeBeat, isKeyboard = isKeyboard)
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

/**
 * The height of [ToolsButtonGroup], held fixed so that the room each tool screen keeps for it is always exactly what the
 * group takes, whatever the text size.
 */
internal val TOOLS_BUTTON_GROUP_HEIGHT = 56.dp

/**
 * How much wider than each of the others the selected button is: enough for "Metronome" beside its icon on a 360dp
 * phone, the widest of the names.
 */
private const val SELECTED_BUTTON_WEIGHT = 2.5f

/**
 * Material's 16dp at each side of a small button's content, with four buttons in 328dp, left the selected one's name
 * too little room. The vertical padding is dropped as the button's height is fixed.
 */
private val BUTTON_CONTENT_PADDING = PaddingValues(horizontal = 12.dp)
