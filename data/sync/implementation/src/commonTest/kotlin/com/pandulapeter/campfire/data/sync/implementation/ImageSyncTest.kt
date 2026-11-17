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

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.sync.implementation.run.SyncEngine
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pictures and attachments between two devices, through the real engine: they follow the songs naming them, a song
 * naming one keeps it on both sides, and a copy that is not what its name says never travels.
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
    fun `a run killed while a picture goes up leaves no song in the folder naming a file it lacks`() = runTest {
        val a = Device(mapOf(song(1) to naming(PHOTO), PHOTO.key to PHOTO.bytes, RECORDING.key to RECORDING.bytes, song(2) to attaching(RECORDING)))
        // The app dies while the files are in flight, after the other transfers have had every chance to finish.
        cloud.onUpload = { key ->
            if (key.kind == LibraryFileKind.FILE) {
                repeat(10) { yield() }
                throw ProcessKilled()
            }
        }
        assertFailsWith<ProcessKilled> { a.sync(cloud) }

        val held = cloud.files.keys.filter { it.kind == LibraryFileKind.FILE }.map { it.name }.toSet()
        cloud.files.filterKeys { it.kind == LibraryFileKind.SONG }.forEach { (key, file) ->
            val named = Regex("""(?:src=|attachment )(\S+)""").findAll(file.first.decodeToString()).map { it.groupValues[1] }.toSet()
            assertTrue(held.containsAll(named), "${key.name} names ${named - held}, which the folder lacks.")
        }
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
        assertConverges(a, b)

        assertNull(b.library.files[PHOTO.key])
        assertNull(cloud.files[PHOTO.key])
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
        assertConverges(a, b)

        assertContentEquals(PHOTO.bytes, b.library.files[PHOTO.key])
        assertContentEquals(PHOTO.bytes, cloud.files[PHOTO.key]?.first)
        assertContentEquals(PHOTO.bytes, a.library.files[PHOTO.key])
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
        assertConverges(a, b)

        assertContentEquals(PHOTO.bytes, a.library.files[PHOTO.key])
        assertContentEquals(PHOTO.bytes, b.library.files[PHOTO.key])
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
        // B's first run deletes the song from the folder and keeps the picture there; its second deletes the picture.
        b.sync(cloud)
        b.sync(cloud)
        assertNull(cloud.files[PHOTO.key])
        // A's first run deletes the song here and keeps the picture; the next one would delete it.
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
    fun `a picture only the editor's unsaved text names is kept by sync`() = runTest {
        val (a, b) = inStep(mapOf(song(1) to naming(PHOTO), PHOTO.key to PHOTO.bytes))
        // A has the song open, its unsaved text still naming the picture, while B drops the picture's line and prunes it.
        a.heldPictures = setOf(PHOTO.key.name)
        b.library.files[song(1)] = "No picture any more".encodeToByteArray()
        b.library.files -= PHOTO.key
        b.sync(cloud)
        a.sync(cloud)
        a.sync(cloud)

        assertContentEquals(PHOTO.bytes, a.library.files[PHOTO.key])
        assertContentEquals(PHOTO.bytes, cloud.files[PHOTO.key]?.first)
    }

    @Test
    fun `a picture gone from the folder stays and goes back up while only the editor's unsaved text names it`() = runTest {
        val (a, b) = inStep(mapOf(song(1) to naming(PHOTO), PHOTO.key to PHOTO.bytes))
        b.library.files -= song(1)
        b.library.files -= PHOTO.key
        b.sync(cloud)
        b.sync(cloud)
        assertNull(cloud.files[PHOTO.key])
        // A's first run deletes the song and keeps the picture, the second would delete it but for the editor.
        a.heldPictures = setOf(PHOTO.key.name)
        a.sync(cloud)
        a.sync(cloud)

        assertContentEquals(PHOTO.bytes, a.library.files[PHOTO.key])
        assertContentEquals(PHOTO.bytes, cloud.files[PHOTO.key]?.first)
    }

    @Test
    fun `an intact picture replaces a folder copy of the same name holding other bytes`() = runTest {
        cloud.files[PHOTO.key] = "planted".encodeToByteArray() to "r1"
        val a = Device(mapOf(song(1) to naming(PHOTO), PHOTO.key to PHOTO.bytes))
        a.sync(cloud)

        assertContentEquals(PHOTO.bytes, cloud.files[PHOTO.key]?.first)
        assertContentEquals(PHOTO.bytes, a.library.files[PHOTO.key])
    }

    @Test
    fun `a picture is kept when a song becomes unreadable after the listing`() = runTest {
        val a = pictureLeftOnlyOnA()
        // Uploaded in the same pass, before the picture's deletion: the song is read fine by the listing and not after.
        a.library.files[song("other_song_1")] = "Edited on A".encodeToByteArray()
        cloud.onUpload = { a.library.onRead = { key -> if (key == song("other_song_2")) throw LibraryStorageFailure() } }
        a.sync(cloud)

        assertContentEquals(PHOTO.bytes, a.library.files[PHOTO.key])
    }

    @Test
    fun `a picture changed after the listing is not deleted`() = runTest {
        val a = pictureLeftOnlyOnA()
        val changed = PHOTO.bytes + 1
        a.library.files[song("other_song_1")] = "Edited on A".encodeToByteArray()
        cloud.onUpload = { a.library.files[PHOTO.key] = changed }
        a.sync(cloud)

        assertContentEquals(changed, a.library.files[PHOTO.key])
    }

    @Test
    fun `no picture is deleted from the folder while a song of this device cannot be read`() = runTest {
        val (a, b) = inStep(mapOf(song(1) to naming(PHOTO), song(2) to "Song 2".encodeToByteArray(), PHOTO.key to PHOTO.bytes))
        a.library.files -= song(1)
        a.library.files -= PHOTO.key
        a.library.onRead = { key -> if (key == song(2)) throw LibraryStorageFailure() }
        a.sync(cloud)
        a.sync(cloud)

        assertContentEquals(PHOTO.bytes, cloud.files[PHOTO.key]?.first)
        a.library.onRead = {}
        assertConverges(a, b)
        assertNull(cloud.files[PHOTO.key])
    }

    @Test
    fun `no picture is deleted on either side while a song of this device is too large to read`() = runTest {
        val (a, b) = inStep(mapOf(song(1) to naming(PHOTO), song(2) to naming(SECOND_PHOTO), PHOTO.key to PHOTO.bytes, SECOND_PHOTO.key to SECOND_PHOTO.bytes))
        a.library.files -= song(1)
        a.library.files -= PHOTO.key
        b.library.files -= song(2)
        b.library.files -= SECOND_PHOTO.key
        b.sync(cloud)
        a.library.files[song(3)] = ByteArray((ImportLimits.MAX_TEXT_FILE_SIZE + 1).toInt())
        a.sync(cloud)
        a.sync(cloud)

        assertContentEquals(PHOTO.bytes, cloud.files[PHOTO.key]?.first)
        assertContentEquals(SECOND_PHOTO.bytes, a.library.files[SECOND_PHOTO.key])
    }

    @Test
    fun `a picture gone from every device comes back from the folder's history when an older version revives its song`() = runTest {
        val (a, _) = inStep(mapOf(song(1) to naming(PHOTO), PHOTO.key to PHOTO.bytes))
        a.library.files -= song(1)
        a.library.files -= PHOTO.key
        a.sync(cloud)
        a.sync(cloud)
        assertNull(cloud.files[PHOTO.key])
        // A version that knows nothing of pictures uploads its offline edit of the song, still naming the picture.
        cloud.files[song(1)] = naming(PHOTO, extra = "Edited by an older version") to "r-old"
        a.sync(cloud)
        a.sync(cloud)

        assertContentEquals(PHOTO.bytes, a.library.files[PHOTO.key])
        assertContentEquals(PHOTO.bytes, cloud.files[PHOTO.key]?.first)
    }

    @Test
    fun `a current device that still holds a revived song's picture brings it back without the history`() = runTest {
        val (a, b) = inStep(mapOf(song(1) to naming(PHOTO), PHOTO.key to PHOTO.bytes))
        a.library.files -= song(1)
        a.library.files -= PHOTO.key
        a.sync(cloud)
        a.sync(cloud)
        cloud.deletedFiles.clear()
        b.library.files[song(1)] = naming(PHOTO, extra = "Edited on B")
        b.sync(cloud)
        assertConverges(a, b)

        assertContentEquals(PHOTO.bytes, a.library.files[PHOTO.key])
        assertContentEquals(PHOTO.bytes, cloud.files[PHOTO.key]?.first)
    }

    @Test
    fun `no picture is deleted while a song of this device cannot be read`() = runTest {
        val (a, b) = inStep(mapOf(song(1) to naming(PHOTO), song(2) to "Song 2".encodeToByteArray(), PHOTO.key to PHOTO.bytes))
        b.library.files -= song(1)
        b.library.files -= PHOTO.key
        b.sync(cloud)
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
        // And the run that deletes the pictures from the folder, once no song is changing, asks nothing either.
        assertIs<SyncEngine.Result.Completed>(a.attempt(cloud, SyncDeletionPolicy.ASK))
        assertConverges(a, b)
        assertTrue(b.library.files.keys.none { it.kind == LibraryFileKind.FILE })
    }

    @Test
    fun `an attachment over a picture's limit reaches the other device`() = runTest {
        val a = Device(mapOf(song(1) to attaching(RECORDING), RECORDING.key to RECORDING.bytes))
        val b = Device()
        a.sync(cloud)
        b.sync(cloud)

        assertContentEquals(RECORDING.bytes, cloud.files[RECORDING.key]?.first)
        assertContentEquals(RECORDING.bytes, b.library.files[RECORDING.key])
        assertConverges(a, b)
    }

    @Test
    fun `files over a picture's limit move one at a time, beside the others`() = runTest {
        val video = attachment(Random(2).nextBytes(RECORDING.bytes.size))
        val a = Device(mapOf(song(1) to attaching(RECORDING, video), RECORDING.key to RECORDING.bytes, video.key to video.bytes))
        a.sync(cloud)
        var inFlight = 0
        var largeInFlight = 0
        var mostLargeInFlight = 0
        var mostInFlight = 0
        cloud.onDownload = { key ->
            val isLarge = key.kind == LibraryFileKind.FILE
            inFlight++
            if (isLarge) largeInFlight++
            mostInFlight = maxOf(mostInFlight, inFlight)
            mostLargeInFlight = maxOf(mostLargeInFlight, largeInFlight)
            yield()
            inFlight--
            if (isLarge) largeInFlight--
        }
        Device().sync(cloud)

        assertEquals(1, mostLargeInFlight)
        assertEquals(2, mostInFlight, "The song moves beside a large file.")
    }

    @Test
    fun `an attachment deleted on one device stays where a song still names it and comes back`() = runTest {
        val sheet = attachment("sheet music")
        val (a, b) = inStep(mapOf(song(1) to attaching(sheet), sheet.key to sheet.bytes))
        a.library.files -= song(1)
        a.library.files -= sheet.key
        b.library.files[song(2)] = attaching(sheet)
        a.sync(cloud)
        b.sync(cloud)
        a.sync(cloud)
        assertConverges(a, b)

        assertContentEquals(sheet.bytes, cloud.files[sheet.key]?.first)
        assertContentEquals(sheet.bytes, a.library.files[sheet.key])
        assertContentEquals(sheet.bytes, b.library.files[sheet.key])
    }

    @Test
    fun `an attachment here over its limit is reported and neither uploaded nor taken for a deletion`() = runTest {
        val huge = attachment(ByteArray((ImportLimits.MAX_ATTACHMENT_FILE_SIZE + 1).toInt()))
        val a = Device(mapOf(song(1) to attaching(huge), huge.key to huge.bytes))

        val result = assertIs<SyncEngine.Result.Completed>(a.attempt(cloud, SyncDeletionPolicy.ASK))

        assertEquals(listOf(huge.key.name), result.summary.failed)
        assertEquals(setOf(song(1)), cloud.files.keys)
        assertContentEquals(huge.bytes, a.library.files[huge.key])
    }

    @Test
    fun `an attachment in the folder over its limit is reported and never downloaded`() = runTest {
        val cloud = FakeSyncProvider(sizes = mapOf(RECORDING.key to ImportLimits.MAX_ATTACHMENT_FILE_SIZE + 1))
        cloud.files[RECORDING.key] = RECORDING.bytes to "r1"
        cloud.files[song(1)] = attaching(RECORDING) to "r1"
        val b = Device()

        val result = assertIs<SyncEngine.Result.Completed>(b.attempt(cloud, SyncDeletionPolicy.ASK))

        assertEquals(listOf(RECORDING.key.name), result.summary.failed)
        assertNull(b.library.files[RECORDING.key])
        assertContentEquals(RECORDING.bytes, cloud.files[RECORDING.key]?.first)
    }

    @Test
    fun `an attachment in the folder that is not what its name says is never written into the library`() = runTest {
        cloud.files[RECORDING.key] = "planted".encodeToByteArray() to "r1"
        cloud.files[song(1)] = attaching(RECORDING) to "r1"
        val b = Device()
        b.sync(cloud)

        assertNull(b.library.files[RECORDING.key])
        assertTrue(b.library.files.containsKey(song(1)))
    }

    /** With a few other songs, so that the deletions of a test are not the whole library, which the guard asks about. */
    private suspend fun inStep(files: Map<SyncKey, ByteArray>): Pair<Device, Device> {
        val a = Device(librarySongs(3).mapKeys { (key, _) -> song("other_${key.name.removeSuffix(".cho")}") } + files)
        val b = Device()
        a.sync(cloud)
        b.sync(cloud)
        return a to b
    }

    /** A device whose song naming [PHOTO] another device deleted with the picture: its next run alone deletes the picture. */
    private suspend fun pictureLeftOnlyOnA(): Device {
        val (a, b) = inStep(mapOf(song(1) to naming(PHOTO), PHOTO.key to PHOTO.bytes))
        b.library.files -= song(1)
        b.library.files -= PHOTO.key
        b.sync(cloud)
        b.sync(cloud)
        a.sync(cloud)
        assertContentEquals(PHOTO.bytes, a.library.files[PHOTO.key])
        return a
    }

    /**
     * Runs on both devices until a round moves nothing, as a few rounds do: a picture's deletion waits for a run that
     * changes no song. Then both devices and the folder hold the same files.
     */
    private suspend fun assertConverges(a: Device, b: Device) {
        repeat(MAXIMUM_ROUNDS) {
            val before = listOf(a.snapshot(), b.snapshot(), cloud.snapshot())
            a.sync(cloud)
            b.sync(cloud)
            if (before == listOf(a.snapshot(), b.snapshot(), cloud.snapshot())) {
                assertEquals(a.snapshot(), b.snapshot())
                assertEquals(a.snapshot(), cloud.snapshot())
                return
            }
        }
        throw AssertionError("Still changing after $MAXIMUM_ROUNDS rounds of runs")
    }

    private fun Device.snapshot() = library.files.entries.associate { (key, bytes) -> key.path to localContentHash(bytes) }

    private fun FakeSyncProvider.snapshot() = files.entries.associate { (key, file) -> key.path to localContentHash(file.first) }

    /** A picture or an attachment: a library file named by its content. */
    private class Picture(val key: SyncKey, val bytes: ByteArray)

    private class LibraryStorageFailure : Exception("Not readable now.")

    private class ProcessKilled : Error("Killed")

    private companion object {

        const val MAXIMUM_ROUNDS = 5

        val PHOTO = picture("photo")

        val SECOND_PHOTO = picture("another photo")

        /** Larger than a picture may be, as a recording is, so that it moves alone. */
        val RECORDING = attachment(Random(1).nextBytes((ImportLimits.MAX_IMAGE_FILE_SIZE + 1).toInt()))

        fun picture(content: String) = libraryFile(content.encodeToByteArray(), extension = ".png")

        fun attachment(content: String) = attachment(content.encodeToByteArray())

        fun attachment(bytes: ByteArray) = libraryFile(bytes, extension = ".m4a")

        private fun libraryFile(bytes: ByteArray, extension: String) =
            Picture(SyncKey(kind = LibraryFileKind.FILE, name = localContentHash(bytes).take(16) + extension), bytes)

        fun naming(vararg pictures: Picture, extra: String = "") =
            (pictures.joinToString("\n") { "{image: src=${it.key.name}}" } + "\n" + extra).encodeToByteArray()

        fun attaching(vararg attachments: Picture) =
            attachments.joinToString("\n", postfix = "\n") { "{meta: attachment ${it.key.name} Rehearsal take.m4a}" }.encodeToByteArray()
    }
}
