/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.back
import com.pandulapeter.campfire.presentation.resources.song_details_tempo_increase
import com.pandulapeter.campfire.presentation.resources.song_details_tempo_reset
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_time
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
internal class TypedTempoTest {

    @Test
    fun `a tempo typed into the stepper is the one the song plays at with no key to confirm it`() = runAppTest {
        openSong()
        typeTempo("132")
        assertEquals("132", tempo())
    }

    @Test
    fun `a tempo typed a digit at a time keeps the digits a number of the range on the way has set`() = runAppTest {
        openSong()
        typeTempo("3")
        typeMore("0")
        assertEquals("30", tempo())
        typeMore("0")
        assertEquals("300", tempo())
    }

    @Test
    fun `a typed number outside the range sets nothing and leaving the field puts the tempo back`() = runAppTest {
        openSong()
        typeTempo("1")
        assertEquals(FILE_TEMPO, tempo())
        tempoField().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(FILE_TEMPO, tempo())
        tempoField().assertTextEquals(FILE_TEMPO)
    }

    @Test
    fun `Escape puts back the tempo the field was focused at without leaving the song`() = runAppTest {
        openSong()
        typeTempo("99")
        assertEquals("99", tempo())
        tempoField().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(FILE_TEMPO, tempo())
        onNodeWithContentDescription(string(Res.string.back)).assertExists()
    }

    @Test
    fun `a changed tempo is reset from the segment that ends the pill while its value is focused`() = runAppTest {
        openSong()
        tap(hasContentDescription(string(Res.string.song_details_tempo_increase)))
        assertEquals("237", tempo())
        tap(tempoFieldMatcher())
        tap(hasContentDescription(string(Res.string.song_details_tempo_reset)) and hasClickAction())
        assertEquals(FILE_TEMPO, tempo())
    }

    @Test
    fun `a press outside the stepper ends typing and keeps the typed tempo`() = runAppTest {
        openSong()
        typeTempo("132")
        onNodeWithContentDescription(string(Res.string.song_details_tempo_reset)).assertExists()
        tap(hasText(string(Res.string.song_editor_insert_time)))
        tempoField().assertIsNotFocused()
        onNodeWithContentDescription(string(Res.string.song_details_tempo_reset)).assertDoesNotExist()
        assertEquals("132", tempo())
    }

    @Test
    fun `a step while typing steps from the typed tempo`() = runAppTest {
        openSong()
        typeTempo("132")
        tap(hasContentDescription(string(Res.string.song_details_tempo_increase)))
        assertEquals("133", tempo())
    }

    @Test
    fun `a click on the tempo selects the number so that typing replaces it`() = runAppTest {
        openSong()
        tempoField().performMouseInput {
            moveTo(center)
            press()
        }
        // Held for a while, as a hand does: the caret a click places comes with the release, frames after the focus.
        mainClock.advanceTimeBy(HELD_CLICK_MILLIS)
        tempoField().performMouseInput { release() }
        waitForIdle()
        tempoField().performTextInput("132")
        waitForIdle()
        assertEquals("132", tempo())
    }

    private fun AppTestScope.tempoFieldMatcher() = hasSetTextAction() and hasContentDescription("BPM", substring = true)

    private fun AppTestScope.tempoField() = onNode(tempoFieldMatcher())

    /** Focuses the tempo's value with a tap, which selects the number, and types [digits] over it. */
    private fun AppTestScope.typeTempo(digits: String) {
        tap(tempoFieldMatcher())
        tempoField().performTextInput(digits)
        waitForIdle()
    }

    /** Types [digits] after what the focused field holds. */
    private fun AppTestScope.typeMore(digits: String) {
        tempoField().performTextInput(digits)
        waitForIdle()
    }

    /** The tempo the stepper reads, which its buttons carry as their state for a screen reader (`132 BPM`). */
    private fun AppTestScope.tempo() = onNodeWithContentDescription(string(Res.string.song_details_tempo_increase))
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription).orEmpty().substringBefore(' ')
}

private const val HELD_CLICK_MILLIS = 200L

/** The demo House of the Rising Sun's `{tempo}`. */
private const val FILE_TEMPO = "236"
