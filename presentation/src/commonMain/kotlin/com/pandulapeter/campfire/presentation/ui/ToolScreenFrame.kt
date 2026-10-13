/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIconBeat
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/**
 * A tool's screen with the room for [ToolsTabRow] above it while the tools are behind the bar's Tools item ([tools] not
 * empty), the room growing and shrinking as the grouping comes and goes, so that the screen moves rather than jumps.
 * The row itself is drawn once over every tool screen ([SharedToolsTabRow]), so that its indicator slides from one
 * tool to the next while the screens fade through under it; only while the screens carry a chrome of their own (a card
 * dealt over them, see [CampfireScreens]) does this draw a copy of it in that room, which moves with the screen.
 */
@Composable
internal fun ToolScreenFrame(
    destination: CampfireDestination.TopLevel,
    tools: List<CampfireDestination.TopLevel>,
    isTabRowDrawn: Boolean,
    contentPadding: PaddingValues,
    metronomeBeat: MetronomeIconBeat,
    isKeyboard: Boolean,
    onToolSelected: (CampfireDestination.TopLevel) -> Unit,
    content: @Composable () -> Unit,
) = Column(modifier = Modifier.fillMaxSize()) {
    val tabRowHeight by animateDpAsState(
        targetValue = if (tools.isEmpty()) 0.dp else TOOLS_TAB_ROW_HEIGHT,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(tabRowHeight)
            .clipToBounds(),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (isTabRowDrawn && tools.isNotEmpty()) {
            ToolsTabRow(
                modifier = Modifier
                    .padding(contentPadding.only(start = true, end = true))
                    .wrapContentHeight(unbounded = true),
                tools = tools,
                selected = destination,
                metronomeBeat = metronomeBeat,
                isKeyboard = isKeyboard,
                onSelected = onToolSelected,
            )
        }
    }
    Box(modifier = Modifier.weight(1f)) {
        content()
    }
}
