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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * Retain a list's height across filtering, without retaining the bottom padding: that is the navigation bar's, which the
 * keyboard covers while it is up, and the keyboard itself pads the sheet outside this content (`CampfireBottomSheet`), so
 * it only ever shrinks the space offered here.
 */
@Composable
internal fun Modifier.retainSheetContentHeight(contentPadding: PaddingValues): Modifier {
    var tallestContentHeight by remember { mutableIntStateOf(0) }
    return layout { measurable, constraints ->
        // Read the padding in the measure pass, alongside the content that applies it: the navigation bar's share of it
        // changes as the keyboard slides over it, during layout, so a value captured during composition can belong to
        // the preceding keyboard animation frame.
        val bottomPadding = contentPadding.calculateBottomPadding().roundToPx()
        val minimumHeight = (tallestContentHeight + bottomPadding).coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = minimumHeight))
        tallestContentHeight = maxOf(tallestContentHeight, placeable.height - bottomPadding)
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
}
