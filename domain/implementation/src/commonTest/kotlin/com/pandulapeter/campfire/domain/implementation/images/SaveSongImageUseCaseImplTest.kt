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

import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.data.repository.api.LibraryImageWriter
import com.pandulapeter.campfire.domain.api.images.SaveSongImageResult
import com.pandulapeter.campfire.domain.implementation.ImageRepositoryStub
import com.pandulapeter.campfire.domain.implementation.SongRepositoryStub
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaveSongImageUseCaseImplTest {

    private class Library(private val intake: ImageIntake) : ImageRepositoryStub() {
        var isKept = false
        var savedWhileKept: Boolean? = null
        val stored = mutableListOf<ByteArray>()
        var onKept: () -> Unit = {}

        override suspend fun <T> withImagesKept(block: suspend (LibraryImageWriter) -> T): T {
            isKept = true
            return block(object : LibraryImageWriter {
                override val heldNames: Set<String> = emptySet()

                override suspend fun addImage(bytes: ByteArray) = intake.also { stored += bytes }
                override suspend fun addAttachment(bytes: ByteArray, extension: String): ImageIntake = error("Not expected.")
                override suspend fun moveDraftImages(names: Set<String>) = emptyMap<String, String>()
            }).also { isKept = false }
        }
    }

    private class Songs(private val fileText: String, private val library: () -> Library) : SongRepositoryStub() {
        var written: SongContent? = null

        override suspend fun saveSong(content: SongContent, expectedText: String?): Boolean {
            library().savedWhileKept = library().isKept
            if (expectedText != fileText) return false
            written = content
            return true
        }
    }

    private val text = "{title: Chart}\n{image: src=photo.heic}\n{image: src=other.png}\n{image: photo.heic}\n"

    @Test
    fun `a stored picture takes the missing name's place in every directive, written while pictures are kept`() = runTest {
        lateinit var library: Library
        library = Library(ImageIntake.Accepted("3f9a0c1d2e4b5a69.jpg"))
        val songs = Songs(text) { library }
        val result = SaveSongImageUseCaseImpl(library, songs)("chart.cho", text, "photo.heic", byteArrayOf(1, 2, 3))
        val expected = "{title: Chart}\n{image: src=3f9a0c1d2e4b5a69.jpg}\n{image: src=other.png}\n{image: 3f9a0c1d2e4b5a69.jpg}\n"
        assertEquals(SaveSongImageResult.Added(expected), result)
        assertEquals(SongContent(fileName = "chart.cho", text = expected), songs.written)
        assertEquals(true, library.savedWhileKept)
    }

    @Test
    fun `a refused picture writes nothing`() = runTest {
        lateinit var library: Library
        library = Library(ImageIntake.Refused(ImageRefusal.UNSUPPORTED_FORMAT))
        val songs = Songs(text) { library }
        val result = SaveSongImageUseCaseImpl(library, songs)("chart.cho", text, "photo.heic", byteArrayOf(1))
        assertEquals(SaveSongImageResult.Refused(ImageRefusal.UNSUPPORTED_FORMAT), result)
        assertNull(songs.written)
    }

    @Test
    fun `a file changed since it was read is left alone`() = runTest {
        lateinit var library: Library
        library = Library(ImageIntake.Accepted("3f9a0c1d2e4b5a69.jpg"))
        val songs = Songs("$text\nMore") { library }
        val result = SaveSongImageUseCaseImpl(library, songs)("chart.cho", text, "photo.heic", byteArrayOf(1))
        assertEquals(SaveSongImageResult.SongChanged, result)
        assertNull(songs.written)
        assertTrue(library.stored.isNotEmpty())
    }
}
