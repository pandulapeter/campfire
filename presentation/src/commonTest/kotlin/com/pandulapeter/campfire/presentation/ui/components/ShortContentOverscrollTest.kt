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

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals

class ShortContentOverscrollTest {

    @Test
    fun `content an effect carries records how far it is pulled up`() {
        val pull = OverscrollPull()
        val effect = RecordingEffect()
        connection(effect, pull, isContentCarried = true).pullUpBy(40f)
        assertEquals(40f, pull.towardsStart)
        assertEquals(Offset(0f, -40f), effect.applied)
    }

    @Test
    fun `content an effect leaves in place records no pull but still reaches the effect`() {
        val pull = OverscrollPull()
        val effect = RecordingEffect()
        connection(effect, pull, isContentCarried = false).pullUpBy(40f)
        assertEquals(0f, pull.towardsStart)
        assertEquals(Offset(0f, -40f), effect.applied)
    }

    private fun connection(effect: OverscrollEffect, pull: OverscrollPull, isContentCarried: Boolean) = ShortContentOverscroll(
        state = FittingContent,
        effect = effect,
        orientation = Orientation.Vertical,
        dispatcher = NestedScrollDispatcher(),
        scope = TestScope(),
        overscrollPull = pull,
        isContentCarried = isContentCarried,
    )

    private fun ShortContentOverscroll.pullUpBy(distance: Float) {
        onPostScroll(consumed = Offset.Zero, available = Offset(0f, -distance), source = NestedScrollSource.UserInput)
    }

    private object FittingContent : ScrollableState {
        override val isScrollInProgress = false
        override val canScrollForward = false
        override val canScrollBackward = false

        override fun dispatchRawDelta(delta: Float) = 0f

        override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) = Unit
    }

    private class RecordingEffect : OverscrollEffect {
        var applied = Offset.Zero
        override val isInProgress get() = applied != Offset.Zero

        override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
            applied += delta
            return delta
        }

        override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) = Unit
    }
}
