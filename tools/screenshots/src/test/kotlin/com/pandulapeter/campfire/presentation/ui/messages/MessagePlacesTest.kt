/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.messages

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performSemanticsAction
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_attachment_not_opened
import com.pandulapeter.campfire.presentation.resources.song_attachment_refused_format
import com.pandulapeter.campfire.presentation.resources.song_details_attachment_add
import com.pandulapeter.campfire.presentation.resources.song_details_attachments_edit
import com.pandulapeter.campfire.presentation.resources.song_details_links_edit
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.openAboutTheSong
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where a message is drawn: every bottom sheet is a window of its own, so a message drawn under an open one would play
 * out unseen. It is drawn in the topmost open sheet, and at the app's root while none is.
 */
@OptIn(ExperimentalTestApi::class)
internal class MessagePlacesTest {

    @Test
    fun `a refusal of the Attachments sheet's Add file is drawn in that sheet`() = runAppTest(
        pickedFiles = listOf(ImportedFile("setup.exe", byteArrayOf(0x4D, 0x5A))),
    ) {
        openAboutTheSongTile(Res.string.song_details_attachments_edit)
        val addFile = hasContentDescription(string(Res.string.song_details_attachment_add)) and hasClickAction()
        tap(addFile)
        val refusal = hasText(string(Res.string.song_attachment_refused_format))
        awaitMessage(refusal)
        assertEquals(1, onAllNodes(refusal).fetchSemanticsNodes().size, "The refusal is drawn more than once.")
        assertEquals(1, onAllNodes(refusal and inSheetWith(addFile)).fetchSemanticsNodes().size, "The refusal is not drawn in the sheet.")
    }

    @Test
    fun `with a sheet over another only the top one draws a message`() = runAppTest {
        openAboutTheSongTile(Res.string.song_details_links_edit)
        val linksSheet = hasText(string(Res.string.song_details_links_edit))
        // The tile that opened the links sheet, which only About the song has.
        val aboutTheSong = hasClickLabel(string(Res.string.song_details_links_edit))
        assertEquals(2, onAllNodes(isDialog()).fetchSemanticsNodes().size, "The links sheet is not over About the song.")
        runOnIdle { viewModel.messageSink.sendMessage(Message.AttachmentNotOpened) }
        val message = hasText(string(Res.string.song_attachment_not_opened))
        awaitMessage(message)
        assertEquals(1, onAllNodes(message).fetchSemanticsNodes().size, "The message is drawn more than once.")
        assertEquals(1, onAllNodes(message and inSheetWith(linksSheet)).fetchSemanticsNodes().size, "The message is not drawn in the top sheet.")
        assertEquals(0, onAllNodes(message and inSheetWith(aboutTheSong)).fetchSemanticsNodes().size, "The message is drawn under the top sheet.")
    }

    @Test
    fun `a message sent with no sheet open is drawn at the root`() = runAppTest {
        runOnIdle { viewModel.messageSink.sendMessage(Message.AttachmentNotOpened) }
        val message = hasText(string(Res.string.song_attachment_not_opened))
        awaitMessage(message)
        assertEquals(1, onAllNodes(message and !hasAnyAncestor(isDialog())).fetchSemanticsNodes().size, "The message is not drawn at the root.")
    }

    private fun AppTestScope.openAboutTheSongTile(clickLabel: StringResource) {
        openAboutTheSong()
        onNode(hasClickLabel(string(clickLabel))).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    private fun AppTestScope.awaitMessage(matcher: SemanticsMatcher) =
        waitUntil(timeoutMillis = MESSAGE_TIMEOUT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    /** Inside the window of the sheet that holds what [matcher] finds. */
    private fun inSheetWith(matcher: SemanticsMatcher) = hasAnyAncestor(isDialog() and hasAnyDescendant(matcher))

    private fun hasClickLabel(label: String) = SemanticsMatcher("is clicked to $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private companion object {
        const val MESSAGE_TIMEOUT_MILLIS = 10_000L
    }
}
