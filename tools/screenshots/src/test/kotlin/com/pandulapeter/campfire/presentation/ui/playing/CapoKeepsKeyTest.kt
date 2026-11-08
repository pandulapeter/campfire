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

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_capo_keeps_key
import com.pandulapeter.campfire.presentation.resources.settings_chord_names_sounding
import com.pandulapeter.campfire.presentation.resources.settings_preferences
import com.pandulapeter.campfire.presentation.resources.song_details_capo_increase
import com.pandulapeter.campfire.presentation.resources.song_details_capo_reset
import com.pandulapeter.campfire.presentation.resources.song_details_transpose_reset
import com.pandulapeter.campfire.presentation.resources.song_details_transpose_up
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.openChordsPreferences
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
internal class CapoKeepsKeyTest {

    @Test
    fun `keeping the key rereads a capoed song at once, and a capo then moves neither the key nor the transposition`() = runAppTest {
        openSong()
        // Named as they sound, so that the transposition stepper names the key the song is heard in.
        changePreference(Res.string.settings_chord_names_sounding)
        repeat(3) { tap(hasContentDescription(string(Res.string.song_details_capo_increase))) }
        // House of the Rising Sun is in Am: three frets up, its Am shapes sound in Cm.
        waitUntil { transposition() == "0 · Cm" }

        changePreference(Res.string.settings_capo_keeps_key)
        waitUntil { transposition() == "0 · Am" }
        assertFalse(hasTranspositionReset(), "Keeping the key is no transposition of the reader's.")

        tap(hasContentDescription(string(Res.string.song_details_capo_increase)))
        waitUntil { capo() == "4" }
        assertEquals("0 · Am", transposition())
        assertFalse(hasTranspositionReset())

        tap(clickLabelled(string(Res.string.song_details_capo_reset)))
        waitUntil { capo() == "0" }
        assertEquals("0 · Am", transposition())
        assertFalse(hasTranspositionReset())
    }

    /** Switches the setting or picks the choice named [label] on the song's Preferences sheet's Chords page, and closes it. */
    private fun AppTestScope.changePreference(label: StringResource) {
        tap(hasContentDescription(string(Res.string.songs_actions)))
        tap(hasText(string(Res.string.settings_preferences)) and hasClickAction())
        openChordsPreferences()
        onNode(hasText(string(label))).performScrollTo()
        tap(hasText(string(label)))
        pressBack()
    }

    /** A stepper's reset is a click on its value, labelled for a screen reader, and only there while it has one to do. */
    private fun clickLabelled(label: String) = SemanticsMatcher("clicks to $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label && it.config.getOrNull(SemanticsProperties.Disabled) == null
    }

    private fun AppTestScope.hasTranspositionReset() =
        onAllNodes(clickLabelled(string(Res.string.song_details_transpose_reset))).fetchSemanticsNodes().isNotEmpty()

    /** What the transposition stepper reads, which its buttons carry as their state for a screen reader. */
    private fun AppTestScope.transposition() = stateOf(Res.string.song_details_transpose_up)

    /** What the capo stepper reads, carried as the transposition's is. */
    private fun AppTestScope.capo() = stateOf(Res.string.song_details_capo_increase)

    private fun AppTestScope.stateOf(button: StringResource) = onNodeWithContentDescription(string(button))
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription).orEmpty()
}
