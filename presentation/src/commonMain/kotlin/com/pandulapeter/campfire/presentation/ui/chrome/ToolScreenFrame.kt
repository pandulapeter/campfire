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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.layout
import com.pandulapeter.campfire.presentation.ui.components.fadingBottomEdge
import kotlin.math.roundToInt
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/**
 * A tool's screen with the room for [ToolsButtonGroup] under it while the tools are behind the chrome's Tools item ([tools] not
 * empty), next to that item and in reach of the thumb, the room growing and shrinking as the grouping comes and goes, so
 * that the screen moves rather than jumps. The room also closes while the keyboard is up ([rememberIsKeyboardUp]). The
 * screen ends above the room, fading out at its bottom edge as it would under a bar ([fadingBottomEdge]), as strongly as
 * the room is open. The group itself is drawn once over every tool screen
 * ([SharedToolsButtonGroup]), so that its selection morphs from one tool to the next while the screens fade through
 * under it; only while the screens draw the chrome as their own (a card dealt over them, see [CampfireScreens]) does this
 * draw the group's last recording ([ChromeLayers]) in that room, which moves with the screen and is covered as the room
 * closes over it.
 * Under the buttons the room keeps what [contentPadding] keeps at the bottom without the keyboard (the window's inset
 * where no navigation bar stands there), read while measuring: with the keyboard counted, the room would open by its
 * height while it closes.
 */
@Composable
internal fun ToolScreenFrame(
    tools: List<CampfireDestination.TopLevel>,
    buttonGroup: RecordedLayer,
    isButtonGroupDrawn: () -> Boolean,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) = Column(modifier = Modifier.fillMaxSize()) {
    val isKeyboardUp by rememberIsKeyboardUp()
    // The expressive spring overshoots its target, which would open the room past the group or below nothing.
    val roomFraction = animateFloatAsState(
        targetValue = if (tools.isEmpty() || isKeyboardUp) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .fadingBottomEdge(strength = { roomFraction.value }, backgroundColor = MaterialTheme.colorScheme.background),
    ) {
        content()
    }
    val ime = WindowInsets.ime
    // The group's recording spans the room's width, its own padding included, so it is drawn at the room's corner.
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val fraction = roomFraction.value.coerceIn(0f, 1f)
                val bottomInset = (contentPadding.calculateBottomPadding().roundToPx() - ime.getBottom(this)).coerceAtLeast(0)
                val height = ((TOOLS_BUTTON_GROUP_HEIGHT.roundToPx() + bottomInset) * fraction).roundToInt()
                val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                layout(placeable.width, height) { placeable.placeRelative(0, 0) }
            }
            .clipToBounds()
            .drawBehind { if (isButtonGroupDrawn()) drawLayer(buttonGroup.layer) },
    )
}
