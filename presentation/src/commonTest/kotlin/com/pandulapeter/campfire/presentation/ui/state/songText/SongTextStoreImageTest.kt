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
import com.pandulapeter.campfire.domain.api.images.PictureDownload
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.pictures.MissingImagePick
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `a picture picked for a missing one is stored under the song - whose held text follows`() = runTest {
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
    fun `a missing picture's link is downloaded and stored under the song in its place`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to LINKED_TEXT)).apply { downloads[LINK] = PictureDownload.Picture(byteArrayOf(1, 2)) }
        val store = files.songTextStore(backgroundScope, MessageSink(backgroundScope))
        store.downloadMissingImage(FILE, missingName = LINK)
        runCurrent()
        val expected = "{title: A}\n{image: src=${FakeSongFiles.STORED_IMAGE_NAME}}\n"
        assertEquals(expected, files.files[FILE])
        assertEquals(expected, store.songTexts.value[FILE])
    }

    @Test
    fun `a missing picture's link that brings no picture is said why and nothing is written`() = runTest {
        val outcomes = listOf(
            PictureDownload.Unreachable to Message.PictureLinkUnreachable,
            PictureDownload.Missing to Message.PictureLinkMissing,
            PictureDownload.NotAPicture to Message.PictureLinkNotPicture,
            PictureDownload.TooLarge to Message.ImageTooLargeToRead,
        )
        outcomes.forEach { (download, message) ->
            val files = FakeSongFiles(mapOf(FILE to LINKED_TEXT)).apply { downloads[LINK] = download }
            val sink = MessageSink(backgroundScope)
            files.songTextStore(backgroundScope, sink).downloadMissingImage(FILE, missingName = LINK)
            runCurrent()
            assertEquals(emptyList(), files.writes)
            assertEquals(listOf(message), sink.messageQueue.value.map { it.value })
        }
    }

    @Test
    fun `a picture whose write fails is said to have failed`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to TEXT)).apply { imageSaveFailure = IllegalStateException("disk") }
        val sink = MessageSink(backgroundScope)
        files.songTextStore(backgroundScope, sink).addMissingImage(FILE, missingName = "photo.jpg") { listOf(ImportedFile("photo.jpg", byteArrayOf(1, 2))) }
        runCurrent()
        assertEquals(TEXT, files.files[FILE])
        assertEquals(listOf<Message>(Message.OperationFailed), sink.messageQueue.value.map { it.value })
    }

    @Test
    fun `a refused picture is said why and nothing is written`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to TEXT))
        val sink = MessageSink(backgroundScope)
        val store = files.songTextStore(backgroundScope, sink)
        store.addMissingImage(FILE, missingName = "photo.jpg") { listOf(ImportedFile("notes.txt", ByteArray(0))) }
        store.addMissingImage(FILE, missingName = "photo.jpg") { listOf(ImportedFile.unread("huge.jpg", isTooLarge = true)) }
        runCurrent()
        assertEquals(emptyList(), files.writes)
        assertEquals(
            listOf<Message>(Message.ImageNotAdded(ImageRefusal.NOT_AN_IMAGE), Message.ImageTooLargeToRead),
            sink.messageQueue.value.map { it.value },
        )
    }

    @Test
    fun `a picture too large for a song is stored as a smaller copy which a snackbar says`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to TEXT))
        val sink = MessageSink(backgroundScope)
        val store = files.songTextStore(backgroundScope, sink)
        store.addMissingImage(FILE, missingName = "photo.jpg") { listOf(ImportedFile("photo.jpg", LARGE_PHOTO)) }
        runCurrent()
        assertEquals("{title: A}\n{image: src=${FakeSongFiles.STORED_IMAGE_NAME}}\n", files.files[FILE])
        assertEquals(listOf<Message>(Message.ImageShrunk), sink.messageQueue.value.map { it.value })
    }

    @Test
    fun `a picture too large for a song that cannot be shrunk is refused as too large and nothing is written`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to TEXT)).apply { reencode = { null } }
        val sink = MessageSink(backgroundScope)
        val store = files.songTextStore(backgroundScope, sink)
        store.addMissingImage(FILE, missingName = "photo.jpg") { listOf(ImportedFile("photo.jpg", LARGE_PHOTO)) }
        runCurrent()
        assertEquals(emptyList(), files.writes)
        assertEquals(listOf<Message>(Message.ImageNotAdded(ImageRefusal.TOO_LARGE)), sink.messageQueue.value.map { it.value })
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

    @Test
    fun `the missing picture a pick is for is in the saved state while its picker is up and gone once it answers`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to TEXT))
        val savedState = SavedStateHandle()
        val store = files.songTextStore(backgroundScope, MessageSink(backgroundScope), savedState)
        val answer = CompletableDeferred<List<ImportedFile>>()
        store.addMissingImage(FILE, missingName = "photo.jpg") { answer.await() }
        runCurrent()
        val restored = files.songTextStore(backgroundScope, MessageSink(backgroundScope), savedState)
        assertEquals(MissingImagePick(fileName = FILE, missingName = "photo.jpg"), restored.takePendingMissingImagePick())
        assertNull(restored.takePendingMissingImagePick())
        store.addMissingImage(FILE, missingName = "photo.jpg") { answer.await() }
        answer.complete(emptyList())
        runCurrent()
        assertNull(files.songTextStore(backgroundScope, MessageSink(backgroundScope), savedState).takePendingMissingImagePick())
    }

    @Test
    fun `a picture picked for a view model that is gone is stored for the missing picture it was picked for`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to TEXT))
        val sink = MessageSink(backgroundScope)
        val store = files.songTextStore(backgroundScope, sink)
        store.addOrphanedMissingImage(MissingImagePick(fileName = FILE, missingName = "photo.jpg"), listOf(ImportedFile("photo.jpg", LARGE_PHOTO)))
        runCurrent()
        assertEquals("{title: A}\n{image: src=${FakeSongFiles.STORED_IMAGE_NAME}}\n", files.files[FILE])
        assertEquals(listOf<Message>(Message.ImageShrunk), sink.messageQueue.value.map { it.value })
    }

    @Test
    fun `a picture picked for a missing picture the song no longer names is let go`() = runTest {
        val files = FakeSongFiles(mapOf(FILE to "{title: A}\n"))
        val sink = MessageSink(backgroundScope)
        val store = files.songTextStore(backgroundScope, sink)
        store.addOrphanedMissingImage(MissingImagePick(fileName = FILE, missingName = "photo.jpg"), listOf(ImportedFile("photo.jpg", byteArrayOf(1, 2))))
        store.addMissingImage(FILE, missingName = "photo.jpg") { listOf(ImportedFile("photo.jpg", byteArrayOf(1, 2))) }
        runCurrent()
        assertEquals(emptyList(), files.writes)
        assertEquals(emptyList(), sink.messageQueue.value)
    }

    private companion object {
        const val FILE = "song.cho"
        const val TEXT = "{title: A}\n{image: src=photo.jpg}\n"
        const val LINK = "https://example.com/chart.png"
        const val LINKED_TEXT = "{title: A}\n{image: src=$LINK}\n"

        /** Over [FakeSongFiles.MAX_IMAGE_BYTES], with a JPEG's signature. */
        val LARGE_PHOTO = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 5)
    }
}
