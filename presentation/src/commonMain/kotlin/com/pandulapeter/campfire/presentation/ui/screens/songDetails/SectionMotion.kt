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
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How the sections of [SongLyrics] get to the place a change of its layout gives them. */
internal enum class SectionMotion {

    /**
     * A change that comes on its own - a window maximised, a step of the text size - is narrated: every section springs
     * to its new place and size (`animateBounds`), and one too tall for that glides like [GLIDE].
     */
    SPRING,

    /**
     * A change that keeps coming - a pinch, a window edge being dragged - is followed as it comes, and only the jump a
     * section makes where the grid changes is glided over. A spring restarted on every frame would trail behind the
     * change, and `animateBounds` measures every section at its animated size and at its target size in every frame,
     * while a line of text keeps only one of those layouts, so each would be laid out twice a frame.
     */
    GLIDE,

    /**
     * Every section is simply where the layout puts it, which is the editor's preview: it shows what is being typed rather
     * than narrating it, and every edit that changes a section's height would otherwise move every section below it.
     */
    NONE,
}

/**
 * Whether a section can be animated to its place inside [SongSectionsLayout]. The layout is the only one that knows
 * how tall a section is, which decides that (see [maxAnimatedSectionHeight]), so it is handed back to the section
 * through this.
 */
internal class SectionAnimation {

    var isTooTallToAnimate by mutableStateOf(false)
}

/**
 * The glide of every section across a change of the grid, see [SongSectionsLayout]. The positions are followed in the
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

    /** Whether the chunk or the card of [key] is still on its way to its place, which is state. */
    fun isGliding(key: Any) = glides[key]?.isGliding == true

    /** How far from its place the chunk or the card of [key] is drawn, which is state. */
    fun offsetOf(key: Any) = glides[key]?.offset ?: IntOffset.Zero

    /**
     * Takes the [positions] the sections of [keys] are placed at in [grid], and starts a glide for every one whose
     * position moved because the grid changed, unless it [isCarriedByBounds], which animates its own.
     */
    fun follow(keys: List<Any>, grid: DecidedGrid, positions: Array<IntOffset>, isCarriedByBounds: (Int) -> Boolean) {
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
            if (isNewGrid && previous != null && previous != positions[index] && !isCarriedByBounds(index)) {
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
    var isGliding by mutableStateOf(false)
        private set
    private val animatable = Animatable(IntOffset.Zero, IntOffset.VectorConverter)
    private var pendingJump = IntOffset.Zero
    private var job: Job? = null

    val offset get() = animatable.value + pendingJump

    fun start(jump: IntOffset, scope: CoroutineScope, spec: AnimationSpec<IntOffset>) {
        pendingJump += jump
        isGliding = true
        job?.cancel()
        job = scope.launch {
            val velocity = animatable.velocity
            animatable.snapTo(animatable.value + pendingJump)
            pendingJump = IntOffset.Zero
            animatable.animateTo(targetValue = IntOffset.Zero, animationSpec = spec, initialVelocity = velocity)
            isGliding = false
        }
    }

    fun stop() {
        job?.cancel()
    }
}

/**
 * The layout id of a section that carries `animateBounds`. It is part of the same modifier chain, so what
 * [SongSectionsLayout] reads can never disagree with what is actually attached, not even for the one frame
 * between a measurement and the composition that answers it.
 */
internal object AnimatedSectionLayoutId
