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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.openSong
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.unclippedBounds
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.tap
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A Hebrew song in the English interface: its body reads from the right, so even its one Latin line, which would start
 * a left-to-right paragraph of its own, ends at the right edge with the Hebrew lines around it. Its music still reads
 * from the left, moved as one block to the right edge as the PDF moves it.
 */
@OptIn(ExperimentalTestApi::class)
internal class SongDirectionTest {

    @Test
    fun `a Hebrew song's lines are laid out from the right in an English interface`() = runAppTest(songs = listOf(SONG_FILE)) {
        tap(hasText(SONG_TITLE) and hasClickAction())
        val latin = textLayoutOf(LATIN_LINE)
        assertTrue(latin.getLineLeft(0) > latin.size.width / 2f, "The Latin line starts at ${latin.getLineLeft(0)} of ${latin.size.width}.")
        assertTrue(latin.getParagraphDirection(0) == ResolvedTextDirection.Rtl, "The Latin line is not a right-to-left paragraph.")
    }

    @Test
    fun `a Hebrew song's short tab and grid end at the right edge, and a wrapped tab stays in its columns`() = runAppTest(
        window = TALL_PHONE,
        songs = listOf(MUSIC_SONG_FILE_RTL),
    ) {
        openSong(MUSIC_SONG_TITLE_RTL)
        val tab = blockBounds(SHORT_TAB)
        val tabInk = inkColumns(tab)
        // The closing bar line is drawn in the middle of the tab's last column.
        assertTrue(tab.right - tabInk.last <= DRAWN_EDGE_TOLERANCE, "The short tab ends at ${tabInk.last} of a column ending at ${tab.right}.")
        assertTrue(tabInk.first > tab.center.x, "The short tab starts at ${tabInk.first}, in the left half of $tab.")
        val firstBar = barBounds(FIRST_BAR)
        val lastBar = barBounds(LAST_BAR)
        assertTrue(abs(tab.right - lastBar.right) <= EDGE_TOLERANCE, "The grid ends at ${lastBar.right} of a column ending at ${tab.right}.")
        assertTrue(firstBar.left - tab.left > WRAPPED_SHIFT_LIMIT, "The grid starts at ${firstBar.left}, unmoved in $tab.")
        // A tab wider than the column wraps into systems that fill it, so it barely moves, and its systems move together.
        val wide = blockBounds(WIDE_TAB)
        val firstSystem = inkColumns(wide)
        val secondSystem = inkColumns(wide.translate(0f, wide.height))
        assertTrue(firstSystem.first - wide.left <= WRAPPED_SHIFT_LIMIT, "The wrapped tab starts at ${firstSystem.first} of a column starting at ${wide.left}.")
        assertTrue(abs(firstSystem.first - secondSystem.first) <= 1, "The wrapped tab's systems start at ${firstSystem.first} and ${secondSystem.first}.")
    }

    @Test
    fun `a left-to-right song's short tab and grid start at the left edge`() = runAppTest(
        window = TALL_PHONE,
        songs = listOf(MUSIC_SONG_FILE_LTR),
    ) {
        openSong(MUSIC_SONG_TITLE_LTR)
        val tab = blockBounds(SHORT_TAB)
        val tabInk = inkColumns(tab)
        assertTrue(tabInk.first - tab.left <= EDGE_TOLERANCE, "The short tab starts at ${tabInk.first} of a column starting at ${tab.left}.")
        assertTrue(abs(barBounds(FIRST_BAR).left - tab.left) <= EDGE_TOLERANCE, "The grid does not start at the column's left edge.")
    }

    private fun AppTestScope.blockBounds(lines: String) =
        onAllNodes(hasContentDescription(lines), useUnmergedTree = true).fetchSemanticsNodes().single().unclippedBounds()

    /** The bar of the grid naming [chord], told from the Chords section's name by the beats beside it. */
    private fun AppTestScope.barBounds(chord: String) =
        onAllNodes(hasText(chord, substring = true) and hasText(BEAT, substring = true), useUnmergedTree = true).fetchSemanticsNodes().single().unclippedBounds()

    /** The first and the last column of [bounds] on screen holding anything other than the most common colour in it. */
    private fun AppTestScope.inkColumns(bounds: Rect): IntRange {
        val image = onRoot().captureToImage()
        val left = bounds.left.toInt().coerceAtLeast(0)
        val top = bounds.top.toInt().coerceAtLeast(0)
        val width = bounds.width.toInt().coerceAtMost(image.width - left)
        val height = bounds.height.toInt().coerceAtMost(image.height - top)
        val pixels = IntArray(width * height).also { image.readPixels(it, startX = left, startY = top, width = width, height = height) }
        val background = pixels.groupBy { it }.maxBy { it.value.size }.key
        fun isInk(pixel: Int) = (0..2).sumOf { abs((pixel shr (it * 8) and 0xFF) - (background shr (it * 8) and 0xFF)) } > INK_DIFFERENCE
        val columns = (0 until width).filter { x -> (0 until height).any { y -> isInk(pixels[y * width + x]) } }
        check(columns.isNotEmpty()) { "Nothing is drawn in $bounds." }
        return left + columns.first()..left + columns.last()
    }

    /** The layout of the one text on screen that is exactly [text]. */
    private fun AppTestScope.textLayoutOf(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }
}

private const val SONG_FILE = "campfire_tests-shir_laderech.cho"
private const val SONG_TITLE = "Shir Laderech"
private const val LATIN_LINE = "Hey"
private const val MUSIC_SONG_FILE_RTL = "campfire_tests-shir_hamusika.cho"
private const val MUSIC_SONG_TITLE_RTL = "Shir HaMusika"
private const val MUSIC_SONG_FILE_LTR = "campfire_tests-the_music_song.cho"
private const val MUSIC_SONG_TITLE_LTR = "The Music Song"
private val SHORT_TAB = listOf("e|-0-|", "B|-1-|", "G|-0-|", "D|-2-|", "A|-3-|", "E|---|").joinToString("\n")
private val WIDE_TAB = listOf(
    "e|--0--1--3--5--7--8--10--12--10--8--7--5--3--1--0--1--3--5--7--8--10--12--10--8--7--5--3--1--0--|",
    "B|--1--1--1--1--1--1--1---1---1---1--1--1--1--1--1--1--1--1--1--1--1---1---1---1--1--1--1--1--1--|",
    "G|--0--0--0--0--0--0--0---0---0---0--0--0--0--0--0--0--0--0--0--0--0---0---0---0--0--0--0--0--0--|",
    "D|--2--2--2--2--2--2--2---2---2---2--2--2--2--2--2--2--2--2--2--2--2---2---2---2--2--2--2--2--2--|",
    "A|--3--3--3--3--3--3--3---3---3---3--3--3--3--3--3--3--3--3--3--3--3---3---3---3--3--3--3--3--3--|",
    "E|-----------------------------------------------------------------------------------------------|",
).joinToString("\n")
private const val FIRST_BAR = "Bm7"
private const val LAST_BAR = "Dm7"
private const val BEAT = "\u00B7"

/** Tall enough for the whole song to be on screen at once, a column as wide as a phone's. */
private val TALL_PHONE = TestWindow(width = 360.dp, height = 1_600.dp)
private const val EDGE_TOLERANCE = 3f
private const val DRAWN_EDGE_TOLERANCE = 8f
private const val WRAPPED_SHIFT_LIMIT = 24f
private const val INK_DIFFERENCE = 96
