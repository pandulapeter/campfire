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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.back
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.RISING_SUN_TITLE
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class PredictiveBackTest {

    @Test
    fun `a back gesture started while a song is still sliding in lets it land and keeps it when called off`() = runAppTest {
        holdBackGestureDuringPush()
        val heldStart = songDetailsStart()
        cancelBackGesture()
        mainClock.advanceTimeBy(SETTLE_MILLIS)
        val restingStart = songDetailsStart()
        assertTrue(
            abs(heldStart - restingStart) < TOLERANCE * TestWindow.PHONE.width.value,
            "The song details screen was held ${heldStart - restingStart} away from where it rests.",
        )
    }

    @Test
    fun `a back gesture after one called off mid slide follows the finger`() = runAppTest {
        holdBackGestureDuringPush()
        cancelBackGesture()
        mainClock.advanceTimeBy(SETTLE_MILLIS)
        val restingStart = songDetailsStart()
        dragBackGestureAndHold()
        val heldStart = songDetailsStart()
        val fingerOffset = HELD_PROGRESS * TestWindow.PHONE.width.value
        assertTrue(
            abs(heldStart - restingStart - fingerOffset) < TOLERANCE * TestWindow.PHONE.width.value,
            "The song details screen was held ${heldStart - restingStart} away from where it rests, the finger $fingerOffset.",
        )
    }

    @Test
    fun `a back gesture started while a song is still sliding in goes back once let go of`() = runAppTest {
        holdBackGestureDuringPush()
        releaseBackGesture()
        mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(0, onAllNodesWithContentDescription(string(Res.string.back)).fetchSemanticsNodes().size)
    }

    /** Opens a song, and before it has slid in, drags a back gesture [HELD_PROGRESS] of the way across and holds it. */
    private fun AppTestScope.holdBackGestureDuringPush() {
        mainClock.autoAdvance = false
        onNode(hasText(RISING_SUN_TITLE) and hasClickAction()).performTouchInput { click() }
        mainClock.advanceTimeBy(PUSH_MILLIS_BEFORE_GESTURE)
        dragBackGestureAndHold()
    }

    /** Starts a back gesture and drags it [HELD_PROGRESS] of the way across, where it is held until things settle. */
    private fun AppTestScope.dragBackGestureAndHold() {
        startBackGesture()
        HELD_STEPS.forEach { step ->
            mainClock.advanceTimeBy(DRAG_STEP_MILLIS)
            dragBackGesture(HELD_PROGRESS * step / HELD_STEPS.last)
        }
        mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    /** Where the song details screen starts, read from its back button, which moves with the whole screen. */
    private fun AppTestScope.songDetailsStart() = onNodeWithContentDescription(string(Res.string.back)).fetchSemanticsNode().boundsInRoot.left
}

private const val PUSH_MILLIS_BEFORE_GESTURE = 150L
private const val DRAG_STEP_MILLIS = 30L
private val HELD_STEPS = 1..10
private const val HELD_PROGRESS = 0.3f
private const val SETTLE_MILLIS = 1_500L
private const val TOLERANCE = 0.1f
