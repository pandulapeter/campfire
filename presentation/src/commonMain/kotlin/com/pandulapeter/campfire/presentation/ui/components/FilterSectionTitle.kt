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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.UserPreferences

/**
 * The title of a filter group, with the [LabelSortingToggle] of its chips right after it and the action that empties
 * it at the other end of the same row.
 *
 * It is up here rather than under the chips because it comes and goes with the selection, and a button of its own
 * would grow and shrink everything below it - in a bottom sheet, the sheet itself - every time a filter was turned on
 * or off. The row is laid out so that it cannot: it is always as tall as the action ([SECTION_ACTION_HEIGHT]), whether
 * the action is showing or not, so the title reads in the same place either way and the action never decides anything.
 */
@Composable
internal fun FilterSectionTitle(
    modifier: Modifier = Modifier,
    title: String,
    sortingMode: UserPreferences.LabelSortingMode,
    onSortingModeSelected: (UserPreferences.LabelSortingMode) -> Unit,
    isClearVisible: Boolean,
    clearText: String,
    onClearClicked: () -> Unit,
) = Row(
    modifier = modifier
        .fillMaxWidth()
        .padding(
            start = CONTROLS_PADDING,
            end = CONTROLS_PADDING - BUTTON_INSET,
            bottom = SECTION_TITLE_BOTTOM_PADDING,
        )
        .height(SECTION_ACTION_HEIGHT),
    verticalAlignment = Alignment.CenterVertically,
) {
    // Only as wide as its text, since the toggle is about the title and sits right after it. The title fills whatever
    // width it is given, so a weight would hand it half of the row's free space.
    SettingsSectionTitle(
        modifier = Modifier.width(IntrinsicSize.Max),
        text = title,
        contentPadding = PaddingValues(),
    )
    // The same reason as the clear action's below: the row is as tall as the action, and the touch target would
    // make it taller.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        LabelSortingToggle(
            modifier = Modifier.padding(start = SORTING_TOGGLE_GAP).size(SORTING_TOGGLE_SIZE),
            sortingMode = sortingMode,
            onSortingModeSelected = onSortingModeSelected,
        )
    }
    Spacer(modifier = Modifier.weight(1f))
    AnimatedVisibility(
        visible = isClearVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        // A button that reserved the 48dp touch target would be the tallest thing in the row and would decide its
        // height, which is the one thing this row must not let it do.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            TextButton(
                modifier = Modifier.height(SECTION_ACTION_HEIGHT),
                onClick = onClearClicked,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                contentPadding = PaddingValues(horizontal = BUTTON_INSET),
            ) {
                Text(text = clearText)
            }
        }
    }
}

/** The height of a [FilterSectionTitle], which is its action's with no touch target around it, and the gap under it. */
private val SECTION_ACTION_HEIGHT = 32.dp
private val SECTION_TITLE_BOTTOM_PADDING = 4.dp

/** The padding a text button keeps inside its own bounds, taken off so that its label lines up with the titles. */
internal val BUTTON_INSET = 12.dp
