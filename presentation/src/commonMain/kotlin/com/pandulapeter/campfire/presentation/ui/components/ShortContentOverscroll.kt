/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Hands the drags content that fits cannot scroll by to [effect] (`bounceScrollableContent`), and records them in
 * [overscrollPull] only where the effect carries the content with them ([isContentCarried]): under Android's glow
 * before 12 the content stays put, and a fade following the finger would fade cards that never moved.
 */
internal class ShortContentOverscroll(
    private val state: ScrollableState,
    private val effect: OverscrollEffect,
    private val orientation: Orientation,
    private val dispatcher: NestedScrollDispatcher,
    private val scope: CoroutineScope,
    private val overscrollPull: OverscrollPull,
    private val isContentCarried: Boolean,
) : NestedScrollConnection {
    private var hasPulled = false
    private var releaseJob: Job? = null

    // Only what the content left over is taken, never a drag before the content has seen it: a scrollable inside the
    // container (a song's strip of columns inside a pager holding that one song) would otherwise never move.
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (state.canScrollForward || state.canScrollBackward || source != NestedScrollSource.UserInput) return Offset.Zero
        val delta = if (orientation == Orientation.Vertical) Offset(0f, available.y) else Offset(available.x, 0f)
        // The parents are offered the drag first, as they would be offered what a list left over: a bottom sheet is
        // dragged down by its content from its post-scroll, which would otherwise only come after this took it all.
        val pull = delta - dispatcher.dispatchPostScroll(consumed = Offset.Zero, available = delta, source = source)
        if (pull != Offset.Zero) {
            hasPulled = true
            releaseJob?.cancel()
            if (isContentCarried) overscrollPull.towardsStart = (overscrollPull.towardsStart - pull.y).coerceAtLeast(0f)
            effect.applyToScroll(pull, source) { Offset.Zero }
        }
        return delta
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // A glow (before Android 12) reports no stretch distance, but still has to be told that the finger let go.
        if (!hasPulled && !effect.isInProgress) return Velocity.Zero
        hasPulled = false
        releaseJob?.cancel()
        if (overscrollPull.towardsStart > 0f) {
            releaseJob = scope.launch {
                animate(
                    initialValue = overscrollPull.towardsStart,
                    targetValue = 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) { value, _ -> overscrollPull.towardsStart = value }
            }
        }
        val velocity = if (orientation == Orientation.Vertical) Velocity(0f, available.y) else Velocity(available.x, 0f)
        effect.applyToFling(velocity) { Velocity.Zero }
        return velocity
    }
}
