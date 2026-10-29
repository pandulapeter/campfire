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
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.preferences.AppLanguage
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.deselect_all
import com.pandulapeter.campfire.presentation.resources.import_conflicts_keep_both
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace
import com.pandulapeter.campfire.presentation.resources.import_files
import com.pandulapeter.campfire.presentation.resources.import_review_already_there
import com.pandulapeter.campfire.presentation.resources.import_review_confirm
import com.pandulapeter.campfire.presentation.resources.import_review_conflicts
import com.pandulapeter.campfire.presentation.resources.import_review_name_taken_counts
import com.pandulapeter.campfire.presentation.resources.import_review_summary_counts
import com.pandulapeter.campfire.presentation.resources.import_review_title
import com.pandulapeter.campfire.presentation.resources.import_replace_title
import com.pandulapeter.campfire.presentation.resources.import_review_songs
import com.pandulapeter.campfire.presentation.resources.select_all
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.screenshots.APP_LANGUAGES
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.HOME_ON_THE_RANGE
import com.pandulapeter.campfire.screenshots.HOUSE_OF_THE_RISING_SUN
import com.pandulapeter.campfire.screenshots.RISING_SUN_TITLE
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.breaksAWord
import com.pandulapeter.campfire.screenshots.demoSongText
import com.pandulapeter.campfire.screenshots.isWhole
import com.pandulapeter.campfire.screenshots.resourceText
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.textLayout
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class ImportReviewTest {

    @Test
    fun `the review sorts a new, an identical, and a colliding song into their groups`() = runAppTest(pickedFiles = listOf(archive)) {
        startImport()
        onNodeWithText(pluralString(Res.plurals.import_review_conflicts, 1)).assertExists()
        onNodeWithText(pluralString(Res.plurals.import_review_already_there, 1)).assertExists()
        onNodeWithText(string(Res.string.import_review_name_taken_counts, 0, 1)).assertExists()
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
        onNodeWithText(string(Res.string.import_review_name_taken_counts, 1, 0)).assertExists()
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
        onNodeWithText(string(Res.string.import_review_name_taken_counts, 0, 1)).assertExists()
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

    @Test
    fun `a taken name's title gets the row's width, with its choice under it, in every language`() = APP_LANGUAGES.forEach { language ->
        listOf(1f, 1.3f).forEach { textScale ->
            runAppTest(pickedFiles = listOf(archive), appearance = TestAppearance(language = language, textScale = textScale)) {
                val case = "${language.id} at $textScale"
                startImport()
                // The summary leads the list, which the row's search below scrolls away.
                listOf(string(Res.string.import_review_summary_counts, 2, 0), string(Res.string.import_review_name_taken_counts, 0, 1)).forEach { counts ->
                    val layout = textLayout(hasText(counts))
                    (0 until layout.lineCount).forEach { line ->
                        val text = counts.substring(layout.getLineStart(line), layout.getLineEnd(line))
                        assertFalse(text.all { it.isDigit() || it.isWhitespace() || it == '\u00A0' }, "$case: a count stands alone on a line of \"$counts\".")
                    }
                }
                if (textScale > 1f && language in LONG_TITLE_LANGUAGES) {
                    assertTrue(textLayout(hasText(string(Res.string.import_review_title))).isWhole, "$case: the review's title is cut short.")
                }
                // The row, its choice included, is the review's last, below the bottom of a phone at a large text size.
                onNode(hasScrollToIndexAction()).performScrollToNode(hasText(RISING_SUN_TITLE, substring = true) and hasClickAction())
                waitForIdle()
                // The colliding song's headline is the only text of the review naming it; the identical song is another.
                val headline = onNode(hasText(RISING_SUN_TITLE, substring = true), useUnmergedTree = true).fetchSemanticsNode()
                assertFalse(headline.textLayout.breaksAWord, "$case: \"${headline.textLayout.layoutInput.text}\" breaks a word.")
                // The row's choice is the only one: a group of one taken name has none for all of them.
                val choice = onAllNodes(hasText(string(Res.string.import_conflicts_keep_both)) and hasClickAction()).fetchSemanticsNodes().single()
                assertTrue(choice.boundsInRoot.top >= headline.boundsInRoot.bottom, "$case: the choice (${choice.boundsInRoot}) is beside the title (${headline.boundsInRoot}).")
            }
        }
    }

    @Test
    fun `a group's Select all fits beside its heading, in every language`() = APP_LANGUAGES.forEach { language ->
        listOf(1f, 1.3f).forEach { textScale ->
            runAppTest(pickedFiles = listOf(newSongsArchive), appearance = TestAppearance(language = language, textScale = textScale)) {
                val case = "${language.id} at $textScale"
                startImport()
                val heading = pluralString(Res.plurals.import_review_songs, 2)
                onNode(hasScrollToIndexAction()).performScrollToNode(hasText(heading))
                waitForIdle()
                // Every box starts ticked, so the button first reads Deselect all; once pressed, Select all.
                listOf(Res.string.deselect_all, Res.string.select_all).forEach { label ->
                    val text = string(label)
                    val button = onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode()
                    assertEquals(1, button.textLayout.lineCount, "$case: \"$text\" is broken onto ${button.textLayout.lineCount} lines.")
                    assertTrue(button.textLayout.isWhole, "$case: \"$text\" is cut short.")
                    val title = onNode(hasText(heading), useUnmergedTree = true).fetchSemanticsNode()
                    assertTrue(title.textLayout.isWhole, "$case: \"$heading\" is cut short beside \"$text\".")
                    val isApart = title.boundsInRoot.right <= button.boundsInRoot.left || button.boundsInRoot.right <= title.boundsInRoot.left
                    assertTrue(isApart, "$case: \"$heading\" runs under \"$text\".")
                    if (label == Res.string.deselect_all) {
                        onNode(hasText(text) and hasClickAction()).performClick()
                        waitForIdle()
                    }
                }
            }
        }
    }

    /** Picks [archive] from the Songs screen's New menu, and waits for its review. */
    private fun AppTestScope.startImport() {
        onNodeWithContentDescription(string(Res.string.songs_new_song)).performClick()
        // In the menu's popup: the demo library is two songs, so the list under it ends in a hint with the same button.
        onNode(hasText(string(Res.string.import_files)) and hasAnyAncestor(isPopup())).performClick()
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

    /** Two songs no library has, so that their group has a Select all. */
    private val newSongsArchive
        get() = ImportedFile(
            name = "songs.zip",
            bytes = zipOf(
                "new.cho" to resourceText("songs/$NEW_SONG"),
                "long_road.cho" to resourceText("songs/campfire_tests-the_long_road_north.cho"),
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

/** The languages whose title for the review was cut short at a large text size, shortened since. */
private val LONG_TITLE_LANGUAGES = setOf(AppLanguage.SPANISH, AppLanguage.HUNGARIAN, AppLanguage.ITALIAN)
