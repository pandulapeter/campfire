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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.import_conflicts_keep_both
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace
import com.pandulapeter.campfire.presentation.resources.import_files
import com.pandulapeter.campfire.presentation.resources.import_preview_choices
import com.pandulapeter.campfire.presentation.resources.import_preview_import
import com.pandulapeter.campfire.presentation.resources.import_preview_import_as_copy
import com.pandulapeter.campfire.presentation.resources.import_preview_include
import com.pandulapeter.campfire.presentation.resources.import_preview_name_taken
import com.pandulapeter.campfire.presentation.resources.import_preview_name_taken_choice
import com.pandulapeter.campfire.presentation.resources.import_preview_title
import com.pandulapeter.campfire.presentation.resources.import_replace_title
import com.pandulapeter.campfire.presentation.resources.import_review_confirm
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.HOUSE_OF_THE_RISING_SUN
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.resourceText
import com.pandulapeter.campfire.screenshots.runAppTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
internal class ImportPreviewTest {

    @Test
    fun `a picked song is previewed, and written only by its Import`() = runAppTest(pickedFiles = listOf(newSong)) {
        val before = songFileNames()
        pickFiles()
        waitForText(string(Res.string.import_preview_title))
        assertEquals(before, songFileNames())
        onNodeWithText(string(Res.string.import_preview_import)).performClick()
        waitForLibrary { NEW_SONG in songFileNames() }
        assertEquals(resourceText("songs/$NEW_SONG"), File(songsFolder, NEW_SONG).readText())
        // The preview becomes the song's own screen, which says nothing of a preview any more.
        waitUntil(timeoutMillis = TIMEOUT_MILLIS) { onAllNodesWithText(string(Res.string.import_preview_title)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `a picked song whose name is taken offers both ways at once, and the copy keeps the library's song`() = runAppTest(pickedFiles = listOf(collision)) {
        val original = File(songsFolder, HOUSE_OF_THE_RISING_SUN).readText()
        pickFiles()
        waitForText(string(Res.string.import_preview_name_taken))
        onNodeWithText(string(Res.string.import_preview_import_as_copy)).assertExists()
        onNodeWithText(string(Res.string.import_conflicts_replace)).assertExists()
        onNodeWithText(string(Res.string.import_preview_import_as_copy)).performClick()
        waitForLibrary { NUMBERED_COLLISION in songFileNames() }
        assertEquals(original, File(songsFolder, HOUSE_OF_THE_RISING_SUN).readText())
    }

    @Test
    fun `in a short window the card is one line, and the page's choices are behind its button`() = runAppTest(
        window = TestWindow.PHONE_ON_ITS_SIDE,
        pickedFiles = listOf(newSong, collision),
    ) {
        pickFiles()
        val reviewImport = pluralString(Res.plurals.import_review_confirm, 2)
        waitUntil(timeoutMillis = TIMEOUT_MILLIS) { onAllNodesWithContentDescription(reviewImport).fetchSemanticsNodes().isNotEmpty() }
        // A phone on its side shows the review's header and little else, its rows below it.
        val collisionRow = hasText(COLLISION_TITLE, substring = true) and hasClickAction()
        onNode(hasScrollToNodeAction()).performScrollToNode(collisionRow)
        onNode(collisionRow).performClick()
        waitForText(string(Res.string.import_preview_name_taken_choice, string(Res.string.import_conflicts_keep_both)))
        onAllNodesWithText(string(Res.string.import_preview_include)).assertCountEquals(0)
        onNodeWithContentDescription(string(Res.string.import_preview_choices)).performClick()
        onNode(hasText(string(Res.string.import_preview_include)) and hasAnyAncestor(isPopup())).assertExists()
        onNode(hasText(string(Res.string.import_conflicts_replace)) and hasAnyAncestor(isPopup())).performClick()
        waitForText(string(Res.string.import_preview_name_taken_choice, string(Res.string.import_conflicts_replace)))
        // The menu stays open after a choice, so that its effect on the line is seen; a back closes it, as it does any menu.
        pressBack()
        onAllNodes(isPopup()).assertCountEquals(0)
        // The preview's button is the review's Import, which goes back to the review and asks about the replace there.
        onNode(hasText(reviewImport) and hasClickAction()).performClick()
        waitForText(pluralString(Res.plurals.import_replace_title, 1))
        onNode(hasText(string(Res.string.import_conflicts_replace)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        waitForLibrary { NEW_SONG in songFileNames() && File(songsFolder, HOUSE_OF_THE_RISING_SUN).readText() == resourceText("songs/$COLLISION") }
    }

    private fun AppTestScope.pickFiles() {
        onNodeWithContentDescription(string(Res.string.songs_new_song)).performClick()
        onNode(hasText(string(Res.string.import_files)) and hasAnyAncestor(isPopup())).performClick()
    }

    private fun AppTestScope.waitForText(text: String) = waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private val newSong get() = ImportedFile(name = "new.cho", bytes = resourceText("songs/$NEW_SONG").encodeToByteArray())

    private val collision get() = ImportedFile(name = "colliding.cho", bytes = resourceText("songs/$COLLISION").encodeToByteArray())
}

/** A song no library has; the import names it after its header. */
private const val NEW_SONG = "campfire_tests-a_new_song.cho"

/** A song whose header names it after a demo song it does not match. */
private const val COLLISION = "house_of_the_rising_sun_rewritten.cho"
private const val COLLISION_TITLE = "House of the Rising Sun"
private val NUMBERED_COLLISION = File(HOUSE_OF_THE_RISING_SUN).nameWithoutExtension + "_2.cho"
private const val TIMEOUT_MILLIS = 10_000L
