/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chrome

import com.pandulapeter.campfire.presentation.ui.CampfireScreens
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIconBeat
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/**
 * The one [ToolsButtonGroup] the tool screens share while none of them carries a chrome of its own (see [ToolScreenFrame]),
 * laid over the room each of them keeps for it, next to the rail's room ([railWidth]) and under the status bar as the
 * screens are. It fades in and out as the stack arrives at a tool or leaves for another screen, and as a resized window
 * groups the tools behind the Tools item ([tools] not empty) or lets them go, while the screens fade through under it
 * or move into the room they keep for it; it keeps the tools and the tool it was on through its exit so that the
 * selection does not move away nor the buttons go blank first. The background is painted across the whole width and
 * under the status bar as well as behind the group, so that nothing of the screens fading through shows beside or above
 * it.
 *
 * It reads the back stack itself rather than being handed what is on top, so that a change of screens recomposes the
 * group rather than everything [CampfireScreens] lays out.
 */
@Composable
internal fun SharedToolsButtonGroup(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    tools: List<CampfireDestination.TopLevel>,
    railWidth: () -> Dp,
    contentPadding: PaddingValues,
    metronomeBeat: MetronomeIconBeat,
    isKeyboard: Boolean,
) {
    val current = viewModel.navigator.backStack.lastOrNull() as? CampfireDestination.TopLevel
    val lastShownTool = remember { mutableStateOf(current?.takeIf { it in tools }) }
    val lastShownTools = remember { mutableStateOf(tools) }
    SideEffect {
        if (current in tools) lastShownTool.value = current
        if (tools.isNotEmpty()) lastShownTools.value = tools
    }
    AnimatedVisibility(
        modifier = modifier,
        visible = current in tools,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
    ) {
        ToolsButtonGroup(
            modifier = Modifier
                .padding(ChromeRoomPadding(start = railWidth, bottom = NoChromeRoom))
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.contentEdges.only(WindowInsetsSides.Top).withMinTopEdge)
                .padding(contentPadding.only(start = true, end = true)),
            tools = tools.ifEmpty { lastShownTools.value },
            selected = current?.takeIf { it in tools } ?: lastShownTool.value,
            metronomeBeat = metronomeBeat,
            isKeyboard = isKeyboard,
            onSelected = viewModel::selectTopLevelDestination,
        )
    }
}
