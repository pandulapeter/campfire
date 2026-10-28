/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.animation.core.animate
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import com.pandulapeter.campfire.presentation.ui.notation.NotationRun
import com.pandulapeter.campfire.presentation.ui.notation.NotationSystemPaint
import com.pandulapeter.campfire.presentation.ui.notation.drawNotationSystem
import com.pandulapeter.campfire.presentation.ui.notation.inSlot
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * One piece of a run of `{start_of_abc}` lines drawn as staff notation: the system at [slot], or every system from
 * there on where it [isLastSlot], and nothing where the tune has no system there. A run is cut into as many slots as
 * its text would be ([runSlotCount]), so the section is cut between systems the way it is between the rows of a tab.
 *
 * The systems are drawn [NotationRun.unitSize] pixels to the SVG unit, the size the song's text asks for now, or
 * smaller where that would be wider than the column; the rendering a resize or a pinch asks for follows once it has
 * settled, so until then the systems before it are scaled, and the height a slot answers for any width is known at
 * once, which lets the section be measured at every width the column layout tries (see [SongTabBlock]). A rendering
 * that replaces one the reader changed ([NotationRun.isRerendered]) cross-fades with it ([NotationCrossfade]) rather
 * than fading in from nothing, which would blink the tune out for a moment; the slot takes its new height at once, and
 * the sections around it glide to their new places ([SectionMotion]).
 */
@Composable
internal fun SongNotationBlock(
    modifier: Modifier = Modifier,
    run: NotationRun,
    description: String,
    slot: Int,
    isLastSlot: Boolean,
) {
    val color = MaterialTheme.colorScheme.onSurface
    val chordColor = LocalSecondAccentColor.current
    val crossfade = rememberNotationCrossfade(run)
    val shown = run.systems.inSlot(slot, isLastSlot)
    val unitSize = run.unitSize
    val pageWidth = run.systems.pageWidth()
    Layout(
        // The notation is drawn, so a screen reader is told what it says instead, by the run's first piece.
        modifier = modifier
            .then(if (slot == 0) Modifier.semantics { contentDescription = description } else Modifier)
            .drawBehind {
                val progress = crossfade.progress
                crossfade.leaving?.let { leaving ->
                    val leavingColor = color.copy(alpha = color.alpha * (1f - progress))
                    val leavingChordColor = chordColor.copy(alpha = chordColor.alpha * (1f - progress))
                    drawSystems(leaving.systems.inSlot(slot, isLastSlot), leaving.systems.pageWidth(), unitSize, leaving.measuredUnitSize, leavingColor, leavingChordColor)
                }
                drawSystems(shown, pageWidth, unitSize, run.measuredUnitSize, color.copy(alpha = color.alpha * progress), chordColor.copy(alpha = chordColor.alpha * progress))
            },
    ) { _, constraints ->
        val width = constraints.maxWidth
        val naturalWidth = ceil(pageWidth * unitSize).toInt()
        val scale = scaleAt(if (width == Constraints.Infinity) naturalWidth else width, pageWidth, unitSize)
        val height = ceil(shown.sumOf { it.height.toDouble() } * scale).toInt()
        layout(
            width = if (width == Constraints.Infinity) naturalWidth else width,
            height = height.coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {}
    }
}

private fun List<NotationSystemPaint>.pageWidth() = maxOfOrNull { it.width } ?: 0f

/** The pixels to the SVG unit a page [pageWidth] units wide is drawn at in [width] pixels: [unitSize], or less to fit. */
private fun scaleAt(width: Int, pageWidth: Float, unitSize: Float) = if (pageWidth <= 0f) unitSize else minOf(unitSize, width / pageWidth)

private fun DrawScope.drawSystems(
    systems: List<NotationSystemPaint>,
    pageWidth: Float,
    unitSize: Float,
    measuredUnitSize: Float,
    color: Color,
    chordColor: Color,
) {
    val scale = scaleAt(size.width.roundToInt(), pageWidth, unitSize)
    var top = 0f
    systems.forEach { system ->
        drawNotationSystem(system = system, top = top, scale = scale, unitSize = measuredUnitSize, color = color, chordColor = chordColor)
        top += system.height * scale
    }
}

/**
 * The rendering a newer one of the same run replaced ([leaving]), drawn fading out as the newer one fades in, both by
 * [progress], which is state: read it where it is drawn. Each rendering is told apart by its systems, which the rendering
 * holds for as long as it stands, while a [NotationRun] is made again on every composition.
 */
private class NotationCrossfade(var systems: List<NotationSystemPaint>) {
    var leaving by mutableStateOf<NotationRun?>(null)
    var progress by mutableFloatStateOf(1f)
    var lastRun: NotationRun? = null
}

/**
 * Starts a cross-fade wherever [run] is a rendering the reader asked for in place of the one before it. The fade is
 * decided while composing rather than in the effect that animates it, which only runs once the frame has been drawn: the
 * newer rendering would otherwise be drawn whole for a frame before it starts from nothing.
 */
@Composable
private fun rememberNotationCrossfade(run: NotationRun): NotationCrossfade {
    val crossfade = remember { NotationCrossfade(run.systems) }
    if (run.systems != crossfade.systems) {
        crossfade.systems = run.systems
        // A rendering that arrives while the one before it is still mostly faint (a few steps of the text size in a row)
        // takes its place in the fade under way, so the rendering that was showing never snaps back to full opacity.
        if (!run.isRerendered || crossfade.leaving == null || crossfade.progress >= HALFWAY) {
            crossfade.leaving = crossfade.lastRun?.takeIf { run.isRerendered }
            crossfade.progress = if (crossfade.leaving == null) 1f else 0f
        }
    }
    crossfade.lastRun = run
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(crossfade.leaving) {
        if (crossfade.leaving == null) return@LaunchedEffect
        animate(initialValue = crossfade.progress, targetValue = 1f, animationSpec = spec) { value, _ -> crossfade.progress = value }
        crossfade.leaving = null
    }
    return crossfade
}

private const val HALFWAY = 0.5f
