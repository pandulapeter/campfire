/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.sync.SyncDeletionDirection
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferences
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesDocument
import com.pandulapeter.campfire.data.sync.implementation.run.SyncEngine
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedLinksEntry
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedLinksJournal
import com.pandulapeter.campfire.data.sync.implementation.shared.copyOf
import com.pandulapeter.campfire.data.sync.implementation.shared.entryOf
import com.pandulapeter.campfire.data.sync.implementation.shared.mirrorOf
import com.pandulapeter.campfire.data.sync.implementation.shared.registry
import com.pandulapeter.campfire.data.sync.implementation.shared.setlistDocument
import com.pandulapeter.campfire.data.sync.implementation.shared.share
import com.pandulapeter.campfire.data.sync.implementation.shared.sharedEntry
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Two devices taking turns against one cloud folder, each run seeing what the other device's did, through the real
 * engine and the real preferences merge. Each test ends with another round on both devices that must change nothing.
 */
class TwoDeviceSyncTest {

    private val cloud = FakeSyncProvider()

    @Test
    fun `a song created on one device reaches the other and an edit made there comes back`() = runTest {
        val a = Device(shared())
        val b = Device()
        a.sync(cloud)
        b.sync(cloud)
        a.library.files[song(4)] = "New".encodeToByteArray()
        a.sync(cloud)
        b.sync(cloud)
        assertEquals("New", b.contents["song_4.cho"])
        b.library.files[song(4)] = "Edited on B".encodeToByteArray()
        b.sync(cloud)
        a.sync(cloud)

        val expected = sharedContents() + ("song_4.cho" to "Edited on B")
        assertEverywhere(expected, a, b)
        assertConverges(a, b)
    }

    @Test
    fun `a song deleted on one device is deleted on the other and stays deleted`() = runTest {
        val (a, b) = devicesInStep()
        a.library.files -= song(1)
        a.sync(cloud)
        b.sync(cloud)
        b.sync(cloud)
        a.sync(cloud)

        assertEverywhere(sharedContents() - "song_1.cho", a, b)
        assertConverges(a, b)
    }

    @Test
    fun `an edit on one device beats a deletion of the same song on the other`() = runTest {
        val (a, b) = devicesInStep()
        // Both ways round at once: the edit of the first song reaches the folder before its deletion does, and the
        // deletion of the second before its edit.
        a.library.files[song(1)] = "Edited on A".encodeToByteArray()
        b.library.files -= song(1)
        a.library.files -= song(2)
        b.library.files[song(2)] = "Edited on B".encodeToByteArray()
        a.sync(cloud)
        b.sync(cloud)
        a.sync(cloud)

        assertEverywhere(sharedContents() + ("song_1.cho" to "Edited on A") + ("song_2.cho" to "Edited on B"), a, b)
        assertConverges(a, b)
    }

    @Test
    fun `a song edited differently on both devices gets one conflict copy everywhere`() = runTest {
        val (a, b) = devicesInStep()
        a.library.files[song(1)] = "Edited on A".encodeToByteArray()
        b.library.files[song(1)] = "Edited on B".encodeToByteArray()
        a.sync(cloud)
        b.sync(cloud)
        a.sync(cloud)

        // The device that found the other's edit in the folder keeps its own under the name and the other's as the copy.
        val expected = sharedContents() + ("song_1.cho" to "Edited on B") + ("song_1 (2).cho" to "Edited on A")
        assertEverywhere(expected, a, b)
        assertConverges(a, b)
    }

    @Test
    fun `a conflict settled over a failing network leaves one copy on both devices`() = runTest {
        val (a, b) = devicesInStep()
        a.library.files[song(1)] = "Edited on A".encodeToByteArray()
        b.library.files[song(1)] = "Edited on B".encodeToByteArray()
        a.sync(cloud)
        cloud.onUpload = { key -> if (key == song(1)) throw SyncNetworkException("Offline") }
        repeat(2) {
            assertFailsWith<SyncNetworkException> { b.syncResult(cloud, SyncDeletionPolicy.ASK) }
        }
        cloud.onUpload = {}
        b.sync(cloud)
        a.sync(cloud)

        val expected = sharedContents() + ("song_1.cho" to "Edited on B") + ("song_1 (2).cho" to "Edited on A")
        assertEverywhere(expected, a, b)
        assertConverges(a, b)
    }

    @Test
    fun `a renamed song arrives on the other device as one song`() = runTest {
        val (a, b) = devicesInStep()
        a.library.files[song("song_one")] = a.library.files.remove(song(1))!!
        a.sync(cloud)
        b.sync(cloud)
        a.sync(cloud)
        b.sync(cloud)

        assertEverywhere(sharedContents() - "song_1.cho" + ("song_one.cho" to "Song 1"), a, b)
        assertConverges(a, b)
    }

    @Test
    fun `a song renamed on one device and edited under its old name on the other is kept twice`() = runTest {
        val (a, b) = devicesInStep()
        // Both ways round at once: the rename of the first song reaches the folder before its edit does, and the edit
        // of the second before its rename.
        a.library.files[song("song_one")] = a.library.files.remove(song(1))!!
        b.library.files[song(1)] = "Edited on B".encodeToByteArray()
        b.library.files[song("song_two")] = b.library.files.remove(song(2))!!
        a.library.files[song(2)] = "Edited on A".encodeToByteArray()
        a.sync(cloud)
        b.sync(cloud)
        a.sync(cloud)

        val expected = sharedContents() +
            ("song_1.cho" to "Edited on B") +
            ("song_one.cho" to "Song 1") +
            ("song_2.cho" to "Edited on A") +
            ("song_two.cho" to "Song 2")
        assertEverywhere(expected, a, b)
        assertConverges(a, b)
    }

    @Test
    fun `a device that sees most of the library deleted elsewhere asks before following`() = runTest {
        val a = Device(librarySongs(10))
        val b = Device()
        a.sync(cloud)
        b.sync(cloud)
        (1..6).forEach { a.library.files -= song(it) }
        a.sync(cloud, SyncDeletionPolicy.DELETE_REMOTELY)

        assertEquals(
            SyncEngine.Result.DeletionsNeedConfirmation(count = 6, total = 10, direction = SyncDeletionDirection.LOCAL),
            b.syncResult(cloud, SyncDeletionPolicy.ASK),
        )
        val all = librarySongs(10).entries.associate { (key, bytes) -> key.name to bytes.decodeToString() }
        assertEquals(all, b.contents, "Device B")
        assertEquals(all - (1..6).map { song(it).name }.toSet(), cloud.contents, "The cloud folder")

        b.sync(cloud, SyncDeletionPolicy.KEEP_AND_UPLOAD)
        assertEquals(all, cloud.contents, "The cloud folder")
        a.sync(cloud)

        assertEverywhere(all, a, b)
        assertConverges(a, b)
    }

    @Test
    fun `a song spelled with different case on the two devices settles on one file`() = runTest {
        // Dropbox, which takes the two spellings for one file.
        val cloud = FakeSyncProvider(ignoresCase = true)
        val a = Device(shared() + (song("Hallelujah") to ORIGINAL))
        val b = Device(shared() + (song("hallelujah") to ORIGINAL))
        a.sync(cloud)
        b.sync(cloud)
        b.library.files[song("hallelujah")] = THERE
        b.sync(cloud)
        a.sync(cloud)

        assertEquals(sharedContents() + ("Hallelujah.cho" to THERE.decodeToString()), a.contents)
        assertEquals(sharedContents() + ("hallelujah.cho" to THERE.decodeToString()), b.contents)
        assertEquals(a.contents, cloud.contents)
        assertConverges(a, b, cloud)
    }

    @Test
    fun `a song spelled in two Unicode forms on the two devices settles on one file`() = runTest {
        // Dropbox, which takes the composed and the decomposed spelling for one file. Escaped, so that no editor or
        // formatter folds the two names into one.
        val cloud = FakeSyncProvider(ignoresCase = true)
        val composed = "caf\u00e9"
        val decomposed = "cafe\u0301"
        val a = Device(shared() + (song(composed) to ORIGINAL))
        val b = Device(shared() + (song(decomposed) to ORIGINAL))
        a.sync(cloud)
        b.sync(cloud)
        b.library.files[song(decomposed)] = THERE
        b.sync(cloud)
        a.sync(cloud)

        assertEquals(sharedContents() + ("$composed.cho" to THERE.decodeToString()), a.contents)
        assertEquals(sharedContents() + ("$decomposed.cho" to THERE.decodeToString()), b.contents)
        assertEquals(a.contents, cloud.contents)
        assertConverges(a, b, cloud)
    }

    @Test
    fun `a setlist edited differently on both devices gets a conflict copy rather than a merge`() = runTest {
        val setlist = "date:2026-10-14;songs:song_1,song_2"
        val (a, b) = devicesInStep(extra = mapOf(GIG to setlist.encodeToByteArray()))
        a.library.files[GIG] = "$setlist,song_3".encodeToByteArray()
        b.library.files[GIG] = "date:2026-10-14;songs:song_2".encodeToByteArray()
        a.sync(cloud)
        b.sync(cloud)
        a.sync(cloud)

        val expected = sharedContents() +
            ("gig.setlist.json" to "date:2026-10-14;songs:song_2") +
            ("gig (2).setlist.json" to "$setlist,song_3")
        assertEverywhere(expected, a, b)
        assertConverges(a, b)
    }

    @Test
    fun `overrides set on either device reach both and a removal travels too`() = runTest {
        val a = Device(shared(), FakeUserPreferencesRepository())
        val b = Device(shared(), FakeUserPreferencesRepository())
        a.sync(cloud)
        b.sync(cloud)
        a.preferences!!.updateUserPreferences { it.copy(transpositions = mapOf("song_1.cho" to 2)) }
        b.preferences!!.updateUserPreferences { it.copy(capos = mapOf("song_2.cho" to 3)) }
        a.sync(cloud)
        b.sync(cloud)
        a.sync(cloud)

        val both = SyncedPreferences(transpositions = mapOf("song_1.cho" to 2), capos = mapOf("song_2.cho" to 3))
        assertPreferencesEverywhere(both, a, b)

        a.preferences.updateUserPreferences { it.copy(transpositions = emptyMap()) }
        a.sync(cloud)
        b.sync(cloud)

        assertPreferencesEverywhere(SyncedPreferences(capos = mapOf("song_2.cho" to 3)), a, b)
        assertConverges(a, b)
    }

    @Test
    fun `a device downloads the preferences only when another device changed them`() = runTest {
        val a = Device(shared(), FakeUserPreferencesRepository(defaultUserPreferences(capos = mapOf("song_1.cho" to 2))))
        val b = Device(shared(), FakeUserPreferencesRepository())
        a.sync(cloud)
        b.sync(cloud)
        a.sync(cloud)
        val downloads = cloud.documentDownloadCount

        a.sync(cloud)

        assertEquals(downloads, cloud.documentDownloadCount)

        b.preferences!!.updateUserPreferences { it.copy(capos = it.capos + ("song_2.cho" to 3)) }
        b.sync(cloud)
        val downloadsBefore = cloud.documentDownloadCount
        a.sync(cloud)

        assertEquals(downloadsBefore + 1, cloud.documentDownloadCount)
        assertPreferencesEverywhere(SyncedPreferences(capos = mapOf("song_1.cho" to 2, "song_2.cho" to 3)), a, b)
        assertConverges(a, b)
    }

    /** Three songs both devices have, settled with the cloud: enough that deleting one is not the whole library. */
    private fun shared() = librarySongs(3)

    private fun sharedContents() = shared().entries.associate { (key, bytes) -> key.name to bytes.decodeToString() }

    @Test
    fun `a shared setlist's copy follows a song edited on the other device`() = runTest {
        val (a, b) = devicesInStep(mapOf(GIG to setlistDocument("Gig", "song_1.cho", "song_2.cho")))
        cloud.share(SHARED_SETLIST)
        a.sync(cloud)
        b.sync(cloud)

        b.library.files[song(1)] = "Edited on B".encodeToByteArray()
        b.sync(cloud)
        val packs = a.sharedContents.packCount
        a.sync(cloud)

        assertTrue("Edited on B" in cloud.copyOf(SHARED_SETLIST.shareId))
        assertEquals(SharedLinksEntry.STATE_OK, cloud.entryOf(SHARED_SETLIST.shareId).state)
        // What one device built, the other skips: its files are the same once synced.
        assertEquals(packs, a.sharedContents.packCount)
        assertConverges(a, b)
    }

    @Test
    fun `a shared song renamed on one device keeps its link, under the new name on the other`() = runTest {
        val (a, b) = devicesInStep()
        cloud.share(SHARED_SONG)
        a.sync(cloud)
        b.sync(cloud)

        a.library.files[song("song_one")] = a.library.files.remove(song(1))!!
        a.journal(SharedLinksJournal.Entry.Rename(from = "song_1.cho", to = "song_one.cho"))
        a.sync(cloud)
        b.sync(cloud)

        val entry = cloud.entryOf(SHARED_SONG.shareId)
        assertEquals("song_one.cho", entry.fileName)
        assertEquals(SharedLinksEntry.STATE_OK, entry.state)
        assertEquals(listOf("song_one.cho"), mirrorOf(b.sharedLinks).links.map { it.fileName })
        assertTrue("[song_one.cho]" in cloud.copyOf(SHARED_SONG.shareId))
        assertNull(a.sharedLinks.journal)
        assertConverges(a, b)
    }

    @Test
    fun `a shared setlist deleted in the app stops its share on every device`() = runTest {
        val (a, b) = devicesInStep(mapOf(GIG to setlistDocument("Gig", "song_1.cho")))
        cloud.share(SHARED_SETLIST)
        a.sync(cloud)
        b.sync(cloud)

        a.library.files -= GIG
        a.journal(SharedLinksJournal.Entry.Stop(SHARED_SETLIST.shareId))
        a.sync(cloud)
        b.sync(cloud)

        assertTrue(cloud.sharedFiles.isEmpty())
        assertTrue(cloud.registry().links.isEmpty())
        assertEquals(sharedContents(), b.contents)
        assertNull(a.sharedLinks.journal)
        assertConverges(a, b)
    }

    @Test
    fun `a shared song deleted outside the app is orphaned, and shared again once it is back`() = runTest {
        val (a, b) = devicesInStep()
        cloud.share(SHARED_SONG)
        a.sync(cloud)
        b.sync(cloud)
        val copy = cloud.copyOf(SHARED_SONG.shareId)

        val bytes = b.library.files.remove(song(1))!!
        b.sync(cloud)
        a.sync(cloud)
        assertEquals(SharedLinksEntry.STATE_ORPHANED, cloud.entryOf(SHARED_SONG.shareId).state)
        assertEquals(copy, cloud.copyOf(SHARED_SONG.shareId))

        a.library.files[song(1)] = bytes
        a.sync(cloud)
        b.sync(cloud)
        assertEquals(SharedLinksEntry.STATE_OK, cloud.entryOf(SHARED_SONG.shareId).state)
        assertEquals(copy, cloud.copyOf(SHARED_SONG.shareId))
        assertConverges(a, b)
    }

    @Test
    fun `two devices rebuilding one copy at once leave it built once`() = runTest {
        val (a, b) = devicesInStep(mapOf(GIG to setlistDocument("Gig", "song_1.cho")))
        cloud.share(SHARED_SETLIST)
        a.sync(cloud)
        b.sync(cloud)

        a.library.files[song(1)] = "Edited on both".encodeToByteArray()
        b.library.files[song(1)] = "Edited on both".encodeToByteArray()
        // B's whole run lands between A building its copy and uploading it, so A's upload meets B's.
        cloud.onUploadSharedFile = {
            cloud.onUploadSharedFile = {}
            b.sync(cloud)
        }
        a.sync(cloud)

        assertTrue(a.areSharedCopiesSettled)
        assertTrue("Edited on both" in cloud.copyOf(SHARED_SETLIST.shareId))
        assertEquals(sharedContents() + ("song_1.cho" to "Edited on both") + (GIG.name to "Gig\nsong_1.cho"), a.contents)
        assertConverges(a, b)
    }

    private suspend fun devicesInStep(extra: Map<SyncKey, ByteArray> = emptyMap()): Pair<Device, Device> {
        val a = Device(shared() + extra)
        val b = Device()
        a.sync(cloud)
        b.sync(cloud)
        assertEverywhere(sharedContents() + extra.entries.associate { (key, bytes) -> key.name to bytes.decodeToString() }, a, b)
        return a to b
    }

    private fun assertEverywhere(expected: Map<String, String>, a: Device, b: Device) {
        assertEquals(expected, a.contents, "Device A")
        assertEquals(expected, b.contents, "Device B")
        assertEquals(expected, cloud.contents, "The cloud folder")
    }

    private fun assertPreferencesEverywhere(expected: SyncedPreferences, a: Device, b: Device) {
        assertEquals(expected, SyncedPreferences.of(a.preferences!!.current), "Device A")
        assertEquals(expected, SyncedPreferences.of(b.preferences!!.current), "Device B")
        val document = SyncedPreferencesDocument.decode(cloud.documents.getValue(SyncedPreferencesDocument.FILE_NAME).first)!!
        assertEquals(expected, SyncedPreferencesDocument.preferencesOf(document), "The cloud folder")
    }

    /**
     * One more round on both devices, which a library that has settled comes out of unchanged: the same files and
     * preferences everywhere, and not one upload to the folder, which every file's and document's revision shows.
     */
    private suspend fun assertConverges(a: Device, b: Device, cloud: FakeSyncProvider = this.cloud) {
        val before = listOf(a.state(), b.state(), cloud.state())
        a.sync(cloud)
        b.sync(cloud)
        assertEquals(before, listOf(a.state(), b.state(), cloud.state()))
    }

    private fun FakeSyncProvider.state() = listOf(
        contents,
        documents.mapValues { it.value.first.decodeToString() },
        files.entries.associate { (key, file) -> key.name to file.second },
        documents.mapValues { it.value.second },
        sharedFiles.mapValues { (_, file) -> file.first.decodeToString() to file.second },
    )

    private fun Device.state() = contents to preferences?.current?.let(SyncedPreferences::of)

    private val FakeSyncProvider.contents get() = files.entries.associate { (key, file) -> key.name to file.first.decodeToString() }

    private companion object {
        val SHARED_SETLIST = sharedEntry(shareId = "setlistshare0001", kind = SharedLinkKind.SETLIST, fileName = GIG.name, title = "Gig")
        val SHARED_SONG = sharedEntry(shareId = "songshare0000001", kind = SharedLinkKind.SONG, fileName = "song_1.cho", title = "Song 1")
    }
}
