/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.launch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_campfire
import com.pandulapeter.campfire.presentation.ui.theme.LaunchScreenColors
import org.jetbrains.compose.resources.painterResource

/**
 * What the window holds until there is an app to look at in it: the mark, still, on the theme's own background.
 *
 * It is what the first frame of every platform paints, and on the desktop it is what stays there for as long as the
 * first composition of the whole app takes - a third of a second on a cold start, which is a long time for a window
 * to sit empty. Nothing here says anything the preferences have not answered yet: the mark carries no text, and it
 * is drawn in a neutral rather than in the accent color, which is the one thing still being waited for.
 *
 * A [Surface] rather than a plain box, because the app is composed and drawn underneath it: without one, a click
 * landing on the mark would reach whatever of the app happens to be under that point - which stays true while it is
 * fading, when the app can already be seen through it but is still nobody's to touch. The surface itself is
 * transparent, and the background and the mark are painted while drawing from [colors], so that their fade from the
 * guessed palette to the stored one recomposes nothing.
 *
 * @param markScale How large the mark is drawn, read in the layer rather than in the composition so that animating
 *   it is a redraw instead of a recomposition of the whole screen.
 */
@Composable
internal fun LaunchScreen(
    modifier: Modifier = Modifier,
    colors: LaunchScreenColors,
    markScale: () -> Float = { 1f },
) = Surface(
    modifier = modifier
        .fillMaxSize()
        .drawBehind { drawRect(colors.background()) },
    color = Color.Transparent,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val painter = painterResource(Res.drawable.ic_campfire)
        Box(
            modifier = Modifier
                .size(LAUNCH_MARK_SIZE)
                .graphicsLayer {
                    scaleX = markScale()
                    scaleY = markScale()
                }
                .drawBehind { with(painter) { draw(size = size, colorFilter = ColorFilter.tint(colors.mark())) } },
        )
    }
}

private val LAUNCH_MARK_SIZE = 72.dp
