/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.images

import com.pandulapeter.campfire.chordpro.model.ChordProAttachment
import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.data.repository.api.LibraryImageWriter
import com.pandulapeter.campfire.domain.api.images.SaveSongAttachmentsResult
import com.pandulapeter.campfire.domain.implementation.ImageRepositoryStub
import com.pandulapeter.campfire.domain.implementation.SongRepositoryStub
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SaveSongAttachmentsUseCaseImplTest {

    private class Library(private val renamed: Map<String, String>) : ImageRepositoryStub() {
        var isKept = false
        val moved = mutableListOf<Set<String>>()

        override suspend fun <T> withImagesKept(block: suspend (LibraryImageWriter) -> T): T {
            isKept = true
            return block(object : LibraryImageWriter {
                override val heldNames: Set<String> = emptySet()

                override suspend fun addImage(content: IncomingContent): ImageIntake = error("Not expected.")
                override suspend fun addAttachment(content: IncomingContent, extension: String): ImageIntake = error("Not expected.")
                override suspend fun moveDraftImages(names: Set<String>) = renamed.also { moved += names }
            }).also { isKept = false }
        }
    }

    private class Songs(private val fileText: String, private val library: Library) : SongRepositoryStub() {
        var written: SongContent? = null
        var savedWhileKept: Boolean? = null

        override suspend fun saveSong(content: SongContent, expectedText: String?): Boolean {
            savedWhileKept = library.isKept
            if (expectedText != fileText) return false
            written = content
            return true
        }
    }

    private val text = "{title: Chart}\n{meta: attachment 0123456789abcdef.mp3 Take}\n\n[C]Line\n"

    @Test
    fun `picked files move into the library and the song names them, written while the files are kept`() = runTest {
        val library = Library(renamed = mapOf("3fa9c2e81b7d4f06.pdf" to "3fa9c2e81b7d4f067.pdf"))
        val songs = Songs(text, library)
        val attachments = listOf(ChordProAttachment("3fa9c2e81b7d4f06.pdf", "Sheet music.pdf"), ChordProAttachment("0123456789abcdef.mp3", "Take"))

        val result = SaveSongAttachmentsUseCaseImpl(library, songs)("chart.cho", text, attachments)

        val expected = "{title: Chart}\n{meta: attachment 3fa9c2e81b7d4f067.pdf Sheet music.pdf}\n" +
            "{meta: attachment 0123456789abcdef.mp3 Take}\n\n[C]Line\n"
        assertEquals(SaveSongAttachmentsResult.Saved(expected), result)
        assertEquals(SongContent(fileName = "chart.cho", text = expected), songs.written)
        assertEquals(listOf(setOf("3fa9c2e81b7d4f06.pdf", "0123456789abcdef.mp3")), library.moved)
        assertEquals(true, songs.savedWhileKept)
    }

    @Test
    fun `a file changed since it was read is left alone`() = runTest {
        val library = Library(renamed = emptyMap())
        val songs = Songs("$text\nMore", library)

        val result = SaveSongAttachmentsUseCaseImpl(library, songs)("chart.cho", text, emptyList())

        assertEquals(SaveSongAttachmentsResult.SongChanged, result)
        assertNull(songs.written)
    }
}
