/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.SongContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A song leaves under the name its own header gives it, which is the name the import brings it back under. */
class ExportSongsUseCaseImplTest {

    private val archive = FakeArchiveRepository()

    @Test
    fun `a single song is named by its header`() = runTest {
        val exported = useCase(mapOf("old_title" to "new_title.cho"), "old_title.cho" to "{title: New title}\n").invoke(listOf("old_title.cho"))

        assertEquals("new_title.cho", assertNotNull(exported).name)
    }

    @Test
    fun `the stored extension is kept`() = runTest {
        val exported = useCase(mapOf("song" to "song.cho"), "song.crd" to "{title: Song}\n").invoke(listOf("song.crd"))

        assertEquals("song.crd", assertNotNull(exported).name)
    }

    @Test
    fun `several songs keep their library names inside the archive`() = runTest {
        val exported = useCase(emptyMap(), "a.cho" to "{title: A}\n", "b_2.cho" to "{title: B}\n").invoke(listOf("a.cho", "b_2.cho"))

        assertEquals("campfire_songs.zip", assertNotNull(exported).name)
        assertEquals(setOf("a.cho", "b_2.cho"), archive.packed.keys)
    }

    /** @param names What the import would name a song, by the fallback title it is asked with. */
    private fun useCase(names: Map<String, String>, vararg files: Pair<String, String>) = ExportSongsUseCaseImpl(
        songRepository = object : SongRepositoryStub() {
            override fun importFileName(fallbackTitle: String, text: String) = names.getValue(fallbackTitle)
        },
        songContentRepository = object : SongContentRepositoryStub() {
            override suspend fun loadSongContent(fileName: String, useCache: Boolean) = files.toMap()[fileName]?.let { SongContent(fileName = fileName, text = it) }
        },
        archiveRepository = archive,
    )
}
