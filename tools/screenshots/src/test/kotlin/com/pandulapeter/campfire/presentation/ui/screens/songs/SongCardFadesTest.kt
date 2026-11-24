/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The song cards' two edge fades are painted in the color behind what they fade rather than masked out of it, which is
 * only the same picture where that color is what stands behind: the card under its labels, the screen's background
 * under the list's top. Checked in the light and the dark scheme and with the background warmed, which tints both.
 */
internal class SongCardFadesTest {

    @Test
    fun `a card's label row that runs past its end fades into the card's color`() = SCHEMES.forEach { scheme ->
        runAppTest(
            appearance = scheme.appearance,
            extraPreferences = scheme.preferences,
            libraryFiles = mapOf("songs/campfire_tests-many_labels.cho" to MANY_LABELS.encodeToByteArray()),
        ) {
            val card = onNode(hasClickAction() and hasText(MANY_LABELS_TITLE, substring = true)).fetchSemanticsNode().boundsInRoot
            val row = onAllNodes(overflowingRow, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.single { card.contains(it.center) }
            val window = captureWindow()
            // The card's own room under its last line.
            val cardColor = window.colorAt(row.right - 1, row.bottom + CARD_ROOM_BELOW_LABELS)
            val edge = (row.top.roundToInt() + 1 until row.bottom.roundToInt() - 1).map { y -> window.colorAt(row.right - 1, y.toFloat()) }
            assertTrue(edge.all { it.isCloseTo(cardColor) }, "${scheme.name}: the row's end is ${edge.distinct()} rather than the card's $cardColor.")
        }
    }

    @Test
    fun `a card scrolled under the list's top fades into the screen's background`() = SCHEMES.forEach { scheme ->
        runAppTest(appearance = scheme.appearance, extraPreferences = scheme.preferences, songs = SONGS) {
            onNode(hasScrollToIndexAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, SCROLL_PX) }
            waitForIdle()
            val cards = onAllNodes(hasClickAction() and hasText(CARD_ARTIST, substring = true)).fetchSemanticsNodes().map { it.boundsInRoot }
            val covered = assertNotNull(cards.firstOrNull { it.contains(Offset(PROBE_X, PROBE_Y)) }, "${scheme.name}: no card under the list's top.")
            val window = captureWindow()
            // Beside a card, in the gutter the screen's background shows through.
            val background = window.colorAt(GUTTER_X, cards.maxBy { it.top }.center.y)
            val probe = window.colorAt(PROBE_X, PROBE_Y)
            assertTrue(probe.isCloseTo(background), "${scheme.name}: the card at $covered shows $probe under the list's top rather than the background's $background.")
        }
    }

    private val overflowingRow = SemanticsMatcher("overflows sideways") { node ->
        node.config.getOrElseNullable(SemanticsProperties.HorizontalScrollAxisRange) { null }?.let { it.maxValue() > 0f } == true
    }

    private fun ImageBitmap.colorAt(x: Float, y: Float) = toPixelMap(startX = x.roundToInt(), startY = y.roundToInt(), width = 1, height = 1)[0, 0]

    private fun Color.isCloseTo(other: Color) =
        abs(red - other.red) < TOLERANCE && abs(green - other.green) < TOLERANCE && abs(blue - other.blue) < TOLERANCE

    private class Scheme(val name: String, val appearance: TestAppearance, val preferences: Map<String, JsonPrimitive> = emptyMap())

    private companion object {
        val SCHEMES = listOf(
            Scheme(name = "Light", appearance = TestAppearance()),
            Scheme(name = "Dark", appearance = TestAppearance(isDark = true)),
            Scheme(
                name = "Warm",
                appearance = TestAppearance(),
                preferences = mapOf("backgroundWarmth" to JsonPrimitive(UserPreferences.MAX_BACKGROUND_WARMTH)),
            ),
        )
        val SONGS = listOf(
            "campfire_tests-a_long_tune.cho",
            "campfire_tests-a_new_song.cho",
            "campfire_tests-a_second_tune.cho",
            "campfire_tests-a_tune.cho",
            "campfire_tests-the_long_road_north.cho",
            "campfire_tests-the_music_song.cho",
        )
        const val CARD_ARTIST = "Campfire Tests"
        const val MANY_LABELS_TITLE = "A Song Of Many Labels"
        const val MANY_LABELS = "{title: $MANY_LABELS_TITLE}\n{artist: Campfire Tests}\n" +
            "{tag: Campfire classics}\n{tag: Fingerpicking pieces}\n{tag: Late night singalongs}\n{tag: Road trip favourites}\n" +
            "[C]A line to sing\n"

        /** Far enough for the cards under the pinned header's row to be faded out whole. */
        const val SCROLL_PX = 150f

        /** Under the pinned header's row, clear of its text on the left and of the app bar's pill on the right. */
        const val PROBE_X = 150f
        const val PROBE_Y = 30f
        const val GUTTER_X = 3f
        const val CARD_ROOM_BELOW_LABELS = 3f
        const val TOLERANCE = 0.015f
    }
}
