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

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.back
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.song_details_transpose_up
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.HOUSE_OF_THE_RISING_SUN
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class SetlistOverrideTest {

    @Test
    fun `a transposition set in a setlist is written to the setlist and not to the song`() = runAppTest(setlists = listOf(SETLIST)) {
        val songsBefore = songContents()
        tap(hasText(string(Res.string.setlists)) and hasClickAction())
        // A setlist's row is numbered, which its text starts with.
        tap(hasText(RISING_SUN_TITLE, substring = true) and hasClickAction())
        tap(hasContentDescription(string(Res.string.song_details_transpose_up)))
        waitForLibrary { setlistEntries().singleOrNull()?.second == 1 }
        // The setlist file can land before the stepper's next frame, since the write and the view model's state both
        // arrive from other threads than the test clock's composition, so the stepper is waited for on its own.
        waitUntil { transposition().startsWith("+1 ") }
        assertEquals(songsBefore, songContents())
        assertEquals(listOf(HOUSE_OF_THE_RISING_SUN to 1), setlistEntries())
        tap(hasContentDescription(string(Res.string.back)))
        tap(hasText(string(Res.string.songs)) and hasClickAction())
        tap(hasText(RISING_SUN_TITLE) and hasClickAction())
        assertTrue(transposition().startsWith("0 "), "The library's stepper reads ${transposition()}.")
    }

    private fun AppTestScope.tap(matcher: SemanticsMatcher) {
        onNode(matcher).performTouchInput { click() }
        waitForIdle()
    }

    /** Every song file of the library with its text. */
    private fun AppTestScope.songContents() = songFileNames().associateWith { File(songsFolder, it).readText() }

    /** The setlist file's entries, each the song file it names and the transposition it plays it in. */
    private fun AppTestScope.setlistEntries() = Json.parseToJsonElement(File(library, "setlists/$SETLIST").readText())
        .jsonObject.getValue("songs").jsonArray
        .map { it.jsonObject.getValue("file").jsonPrimitive.content to it.jsonObject.getValue("transposition").jsonPrimitive.int }

    /** What the transposition stepper reads, which its buttons carry as their state for a screen reader. */
    private fun AppTestScope.transposition() = onNodeWithContentDescription(string(Res.string.song_details_transpose_up))
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription).orEmpty()
}

private const val SETLIST = "campfire_tests-an_evening_by_the_fire.setlist.json"
private const val RISING_SUN_TITLE = "House of the Rising Sun"
