/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.pictures

import com.pandulapeter.campfire.data.model.image.ImageSize
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.importing.StagedFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportedSongImagesTest {

    private val staging = mutableMapOf<String, ByteArray>()
    private val library = LibraryPictures(mapOf(KEPT to picture(width = 3, height = 4)))

    @Test
    fun `a staged picture is sized and shown from the staging folder as one in memory is`() = runTest {
        val staged = stage(picture(width = 60, height = 40))
        val images = images(STAGED to IncomingContent.Staged(staged), IN_MEMORY to IncomingContent.Bytes(picture(width = 20, height = 10)))

        images.loadSizes(setOf(STAGED, IN_MEMORY, KEPT))

        assertEquals(
            mapOf(
                STAGED to SongImageSize.Known(60, 40),
                IN_MEMORY to SongImageSize.Known(20, 10),
                KEPT to SongImageSize.Known(3, 4),
            ),
            images.sizes.value,
        )
        assertContentEquals(picture(width = 60, height = 40), images.loadImage(STAGED))
        assertContentEquals(picture(width = 3, height = 4), images.loadImage(KEPT))
    }

    @Test
    fun `a staged picture that is gone is missing rather than failing the preview`() = runTest {
        val staged = stage(picture(width = 60, height = 40))
        staging.clear()
        val images = images(STAGED to IncomingContent.Staged(staged))

        images.loadSizes(setOf(STAGED))

        assertEquals(mapOf(STAGED to SongImageSize.Missing), images.sizes.value)
        assertNull(images.loadImage(STAGED))
    }

    private fun stage(bytes: ByteArray) = StagedFile(name = "session-1-${staging.size}.png", size = bytes.size.toLong(), sha256Hex = null)
        .also { staging[it.name] = bytes }

    private fun images(vararg pictures: Pair<String, IncomingContent>) = ImportedSongImages(
        pictures = pictures.toMap(),
        library = library,
        readSize = ::sizeOf,
        readStaged = { file -> staging[file.name] },
    )

    /** The library's pictures in memory, sized by [sizeOf]. */
    private class LibraryPictures(private val pictures: Map<String, ByteArray>) : SongImageLoader {
        private val _sizes = MutableStateFlow(emptyMap<String, SongImageSize>())
        override val sizes = _sizes.asStateFlow()
        override val cacheKey = "library"

        override suspend fun loadSizes(names: Set<String>) = _sizes.update { sizes ->
            sizes + names.associateWith { name -> pictures[name]?.let(::sizeOf)?.let { SongImageSize.Known(it.width, it.height) } ?: SongImageSize.Missing }
        }

        override suspend fun loadImage(name: String) = pictures[name]
    }

    private companion object {
        const val STAGED = "aaaaaaaaaaaaaaaa.png"
        const val IN_MEMORY = "bbbbbbbbbbbbbbbb.png"
        const val KEPT = "cccccccccccccccc.png"

        /** Not a real picture: its two bytes are its size, which [sizeOf] reads back. */
        fun picture(width: Int, height: Int) = byteArrayOf(width.toByte(), height.toByte())

        fun sizeOf(bytes: ByteArray) = ImageSize(width = bytes[0].toInt(), height = bytes[1].toInt())
    }
}
