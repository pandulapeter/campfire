/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Draws [content] at [interfaceScale], by scaling the density everything under it converts its dp and sp with. That is
 * the one lever that shrinks Material as a whole - text, icons, paddings, touch targets, bars and dialogs in the
 * proportions they were designed in - where scaling the typography alone would leave every row and button at the size
 * it has on a phone, around smaller text. The window size classes follow along, since they are counted in the same dp:
 * a smaller interface gets to the wider layouts in a narrower window.
 *
 * The text size of the song details screen is multiplied on top of this rather than replaced by it, so it keeps meaning
 * the same thing relative to the rest of the interface.
 */
@Composable
internal fun ProvideInterfaceScale(
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density = density.density * interfaceScale, fontScale = density.fontScale),
        content = content,
    )
}
