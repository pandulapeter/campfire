/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_sheet_music_part_choose
import com.pandulapeter.campfire.presentation.resources.song_details_sheet_music_part_unnamed
import com.pandulapeter.campfire.presentation.resources.song_details_sheet_music_parts
import com.pandulapeter.campfire.presentation.ui.components.RadioListItem
import com.pandulapeter.campfire.presentation.ui.components.list.LIST_ITEM_KEYLINE
import com.pandulapeter.campfire.presentation.ui.components.menu.OverflowMenu
import com.pandulapeter.campfire.presentation.ui.notation.AbcPart
import com.pandulapeter.campfire.presentation.ui.notation.NotationPartPicker
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSectionTitle

/**
 * A sheet music heading ([heading]) with the picker of the part its score shows right after it, where there is one to
 * pick (see `NotationPartChoices`). It comes and goes with the player's instrument, so it travels in and out.
 */
@Composable
internal fun HeadingWithPartPicker(
    modifier: Modifier = Modifier,
    picker: NotationPartPicker?,
    style: TextStyle,
    heading: @Composable () -> Unit,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
) {
    heading()
    // Kept while it leaves, so that it leaves as it was rather than as nothing. Not a state: nothing is redrawn for it.
    val lastPicker = remember { LastPartPicker() }
    picker?.let { lastPicker.value = it }
    AnimatedVisibility(
        visible = picker != null,
        enter = fadeIn() + expandHorizontally(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
        exit = fadeOut() + shrinkHorizontally(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
    ) {
        lastPicker.value?.let { PartPicker(modifier = Modifier.padding(start = PICKER_GAP), picker = it, style = style) }
    }
}

@Composable
private fun PartPicker(
    modifier: Modifier = Modifier,
    picker: NotationPartPicker,
    style: TextStyle,
) {
    val labels = picker.parts.map { partLabel(it) }
    val title = stringResource(Res.string.song_details_sheet_music_parts)
    val chooseLabel = stringResource(Res.string.song_details_sheet_music_part_choose)
    OverflowMenu(
        modifier = modifier,
        button = { open ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                AnimatedContent(
                    targetState = labels[picker.selected],
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                ) { label ->
                    Text(
                        modifier = Modifier
                            .clickable(onClickLabel = chooseLabel, role = Role.Button, onClick = open)
                            .padding(horizontal = PICKER_HORIZONTAL_PADDING, vertical = PICKER_VERTICAL_PADDING),
                        text = label,
                        style = style,
                        maxLines = 1,
                    )
                }
            }
        },
    ) { select ->
        SettingsSectionTitle(
            modifier = Modifier.width(PICKER_MENU_WIDTH),
            text = title,
            contentPadding = PaddingValues(start = LIST_ITEM_KEYLINE, end = LIST_ITEM_KEYLINE, top = 8.dp, bottom = 4.dp),
        )
        labels.forEachIndexed { index, label ->
            RadioListItem(
                modifier = Modifier.width(PICKER_MENU_WIDTH),
                title = label,
                isSelected = index == picker.selected,
                onSelected = { select { picker.onSelected(index) } },
            )
        }
    }
}

/** A part by its name, or by its voice's id where the score names it nothing. */
@Composable
private fun partLabel(part: AbcPart) = part.name ?: textResource(Res.string.song_details_sheet_music_part_unnamed, part.voiceId)

private class LastPartPicker(var value: NotationPartPicker? = null)

private val PICKER_GAP = 8.dp
private val PICKER_HORIZONTAL_PADDING = 12.dp

/** The fold toggle row's own, so that the heading is as tall with the picker as without it. */
private val PICKER_VERTICAL_PADDING = 4.dp
private val PICKER_MENU_WIDTH = 220.dp
