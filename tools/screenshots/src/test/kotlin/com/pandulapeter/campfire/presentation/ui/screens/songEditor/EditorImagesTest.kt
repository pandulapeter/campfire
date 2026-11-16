/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.performSemanticsAction
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_editor_discard
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_image
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import com.pandulapeter.campfire.presentation.ui.platform.ImpersonatedPlatform
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.HOUSE_OF_THE_RISING_SUN
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.demoSongText
import com.pandulapeter.campfire.screenshots.openEditor
import com.pandulapeter.campfire.screenshots.runAppTest
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pictures put into a song from the editor: kept on the draft shelf, outside the library, until Save moves them in, and
 * gone with a discarded draft; and a picture opened on its own, which becomes a new song in the editor.
 */
@OptIn(ExperimentalTestApi::class)
internal class EditorImagesTest {

    private val picture = png()

    /** The name the library keeps [picture] under: 16 hex digits of its SHA-256 and its extension. */
    private val pictureName = MessageDigest.getInstance("SHA-256").digest(picture).joinToString("") { "%02x".format(it) }.take(16) + ".png"

    @Test
    fun `a picture inserted from the toolbar waits outside the library until Save moves it in`() = runAppTest(
        window = TestWindow.DESKTOP,
        platform = ImpersonatedPlatform.WINDOWS,
        pickedFiles = listOf(ImportedFile("holiday.png", picture)),
    ) {
        openEditor()
        insertPicture()
        assertTrue(draftImage().exists(), "The picture is not on the draft shelf.")
        assertFalse(libraryImage().exists(), "The picture reached the library before Save.")
        onNodeWithContentDescription(string(Res.string.save)).performClick()
        waitForLibrary { "{image: src=$pictureName}" in houseSong() && libraryImage().exists() }
        assertTrue(libraryImage().readBytes().contentEquals(picture), "The library's copy is not the picked picture.")
        onNodeWithContentDescription(string(Res.string.close)).performClick()
        waitForLibrary { !draftImage().exists() }
        assertTrue(libraryImage().exists(), "The saved song's picture went with the draft's.")
    }

    @Test
    fun `a picture inserted into a draft that is discarded reaches neither the library nor stays on the draft shelf`() = runAppTest(
        window = TestWindow.DESKTOP,
        platform = ImpersonatedPlatform.WINDOWS,
        pickedFiles = listOf(ImportedFile("holiday.png", picture)),
    ) {
        openEditor()
        insertPicture()
        onNodeWithContentDescription(string(Res.string.close)).performClick()
        onNode(hasText(string(Res.string.song_editor_discard)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        waitForLibrary { !draftImage().exists() }
        assertFalse(libraryImage().exists(), "A discarded picture reached the library.")
        assertEquals(demoSongText(HOUSE_OF_THE_RISING_SUN), houseSong())
    }

    @Test
    fun `Ctrl+Shift+I presses the Image button, whose tooltip names the keys`() = runAppTest(
        window = TestWindow.DESKTOP,
        platform = ImpersonatedPlatform.WINDOWS,
        pickedFiles = listOf(ImportedFile("holiday.png", picture)),
    ) {
        openEditor()
        onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.I) } } }
        waitUntil(timeoutMillis = EDITOR_TIMEOUT_MILLIS) { "{image: src=$pictureName}" in fieldText() }
        onNode(hasText(string(Res.string.song_editor_insert_image)) and hasClickAction()).performMouseInput { enter(center) }
        waitUntil(timeoutMillis = EDITOR_TIMEOUT_MILLIS) {
            onAllNodes(hasText(keys(KeyShortcut.INSERT_IMAGE), substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `a picture opened on its own becomes a new song in the editor, written only by Save`() = runAppTest(
        window = TestWindow.DESKTOP,
        platform = ImpersonatedPlatform.WINDOWS,
    ) {
        val songsBefore = songFileNames()
        runOnIdle { viewModel.importController.importFiles(listOf(ImportedFile("Lantern Chart.png", picture))) }
        waitUntil(timeoutMillis = EDITOR_TIMEOUT_MILLIS) { "{image: src=$pictureName}" in fieldText() }
        assertTrue("{title: Lantern Chart}" in fieldText(), "The new song is not titled by the file: ${fieldText()}")
        assertTrue(draftImage().exists(), "The picture is not on the draft shelf.")
        assertFalse(libraryImage().exists(), "The picture reached the library before Save.")
        assertEquals(songsBefore, songFileNames())
        onNodeWithContentDescription(string(Res.string.save)).performClick()
        waitForLibrary { songFileNames().size > songsBefore.size && libraryImage().exists() }
        val song = File(songsFolder, (songFileNames() - songsBefore).single()).readText()
        assertTrue("{image: src=$pictureName}" in song, "The saved song does not show the picture: $song")
    }

    /** Presses the toolbar's Image button, whose picker hands over the test's picture, and waits for its line. */
    private fun AppTestScope.insertPicture() {
        onNode(hasText(string(Res.string.song_editor_insert_image)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(timeoutMillis = EDITOR_TIMEOUT_MILLIS) { "{image: src=$pictureName}" in fieldText() }
    }

    /** The editor's text, empty while there is no editor yet. */
    private fun AppTestScope.fieldText() = onAllNodes(hasSetTextAction()).fetchSemanticsNodes().firstOrNull()
        ?.config?.let { if (SemanticsProperties.EditableText in it) it[SemanticsProperties.EditableText].text else null }.orEmpty()

    private fun AppTestScope.houseSong() = File(songsFolder, HOUSE_OF_THE_RISING_SUN).readText()

    private fun AppTestScope.draftImage() = File(dataDirectory, "editor-draft-images/$pictureName")

    private fun AppTestScope.libraryImage() = File(library, "files/$pictureName")

    /** A small PNG of a few colours, as a photo of a chart would be. */
    private fun png() = ByteArrayOutputStream().also { output ->
        val image = BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 80) for (x in 0 until 120) image.setRGB(x, y, Color(x * 2, y * 3, 90).rgb)
        ImageIO.write(image, "png", output)
    }.toByteArray()

    private companion object {
        const val EDITOR_TIMEOUT_MILLIS = 10_000L
    }
}
