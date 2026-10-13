/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagramGeometry

/**
 * The diagrams of a Chords section on their way from the shape they were drawn with to the one they are drawn with now:
 * [fadingOut] holds the geometry each such cell had, by its index, and [progress] runs from 0, the old shape alone, to 1,
 * the new one alone.
 */
@Stable
internal class ChordShapeCrossfade(
    val fadingOut: Map<Int, ChordDiagramGeometry>,
    val progress: Animatable<Float, AnimationVector1D>,
)

/**
 * The crossfade of the cells whose shape a new [layouts] changed (see [changedShapeCells]). It starts at the old shapes
 * in the very frame the new layouts are drawn in, which an effect started after that frame would show the new shape in
 * for a moment, and it compares with the layouts last composed, so a change that arrives mid-fade fades from the shape
 * the last one was fading into.
 */
@Composable
internal fun rememberChordShapeCrossfade(layouts: ChordCellLayouts): ChordShapeCrossfade {
    val shown = remember { ShownLayouts() }
    val crossfade = remember(layouts) {
        val previous = shown.layouts
        ChordShapeCrossfade(
            fadingOut = previous?.let { changedShapeCells(it.cells, layouts.cells).associateWith { cell -> previous.geometries[cell] } }.orEmpty(),
            progress = Animatable(0f),
        )
    }
    SideEffect { shown.layouts = layouts }
    val animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(crossfade) {
        if (crossfade.fadingOut.isNotEmpty()) crossfade.progress.animateTo(1f, animationSpec)
    }
    return crossfade
}

/**
 * The indices of [cells] whose shape changed since [previous]: the same chord at the same index on the same instrument,
 * drawn with one shape before and with another now, a shape picked in its sheet or a tuning picked for the instrument.
 * A cell that was the empty frame is not one, since that is a shape the search found arriving rather than one the player
 * changed (see `SongChord.isShapePending`), and neither is a cell that holds another chord now, whose name above it
 * changes in one frame anyway.
 */
internal fun changedShapeCells(previous: List<ChordCell>, cells: List<ChordCell>) = cells.indices.filter { index ->
    val before = previous.getOrNull(index) ?: return@filter false
    val after = cells[index]
    before.name == after.name &&
        before.tuning.instrument == after.tuning.instrument &&
        before.selection.shape != null &&
        (before.selection.shape != after.selection.shape || before.tuning != after.tuning)
}

/** The layouts a Chords block was last composed with, kept outside snapshot state since only the next composition reads it. */
private class ShownLayouts {
    var layouts: ChordCellLayouts? = null
}
