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

import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.sync.implementation.run.SyncEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pictures between two devices, through the real engine: they follow the songs naming them, a song naming one keeps it
 * on both sides, and a copy that is not what its name says never travels.
 */
class ImageSyncTest {

    private val cloud = FakeSyncProvider()

    @Test
    fun `a song and its picture reach the other device`() = runTest {
        val a = Device(mapOf(song(1) to naming(PHOTO), PHOTO.key to PHOTO.bytes))
        val b = Device()
        a.sync(cloud)
        b.sync(cloud)

        assertContentEquals(PHOTO.bytes, b.library.files[PHOTO.key])
        assertConverges(a, b)
    }

    @Test
    fun `a picture deleted with its last song is deleted on the other device`() = runTest {
        val (a, b) = inStep(mapOf(song(1) to naming(PHOTO), PHOTO.key to PHOTO.bytes))
        a.library.files -= song(1)
        a.library.files -= PHOTO.key
        a.sync(cloud)
        b.sync(cloud)
        // The run that deletes the song here keeps the picture until a run knows the song is gone for good.
        assertContentEquals(PHOTO.bytes, b.library.files[PHOTO.key])
        b.sync(cloud)

        assertNull(b.library.files[PHOTO.key])
        assertNull(cloud.files[PHOTO.key])
        assertConverges(a, b)
    }

    @Test
    fun `a picture deleted on one device stays where a song still names it and comes back`() = runTest {
        val (a, b) = inStep(mapOf(song(1) to naming(PHOTO), PHOTO.key to PHOTO.bytes))
        // A drops its only song naming the picture, while B starts using it in a new song.
        a.library.files -= song(1)
        a.library.files -= PHOTO.key
        b.library.files[song(2)] = naming(PHOTO)
        a.sync(cloud)
        b.sync(cloud)
        a.sync(cloud)

        assertContentEquals(PHOTO.bytes, b.library.files[PHOTO.key])
        assertContentEquals(PHOTO.bytes, cloud.files[PHOTO.key]?.first)
        assertContentEquals(PHOTO.bytes, a.library.files[PHOTO.key])
        assertConverges(a, b)
    }

    @Test
    fun `an edit beating the deletion of a song brings its picture back too`() = runTest {
        val (a, b) = inStep(mapOf(song(1) to naming(PHOTO), PHOTO.key to PHOTO.bytes))
        a.library.files -= song(1)
        a.library.files -= PHOTO.key
        b.library.files[song(1)] = naming(PHOTO, extra = "Edited on B")
        a.sync(cloud)
        b.sync(cloud)
        a.sync(cloud)

        assertContentEquals(PHOTO.bytes, a.library.files[PHOTO.key])
        assertContentEquals(PHOTO.bytes, b.library.files[PHOTO.key])
        assertConverges(a, b)
    }

    @Test
    fun `a picture this device deleted comes back down while one of its songs names it`() = runTest {
        val (a, b) = inStep(mapOf(song(1) to naming(PHOTO), PHOTO.key to PHOTO.bytes))
        a.library.files -= PHOTO.key
        a.sync(cloud)
        b.sync(cloud)

        assertContentEquals(PHOTO.bytes, a.library.files[PHOTO.key])
        assertContentEquals(PHOTO.bytes, b.library.files[PHOTO.key])
        assertConverges(a, b)
    }

    @Test
    fun `a pass bringing down a song deletes no picture until its songs are known`() = runTest {
        val (a, b) = inStep(mapOf(PHOTO.key to PHOTO.bytes, song(1) to naming(PHOTO)))
        // A prunes the picture while the song naming it is being edited on B: the run that brings the edit down must not
        // delete the picture from the folder, and the next one brings it back.
        a.library.files -= PHOTO.key
        a.library.files -= song(1)
        b.library.files[song(1)] = naming(PHOTO, extra = "Edited on B")
        b.sync(cloud)
        a.sync(cloud)

        assertContentEquals(PHOTO.bytes, cloud.files[PHOTO.key]?.first)
        a.sync(cloud)
        assertContentEquals(PHOTO.bytes, a.library.files[PHOTO.key])
        assertConverges(a, b)
    }

    @Test
    fun `a song saved during the run naming a picture the run is about to delete keeps it`() = runTest {
        val (a, b) = inStep(mapOf(song(1) to naming(PHOTO), PHOTO.key to PHOTO.bytes))
        b.library.files -= song(1)
        b.library.files -= PHOTO.key
        b.sync(cloud)
        // The run that deletes the song here keeps the picture; the next one would delete it.
        a.sync(cloud)
        a.library.files[song("other_song_1")] = "Edited on A".encodeToByteArray()
        cloud.onUpload = { a.library.files[song(9)] = naming(PHOTO) }
        a.sync(cloud)
        cloud.onUpload = {}

        assertContentEquals(PHOTO.bytes, a.library.files[PHOTO.key])
        assertContentEquals(PHOTO.bytes, cloud.files[PHOTO.key]?.first)
        assertConverges(a, b)
        assertContentEquals(PHOTO.bytes, b.library.files[PHOTO.key])
    }

    @Test
    fun `no picture is deleted while a song of this device cannot be read`() = runTest {
        val (a, b) = inStep(mapOf(song(1) to naming(PHOTO), song(2) to "Song 2".encodeToByteArray(), PHOTO.key to PHOTO.bytes))
        b.library.files -= song(1)
        b.library.files -= PHOTO.key
        b.sync(cloud)
        a.library.onRead = { key -> if (key == song(2)) throw LibraryStorageFailure() }
        a.sync(cloud)

        assertContentEquals(PHOTO.bytes, a.library.files[PHOTO.key])
        a.library.onRead = {}
        a.sync(cloud)
        assertNull(a.library.files[PHOTO.key])
        assertConverges(a, b)
    }

    @Test
    fun `the same picture on two devices meeting for the first time is one file with nothing transferred`() = runTest {
        val a = Device(mapOf(song(1) to naming(PHOTO), PHOTO.key to PHOTO.bytes))
        val b = Device(mapOf(song(2) to naming(PHOTO), PHOTO.key to PHOTO.bytes))
        a.sync(cloud)
        val uploads = mutableListOf<SyncKey>()
        cloud.onUpload = { uploads += it }
        b.sync(cloud)

        assertEquals(listOf(song(2)), uploads, "Only B's song goes up.")
        assertEquals(listOf(PHOTO.key.name), b.library.files.keys.filter { it.kind == LibraryFileKind.FILE }.map { it.name })
        assertConverges(a, b)
    }

    @Test
    fun `a damaged picture is never uploaded`() = runTest {
        val damaged = PHOTO.key to "not the photo".encodeToByteArray()
        val a = Device(mapOf(song(1) to naming(PHOTO), damaged))
        a.sync(cloud)

        assertNull(cloud.files[PHOTO.key])
    }

    @Test
    fun `a damaged picture is replaced by the intact copy in the folder`() = runTest {
        val (a, b) = inStep(mapOf(song(1) to naming(PHOTO), PHOTO.key to PHOTO.bytes))
        // Damaged to another size: one of the same size reads as unchanged, since a picture's size stands for its hash.
        b.library.files[PHOTO.key] = PHOTO.bytes.copyOf(PHOTO.bytes.size - 1)
        b.sync(cloud)

        assertContentEquals(PHOTO.bytes, b.library.files[PHOTO.key])
        assertContentEquals(PHOTO.bytes, cloud.files[PHOTO.key]?.first)
        assertConverges(a, b)
    }

    @Test
    fun `a picture in the folder that is not what its name says is never written into the library`() = runTest {
        cloud.files[PHOTO.key] = "planted".encodeToByteArray() to "r1"
        cloud.files[song(1)] = naming(PHOTO) to "r1"
        val b = Device()
        b.sync(cloud)

        assertNull(b.library.files[PHOTO.key])
        assertTrue(b.library.files.containsKey(song(1)))
    }

    @Test
    fun `only names of the picture rule are pictures, and anything else in the files folder is left alone`() = runTest {
        val stray = SyncKey(kind = LibraryFileKind.FILE, name = "Holiday.JPG")
        cloud.files[stray] = PHOTO.bytes to "r1"
        val b = Device(mapOf(SyncKey(kind = LibraryFileKind.FILE, name = "notes.txt") to "x".encodeToByteArray()))
        b.sync(cloud)

        assertFalse(b.library.files.containsKey(stray))
        assertFalse(cloud.files.keys.any { it.name == "notes.txt" })
        assertTrue(cloud.files.containsKey(stray))
    }

    @Test
    fun `deleting many pictures with a few songs asks nothing`() = runTest {
        val photos = (1..10).map { picture("photo $it") }
        val (a, b) = inStep(
            mapOf(song(1) to naming(*photos.toTypedArray()), song(2) to "Song 2".encodeToByteArray(), song(3) to "Song 3".encodeToByteArray()) +
                photos.associate { it.key to it.bytes },
        )
        a.library.files -= song(1)
        photos.forEach { a.library.files -= it.key }

        assertIs<SyncEngine.Result.Completed>(a.attempt(cloud, SyncDeletionPolicy.ASK))
        b.sync(cloud)
        b.sync(cloud)
        assertTrue(b.library.files.keys.none { it.kind == LibraryFileKind.FILE })
        assertConverges(a, b)
    }

    /** With a few other songs, so that the deletions of a test are not the whole library, which the guard asks about. */
    private suspend fun inStep(files: Map<SyncKey, ByteArray>): Pair<Device, Device> {
        val a = Device(librarySongs(3).mapKeys { (key, _) -> song("other_${key.name.removeSuffix(".cho")}") } + files)
        val b = Device()
        a.sync(cloud)
        b.sync(cloud)
        return a to b
    }

    private suspend fun assertConverges(a: Device, b: Device) {
        a.sync(cloud)
        b.sync(cloud)
        val before = listOf(a.snapshot(), b.snapshot(), cloud.snapshot())
        a.sync(cloud)
        b.sync(cloud)
        assertEquals(before, listOf(a.snapshot(), b.snapshot(), cloud.snapshot()))
        assertEquals(a.snapshot(), b.snapshot())
        assertEquals(a.snapshot(), cloud.snapshot())
    }

    private fun Device.snapshot() = library.files.entries.associate { (key, bytes) -> key.path to localContentHash(bytes) }

    private fun FakeSyncProvider.snapshot() = files.entries.associate { (key, file) -> key.path to localContentHash(file.first) }

    private class Picture(val key: SyncKey, val bytes: ByteArray)

    private class LibraryStorageFailure : Exception("Not readable now.")

    private companion object {

        val PHOTO = picture("photo")

        fun picture(content: String): Picture {
            val bytes = content.encodeToByteArray()
            return Picture(SyncKey(kind = LibraryFileKind.FILE, name = localContentHash(bytes).take(16) + ".png"), bytes)
        }

        fun naming(vararg pictures: Picture, extra: String = "") =
            (pictures.joinToString("\n") { "{image: src=${it.key.name}}" } + "\n" + extra).encodeToByteArray()
    }
}
