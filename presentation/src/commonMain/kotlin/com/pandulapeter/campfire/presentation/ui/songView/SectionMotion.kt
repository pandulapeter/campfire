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

import com.pandulapeter.campfire.presentation.ui.songView.grid.DecidedGrid
import com.pandulapeter.campfire.presentation.ui.songView.grid.hasSameCellsAs
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How the sections of [SongLyrics] get to the place a change of its layout gives them. */
internal enum class SectionMotion {

    /**
     * A change that comes on its own - a window maximised, a step of the text size, a preference switched - is narrated:
     * every section springs from where it was to its new place ([SectionGlides]). Only the place is animated, never the
     * size: a section is laid out at its new size at once, so its lines, controls, and chips are arranged once and travel
     * with it, rather than being arranged again at every size it passes through on the way.
     */
    SPRING,

    /**
     * A change that keeps coming - a pinch, a window edge being dragged - is followed as it comes, and only the jump a
     * section makes where the grid changes is glided over: a spring restarted on every frame would trail behind the
     * change.
     */
    GLIDE,

    /**
     * Every section is simply where the layout puts it, which is the editor's preview: it shows what is being typed rather
     * than narrating it, and every edit that changes a section's height would otherwise move every section below it.
     */
    NONE,
}

/**
 * The glide of every section to a new place, see [SongSectionsLayout] and [SectionMotion]. The positions are followed in the
 * lookahead pass and the offsets read in the approach pass, both by the key of each section, so a section that is
 * added or edited has no position to glide from.
 */
internal class SectionGlides(
    private val scope: CoroutineScope,
    private val spec: AnimationSpec<IntOffset>,
) {

    private val glides = HashMap<Any, SectionGlide>()
    private var lastKeys: List<Any>? = null
    private var lastGrid: DecidedGrid? = null

    /** How far from its place the chunk or the card of [key] is drawn, which is state. */
    fun offsetOf(key: Any) = glides[key]?.offset ?: IntOffset.Zero

    /**
     * Takes the [positions] the sections of [keys] are placed at in [grid], and starts a glide for every one whose
     * position moved: every one where [glidesEveryMove], otherwise only where the grid changed (see [SectionMotion]).
     */
    fun follow(keys: List<Any>, grid: DecidedGrid, positions: Array<IntOffset>, glidesEveryMove: Boolean) {
        if (keys !== lastKeys) {
            val current = keys.toHashSet()
            glides.entries.removeAll { (key, glide) -> (key !in current).also { if (it) glide.stop() } }
            lastKeys = keys
        }
        val isNewGrid = lastGrid.let { it != null && !it.hasSameCellsAs(grid) }
        lastGrid = grid
        keys.forEachIndexed { index, key ->
            val glide = glides.getOrPut(key) { SectionGlide() }
            val previous = glide.target
            glide.target = positions[index]
            if ((glidesEveryMove || isNewGrid) && previous != null && previous != positions[index]) {
                glide.start(jump = previous - positions[index], scope = scope, spec = spec)
            }
        }
    }
}

/**
 * The glide of one section: where it was last placed, and how far from there it is still drawn. A jump is added to
 * [offset] in the pass that finds it rather than when the coroutine that animates it gets to run, which is after that
 * frame has been placed, so the section would otherwise be drawn at its new place for a frame before being sent back.
 */
private class SectionGlide {

    var target: IntOffset? = null
    private val animatable = Animatable(IntOffset.Zero, IntOffset.VectorConverter)
    private var pendingJump = IntOffset.Zero
    private var job: Job? = null

    val offset get() = animatable.value + pendingJump

    fun start(jump: IntOffset, scope: CoroutineScope, spec: AnimationSpec<IntOffset>) {
        pendingJump += jump
        job?.cancel()
        job = scope.launch {
            val velocity = animatable.velocity
            animatable.snapTo(animatable.value + pendingJump)
            pendingJump = IntOffset.Zero
            animatable.animateTo(targetValue = IntOffset.Zero, animationSpec = spec, initialVelocity = velocity)
        }
    }

    fun stop() {
        job?.cancel()
    }
}
