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

import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.sync.implementation.Device
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncProvider
import com.pandulapeter.campfire.data.sync.implementation.GIG
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.song
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shared-copy step after a real engine's run: a copy is built only when
 * its inputs or the build changed, and uploaded only when its bytes did.
 */
class SharedCopySyncTest {

    private val cloud = FakeSyncProvider()

    @Test
    fun `a run with nothing changed reads no song, builds no copy, and writes no registry`() = runTest {
        val device = sharedDevice()
        val packs = device.sharedContents.packCount
        val registry = cloud.documents.getValue(SharedLinksFormat.NAME).second
        val downloads = cloud.documentDownloadCount
        val reads = mutableListOf<SyncKey>()
        device.library.onRead = { reads += it }

        device.sync(cloud)

        // The run's own pass hashes every file once; the step reads none of the songs again.
        assertEquals(listOf(song(1), song(2)), reads.filter { it != GIG }.sortedBy { it.name })
        assertEquals(packs, device.sharedContents.packCount)
        assertEquals(registry, cloud.documents.getValue(SharedLinksFormat.NAME).second)
        assertEquals(downloads, cloud.documentDownloadCount)
    }

    @Test
    fun `the setlist, one of its songs, and a song arriving that it names each rebuild the copy`() = runTest {
        val device = sharedDevice()

        device.library.files[GIG] = setlistDocument("Gig", "song_2.cho", "song_1.cho", "song_3.cho")
        device.sync(cloud)
        assertTrue(cloud.copyOf(SETLIST.shareId).indexOf("[song_2.cho]") < cloud.copyOf(SETLIST.shareId).indexOf("[song_1.cho]"))

        device.library.files[song(1)] = "One, edited".encodeToByteArray()
        device.sync(cloud)
        assertTrue("One, edited" in cloud.copyOf(SETLIST.shareId))

        device.library.files[song(3)] = "Three".encodeToByteArray()
        device.sync(cloud)
        assertTrue("[song_3.cho]\nThree" in cloud.copyOf(SETLIST.shareId))
        assertEquals(SharedLinksEntry.STATE_OK, cloud.entryOf(SETLIST.shareId).state)
    }

    @Test
    fun `a newer build rebuilds once and uploads nothing for the same bytes`() = runTest {
        val device = sharedDevice()
        val revision = cloud.sharedFiles.getValue(SETLIST.shareId).second
        val packs = device.sharedContents.packCount

        device.buildNumber = 2
        device.sync(cloud)
        device.sync(cloud)

        assertEquals(packs + 1, device.sharedContents.packCount)
        assertEquals(revision, cloud.sharedFiles.getValue(SETLIST.shareId).second)
        assertEquals(2, cloud.entryOf(SETLIST.shareId).built?.build)
    }

    @Test
    fun `devices on different builds leave each other's copies alone and settle after one run each`() = runTest {
        val newer = Device(library()).apply { buildNumber = 2 }
        val older = Device()
        cloud.share(SETLIST)
        newer.sync(cloud)
        older.sync(cloud)
        assertEquals(0, older.sharedContents.packCount)

        older.library.files[song(1)] = "Edited on the older one".encodeToByteArray()
        older.sync(cloud)
        val revision = cloud.sharedFiles.getValue(SETLIST.shareId).second
        assertTrue("Edited on the older one" in cloud.copyOf(SETLIST.shareId))
        val newerPacks = newer.sharedContents.packCount
        newer.sync(cloud)
        assertEquals(newerPacks + 1, newer.sharedContents.packCount)
        assertEquals(revision, cloud.sharedFiles.getValue(SETLIST.shareId).second)

        val packs = newer.sharedContents.packCount to older.sharedContents.packCount
        val registry = cloud.documents.getValue(SharedLinksFormat.NAME).second
        older.sync(cloud)
        newer.sync(cloud)
        assertEquals(packs, newer.sharedContents.packCount to older.sharedContents.packCount)
        assertEquals(registry, cloud.documents.getValue(SharedLinksFormat.NAME).second)
    }

    @Test
    fun `a copy replaced in the folder is built and uploaded again`() = runTest {
        val device = sharedDevice()
        val copy = cloud.copyOf(SETLIST.shareId)

        cloud.sharedFiles[SETLIST.shareId] = "By hand".encodeToByteArray() to "s-hand"
        device.sync(cloud)

        assertEquals(copy, cloud.copyOf(SETLIST.shareId))
    }

    @Test
    fun `a copy too large to share keeps the last one that fit and is built again only once an input changes`() = runTest {
        val device = sharedDevice()
        val copy = cloud.copyOf(SETLIST.shareId)
        device.sharedContents.isOversized = true

        device.library.files[song(1)] = "One, edited".encodeToByteArray()
        device.sync(cloud)
        assertEquals(SharedLinksEntry.STATE_TOO_LARGE, cloud.entryOf(SETLIST.shareId).state)
        assertEquals(copy, cloud.copyOf(SETLIST.shareId))
        val packs = device.sharedContents.packCount
        device.sync(cloud)
        assertEquals(packs, device.sharedContents.packCount)

        device.sharedContents.isOversized = false
        device.library.files[song(2)] = "Two, edited".encodeToByteArray()
        device.sync(cloud)
        assertEquals(SharedLinksEntry.STATE_OK, cloud.entryOf(SETLIST.shareId).state)
        assertTrue("One, edited" in cloud.copyOf(SETLIST.shareId))
    }

    @Test
    fun `a share never built is built and uploaded only where its bytes differ`() = runTest {
        val device = Device(library())
        val packed = FakeSharedCopyContents().pack(
            linkedMapOf(GIG.name to library().getValue(GIG), "song_1.cho" to ONE, "song_2.cho" to TWO),
        )
        cloud.share(SETLIST, copy = packed)

        device.sync(cloud)

        assertEquals("s-${SETLIST.shareId}", cloud.sharedFiles.getValue(SETLIST.shareId).second)
        assertEquals(cloud.contentHashOf(packed), cloud.entryOf(SETLIST.shareId).built?.hash)
    }

    @Test
    fun `nothing shared and nothing journaled asks for nothing`() = runTest {
        val device = Device(library())

        device.sync(cloud)

        assertEquals(0, cloud.documentDownloadCount)
        assertEquals(0, device.sharedContents.packCount)
    }

    /** A device with [library] whose setlist is shared, after the run that built its first copy. */
    private suspend fun sharedDevice() = Device(library()).also {
        cloud.share(SETLIST)
        it.sync(cloud)
    }

    private fun library() = mapOf(
        GIG to setlistDocument("Gig", "song_1.cho", "song_2.cho", "song_3.cho"),
        song(1) to ONE,
        song(2) to TWO,
    )

    private companion object {
        val SETLIST = sharedEntry(shareId = "setlistshare0001", kind = SharedLinkKind.SETLIST, fileName = GIG.name, title = "Gig")
        val ONE = "One".encodeToByteArray()
        val TWO = "Two".encodeToByteArray()
    }
}
