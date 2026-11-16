/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.image.ImageSize
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.data.repository.api.EditorDraftRepository
import com.pandulapeter.campfire.data.repository.implementation.base.testEnvironment
import com.pandulapeter.campfire.data.source.local.api.ImageShelf
import com.pandulapeter.campfire.data.source.remote.api.hashing.Sha256
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageRepositoryImplTest {

    private val local = FakeImageLocalSource()
    private val songs = MutableStateFlow<DataState<List<Song>>>(DataState.Idle(emptyList()))
    private var songFiles = emptySet<String>()

    @Test
    fun `a picture is named by its content and its format, whatever it arrived as`() = runTest {
        val repository = repository()

        val intake = repository.withImagesKept { it.addImage(PHOTO) }

        val name = assertAccepted(intake)
        assertEquals(Sha256.hashToHex(PHOTO).take(16) + ".png", name)
        assertTrue(LibraryFiles.isImageNamedFor(name, Sha256.hashToHex(PHOTO)))
        assertContentEquals(PHOTO, local.shelves.getValue(ImageShelf.LIBRARY)[name])
        assertEquals(ImageSize(width = 40, height = 30), repository.loadImageSize(name))
    }

    @Test
    fun `the same picture added twice is one file`() = runTest {
        val repository = repository()

        val first = repository.withImagesKept { it.addImage(PHOTO) }
        val second = repository.withImagesKept { it.addImage(PHOTO.copyOf()) }

        assertEquals(first, second)
        assertEquals(1, local.shelves.getValue(ImageShelf.LIBRARY).size)
    }

    @Test
    fun `a different picture that would take an existing name takes more digits`() = runTest {
        val repository = repository()
        val shortName = Sha256.hashToHex(PHOTO).take(16) + ".png"
        local.shelves.getValue(ImageShelf.LIBRARY)[shortName] = OTHER

        val name = assertAccepted(repository.withImagesKept { it.addImage(PHOTO) })

        assertEquals(Sha256.hashToHex(PHOTO).take(17) + ".png", name)
        assertContentEquals(OTHER, local.shelves.getValue(ImageShelf.LIBRARY)[shortName])
    }

    @Test
    fun `the name a picture would take is the one it is then stored under, a taken one included, and asking writes nothing`() = runTest {
        val repository = repository()
        local.shelves.getValue(ImageShelf.LIBRARY)[Sha256.hashToHex(PHOTO).take(16) + ".png"] = OTHER

        val planned = repository.libraryNameOf(PHOTO)
        val untouched = local.shelves.getValue(ImageShelf.LIBRARY).keys.toSet()
        val stored = repository.withImagesKept { it.addImage(PHOTO) }

        assertEquals(ImageIntake.Accepted(Sha256.hashToHex(PHOTO).take(17) + ".png"), planned)
        assertEquals(1, untouched.size)
        assertEquals(planned, stored)
        assertEquals(ImageIntake.Refused(ImageRefusal.NOT_AN_IMAGE), repository.libraryNameOf("{title: x}".encodeToByteArray()))
    }

    @Test
    fun `the library's picture names are listed sorted, never the draft's`() = runTest {
        val repository = repository()
        val stored = assertAccepted(repository.withImagesKept { it.addImage(PHOTO) })
        repository.addDraftImage(PHOTO.copyOf())
        val otherName = "0000000000000000.png"
        local.shelves.getValue(ImageShelf.LIBRARY)[otherName] = OTHER

        assertEquals(listOf(otherName, stored).sorted(), repository.loadLibraryImageNames())
    }

    @Test
    fun `what cannot be shown everywhere or is too large is refused and nothing is written`() = runTest {
        val repository = repository()
        val heic = byteArrayOf(0, 0, 0, 24) + "ftypheic".encodeToByteArray() + ByteArray(12)

        assertEquals(ImageIntake.Refused(ImageRefusal.UNSUPPORTED_FORMAT), repository.addDraftImage(heic))
        assertEquals(ImageIntake.Refused(ImageRefusal.NOT_AN_IMAGE), repository.addDraftImage("{title: x}".encodeToByteArray()))
        assertEquals(
            ImageIntake.Refused(ImageRefusal.TOO_LARGE),
            repository.addDraftImage(PHOTO + ByteArray(ImportLimits.MAX_IMAGE_FILE_SIZE.toInt())),
        )
        assertTrue(local.shelves.values.all { it.isEmpty() })
    }

    @Test
    fun `a draft picture waits outside the library until Save moves it`() = runTest {
        val repository = repository()

        val name = assertAccepted(repository.addDraftImage(PHOTO))
        assertTrue(local.shelves.getValue(ImageShelf.LIBRARY).isEmpty())
        assertContentEquals(PHOTO, repository.loadImage(name))
        val renamed = repository.withImagesKept { writer -> writer.moveDraftImages(setOf(name)).also { publish(song("a.cho", name)) } }
        repository.clearDraftImages()

        assertEquals(emptyMap(), renamed)
        assertContentEquals(PHOTO, local.shelves.getValue(ImageShelf.LIBRARY)[name])
        assertTrue(local.shelves.getValue(ImageShelf.DRAFT).isEmpty())
    }

    @Test
    fun `a picture no song names is deleted once the library has been read, and a named one is kept`() = runTest {
        val repository = repository()
        val kept = assertAccepted(repository.withImagesKept { it.addImage(PHOTO) })
        val dropped = assertAccepted(repository.withImagesKept { it.addImage(OTHER) })
        publish(song("a.cho", kept))
        runCurrent()

        assertEquals(setOf(kept), local.shelves.getValue(ImageShelf.LIBRARY).keys)
        assertTrue(dropped !in local.shelves.getValue(ImageShelf.LIBRARY))
    }

    @Test
    fun `nothing is pruned while a song file of the folder is missing from the list`() = runTest {
        repository()
        local.shelves.getValue(ImageShelf.LIBRARY)[NAME] = PHOTO
        publish(song("a.cho"))
        songFiles = setOf("a.cho", "unreadable.cho")
        runCurrent()

        assertTrue(NAME in local.shelves.getValue(ImageShelf.LIBRARY))
    }

    @Test
    fun `nothing is pruned while the list is still being read`() = runTest {
        repository()
        local.shelves.getValue(ImageShelf.LIBRARY)[NAME] = PHOTO
        songs.value = DataState.Loading(listOf(song("a.cho")))
        runCurrent()

        assertTrue(NAME in local.shelves.getValue(ImageShelf.LIBRARY))
    }

    @Test
    fun `a picture the editor's open text names is kept until the editor lets it go`() = runTest {
        val repository = repository()
        local.shelves.getValue(ImageShelf.LIBRARY)[NAME] = PHOTO
        repository.holdEditorImages(setOf(NAME))
        publish(song("a.cho"))
        runCurrent()
        assertTrue(NAME in local.shelves.getValue(ImageShelf.LIBRARY))

        repository.holdEditorImages(emptySet())
        runCurrent()
        assertTrue(NAME !in local.shelves.getValue(ImageShelf.LIBRARY))
    }

    @Test
    fun `on launch the draft keeps only the pictures its saved text names, which the library keeps too`() = runTest {
        val other = Sha256.hashToHex(OTHER).take(16) + ".png"
        local.shelves.getValue(ImageShelf.DRAFT)[NAME] = PHOTO
        local.shelves.getValue(ImageShelf.DRAFT)[other] = OTHER
        local.shelves.getValue(ImageShelf.LIBRARY)[other] = OTHER
        val draftText = "{title: Draft}\n{image: src=$NAME}\n{image: src=$other}"
        repository(draft = SongContent(fileName = "draft.cho", text = draftText))
        runCurrent()

        assertEquals(setOf(NAME, other), local.shelves.getValue(ImageShelf.DRAFT).keys)
        assertTrue(other in local.shelves.getValue(ImageShelf.LIBRARY))
    }

    @Test
    fun `on launch the draft's pictures no draft names are deleted`() = runTest {
        local.shelves.getValue(ImageShelf.DRAFT)[NAME] = PHOTO
        repository(draft = null)
        runCurrent()

        assertTrue(local.shelves.getValue(ImageShelf.DRAFT).isEmpty())
    }

    @Test
    fun `a song written inside a kept block keeps its new picture`() = runTest {
        val repository = repository()
        runCurrent()
        repository.withImagesKept { writer ->
            val name = assertAccepted(writer.addImage(PHOTO))
            // A prune asked for meanwhile waits for the block, and then sees the song.
            publish()
            runCurrent()
            publish(song("a.cho", name))
        }
        runCurrent()

        assertEquals(1, local.shelves.getValue(ImageShelf.LIBRARY).size)
    }

    private fun publish(vararg list: Song) {
        songFiles = list.mapTo(mutableSetOf()) { it.fileName }
        songs.value = DataState.Idle(list.toList())
    }

    private fun TestScope.repository(draft: SongContent? = null) = ImageRepositoryImpl(
        imageLocalSource = local,
        songRepository = RecordingSongRepository(songs = songs, songFileNames = { songFiles }),
        editorDraftRepository = object : EditorDraftRepository {
            override suspend fun loadEditorDraft() = draft

            override suspend fun saveEditorDraft(draft: SongContent?) = throw UnsupportedOperationException()
        },
        environment = testEnvironment(),
    )

    private fun assertAccepted(intake: ImageIntake) = (intake as? ImageIntake.Accepted)?.name ?: throw AssertionError("Refused: $intake")

    private companion object {

        val PHOTO = png(width = 40, height = 30)
        val OTHER = png(width = 10, height = 10)
        val NAME = Sha256.hashToHex(PHOTO).take(16) + ".png"

        fun song(fileName: String, vararg imageNames: String) = Song(
            fileName = fileName,
            title = fileName,
            artist = "",
            key = null,
            transpose = 0,
            tags = emptyList(),
            languages = emptyList(),
            coverArtUrl = null,
            hasChords = false,
            canUpdateFileName = false,
            lastModified = 0,
            size = 0,
            imageNames = imageNames.toSet(),
        )

        fun png(width: Int, height: Int): ByteArray {
            fun u32(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
            return byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + u32(13) + "IHDR".encodeToByteArray() +
                u32(width) + u32(height) + byteArrayOf(8, 2, 0, 0, 0) + u32(0) + u32(0) + "IEND".encodeToByteArray() + u32(0)
        }
    }
}
