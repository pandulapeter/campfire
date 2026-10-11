/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_next
import com.pandulapeter.campfire.presentation.resources.ic_previous
import com.pandulapeter.campfire.presentation.resources.print_next
import com.pandulapeter.campfire.presentation.resources.print_page
import com.pandulapeter.campfire.presentation.resources.print_previous
import org.jetbrains.compose.resources.painterResource

/**
 * The page buttons and the count between them, on a pill floating below the toolbar, coming and going with the preview
 * whose pages they turn, which crossfades to the files a change of the format shows. It fades and grows in and fades and
 * shrinks away as the save button does, but through a layer of its own rather than `fadeIn` and `scaleIn`: a fade draws
 * what it fades into an offscreen buffer the size of its layer while it is not yet opaque, and `fadeIn`'s layer is the
 * pill's size, which cut the shadow around it off until the fade was over. This layer reaches [SHADOW_ROOM] past the
 * pill on every side, so the shadow fades in with it, while the pill still takes up only its own size in the layout.
 */
@Composable
internal fun PageButtons(
    modifier: Modifier,
    isVisible: Boolean,
    page: Int,
    pageCount: Int,
    onTurn: (Int) -> Unit,
) = AnimatedVisibility(isVisible, modifier = modifier, enter = EnterTransition.None, exit = ExitTransition.None) {
    // AnimatedVisibility waits for this animation too before it takes the pill away.
    val progress by transition.animateFloat(transitionSpec = { spring(stiffness = Spring.StiffnessMediumLow) }) { state ->
        if (state == EnterExitState.Visible) 1f else 0f
    }
    PageButtonsPill(
        modifier = Modifier
            .layout { measurable, constraints ->
                val room = SHADOW_ROOM.roundToPx()
                val placeable = measurable.measure(constraints.offset(horizontal = 2 * room, vertical = 2 * room))
                layout(placeable.width - 2 * room, placeable.height - 2 * room) { placeable.place(-room, -room) }
            }
            .graphicsLayer {
                alpha = progress
                scaleX = progress
                scaleY = progress
            }
            .padding(SHADOW_ROOM),
        page = page,
        pageCount = pageCount,
        onTurn = onTurn,
    )
}

@Composable
private fun PageButtonsPill(
    modifier: Modifier,
    page: Int,
    pageCount: Int,
    onTurn: (Int) -> Unit,
) = Surface(
    modifier = modifier.height(PAGE_BUTTONS_HEIGHT),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shadowElevation = 6.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(enabled = page > 0, onClick = { onTurn(page - 1) }) {
            Icon(painterResource(Res.drawable.ic_previous), contentDescription = stringResource(Res.string.print_previous))
        }
        Text(stringResource(Res.string.print_page, page + 1, pageCount), style = MaterialTheme.typography.bodySmall)
        IconButton(enabled = page + 1 < pageCount, onClick = { onTurn(page + 1) }) {
            Icon(painterResource(Res.drawable.ic_next), contentDescription = stringResource(Res.string.print_next))
        }
    }
}

/** The height of the floating page buttons. */
private val PAGE_BUTTONS_HEIGHT = 48.dp

/** How far past the pill its shadow can reach, which the layer that fades it has to cover. */
private val SHADOW_ROOM = 16.dp
