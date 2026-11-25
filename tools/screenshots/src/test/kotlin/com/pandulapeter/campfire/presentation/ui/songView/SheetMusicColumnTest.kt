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

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.PageTurn
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_section_abc
import com.pandulapeter.campfire.presentation.ui.platform.ImpersonatedPlatform
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.unclippedBounds
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A long guitar score, a staff over its tablature, in a song turned by column on a wide desktop window: the tune takes the
 * columns the strip gives its lyrics, never one wider, and its engraving fills the column it was given rather than half
 * of it. Read at the default text size and at a larger one, which reach their columns by different paths.
 */
@OptIn(ExperimentalTestApi::class)
internal class SheetMusicColumnTest {

    @Test
    fun `a tune turned by column fills the strip's own columns`() = fillsItsColumn(fontScale = 1f)

    @Test
    fun `a tune turned by column fills the strip's own columns at a larger text size`() = fillsItsColumn(fontScale = LARGER_TEXT)

    private fun fillsItsColumn(fontScale: Float) = runAppTest(
        window = TestWindow(width = 1512.dp, height = 938.dp),
        songs = listOf(SONG_FILE),
        fontScale = fontScale,
        pageTurn = PageTurn.COLUMN,
        platform = ImpersonatedPlatform.WINDOWS,
    ) {
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText(SONG_TITLE))
        tap(hasText(SONG_TITLE) and hasClickAction())
        waitUntil(timeoutMillis = RENDER_TIMEOUT_MILLIS) { tuneBounds() != null }
        settle()
        val tune = checkNotNull(tuneBounds())
        val lineWidth = lineWidths().max()
        val padding = CARD_PADDING.value * density.density
        assertTrue(tune.width <= lineWidth + padding, "The tune's column is ${tune.width} px wide, the lines' $lineWidth px.")
        // The first system, which abc2svg spreads across the page, unlike a tune's last one.
        val ink = inkRight(tune)
        assertTrue(ink - tune.left >= tune.width * FILLED, "The tune's first system ends at ${ink - tune.left} px of a ${tune.width} px column.")
    }

    /** The first piece of the tune drawn as notation, the one a screen reader is told it by, where it is on screen. */
    private fun AppTestScope.tuneBounds(): Rect? = runWithoutImplicitWait {
        onAllNodes(hasContentDescription(string(Res.string.song_details_section_abc), substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().firstOrNull()?.unclippedBounds()
    }

    /** The widths of the verse's lines, each as wide as its column. */
    private fun AppTestScope.lineWidths() = LINES.map { line ->
        onAllNodes(hasContentDescription(line, substring = true), useUnmergedTree = true).fetchSemanticsNodes().single().size.width.toFloat()
    }

    /** Waits until the tune's engravings stop following its column: an engraving lands, the layout follows it, and so on. */
    private fun AppTestScope.settle() {
        var bounds: Rect? = null
        repeat(MAX_SETTLE_ROUNDS) {
            waitForIdle()
            Thread.sleep(SETTLE_MILLIS)
            waitForIdle()
            val now = tuneBounds()
            if (now == bounds) return
            bounds = now
        }
    }

    /** The rightmost pixel inside [bounds] that is not its background. */
    private fun AppTestScope.inkRight(bounds: Rect): Int {
        val image = captureWindow()
        val left = bounds.left.toInt().coerceAtLeast(0)
        val top = bounds.top.toInt().coerceAtLeast(0)
        val width = bounds.width.toInt().coerceAtMost(image.width - left)
        val height = bounds.height.toInt().coerceAtMost(image.height - top)
        val pixels = IntArray(width * height).also { image.readPixels(it, startX = left, startY = top, width = width, height = height) }
        val background = pixels.groupBy { it }.maxBy { it.value.size }.key
        fun isInk(pixel: Int) = (0..2).sumOf { abs((pixel shr (it * 8) and 0xFF) - (background shr (it * 8) and 0xFF)) } > INK_DIFFERENCE
        val columns = (0 until width).filter { x -> (0 until height).any { y -> isInk(pixels[y * width + x]) } }
        check(columns.isNotEmpty()) { "Nothing is drawn in $bounds." }
        return left + columns.last()
    }

    private companion object {
        const val SONG_FILE = "campfire_tests-a_long_tune.cho"
        const val SONG_TITLE = "A Long Tune"
        val LINES = listOf("A verse of chords stands", "Its lines are as wide as the")
        const val LARGER_TEXT = 1.35f
        val CARD_PADDING = 16.dp
        const val FILLED = 0.9f
        const val INK_DIFFERENCE = 96
        const val RENDER_TIMEOUT_MILLIS = 10_000L
        const val SETTLE_MILLIS = 1_000L
        const val MAX_SETTLE_ROUNDS = 10
    }
}
