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

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
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
import com.pandulapeter.campfire.data.source.local.api.ImportStagingLocalSource
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
import com.pandulapeter.campfire.presentation.resources.instrument_guitar
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_standard
import com.pandulapeter.campfire.presentation.resources.song_image
import com.pandulapeter.campfire.presentation.resources.song_image_not_found
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.DOCK_PANEL_PADDING
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.HOUSE_OF_THE_RISING_SUN
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.opensTempoSheet
import com.pandulapeter.campfire.screenshots.resourceText
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `a preview's dock is one row of chord chips with no tempo chip`() = runAppTest(pickedFiles = listOf(newSong)) {
        pickFiles()
        waitForText(string(Res.string.import_preview_title))
        // A chip names its chord and nothing more, where a diagram is also said as its shape.
        val chord = hasText(PREVIEW_CHORD) and hasClickAction() and hasAnyAncestor(hasScrollAction())
        waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            onAllNodes(chord and SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription)).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodes(chord and SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).assertCountEquals(0)
        onNode(opensTempoSheet()).assertDoesNotExist()
    }

    @Test
    fun `a wide window's preview card stands under the song beside the side panel, whose instrument card fills its width`() = runAppTest(
        window = TestWindow.DESKTOP,
        pickedFiles = listOf(newSong),
    ) {
        pickFiles()
        waitForText(string(Res.string.import_preview_title))
        waitForIdle()
        val message = onNode(hasText(string(Res.string.import_preview_title))).fetchSemanticsNode().boundsInRoot
        val instrument = onNode(hasText(string(Res.string.instrument_guitar)) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val tuning = onNode(hasText(string(Res.string.instrument_tuning_standard)) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(message.right <= instrument.left, "The card's message at $message is not beside the panel's instrument card at $instrument.")
        assertTrue(message.top > instrument.bottom, "The card's message at $message is not under the song, at the bottom.")
        // The panel's content runs to 8 dp from the window's end, and the card's halves fill it, a preview having no tuner.
        val panelContentEnd = TestWindow.DESKTOP.width.value - DOCK_PANEL_PADDING.value
        assertTrue(abs(instrument.right - panelContentEnd) <= 1.5f && abs(tuning.right - panelContentEnd) <= 1.5f, "The halves at $instrument and $tuning do not reach the card's end at $panelContentEnd.")
        assertTrue(abs(instrument.left - tuning.left) <= 1.5f, "The halves at $instrument and $tuning are not as wide as each other.")
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

    @Test
    fun `an archive song's picture is shown in its preview before Import writes it`() = runAppTest(pickedFiles = listOf(archiveWithPicture())) {
        pickFiles()
        waitForText(string(Res.string.import_preview_title))
        waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            onAllNodesWithContentDescription(string(Res.string.song_image), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(onAllNodes(hasText(text(Res.string.song_image_not_found, "chart.png"), substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(File(library, "files").list().isNullOrEmpty(), "A picture reached the library before Import.")
    }

    @Test
    fun `a staged archive song's picture is shown in its preview from the staging folder`() = runAppTest {
        val archive = archiveWithPicture().bytes
        val staged = runBlocking {
            GlobalContext.get().get<ImportStagingLocalSource>().receiveArchive(announcedSize = archive.size.toLong()).run {
                write(archive, 0, archive.size)
                finish()
            }
        }
        runOnIdle { viewModel.importController.importFiles(listOf(ImportedFile(name = "chart.zip", bytes = ByteArray(0), staged = staged))) }
        waitForText(string(Res.string.import_preview_title))
        waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            onAllNodesWithContentDescription(string(Res.string.song_image), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(onAllNodes(hasText(text(Res.string.song_image_not_found, "chart.png"), substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        // The picture the preview shows is the one the unpack staged beside the archive, read from there.
        assertTrue(File(dataDirectory, "imports").list().orEmpty().any { it.endsWith(".png") }, "The archive's picture was not staged.")
        assertTrue(File(library, "files").list().isNullOrEmpty(), "A picture reached the library before Import.")
    }

    /** A zip of one song and the picture it names, in an `images/` folder, as an export writes it. */
    private fun archiveWithPicture() = ImportedFile(
        name = "chart.zip",
        bytes = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("chart.cho"))
                zip.write("{title: A Chart}\n{artist: Campfire Tests}\n{image: src=chart.png}\n".encodeToByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("images/chart.png"))
                zip.write(ByteArrayOutputStream().also { ImageIO.write(BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray())
                zip.closeEntry()
            }
        }.toByteArray(),
    )

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

/** A chord of [NEW_SONG] that is not its key. */
private const val PREVIEW_CHORD = "G"

/** A song whose header names it after a demo song it does not match. */
private const val COLLISION = "house_of_the_rising_sun_rewritten.cho"
private const val COLLISION_TITLE = "House of the Rising Sun"
private val NUMBERED_COLLISION = File(HOUSE_OF_THE_RISING_SUN).nameWithoutExtension + "_2.cho"
private const val TIMEOUT_MILLIS = 10_000L
