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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_drag_handle
import com.pandulapeter.campfire.presentation.resources.setlists_reorder
import org.jetbrains.compose.resources.painterResource

/**
 * The grip that says a row can be dragged somewhere else, placed in front of the row's overflow button so that the
 * button stays on the keyline every other song row keeps it on. It is an icon inside a plain box rather than an
 * [IconButton], because it is never pressed on its own: the caller is the one that puts the reorderable drag
 * modifier on it, and a button's ripple would promise a tap that does nothing. The box is what makes it big enough to
 * catch a finger, so the drag modifier has to go on [modifier] rather than on the icon. The icon sits at the end of
 * the box, against the button, since the button's own padding is already more of a gap than the two need.
 */
@Composable
internal fun DragHandle(
    modifier: Modifier = Modifier,
) = Box(
    modifier = modifier.size(width = DRAG_HANDLE_WIDTH, height = DRAG_HANDLE_HEIGHT),
    contentAlignment = Alignment.CenterEnd,
) {
    Icon(
        painter = painterResource(Res.drawable.ic_drag_handle),
        contentDescription = stringResource(Res.string.setlists_reorder),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The width of a [DragHandle]'s touch target. It is narrower than the `IconButton` after it, and can be, since
 * the handle is not a button: the width it gives up is empty space around an icon rather than anything that can be
 * pressed, and the drag it offers is on the row's own long press as well.
 */
private val DRAG_HANDLE_WIDTH = 32.dp

/** The height of a [DragHandle], which is a full touch target since it is the one thing on the row that is dragged. */
private val DRAG_HANDLE_HEIGHT = 48.dp
