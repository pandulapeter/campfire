/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.tuner

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.AppLanguage
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_choose
import com.pandulapeter.campfire.presentation.resources.instrument_guitar
import com.pandulapeter.campfire.presentation.resources.instrument_guitar_strings
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_choose
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_standard
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.resources.tuner_mode_chromatic
import com.pandulapeter.campfire.presentation.resources.tuner_play_reference
import com.pandulapeter.campfire.presentation.resources.tuner_reference_pitch_increase
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openTuner
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.math.abs
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class TunerInstrumentPillTest {

    @Test
    fun `a long instrument name beside the reference pitch still leaves its tuning in sight`() = runAppTest {
        openTuner()
        tap(hasText(string(Res.string.instrument_guitar)) and hasClickAction())
        val twelveString = hasText(string(Res.string.instrument_guitar_strings, 12)) and hasClickAction()
        onNode(twelveString).performScrollTo()
        tap(twelveString)
        assertWhole(string(Res.string.instrument_tuning_standard))
    }

    @Test
    fun `on the smallest phone the instrument and its tuning are named in full in every language`() = AppLanguage.entries
        .filter { it != AppLanguage.SYSTEM_DEFAULT }
        .forEach { language ->
            runAppTest(appearance = TestAppearance(language = language)) {
                openTuner()
                assertWhole(string(Res.string.instrument_guitar))
                assertWhole(string(Res.string.instrument_tuning_standard))
            }
        }

    @Test
    fun `at the largest text size the tuning is shown, with the reference pitch under the instrument`() = runAppTest(
        appearance = TestAppearance(textScale = 2f),
    ) {
        openTuner()
        val tuning = onNodeWithText(string(Res.string.instrument_tuning_standard), useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(tuning.size.width > 0, "The tuning's name is ${tuning.size.width} px wide.")
        val instrument = bounds(hasText(string(Res.string.instrument_guitar)) and hasClickAction())
        val increase = bounds(hasContentDescription(string(Res.string.tuner_reference_pitch_increase)))
        assertTrue(increase.top >= instrument.bottom, "The stepper at $increase is beside the instrument at $instrument.")
        // The mode toggle is still under the play circle, ending where it ends.
        val play = bounds(hasContentDescription(string(Res.string.tuner_play_reference, "A")))
        val chromatic = bounds(hasText(string(Res.string.tuner_mode_chromatic)) and hasClickAction())
        assertTrue(abs(chromatic.right - play.right) <= 1f, "The toggle ends at ${chromatic.right}, the play circle at ${play.right}.")
        assertTrue(chromatic.top > play.bottom, "The toggle at $chromatic is not under the play circle at $play.")
    }

    @Test
    fun `on the smallest phone the reference pitch stands under the instrument`() = runAppTest {
        openTuner()
        val instrument = bounds(hasText(string(Res.string.instrument_guitar)) and hasClickAction())
        val increase = bounds(hasContentDescription(string(Res.string.tuner_reference_pitch_increase)))
        assertTrue(increase.top >= instrument.bottom, "The stepper at $increase is beside the instrument at $instrument.")
    }

    @Test
    fun `where the whole pill fits beside it the reference pitch keeps the instrument's line`() = runAppTest(window = WIDE_PHONE) {
        // A window this wide has room for every tool on its navigation, with no Tools item to open them from.
        val tuner = string(Res.string.tuner)
        tap((hasText(tuner) or hasContentDescription(tuner)) and hasClickAction())
        val instrument = bounds(hasText(string(Res.string.instrument_guitar)) and hasClickAction())
        val increase = bounds(hasContentDescription(string(Res.string.tuner_reference_pitch_increase)))
        assertTrue(increase.center.y in instrument.top..instrument.bottom, "The stepper at $increase left the instrument's line at $instrument.")
    }

    @Test
    fun `a touch just above the drawn instrument half picks the instrument`() = runAppTest {
        openTuner()
        touchOutside(hasText(string(Res.string.instrument_guitar)) and hasClickAction(), isAbove = true)
        onNodeWithText(string(Res.string.instrument_choose)).assertIsDisplayed()
    }

    @Test
    fun `a touch just below the drawn tuning half picks the tuning`() = runAppTest {
        openTuner()
        touchOutside(hasText(string(Res.string.instrument_tuning_standard)) and hasClickAction(), isAbove = false)
        onNodeWithText(string(Res.string.instrument_tuning_choose)).assertIsDisplayed()
    }

    /**
     * Asserts that the name [text] is drawn whole: on its one line, and at least as wide as its natural width. Its own
     * `Text` is the one asked, since the half's padded press is wider than nothing whatever its name is given. Neither
     * `isLineEllipsized`, which Skiko's paragraph always answers false, nor `didOverflowWidth`, which a name narrower than
     * its room also reports (the paragraph is laid out at the room's width), can tell.
     */
    private fun AppTestScope.assertWhole(text: String) {
        val node = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layout = mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.single()
        val naturalWidth = ceil(layout.multiParagraph.intrinsics.maxIntrinsicWidth).toInt()
        assertTrue(!layout.multiParagraph.didExceedMaxLines && naturalWidth <= layout.size.width, "\"$text\" is cut short.")
    }

    private fun AppTestScope.bounds(matcher: SemanticsMatcher) = onNode(matcher).fetchSemanticsNode().boundsInRoot

    /** Touches 6 dp past the drawn top or bottom of the half [matcher] finds, inside the 48 dp a finger is given. */
    private fun AppTestScope.touchOutside(matcher: SemanticsMatcher, isAbove: Boolean) {
        onNode(matcher).performTouchInput {
            val outside = 6.dp.toPx()
            click(Offset(centerX, if (isAbove) top - outside else bottom + outside))
        }
        waitForIdle()
    }
}

/** A window wide enough for the whole pill beside the reference pitch. */
private val WIDE_PHONE = TestWindow(width = 600.dp, height = 640.dp)
