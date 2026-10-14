/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.labels

import com.pandulapeter.campfire.presentation.ui.components.only
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.Painter
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_label_fill
import org.jetbrains.compose.resources.painterResource

/**
 * The mark of a tag: the outlined label every tag carries, filled with [fill] where the tag was given a color. The
 * outline stays in [tint] either way, so a colored tag is still drawn as the same mark, and the color is only ever what
 * tells one tag from another at a glance, never the one thing that says which tag it is.
 *
 * @param painter The outline, which only a pill or a chip that draws a language rather than a tag replaces, never
 *   with a fill: the fill is the inside of the tag's own outline.
 * @param fill [Color.Unspecified] for a tag left at the default, which is the outline alone.
 */
@Composable
internal fun TagIcon(
    modifier: Modifier = Modifier,
    painter: Painter = painterResource(Res.drawable.ic_label),
    fill: Color,
    tint: Color = LocalContentColor.current,
) = Box(modifier = modifier) {
    if (fill.isSpecified && fill.alpha > 0f) {
        Icon(
            modifier = Modifier.matchParentSize(),
            painter = painterResource(Res.drawable.ic_label_fill),
            contentDescription = null,
            tint = fill,
        )
    }
    Icon(
        painter = painter,
        contentDescription = null,
        tint = tint,
    )
}
