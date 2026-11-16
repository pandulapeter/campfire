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
import com.pandulapeter.campfire.data.repository.api.LibraryImageWriter
import com.pandulapeter.campfire.domain.implementation.ImageRepositoryStub
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SaveWithDraftImagesUseCaseImplTest {

    private class Library(private val renamed: Map<String, String> = emptyMap()) : ImageRepositoryStub() {
        var isKept = false
        val moved = mutableListOf<Set<String>>()

        override suspend fun <T> withImagesKept(block: suspend (LibraryImageWriter) -> T): T {
            isKept = true
            return try {
                block(object : LibraryImageWriter {
                    override val heldNames: Set<String> = emptySet()

                    override suspend fun addImage(bytes: ByteArray): ImageIntake = error("Not expected.")
                    override suspend fun moveDraftImages(names: Set<String>) = renamed.also { moved += names }
                })
            } finally {
                isKept = false
            }
        }
    }

    @Test
    fun `a text naming no picture is saved as it is, without holding the pruning`() = runTest {
        // The stub fails every call it does not expect, withImagesKept included.
        val text = "{title: Song}\n[G]Hello\n"
        val saved = SaveWithDraftImagesUseCaseImpl(ImageRepositoryStub())(text) { it }
        assertEquals(text, saved)
    }

    @Test
    fun `the pictures a text names are moved into the library and the song saved while they are kept`() = runTest {
        val library = Library()
        val text = "{title: Chart}\n{image: src=a.png}\n{image: b.jpg}\n"
        val savedWhileKept = SaveWithDraftImagesUseCaseImpl(library)(text) { saved -> saved to library.isKept }
        assertEquals(text to true, savedWhileKept)
        assertEquals(listOf(setOf("a.png", "b.jpg")), library.moved)
    }

    @Test
    fun `a picture that took another name in the library is saved under that name`() = runTest {
        val library = Library(renamed = mapOf("a.png" to "a0.png"))
        val saved = SaveWithDraftImagesUseCaseImpl(library)("{title: Chart}\n{image: src=a.png}\n{image: src=b.png}\n") { it }
        assertEquals("{title: Chart}\n{image: src=a0.png}\n{image: src=b.png}\n", saved)
    }
}
