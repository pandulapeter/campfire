/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_details_duration
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_title
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.resources.songs_update_file_name
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.HOUSE_OF_THE_RISING_SUN
import com.pandulapeter.campfire.screenshots.demoSongText
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
internal class UpdateFileNameTest {

    @Test
    fun `Update file name renames the song file and the setlists that name it, and nothing else`() = runAppTest(setlists = listOf(SETLIST)) {
        val before = songContents()
        val savedText = demoSongText(HOUSE_OF_THE_RISING_SUN).replace("{title: $RISING_SUN_TITLE}", "{title: $NEW_TITLE}")
        tap(hasText(RISING_SUN_TITLE) and hasClickAction())
        tap(hasText(RISING_SUN_TITLE, substring = true) and hasClickAction())
        tap(hasText(string(Res.string.song_details_duration), substring = true) and hasClickAction())
        onNode(hasText(string(Res.string.song_editor_insert_title)) and hasSetTextAction()).performTextReplacement(NEW_TITLE)
        tap(hasText(string(Res.string.save)) and hasClickAction() and hasAnyAncestor(isDialog()))
        waitForLibrary { File(songsFolder, HOUSE_OF_THE_RISING_SUN).readText() == savedText }
        assertEquals(before.keys, songFileNames())
        tap(hasContentDescription(string(Res.string.close)) and hasAnyAncestor(isDialog()))
        // A tap while the About the song sheet is still sliding away lands on its scrim, not on the screen under it.
        waitUntil(timeoutMillis = RESCAN_TIMEOUT_MILLIS) { onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        tap(hasContentDescription(string(Res.string.songs_actions)))
        // The entry is offered only once the library has read the saved file again and found its name out of date.
        waitUntil(timeoutMillis = RESCAN_TIMEOUT_MILLIS) {
            onAllNodes(hasText(string(Res.string.songs_update_file_name)) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        tap(hasText(string(Res.string.songs_update_file_name)) and hasClickAction())
        // The file is moved first and the setlists that name it are rewritten after, so both are waited for.
        waitForLibrary { HOUSE_OF_THE_RISING_SUN !in songFileNames() && RENAMED in setlistFiles() }
        assertEquals(before - HOUSE_OF_THE_RISING_SUN + (RENAMED to savedText), songContents())
        assertEquals(listOf(RENAMED), setlistFiles())
    }

    private fun AppTestScope.tap(matcher: SemanticsMatcher) {
        onNode(matcher).performTouchInput { click() }
        waitForIdle()
    }

    /** Every song file of the library with its text. */
    private fun AppTestScope.songContents() = songFileNames().associateWith { File(songsFolder, it).readText() }

    /** The song files the setlist names, in its order. */
    private fun AppTestScope.setlistFiles() = Json.parseToJsonElement(File(library, "setlists/$SETLIST").readText())
        .jsonObject.getValue("songs").jsonArray
        .map { it.jsonObject.getValue("file").jsonPrimitive.content }
}

private const val SETLIST = "campfire_tests-an_evening_by_the_fire.setlist.json"
private const val RISING_SUN_TITLE = "House of the Rising Sun"
private const val NEW_TITLE = "Rising Sun Blues"
private const val RENAMED = "traditional_american-rising_sun_blues.cho"
private const val RESCAN_TIMEOUT_MILLIS = 10_000L
