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

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut

/**
 * Material's icon button with a [CampfireTooltip] naming it, which is what every icon-only button of the app is: one
 * whose [icon] is drawn with [label] as its content description, the tooltip saying the same.
 *
 * [modifier] is the button's, not the tooltip's, so focus, semantics, and size modifiers keep working on it; a modifier
 * that is the parent layout's to read (a `weight`, an `align`) goes on a [CampfireTooltip] wrapped around the button
 * instead, since the tooltip's anchor stands between the two.
 */
@Composable
internal fun CampfireIconButton(
    modifier: Modifier = Modifier,
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    shortcut: KeyShortcut? = null,
    iconModifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
) = CampfireIconButton(
    modifier = modifier,
    label = label,
    onClick = onClick,
    enabled = enabled,
    shortcut = shortcut,
    colors = colors,
) {
    Icon(modifier = iconModifier, painter = icon, contentDescription = label, tint = tint)
}

/**
 * A [CampfireIconButton] drawing [content] of its own (a crossfade, a turning chevron), which gives screen readers its
 * name itself; [label] is only the tooltip's.
 *
 * @param shortcut The keys that press the button too, written after [label] (see [CampfireTooltip]).
 * @param isTooltipShownOnLongPress See [CampfireTooltip]'s `isShownOnLongPress`.
 */
@Composable
internal fun CampfireIconButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    shortcut: KeyShortcut? = null,
    isTooltipShownOnLongPress: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) = CampfireTooltip(
    label = label,
    shortcut = shortcut,
    isShownOnLongPress = isTooltipShownOnLongPress,
) {
    IconButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}
