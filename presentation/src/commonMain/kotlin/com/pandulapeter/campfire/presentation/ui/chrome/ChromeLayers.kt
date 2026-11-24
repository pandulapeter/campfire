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

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.toSize

/**
 * The chrome's parts that a screen covered by a card or by the export screen draws as its own, so that they ride its
 * parallax and its scrim, instead of a copy of them composed into that screen on every push and pop. Each part is
 * composed and laid out once, where it always is ([NavigationChromeScaffold], [SharedToolsButtonGroup]), records what it
 * draws on every frame it is drawn ([recordedInto]), and is put out of the window meanwhile ([coveredChrome]), where it
 * is still drawn, so still recorded, which keeps the metronome's beat and a selection's morph live in every copy.
 */
@Stable
internal class ChromeLayers(
    val chrome: RecordedLayer,
    val outgoingChrome: RecordedLayer,
    val toolsButtonGroup: RecordedLayer,
)

@Composable
internal fun rememberChromeLayers(): ChromeLayers {
    val chrome = rememberGraphicsLayer()
    val outgoingChrome = rememberGraphicsLayer()
    val toolsButtonGroup = rememberGraphicsLayer()
    return remember(chrome, outgoingChrome, toolsButtonGroup) {
        ChromeLayers(
            chrome = RecordedLayer(chrome),
            outgoingChrome = RecordedLayer(outgoingChrome),
            toolsButtonGroup = RecordedLayer(toolsButtonGroup),
        )
    }
}

/**
 * What one part drew on its last frame, with where it rests ([position], in the coordinates the screens start at, which
 * are the window's) and how faded in it is there ([alpha]), both written while it is placed.
 */
@Stable
internal class RecordedLayer(val layer: GraphicsLayer) {
    var position by mutableStateOf(Offset.Zero)
    var alpha by mutableFloatStateOf(0f)

    /** Draws the last recording where the part rests, as faded as the part is. */
    fun draw(scope: DrawScope) = with(scope) {
        if (alpha <= 0f) return@with
        translate(left = position.x, top = position.y) {
            if (alpha >= 1f) {
                drawLayer(layer)
            } else {
                // Only while the chrome hands over from one kind to another under a card, so a layer of its own is rare.
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(Offset.Zero, layer.size.toSize()), Paint().also { it.alpha = alpha })
                    drawLayer(layer)
                    canvas.restore()
                }
            }
        }
    }
}

/** Records what this draws into [recorded] on every frame it is drawn, and draws it in place as well. */
internal fun Modifier.recordedInto(recorded: RecordedLayer) = drawWithContent {
    recorded.layer.record { this@drawWithContent.drawContent() }
    drawLayer(recorded.layer)
}

/**
 * Keeps a part of the chrome that the covered screens draw ([isCovered]) away from the keyboard and screen readers, the
 * way an unplaced one is. The part is also put out of the window meanwhile ([NavigationChromeScaffold],
 * [awayFromWindow]) rather than under something that eats presses: one left where it rests would be hovered through a
 * card's empty areas, its tooltip and hover state drawn into the very recording the screens show, and the tools' group,
 * laid over the screens, would take the presses meant for a song opened from a tool.
 */
internal fun Modifier.coveredChrome(isCovered: Boolean) = if (isCovered) {
    clearAndSetSemantics { }
        .focusProperties { onEnter = { cancelFocusChange() } }
        .focusGroup()
} else {
    this
}

/**
 * Places a part that spans the window's width ([coveredChrome]) one width towards the end edge while [isCovered], read
 * while placing. Compose does not cull by bounds, so a part out of the window is still drawn, and so still recorded.
 */
internal fun Modifier.awayFromWindow(isCovered: () -> Boolean) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { placeable.placeRelative(x = if (isCovered()) placeable.width else 0, y = 0) }
}
