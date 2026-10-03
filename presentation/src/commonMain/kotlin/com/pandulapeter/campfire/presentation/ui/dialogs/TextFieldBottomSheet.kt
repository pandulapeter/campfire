/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp

/**
 * A form in the app's usual sheet, with its actions in the header and its original fields kept in one composition.
 * The header's close button cancels the draft. Content keeps clear of the keyboard and system bars, while the sheet
 * handles short windows the same way as the assignment sheets.
 *
 * @param text Handed the sheet's `contentPadding`, which scrolling content applies inside its scroll, so that it scrolls on
 *   under the navigation bar, and anything else leaves under its last row.
 * @param retainHeight Keeps a list editor at its largest measured height while it is open, so filtering or removing
 *   rows does not move the header and search field. Keyboard and system-bar padding are not retained, so the sheet
 *   returns to its content's height when the keyboard closes. Small lists still open at their content's height.
 */
@Composable
internal fun TextFieldBottomSheet(
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String = "",
    retainHeight: Boolean = false,
    text: @Composable (contentPadding: PaddingValues) -> Unit,
    confirmButton: @Composable () -> Unit,
    startButton: (@Composable () -> Unit)? = null,
) = CampfireBottomSheet(
    title = title,
    subtitle = subtitle,
    actions = {
        startButton?.invoke()
        confirmButton()
    },
    onDismiss = onDismissRequest,
) { contentPadding ->
    Box(
        modifier = Modifier
            .weight(1f, fill = false)
            .then(
                if (retainHeight) {
                    Modifier.retainSheetContentHeight(contentPadding)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp),
    ) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text(contentPadding) }
    }
}

/** Retain a list's height across filtering, without retaining the space occupied by the keyboard. */
@Composable
internal fun Modifier.retainSheetContentHeight(contentPadding: PaddingValues): Modifier {
    var tallestContentHeight by remember { mutableIntStateOf(0) }
    return layout { measurable, constraints ->
        // Read the padding in the measure pass, alongside the content that applies it: IME insets change during
        // layout, so a value captured during composition can belong to the preceding keyboard animation frame.
        val bottomPadding = contentPadding.calculateBottomPadding().roundToPx()
        val minimumHeight = (tallestContentHeight + bottomPadding).coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = minimumHeight))
        tallestContentHeight = maxOf(tallestContentHeight, placeable.height - bottomPadding)
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
}
