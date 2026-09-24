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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_expand
import org.jetbrains.compose.resources.painterResource

/**
 * The chevron of everything in the app that folds away, pointing down while there is something to unfold and turning
 * over as it is unfolded. Every fold draws this one, so that they all turn the same way at the same pace.
 *
 * @param contentDescription What pressing the control it belongs to does, which changes with [isExpanded].
 */
@Composable
internal fun ExpandChevron(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    contentDescription: String?,
    tint: Color = LocalContentColor.current,
) {
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f)
    Icon(
        modifier = modifier.rotate(rotation),
        painter = painterResource(Res.drawable.ic_expand),
        contentDescription = contentDescription,
        tint = tint,
    )
}
