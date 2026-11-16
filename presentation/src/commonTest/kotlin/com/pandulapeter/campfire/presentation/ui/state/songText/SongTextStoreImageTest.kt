/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songText

import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SongTextStoreImageTest {

    @Test
    fun `a text read is handed on for its pictures' sizes before it is held`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to TEXT))
        val store = files.songTextStore(backgroundScope, MessageSink(backgroundScope))
        store.loadSongContent(FILE)
        runCurrent()
        assertEquals(listOf(TEXT), files.textsReadForImages)
        assertEquals(TEXT, store.songTexts.value[FILE])
    }

    @Test
    fun `a picture picked for a missing one is stored under the song, whose held text follows`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to TEXT))
        val store = files.songTextStore(backgroundScope, MessageSink(backgroundScope))
        store.addMissingImage(FILE, missingName = "photo.jpg") { listOf(ImportedFile("photo.jpg", byteArrayOf(1, 2))) }
        runCurrent()
        val expected = "{title: A}\n{image: src=${FakeSongFiles.STORED_IMAGE_NAME}}\n"
        assertEquals(expected, files.files[FILE])
        assertEquals(expected, store.songTexts.value[FILE])
        assertEquals(expected, files.textsReadForImages.last())
    }

    @Test
    fun `a refused picture is said why, and nothing is written`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to TEXT))
        val sink = MessageSink(backgroundScope)
        val store = files.songTextStore(backgroundScope, sink)
        store.addMissingImage(FILE, missingName = "photo.jpg") { listOf(ImportedFile("notes.txt", ByteArray(0))) }
        store.addMissingImage(FILE, missingName = "photo.jpg") { listOf(ImportedFile.unread("huge.jpg", isTooLarge = true)) }
        runCurrent()
        assertEquals(emptyList(), files.writes)
        assertEquals(
            listOf<Message>(Message.ImageNotAdded(ImageRefusal.NOT_AN_IMAGE), Message.ImageNotAdded(ImageRefusal.TOO_LARGE)),
            sink.messageQueue.value.map { it.value },
        )
    }

    @Test
    fun `picking nothing does nothing`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to TEXT))
        val sink = MessageSink(backgroundScope)
        val store = files.songTextStore(backgroundScope, sink)
        store.addMissingImage(FILE, missingName = "photo.jpg") { emptyList() }
        runCurrent()
        assertEquals(emptyList(), files.writes)
        assertEquals(emptyList(), sink.messageQueue.value)
    }

    private companion object {
        const val FILE = "song.cho"
        const val TEXT = "{title: A}\n{image: src=photo.jpg}\n"
    }
}
