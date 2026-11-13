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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_grid_beats
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.openSong
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.unclippedBounds
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Interlude of The Long Road North, a run of grid lines of four bars and one of two: drawn as boxes, read aloud as
 * chords and beats, and broken after the same bar on every line where a phone at the largest text size is too narrow.
 */
@OptIn(ExperimentalTestApi::class)
internal class GridDrawingTest {

    @Test
    fun `a grid line is drawn as bars and read as its chords and beats`() = runAppTest(window = TALL_PHONE, songs = listOf(SONG_FILE)) {
        openSong(SONG_TITLE)
        val bounds = onAllNodes(hasContentDescription(lineDescription("G", "C", "G", "D")), useUnmergedTree = true).fetchSemanticsNodes().first().unclippedBounds()
        val image = onRoot().captureToImage()
        val pixels = IntArray(bounds.width.toInt() * bounds.height.toInt())
        image.readPixels(pixels, startX = bounds.left.toInt(), startY = bounds.top.toInt(), width = bounds.width.toInt(), height = bounds.height.toInt())
        assertTrue(pixels.distinct().size > 2, "Nothing is drawn where the grid line is.")
    }

    @Test
    fun `a narrow window breaks every line of a run after the same bar`() {
        val unbroken = lineHeights(fontScale = 1f)
        val broken = lineHeights(fontScale = LARGEST_TEXT)
        assertEquals(broken.first(), broken.last(), 0.5f, "The run's four-bar lines are $broken tall.")
        // Every size of the drawing scales with the text, so a line that stays one row grows only by the text's scale.
        assertTrue(broken.first() > unbroken.first() * LARGEST_TEXT * 1.5f, "The four-bar lines went from $unbroken to $broken.")
    }

    /** The heights of the run's first and last lines, four bars each, in a tall phone at [fontScale]. */
    private fun lineHeights(fontScale: Float): List<Float> {
        var heights = emptyList<Float>()
        runAppTest(window = TALL_PHONE, songs = listOf(SONG_FILE), fontScale = fontScale) {
            openSong(SONG_TITLE)
            heights = listOf(lineDescription("G", "C", "G", "D"), lineDescription("D", "D", "G", "G")).map { description ->
                onAllNodes(hasContentDescription(description), useUnmergedTree = true).fetchSemanticsNodes().first().size.height.toFloat()
            }
        }
        return heights
    }

    /** What a line of four-beat bars of [chords] is read aloud as. */
    private fun AppTestScope.lineDescription(vararg chords: String) =
        chords.joinToString("; ") { "$it, ${pluralString(Res.plurals.song_details_grid_beats, 4)}" }
}

private const val SONG_FILE = "campfire_tests-the_long_road_north.cho"
private const val SONG_TITLE = "The Long Road North"
private const val LARGEST_TEXT = 2.5f

/** Tall enough for the whole Interlude to be on the first page at any text size, a column as wide as a phone's. */
private val TALL_PHONE = TestWindow(width = 360.dp, height = 8_000.dp)
