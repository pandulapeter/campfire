/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.library.LibraryCollections
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.sync.implementation.FakeLibraryFileLocalSource
import com.pandulapeter.campfire.data.sync.implementation.GIG
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.song
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SharedCopyBuilderTest {

    private val contents = FakeSharedCopyContents()

    @Test
    fun `a setlist naming a song in another case is built under the setlist's spelling by the listing and the index alike`() = runTest {
        val library = FakeLibraryFileLocalSource(mapOf(GIG to setlistDocument("Gig", "Song.cho"), song("song") to "Song".encodeToByteArray()))

        val (listed, indexed) = buildBoth(library, SharedLinkKind.SETLIST, GIG, songNames = listOf("Song.cho"))

        assertEquals("[gig.setlist.json]\nGig\nSong.cho\n[Song.cho]\nSong", listed.decodeToString())
        assertContentEquals(listed, indexed)
    }

    @Test
    fun `an attachment past the budget is left out of both and its line taken out of the song`() = runTest {
        val sheet = SyncKey(kind = LibraryCollections.FILES, name = "fedcba9876543210.pdf")
        val video = SyncKey(kind = LibraryCollections.FILES, name = "0123456789abcdef.mp4")
        val text = "Song\n{meta: attachment 0123456789abcdef.mp4 Video}\n{meta: attachment fedcba9876543210.pdf Sheet}"
        val library = FakeLibraryFileLocalSource(
            mapOf(
                song("song") to text.encodeToByteArray(),
                sheet to "pdf".encodeToByteArray(),
                video to ByteArray((ImportLimits.MAX_SHARED_ATTACHMENTS_SIZE + 1).toInt()),
            ),
        )

        val (listed, indexed) = buildBoth(library, SharedLinkKind.SONG, song("song"), songNames = null)

        assertEquals("[song.cho]\nSong\n{meta: attachment fedcba9876543210.pdf Sheet}\n[images/fedcba9876543210.pdf]\npdf", listed.decodeToString())
        assertContentEquals(listed, indexed)
    }

    /** The copy of [item] as a share builds it, by the library's listing, and as a run does, by its index. */
    private suspend fun buildBoth(library: FakeLibraryFileLocalSource, kind: SharedLinkKind, item: SyncKey, songNames: List<String>?): Pair<ByteArray, ByteArray> {
        val builder = SharedCopyBuilder(library, contents)
        val index = library.files.mapValues { (_, bytes) -> SyncIndexEntry(localHash = localContentHash(bytes), remoteRevision = "r") }
        val build: suspend (SharedCopyNames) -> SharedCopyBuilt = { names ->
            builder.build(kind = kind, fileName = item.name, itemBytes = library.files.getValue(item), songNames = songNames, names = names)
        }
        val listed = build(ListedNames(library.loadLibraryFiles()))
        val indexed = build(IndexedNames(index))
        assertEquals(listed.leftOut, indexed.leftOut)
        assertEquals(listed.images, indexed.images)
        return assertNotNull(listed.copy) to assertNotNull(indexed.copy)
    }
}
