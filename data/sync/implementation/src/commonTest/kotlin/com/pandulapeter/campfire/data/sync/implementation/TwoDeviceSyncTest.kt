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

import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferences
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesDocument
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    /** Three songs both devices have, settled with the cloud: enough that deleting one is not the whole library. */
    private fun shared() = librarySongs(3)

    private fun sharedContents() = shared().entries.associate { (key, bytes) -> key.name to bytes.decodeToString() }

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

    /** One more round on both devices, which a library that has settled comes out of unchanged. */
    private suspend fun assertConverges(a: Device, b: Device, cloud: FakeSyncProvider = this.cloud) {
        val before = listOf(a.state(), b.state(), cloud.contents to cloud.documents.mapValues { it.value.first.decodeToString() })
        a.sync(cloud)
        b.sync(cloud)
        assertEquals(before, listOf(a.state(), b.state(), cloud.contents to cloud.documents.mapValues { it.value.first.decodeToString() }))
    }

    private fun Device.state() = contents to preferences?.current?.let(SyncedPreferences::of)

    private val FakeSyncProvider.contents get() = files.entries.associate { (key, file) -> key.name to file.first.decodeToString() }
}
