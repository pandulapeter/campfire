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

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/** Lays the content out [amount] wider on either side than it is given, over the padding around it. */
internal fun Modifier.bleedHorizontally(amount: Dp) = layout { measurable, constraints ->
    val bleed = amount.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + bleed * 2,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + bleed * 2 else constraints.maxWidth,
        ),
    )
    layout(placeable.width - bleed * 2, placeable.height) { placeable.place(-bleed, 0) }
}
