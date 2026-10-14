/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.import_conflicts_keep_both
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace
import com.pandulapeter.campfire.presentation.resources.import_files
import com.pandulapeter.campfire.presentation.resources.import_review_already_there
import com.pandulapeter.campfire.presentation.resources.import_review_confirm
import com.pandulapeter.campfire.presentation.resources.import_review_conflicts
import com.pandulapeter.campfire.presentation.resources.import_review_numbered
import com.pandulapeter.campfire.presentation.resources.import_review_replacing
import com.pandulapeter.campfire.presentation.resources.import_replace_title
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.HOME_ON_THE_RANGE
import com.pandulapeter.campfire.screenshots.HOUSE_OF_THE_RISING_SUN
import com.pandulapeter.campfire.screenshots.demoSongText
import com.pandulapeter.campfire.screenshots.resourceText
import com.pandulapeter.campfire.screenshots.runAppTest
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
internal class ImportReviewTest {

    @Test
    fun `the review sorts a new, an identical, and a colliding song into their groups`() = runAppTest(pickedFiles = listOf(archive)) {
        startImport()
        onNodeWithText(string(Res.string.import_review_conflicts)).assertExists()
        onNodeWithText(pluralString(Res.plurals.import_review_already_there, 1)).assertExists()
        onNodeWithText(pluralString(Res.plurals.import_review_numbered, 1)).assertExists()
        onNodeWithContentDescription(pluralString(Res.plurals.import_review_confirm, 2)).assertExists()
    }

    @Test
    fun `importing writes the new song and numbers the colliding one`() = runAppTest(pickedFiles = listOf(archive)) {
        val before = libraryContents()
        startImport()
        onNodeWithContentDescription(pluralString(Res.plurals.import_review_confirm, 2)).performClick()
        waitForLibrary { songFileNames().size == before.size + 2 }
        assertEquals(
            before + mapOf(
                NEW_SONG to resourceText("songs/$NEW_SONG"),
                NUMBERED_COLLISION to resourceText("songs/$COLLISION"),
            ),
            libraryContents(),
        )
    }

    @Test
    fun `a confirmed replace overwrites the colliding file and nothing else`() = runAppTest(pickedFiles = listOf(archive)) {
        val before = libraryContents()
        startImport()
        onAllNodesWithText(string(Res.string.import_conflicts_keep_both)).onFirst().performClick()
        onNodeWithText(string(Res.string.import_conflicts_replace)).performClick()
        onNodeWithText(pluralString(Res.plurals.import_review_replacing, 1)).assertExists()
        onNodeWithContentDescription(pluralString(Res.plurals.import_review_confirm, 2)).performClick()
        onNodeWithText(pluralString(Res.plurals.import_replace_title, 1)).assertExists()
        onNode(hasText(string(Res.string.import_conflicts_replace)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        waitForLibrary { NEW_SONG in songFileNames() && libraryContents()[HOUSE_OF_THE_RISING_SUN] == resourceText("songs/$COLLISION") }
        assertEquals(
            before + mapOf(
                NEW_SONG to resourceText("songs/$NEW_SONG"),
                HOUSE_OF_THE_RISING_SUN to resourceText("songs/$COLLISION"),
            ),
            libraryContents(),
        )
    }

    @Test
    fun `nothing is written before the replace question is answered`() = runAppTest(pickedFiles = listOf(archive)) {
        val before = libraryContents()
        startImport()
        onAllNodesWithText(string(Res.string.import_conflicts_keep_both)).onFirst().performClick()
        onNodeWithText(string(Res.string.import_conflicts_replace)).performClick()
        onNodeWithContentDescription(pluralString(Res.plurals.import_review_confirm, 2)).performClick()
        onNode(hasText(string(Res.string.cancel)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        onAllNodesWithText(string(Res.string.import_conflicts_replace)).onFirst().performClick()
        onNodeWithText(string(Res.string.import_conflicts_keep_both)).performClick()
        onNodeWithText(pluralString(Res.plurals.import_review_numbered, 1)).assertExists()
        onNodeWithContentDescription(pluralString(Res.plurals.import_review_confirm, 2)).performClick()
        // Library writes land on IO threads, so only the end state of a second, unquestioned import can show that the
        // first Import wrote nothing: a write before the answer would have replaced the demo song or numbered the new one.
        waitForLibrary { songFileNames().size == before.size + 2 }
        assertEquals(
            before + mapOf(
                NEW_SONG to resourceText("songs/$NEW_SONG"),
                NUMBERED_COLLISION to resourceText("songs/$COLLISION"),
            ),
            libraryContents(),
        )
    }

    /** Picks [archive] from the Songs screen's New menu, and waits for its review. */
    private fun AppTestScope.startImport() {
        onNodeWithContentDescription(string(Res.string.songs_new_song)).performClick()
        onNodeWithText(string(Res.string.import_files)).performClick()
        waitUntil(timeoutMillis = REVIEW_TIMEOUT_MILLIS) {
            onAllNodesWithContentDescription(pluralString(Res.plurals.import_review_confirm, 2)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Every song file of the library with its text. */
    private fun AppTestScope.libraryContents() = songFileNames().associateWith { File(songsFolder, it).readText() }

    private val archive
        get() = ImportedFile(
            name = "songs.zip",
            bytes = zipOf(
                "new.cho" to resourceText("songs/$NEW_SONG"),
                "identical.cho" to demoSongText(HOME_ON_THE_RANGE),
                "colliding.cho" to resourceText("songs/$COLLISION"),
            ),
        )

    private fun zipOf(vararg entries: Pair<String, String>) = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.encodeToByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()
}

/** A song no library has; the import names it after its header. */
private const val NEW_SONG = "campfire_tests-a_new_song.cho"

/** A song whose header names it after a demo song it does not match. */
private const val COLLISION = "house_of_the_rising_sun_rewritten.cho"
private val NUMBERED_COLLISION = File(HOUSE_OF_THE_RISING_SUN).nameWithoutExtension + "_2.cho"
private const val REVIEW_TIMEOUT_MILLIS = 10_000L
