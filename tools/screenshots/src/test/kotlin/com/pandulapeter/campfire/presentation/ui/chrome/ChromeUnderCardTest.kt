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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chords
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.scales
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.presentation.resources.tools
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.ImpersonatedPlatform
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.HOUSE_OF_THE_RISING_SUN
import com.pandulapeter.campfire.screenshots.RISING_SUN_TITLE
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openChords
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class ChromeUnderCardTest {

    @Test
    fun `the navigation bar slides with the list under a song being opened`() = runAppTest {
        val label = onAllNodes(hasText(string(Res.string.songs)) and hasAnyAncestor(hasClickAction()), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .maxBy { it.boundsInRoot.top }
            .boundsInRoot
        // A row through the selected item's indicator, just above its label and beside its icon.
        val y = (label.top - INDICATOR_ROW_ABOVE_LABEL).roundToInt()
        val probeX = (label.center.x - INDICATOR_PROBE_BESIDE_ICON).roundToInt()
        val before = rowOf(y)
        val indicator = before.colorAt(probeX)
        val indicatorStart = (probeX downTo 0).takeWhile { before.colorAt(it).isCloseTo(indicator) }.last()
        mainClock.autoAdvance = false
        try {
            onNode(hasText(RISING_SUN_TITLE) and hasClickAction()).performTouchInput { click() }
            repeat(SLIDE_SAMPLE_FRAMES) { mainClock.advanceTimeByFrame() }
            val moving = rowOf(y)
            // The indicator is drawn darker under the scrim, by the same factor in every channel, and nearer the start.
            val movedStart = (0 until moving.width).firstOrNull { x ->
                (x until (x + INDICATOR_RUN).coerceAtMost(moving.width)).all { moving.colorAt(it).isDarkened(indicator) }
            }
            assertNotNull(movedStart, "No darkened indicator was found in the bar's row under the song being opened.")
            assertTrue(movedStart < indicatorStart, "The indicator started at $movedStart under the song, and at $indicatorStart before it.")
        } finally {
            mainClock.autoAdvance = true
        }
    }

    @Test
    fun `a tool's button group stays above the bar under a song and is pressable again once the song is closed`() = runAppTest {
        openChords()
        openSongOverTheTool()
        pressBack()
        tap(toolButton(string(Res.string.tuner)))
        // Only the selected tool's button names it in text.
        onNode(hasText(string(Res.string.tuner)) and hasClickAction()).assertExists()
    }

    @Test
    fun `a song opened from a tool's search takes the taps on its bottom edge`() = runAppTest {
        openChords()
        val tunerButton = onNode(toolButton(string(Res.string.tuner))).fetchSemanticsNode().boundsInRoot.center
        openSongOverTheTool()
        onRoot().performTouchInput { click(tunerButton) }
        waitForIdle()
        onNode(hasText(RISING_SUN_TITLE)).assertExists()
        // The press may land on one of the song's dock's controls, whose sheet goes before the song does.
        repeat(2) { if (onAllNodes(hasText(string(Res.string.chords)) and hasClickAction()).fetchSemanticsNodes().isEmpty()) pressBack() }
        onNode(hasText(string(Res.string.chords)) and hasClickAction()).assertExists()
        onNode(hasText(string(Res.string.tuner)) and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `the keyboard never reaches the navigation under an open song`() = runAppTest(
        window = TestWindow.DESKTOP,
        platform = ImpersonatedPlatform.WINDOWS,
    ) {
        val navigationLabels = listOf(
            Res.string.songs,
            Res.string.setlists,
            Res.string.metronome,
            Res.string.tuner,
            Res.string.chords,
            Res.string.scales,
            Res.string.settings,
            Res.string.tools,
        ).map { string(it) }.toSet()
        tap(hasText(RISING_SUN_TITLE) and hasClickAction())
        repeat(TAB_PRESSES) { press ->
            onRoot().performKeyInput { pressKey(Key.Tab) }
            waitForIdle()
            onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                // Told apart by the navigation's own role, since the song's dock has a tuner named as its Tuner item is.
                val isNavigation = node.isNavigationItem() && node.names().any { it in navigationLabels }
                assertTrue(!isNavigation, "Tab $press reached ${node.names()} under the song.")
            }
        }
    }

    /** Puts a song on top of the tool on screen, as a song opened from the app's search over it is. */
    private fun AppTestScope.openSongOverTheTool() {
        runOnIdle {
            viewModel.navigator.updateBackStack {
                add(CampfireDestination.SongDetails(songFileNames = listOf(HOUSE_OF_THE_RISING_SUN), setlistFileName = null, initialIndex = 0))
            }
        }
        waitForIdle()
        onNode(hasText(RISING_SUN_TITLE)).assertExists()
    }

    private fun AppTestScope.rowOf(y: Int) = captureWindow().let { it.toPixelMap(startX = 0, startY = y, width = it.width, height = 1) }

    private fun PixelMap.colorAt(x: Int) = this[x, 0]

    private fun SemanticsNode.names() =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    /**
     * Whether this node is one of the navigation's items, or inside one: a rail's and a bar's items are tabs, and the
     * Tools group's buttons radio buttons in a selectable group.
     */
    private fun SemanticsNode.isNavigationItem() = generateSequence(this) { it.parent }.any { node ->
        node.config.getOrNull(SemanticsProperties.Role).let { it == Role.Tab || it == Role.RadioButton }
    }

    /** A tool's button, which shows its name only while selected and is named by its description otherwise. */
    private fun toolButton(name: String) = (hasText(name) or hasContentDescription(name)) and hasClickAction()

    private fun Color.isCloseTo(other: Color) =
        abs(red - other.red) < TOLERANCE && abs(green - other.green) < TOLERANCE && abs(blue - other.blue) < TOLERANCE

    private fun Color.isDarkened(original: Color): Boolean {
        val factor = (red + green + blue) / (original.red + original.green + original.blue)
        return factor in DARKENED_FACTORS && isCloseTo(Color(original.red * factor, original.green * factor, original.blue * factor))
    }
}

/** Frames into the slide of a song being opened: under way, the card still far to the end of the bar's first item. */
private const val SLIDE_SAMPLE_FRAMES = 5
private const val INDICATOR_ROW_ABOVE_LABEL = 10f
private const val INDICATOR_PROBE_BESIDE_ICON = 20f
private const val INDICATOR_RUN = 8
private const val TAB_PRESSES = 30
private const val TOLERANCE = 0.03f
private val DARKENED_FACTORS = 0.5f..0.98f
