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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import com.pandulapeter.campfire.presentation.ui.notation.NotationRun
import com.pandulapeter.campfire.presentation.ui.notation.drawNotationSystem
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
 * that replaces one the reader changed ([NotationRun.isRerendered]) fades in.
 */
@Composable
internal fun SongNotationBlock(
    modifier: Modifier = Modifier,
    run: NotationRun,
    text: String,
    slot: Int,
    isLastSlot: Boolean,
) = key(run.systems) {
    val color = MaterialTheme.colorScheme.onSurface
    val chordColor = LocalSecondAccentColor.current
    val systems = run.systems
    val unitSize = run.unitSize
    val shown = if (isLastSlot) systems.drop(slot) else listOfNotNull(systems.getOrNull(slot))
    val pageWidth = systems.maxOfOrNull { it.width } ?: 0f
    fun scaleAt(width: Int) = if (pageWidth <= 0f) unitSize else minOf(unitSize, width / pageWidth)
    Layout(
        // The notation is drawn, so a screen reader is handed the tune as it stands in the file, by the run's first piece.
        modifier = modifier
            .fadingIn(isFadingIn = run.isRerendered)
            .then(if (slot == 0) Modifier.semantics { contentDescription = text } else Modifier)
            .drawBehind {
                val scale = scaleAt(size.width.roundToInt())
                var top = 0f
                shown.forEach { system ->
                    drawNotationSystem(system = system, top = top, scale = scale, unitSize = run.measuredUnitSize, color = color, chordColor = chordColor)
                    top += system.height * scale
                }
            },
    ) { _, constraints ->
        val width = constraints.maxWidth
        val naturalWidth = ceil(pageWidth * unitSize).toInt()
        val scale = scaleAt(if (width == Constraints.Infinity) naturalWidth else width)
        val height = ceil(shown.sumOf { it.height.toDouble() } * scale).toInt()
        layout(
            width = if (width == Constraints.Infinity) naturalWidth else width,
            height = height.coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {}
    }
}
