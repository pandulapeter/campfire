/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.export

import com.pandulapeter.campfire.domain.implementation.FakeArchiveRepository
import com.pandulapeter.campfire.domain.implementation.FakeImageRepository
import com.pandulapeter.campfire.domain.implementation.FakeExportFileRepository
import com.pandulapeter.campfire.domain.implementation.SongContentRepositoryStub
import com.pandulapeter.campfire.domain.implementation.SongRepositoryStub
import com.pandulapeter.campfire.data.model.library.ExportedContent
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.model.song.SongContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A song leaves under the name its own header gives it, which is the name the import brings it back under. */
class ExportSongsUseCaseImplTest {

    private val archive = FakeArchiveRepository()
    private val images = FakeImageRepository()

    @Test
    fun `a single song is named by its header`() = runTest {
        val exported = useCase(mapOf("old_title" to "new_title.cho"), "old_title.cho" to "{title: New title}\n").invoke(listOf("old_title.cho"), isArchive = false, isDated = true, rewrite = asStored)

        assertEquals("new_title.cho", assertNotNull(exported).name)
    }

    @Test
    fun `the stored extension is kept`() = runTest {
        val exported = useCase(mapOf("song" to "song.cho"), "song.crd" to "{title: Song}\n").invoke(listOf("song.crd"), isArchive = false, isDated = true, rewrite = asStored)

        assertEquals("song.crd", assertNotNull(exported).name)
    }

    @Test
    fun `several songs keep their library names inside the archive`() = runTest {
        val exported = useCase(emptyMap(), "a.cho" to "{title: A}\n", "b_2.cho" to "{title: B}\n").invoke(listOf("a.cho", "b_2.cho"), isArchive = false, isDated = true, rewrite = asStored)

        assertEquals("campfire_songs.zip", assertNotNull(exported).name)
        assertEquals(setOf("a.cho", "b_2.cho"), archive.packed.keys)
    }

    @Test
    fun `an archive of songs knows what an import unpacks it to`() = runTest {
        val exported = useCase(emptyMap(), "a.cho" to "{title: A}\n", "b.cho" to "{title: Bee}\n").invoke(listOf("a.cho", "b.cho"), isArchive = true, isDated = true, rewrite = asStored)

        assertEquals(archive.packed.values.sumOf { it.size.toLong() }, assertNotNull(exported).unpackedSize)
    }

    @Test
    fun `songs asked for as an archive are zipped even when one is left`() = runTest {
        val exported = useCase(emptyMap(), "a.cho" to "{title: A}\n").invoke(listOf("a.cho", "gone.cho"), isArchive = true, isDated = true, rewrite = asStored)

        assertEquals("campfire_songs.zip", assertNotNull(exported).name)
        assertEquals(setOf("a.cho"), archive.packed.keys)
    }

    @Test
    fun `each song goes out as the rewrite answers it, whether alone or zipped`() = runTest {
        val rewrite = { fileName: String, text: String -> if (fileName == "a.cho") "$text{capo: 2}\n" else text }
        val single = useCase(mapOf("a" to "a.cho"), "a.cho" to "{title: A}\n").invoke(listOf("a.cho"), isArchive = false, isDated = true, rewrite = rewrite)
        assertEquals("{title: A}\n{capo: 2}\n", (assertNotNull(single).content as ExportedContent.Bytes).bytes.decodeToString())

        useCase(emptyMap(), "a.cho" to "{title: A}\n", "b.cho" to "{title: B}\n").invoke(listOf("a.cho", "b.cho"), isArchive = true, isDated = true, rewrite = rewrite)
        assertEquals("{title: A}\n{capo: 2}\n", archive.packed.getValue("a.cho").decodeToString())
        assertEquals("{title: B}\n", archive.packed.getValue("b.cho").decodeToString())
    }

    @Test
    fun `a song showing a picture the library holds leaves as a zip named after it, holding the song and the picture`() = runTest {
        val picture = FakeImageRepository.picture(1)
        val name = images.store(picture)
        val text = "{title: A}\n{image: src=$name}\n{image: src=gone.png}\n"

        val exported = assertNotNull(useCase(mapOf("a" to "a.cho"), "a.cho" to text).invoke(listOf("a.cho"), isArchive = false, isDated = true, rewrite = asStored))

        assertEquals("a.zip", exported.name)
        assertEquals(ExportedFile.ZIP_MIME_TYPE, exported.mimeType)
        assertEquals(listOf("a.cho", "images/$name"), archive.packed.keys.toList())
        assertEquals(text, archive.packed.getValue("a.cho").decodeToString())
        assertEquals(picture.size.toLong(), exported.unpackedImageSize)
    }

    @Test
    fun `a song naming only pictures the library lacks stays a lone file`() = runTest {
        images.store(FakeImageRepository.picture(1))
        val text = "{title: A}\n{image: src=gone.png}\n{image: src=0123456789abcdef.png}\n"

        val exported = assertNotNull(useCase(mapOf("a" to "a.cho"), "a.cho" to text).invoke(listOf("a.cho"), isArchive = false, isDated = true, rewrite = asStored))

        assertEquals("a.cho", exported.name)
        assertEquals(ExportedFile.TEXT_MIME_TYPE, exported.mimeType)
        assertEquals(text, (exported.content as ExportedContent.Bytes).bytes.decodeToString())
    }

    @Test
    fun `songs zipped together carry each picture once, after the songs and in the order of their names`() = runTest {
        val names = listOf(images.store(FakeImageRepository.picture(1)), images.store(FakeImageRepository.picture(2))).sorted()
        val unused = images.store(FakeImageRepository.picture(3))

        useCase(
            emptyMap(),
            "a.cho" to "{title: A}\n{image: src=${names[1]}}\n{image: src=${names[0]}}",
            "b.cho" to "{title: B}\n{image: src=${names[1]}}",
        ).invoke(listOf("a.cho", "b.cho"), isArchive = true, isDated = false, rewrite = asStored)

        assertEquals(listOf("a.cho", "b.cho") + names.map { "images/$it" }, archive.packed.keys.toList())
        assertEquals(false, "images/$unused" in archive.packed)
    }

    private val asStored = { _: String, text: String -> text }

    /** @param names What the import would name a song, by the fallback title it is asked with. */
    private fun useCase(names: Map<String, String>, vararg files: Pair<String, String>) = ExportSongsUseCaseImpl(
        songRepository = object : SongRepositoryStub() {
            override fun importFileName(fallbackTitle: String, text: String) = names.getValue(fallbackTitle)
        },
        songContentRepository = object : SongContentRepositoryStub() {
            override suspend fun loadSongContent(fileName: String, useCache: Boolean) = files.toMap()[fileName]?.let { SongContent(fileName = fileName, text = it) }
        },
        archiveRepository = archive,
        exportFileRepository = FakeExportFileRepository(),
        imageRepository = images,
    )
}
