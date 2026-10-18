/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.appBar

import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * An icon button that, standing on the tonal pill of a list screen's app bar, leaves its ripple to the pill: Material's
 * round ripple on a pill leaves its ends and the corners between the buttons untouched, so the pill draws each press
 * across the button's whole share of it instead (see [SearchPillPresses]). Anywhere else it is Material's icon button.
 */
@Composable
internal fun PillIconButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit,
) {
    val pill = LocalSearchPill.current
    if (pill == null) {
        IconButton(modifier = modifier, onClick = onClick, enabled = enabled, colors = colors, content = content)
        return
    }
    val button = remember { SearchPillButton() }
    DisposableEffect(pill, button) {
        pill.buttons += button
        onDispose { pill.buttons -= button }
    }
    val rippleConfiguration = LocalRippleConfiguration.current
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        IconButton(
            // Globally positioned rather than placed, since that is called once the bar has been placed as well, and
            // again whenever the button moves for its parent having moved.
            modifier = modifier.onGloballyPositioned { coordinates ->
                pill.bar?.takeIf { it.isAttached }?.let { button.bounds = it.localBoundingBoxOf(coordinates, clipBounds = false) }
            },
            onClick = onClick,
            enabled = enabled,
            colors = colors,
            interactionSource = button.interactions,
        ) {
            // Only the button's own ripple is turned off, not those of whatever it holds.
            CompositionLocalProvider(LocalRippleConfiguration provides rippleConfiguration, content = content)
        }
    }
}
