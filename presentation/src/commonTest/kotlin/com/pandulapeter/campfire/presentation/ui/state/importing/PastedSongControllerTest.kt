/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import androidx.compose.runtime.mutableStateListOf
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.paste_refused_web
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.ClipboardText
import com.pandulapeter.campfire.presentation.ui.platform.SharedTextPayload
import com.pandulapeter.campfire.presentation.ui.platform.SharedTextTitle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PastedSongControllerTest {

    private val backStack = mutableStateListOf<CampfireDestination>(CampfireDestination.Songs)
    private val isImporting = MutableStateFlow(false)
    private val isPerformanceModeEnabled = MutableStateFlow(false)
    private val queued = mutableListOf<Pair<List<ImportedFile>, Boolean>>()
    private val suggestions = mutableListOf<SharedTextTitle?>()
    private val importSettled = CompletableDeferred<Unit>()
    private lateinit var messageSink: MessageSink
    private lateinit var dialogHost: DialogHost

    private fun TestScope.controller(): PastedSongController {
        messageSink = MessageSink(backgroundScope)
        dialogHost = DialogHost(backgroundScope)
        return PastedSongController(
            scope = backgroundScope,
            messageSink = messageSink,
            dialogHost = dialogHost,
            backStack = backStack,
            isImporting = isImporting,
            isPerformanceModeEnabled = isPerformanceModeEnabled,
            enqueueImport = { files, isNewSongFromText, suggestion ->
                queued += files to isNewSongFromText
                suggestions += suggestion
                importSettled
            },
        )
    }

    private val messages get() = messageSink.messageQueue.value.map { it.value }

    @Test
    fun `a chord sheet is queued as a new song named by its first line that is not chords`() = runTest {
        controller().newSongFromText("Am  C\nHello")
        val (files, isNewSongFromText) = queued.single()
        val file = files.single()
        assertEquals("hello.txt", file.name)
        assertEquals("Am  C\nHello", file.bytes.decodeToString())
        assertTrue(isNewSongFromText)
    }

    @Test
    fun `blank text, links alone and an oversized text are answered without an import`() = runTest {
        val controller = controller()
        controller.newSongFromText("  \n ")
        controller.newSongFromText("https://example.com/song")
        controller.newSongFromText("a".repeat(ImportLimits.MAX_TEXT_FILE_SIZE.toInt() + 1))
        assertEquals(listOf(Message.ClipboardEmpty, Message.PastedOnlyLinks, Message.PastedTextTooLarge), messages)
        assertTrue(queued.isEmpty())
    }

    @Test
    fun `in read only mode a text is still a new song`() = runTest {
        isPerformanceModeEnabled.value = true
        controller().newSongFromText("Am  C\nHello")
        assertTrue(queued.single().second)
    }

    @Test
    fun `one shared text is a new song named by the share's subject`() = runTest {
        controller().importSharedText(SharedTextPayload(texts = listOf("", "Am  C\nHello"), subject = "Page title"))
        val (files, isNewSongFromText) = queued.single()
        assertEquals("page_title.txt", files.single().name)
        assertTrue(isNewSongFromText)
    }

    @Test
    fun `a share's subject is queued as the suggestion`() = runTest {
        val controller = controller()
        val subject = "Riverlight Chords by The Invented Band @ Ultimate-Guitar.Com"
        controller.importSharedText(SharedTextPayload(texts = listOf("Am  C\nHello"), subject = subject))
        controller.newSongFromText("Am  C\nHello")
        assertEquals(listOf(SharedTextTitle(title = "Riverlight", artist = "The Invented Band"), null), suggestions)
    }

    @Test
    fun `several shared texts are one ordinary import of a file each`() = runTest {
        controller().importSharedText(SharedTextPayload(texts = listOf("Am\nOne", "C\nTwo"), subject = null))
        val (files, isNewSongFromText) = queued.single()
        assertEquals(listOf("one.txt", "two.txt"), files.map { it.name })
        assertFalse(isNewSongFromText)
    }

    @Test
    fun `a shared page address is answered with a sentence rather than imported`() = runTest {
        val controller = controller()
        controller.importSharedText(SharedTextPayload(texts = listOf("https://example.com/song"), subject = "A page"))
        controller.importSharedText(SharedTextPayload(texts = listOf("https://example.com/a", "\nhttps://example.com/b"), subject = null))
        assertEquals(listOf(Message.SharedLinkNotImported, Message.SharedLinkNotImported), messages)
        assertTrue(queued.isEmpty())
    }

    @Test
    fun `a shared page title beside its address is answered as a link, as is the same text pasted`() = runTest {
        val controller = controller()
        controller.importSharedText(SharedTextPayload(texts = listOf("Wonderwall – Oasis\nhttps://example.com/w"), subject = null))
        controller.newSongFromText("Wonderwall – Oasis\nhttps://example.com/w")
        assertEquals(listOf(Message.SharedLinkNotImported, Message.PastedOnlyLinks), messages)
        assertTrue(queued.isEmpty())
    }

    @Test
    fun `a share answered with a sentence has settled at once`() = runTest {
        val settled = controller().importSharedText(SharedTextPayload(texts = listOf("https://example.com/song"), subject = null))
        assertTrue(settled.isCompleted)
    }

    @Test
    fun `a share of one song settles when its import does`() = runTest {
        val settled = controller().importSharedText(SharedTextPayload(texts = listOf("Am  C\nHello"), subject = null))
        assertSame(importSettled, settled)
    }

    @Test
    fun `an empty share still reaches the import, which reports it as skipped`() = runTest {
        controller().importSharedText(SharedTextPayload(texts = listOf(" "), subject = null))
        assertTrue(queued.single().first.single().bytes.decodeToString().isBlank())
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `the clipboard's answers each become what they say`() = runTest {
        val controller = controller()
        controller.newSongFromClipboard { ClipboardText.None }
        controller.newSongFromClipboard { ClipboardText.Refused(Res.string.paste_refused_web) }
        controller.newSongFromClipboard { error("The clipboard is busy.") }
        controller.newSongFromClipboard { ClipboardText.Text("Am  C\nHello") }
        runCurrent()
        assertEquals(listOf(Message.ClipboardEmpty, Message.ClipboardRefused(Res.string.paste_refused_web), Message.ClipboardEmpty), messages)
        assertEquals(1, queued.size)
    }

    @Test
    fun `the clipboard is not read while an import runs`() = runTest {
        isImporting.value = true
        var isRead = false
        controller().newSongFromClipboard { ClipboardText.None.also { isRead = true } }
        runCurrent()
        assertFalse(isRead)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `a paste is answered only on the song list with nothing over it, outside read only mode`() = runTest {
        val controller = controller()
        assertTrue(controller.isNewSongPasteAvailable)
        dialogHost.showDialog(DialogType.NewSong)
        assertFalse(controller.isNewSongPasteAvailable)
        dialogHost.dismissDialog()
        backStack += CampfireDestination.Setlists
        assertFalse(controller.isNewSongPasteAvailable)
        backStack.removeAt(backStack.lastIndex)
        isPerformanceModeEnabled.value = true
        assertFalse(controller.isNewSongPasteAvailable)
    }
}
