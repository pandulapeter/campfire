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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * A setting whose control does not fit at the end of a row - a segmented choice, the color discs, a list of radio
 * buttons - and so goes under its name instead. The name and the description are set the way a list item sets its
 * headline and supporting text, since that is what they are: the row next to it is a switch with the same two lines.
 *
 * @param isEnabled Dims the title and the description the way a disabled row is dimmed; the control inside is left
 *   to disable itself, so that it is dimmed once rather than twice.
 */
@Composable
internal fun SettingsSubsection(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    isEnabled: Boolean = true,
    shouldApplyPadding: Boolean = true,
    content: @Composable () -> Unit,
) = Column(modifier = modifier.padding(vertical = if (shouldApplyPadding) SUBSECTION_PADDING else 0.dp)) {
    val labelAlpha = if (isEnabled) 1f else 0.5f
    title?.let {
        Text(
            modifier = Modifier.alpha(labelAlpha).padding(horizontal = 16.dp),
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    description?.let {
        Text(
            modifier = Modifier.alpha(labelAlpha).padding(horizontal = 16.dp),
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column(modifier = Modifier.padding(top = SUBSECTION_CONTROL_GAP)) { content() }
}

/** The room a [SettingsSubsection] keeps above and below itself, which is what a list item pads itself by. */
private val SUBSECTION_PADDING = 12.dp

/** The gap between the text of a [SettingsSubsection] and its control. */
private val SUBSECTION_CONTROL_GAP = 12.dp
