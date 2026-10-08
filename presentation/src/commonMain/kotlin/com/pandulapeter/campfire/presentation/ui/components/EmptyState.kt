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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * One of the buttons under an [EmptyState]'s text. The first of them is filled and the rest are outlined, so that a
 * list of them reads as the thing to do here followed by the other things that could be done instead.
 *
 * @param onClick Null leaves the button visible but disabled, for an action the app will be able to offer but
 *   cannot yet.
 */
internal data class EmptyStateAction(
    val text: String,
    val onClick: (() -> Unit)?,
)

/**
 * @param actions The buttons under the hint. Empty leaves the state text only, which is what a list that is empty
 *   for a good reason gets: there is nothing to retry.
 */
@Composable
internal fun EmptyState(
    modifier: Modifier = Modifier,
    icon: Painter,
    title: String,
    /** Null when the title already says everything, e.g. for a song file that is simply still empty. */
    hint: String? = null,
    actions: List<EmptyStateAction> = emptyList(),
) = Column(
    // Always the full width, so that the text is centered on the list rather than on itself.
    modifier = modifier.fillMaxWidth().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Icon(
        modifier = Modifier.padding(bottom = 16.dp).alpha(0.6f),
        painter = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    if (hint != null) {
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    if (actions.isNotEmpty()) {
        // The window rather than the space this is being laid out in: an empty state sits in a lazy list, whose
        // items are measured with no height at all to compare a width against.
        val windowSize = LocalWindowInfo.current.containerSize
        if (windowSize.height > windowSize.width) {
            // One under the other and all the same width, which is what a portrait window has room for: side by
            // side these wrap into a ragged two and one, and three labels this long read as a list rather than as
            // a row anyway.
            Column(
                modifier = Modifier.padding(top = 16.dp).widthIn(max = EMPTY_STATE_ACTION_WIDTH),
                verticalArrangement = Arrangement.spacedBy(EMPTY_STATE_ACTION_GAP),
            ) {
                actions.forEachIndexed { index, action ->
                    EmptyStateActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        action = action,
                        isEmphasized = index == 0,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(EMPTY_STATE_ACTION_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEachIndexed { index, action ->
                    EmptyStateActionButton(action = action, isEmphasized = index == 0)
                }
            }
        }
    }
}

/** @param isEmphasized Whether this is the first of an [EmptyState]'s actions, which is the filled one. */
@Composable
private fun EmptyStateActionButton(
    modifier: Modifier = Modifier,
    action: EmptyStateAction,
    isEmphasized: Boolean,
) = if (isEmphasized) {
    Button(
        modifier = modifier,
        enabled = action.onClick != null,
        onClick = { action.onClick?.invoke() },
    ) {
        Text(action.text)
    }
} else {
    OutlinedButton(
        modifier = modifier,
        enabled = action.onClick != null,
        onClick = { action.onClick?.invoke() },
    ) {
        Text(action.text)
    }
}

/** The gap between two of an [EmptyState]'s buttons. */
private val EMPTY_STATE_ACTION_GAP = 8.dp

/** How wide a stacked column of [EmptyState] buttons grows, so that a portrait tablet does not stretch them. */
private val EMPTY_STATE_ACTION_WIDTH = 280.dp
