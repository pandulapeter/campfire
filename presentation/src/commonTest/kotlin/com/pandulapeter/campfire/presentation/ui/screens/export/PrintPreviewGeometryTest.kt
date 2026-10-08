/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class PrintPreviewGeometryTest {

    private val viewport = Size(400f, 600f)
    private val area = fitArea(viewport, margin = 20f, bottom = 80f)

    @Test
    fun theAreaLeavesTheMarginsAndNeverTurnsInsideOut() {
        assertEquals(Rect(20f, 20f, 380f, 520f), area)
        assertEquals(Rect(20f, 20f, 20f, 20f), fitArea(Size(30f, 50f), margin = 20f, bottom = 80f))
    }

    @Test
    fun aPageFitsTheAreaByItsTighterSide() {
        assertEquals(Size(250f, 500f), fittedPageSize(area.size, ASPECT_RATIO))
        assertEquals(Size(360f, 180f), fittedPageSize(area.size, 2f))
        assertEquals(Offset(75f, 20f), centeredTopLeft(Size(250f, 500f), area))
    }

    @Test
    fun aPageThatFitsTheAreaStaysCentered() {
        assertOffset(Offset.Zero, clampPan(Offset(100f, -50f), area, Size(250f, 500f), viewport))
    }

    @Test
    fun aPageBetweenTheAreaAndTheViewportCoversTheAreaAndStaysInsideTheViewport() {
        val pageSize = Size(275f, 550f)
        // Centered, its top is at -5: it may move from a top of 0, the viewport's, to one of 20, the area's.
        assertOffset(Offset(0f, 25f), clampPan(Offset(0f, 100f), area, pageSize, viewport))
        assertOffset(Offset(0f, 5f), clampPan(Offset(0f, -100f), area, pageSize, viewport))
        assertOffset(Offset(0f, 10f), clampPan(Offset(0f, 10f), area, pageSize, viewport))
    }

    @Test
    fun aPageLargerThanTheViewportAlwaysCoversIt() {
        val pageSize = Size(500f, 1000f)
        assertOffset(Offset(50f, 230f), clampPan(Offset(1000f, 1000f), area, pageSize, viewport))
        assertOffset(Offset(-50f, -170f), clampPan(Offset(-1000f, -1000f), area, pageSize, viewport))
        assertOffset(Offset(30f, -100f), clampPan(Offset(30f, -100f), area, pageSize, viewport))
    }

    @Test
    fun aViewKeepsThePanItWasMadeFromWithinItsRange() {
        val pageSize = Size(500f, 1000f)
        for (pan in listOf(Offset(30f, -100f), Offset(-50f, 230f), Offset.Zero)) {
            assertOffset(pan, pageViewOf(zoom = 2f, pan = pan, area = area, pageSize = pageSize, viewport = viewport).panOf(pageSize))
        }
        val beyond = pageViewOf(zoom = 2f, pan = Offset(1000f, 0f), area = area, pageSize = pageSize, viewport = viewport)
        assertEquals(0.4f, beyond.focus.x, TOLERANCE)
        assertOffset(Offset(50f, 0f), beyond.panOf(pageSize))
    }

    @Test
    fun theBoundsOfAPageFollowItsView() {
        val pageSize = fittedPageSize(area.size, ASPECT_RATIO) * 2f
        val view = pageViewOf(zoom = 2f, pan = Offset(30f, -100f), area = area, pageSize = pageSize, viewport = viewport)
        val bounds = pageBounds(viewport, area, ASPECT_RATIO, view)
        assertOffset(Offset(-20f, -330f), bounds.topLeft)
        assertEquals(500f, bounds.width, TOLERANCE)
        assertEquals(1000f, bounds.height, TOLERANCE)
        assertEquals(Rect(Offset(75f, 20f), Size(250f, 500f)), pageBounds(viewport, area, ASPECT_RATIO, PageView()))
    }

    private fun assertOffset(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, TOLERANCE)
        assertEquals(expected.y, actual.y, TOLERANCE)
    }

    private companion object {
        const val ASPECT_RATIO = 0.5f
        const val TOLERANCE = 0.001f
    }
}
