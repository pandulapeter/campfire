/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.sharing

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.model.share.CreateShareResult
import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.ShareLinks
import com.pandulapeter.campfire.data.model.share.ShareState
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.share.SharingFailure
import com.pandulapeter.campfire.domain.api.export.ExportSetlistUseCase
import com.pandulapeter.campfire.domain.api.export.ExportSongsUseCase
import com.pandulapeter.campfire.domain.implementation.SharedLinksRepositoryStub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CreateShareLinkUseCaseImplTest {

    @Test
    fun `an item already shared answers its link without building a copy`() = runTest {
        val existing = share(fileName = "Gig.setlist.json")
        val repository = FakeSharedLinksRepository(afterRefresh = listOf(existing))

        val result = useCase(repository, setlist = { _, _, _ -> error("Built a copy.") }).invoke(SharedLinkKind.SETLIST, "gig.setlist.json", "Gig")

        assertEquals(CreateShareResult.Shared(existing), result)
        assertNull(repository.shared)
    }

    @Test
    fun `a setlist is shared whole, as an undated copy`() = runTest {
        val repository = FakeSharedLinksRepository()
        var request: Triple<String, Set<String>?, Boolean>? = null

        useCase(repository, setlist = { name, songs, isDated -> zip(1).also { request = Triple(name, songs, isDated) } })
            .invoke(SharedLinkKind.SETLIST, "gig.setlist.json", "Gig")

        assertEquals(Triple("gig.setlist.json", null, false), request)
        assertContentEquals(zip(1).bytes, repository.shared)
    }

    @Test
    fun `a song is shared as an undated zip`() = runTest {
        val repository = FakeSharedLinksRepository()
        var request: Triple<List<String>, Boolean, Boolean>? = null

        useCase(repository, songs = { names, isArchive, isDated -> zip(1).also { request = Triple(names, isArchive, isDated) } })
            .invoke(SharedLinkKind.SONG, "song.cho", "Song")

        assertEquals(Triple(listOf("song.cho"), true, false), request)
    }

    @Test
    fun `a copy over the import limit is not shared`() = runTest {
        val repository = FakeSharedLinksRepository()

        val result = useCase(repository, songs = { _, _, _ -> zip(ImportLimits.MAX_IMPORT_SIZE.toInt() + 1) })
            .invoke(SharedLinkKind.SONG, "song.cho", "Song")

        assertEquals(CreateShareResult.TooLarge, result)
        assertNull(repository.shared)
    }

    @Test
    fun `an item no longer in the library is missing`() = runTest {
        val result = useCase(FakeSharedLinksRepository(), songs = { _, _, _ -> null }).invoke(SharedLinkKind.SONG, "gone.cho", "Gone")

        assertEquals(CreateShareResult.Missing, result)
    }

    @Test
    fun `a list that cannot be read stops the share before anything is built`() = runTest {
        val repository = FakeSharedLinksRepository(refreshFailure = SharingFailure.NETWORK)

        val result = useCase(repository, songs = { _, _, _ -> error("Built a copy.") }).invoke(SharedLinkKind.SONG, "song.cho", "Song")

        assertEquals(CreateShareResult.Failed(SharingFailure.NETWORK), result)
    }

    private fun useCase(
        repository: FakeSharedLinksRepository,
        setlist: (String, Set<String>?, Boolean) -> ExportedFile? = { _, _, _ -> error("Not a setlist.") },
        songs: (List<String>, Boolean, Boolean) -> ExportedFile? = { _, _, _ -> error("Not a song.") },
    ) = CreateShareLinkUseCaseImpl(
        sharedLinksRepository = repository,
        exportSetlist = object : ExportSetlistUseCase {
            override suspend fun invoke(setlistFileName: String, songFileNames: Set<String>?, isDated: Boolean) =
                setlist(setlistFileName, songFileNames, isDated)
        },
        exportSongs = object : ExportSongsUseCase {
            override suspend fun invoke(fileNames: List<String>, isArchive: Boolean, isDated: Boolean) = songs(fileNames, isArchive, isDated)
        },
    )

    private fun zip(size: Int) = ExportedFile(name = "copy.zip", mimeType = ExportedFile.ZIP_MIME_TYPE, bytes = ByteArray(size))

    private fun share(fileName: String) = Share(
        shareId = "aaaaaaaaaaaaaaaa",
        kind = SharedLinkKind.SETLIST,
        fileName = fileName,
        title = "Gig",
        link = ShareLinks.create(kind = SharedLinkKind.SETLIST, shareId = "aaaaaaaaaaaaaaaa", fileId = "f", rlkey = "k", title = "Gig")!!,
        sharedAt = null,
        state = ShareState.OK,
    )

    /** Shows [afterRefresh] once refreshed, and keeps the copy it was asked to share. */
    private class FakeSharedLinksRepository(
        private val afterRefresh: List<Share> = emptyList(),
        private val refreshFailure: SharingFailure? = null,
    ) : SharedLinksRepositoryStub() {

        override val shares = MutableStateFlow(emptyList<Share>())
        var shared: ByteArray? = null

        override suspend fun refresh() = refreshFailure.also { if (it == null) shares.value = afterRefresh }

        override suspend fun share(kind: SharedLinkKind, fileName: String, title: String, copy: ByteArray): CreateShareResult {
            shared = copy
            return CreateShareResult.Failed(SharingFailure.UNKNOWN)
        }
    }
}
