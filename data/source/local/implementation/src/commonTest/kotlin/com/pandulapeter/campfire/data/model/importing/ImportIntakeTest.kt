/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipWriter
import com.pandulapeter.campfire.data.model.importing.ImportIntake.Decision
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [ImportIntake.decide] and [ImportBudget.take], the intake, through every case of
 * [ImportLimitsTest]: what that reads whole as an archive is staged here, and everything else ends as it does there.
 * Here rather than in `:data:model` for the zip writer, as [ImportLimitsTest] is.
 */
internal class ImportIntakeTest {

    private val zipHead = byteArrayOf(0x50, 0x4B, 3, 4)
    private val textHead = "G   D\nDown by the river\n".encodeToByteArray()

    @Test
    fun `a file of an unknown type is staged only where it is an archive`() = runTest {
        val archive = ZipWriter.write(listOf(ZipEntry("a.cho", "{title: A}".encodeToByteArray())))

        assertEquals(Decision.LeaveUnread(isTooLarge = false), ImportIntake.decide("0.mp4", 10L shl 20, ByteArray(512)))
        assertEquals(Decision.Stage, ImportIntake.decide("library.sbpbackup2", archive.size.toLong(), archive))
    }

    @Test
    fun `an unknown loose file that is text is read and a binary one is not`() {
        val binary = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 0, 1, 2)

        assertEquals(Decision.ReadWhole(ImportLimits.MAX_TEXT_FILE_SIZE), ImportIntake.decide("riverlight.songsheet", textHead.size.toLong(), textHead))
        assertEquals(Decision.LeaveUnread(isTooLarge = false), ImportIntake.decide("riverlight", binary.size.toLong(), binary))
        assertEquals(Decision.LeaveUnread(isTooLarge = false), ImportIntake.decide("take.mp3", textHead.size.toLong(), textHead))
    }

    @Test
    fun `a file of an unknown type larger than a text is not read`() {
        assertEquals(Decision.LeaveUnread(isTooLarge = false), ImportIntake.decide("video.mp4", ImportLimits.MAX_ARCHIVE_SIZE + 1, ByteArray(512)))
        assertEquals(Decision.LeaveUnread(isTooLarge = false), ImportIntake.decide("notes", ImportLimits.MAX_TEXT_FILE_SIZE + 1, textHead))
    }

    @Test
    fun `another app's backup is staged by its name`() {
        assertEquals(Decision.Stage, ImportIntake.decide("Library.SBPBACKUP", null, ByteArray(0)))
        assertEquals(Decision.Stage, ImportIntake.decide("set.sbp", 10, textHead))
    }

    @Test
    fun `a song over its limit is not read`() {
        assertEquals(Decision.LeaveUnread(isTooLarge = true), ImportIntake.decide("a.cho", ImportLimits.MAX_ARCHIVE_SIZE + 1, textHead))
    }

    @Test
    fun `a song of unknown size is read within its limit`() {
        assertEquals(Decision.ReadWhole(ImportLimits.MAX_TEXT_FILE_SIZE), ImportIntake.decide("a.cho", null, textHead))
    }

    @Test
    fun `a zip under a text name is staged whatever its size`() {
        assertEquals(Decision.Stage, ImportIntake.decide("backup.txt", ImportLimits.MAX_TEXT_FILE_SIZE + 1, zipHead))
        assertEquals(Decision.Stage, ImportIntake.decide("backup.json", 100, zipHead))
    }

    @Test
    fun `a text over its limit is reported too large`() {
        assertEquals(Decision.LeaveUnread(isTooLarge = true), ImportIntake.decide("huge.txt", ImportLimits.MAX_TEXT_FILE_SIZE + 1, textHead))
    }

    @Test
    fun `an archive is staged up to the staging limit and refused past it`() {
        assertEquals(Decision.Stage, ImportIntake.decide("songs.zip", ImportLimits.MAX_ARCHIVE_SIZE + 1, zipHead))
        assertEquals(Decision.Stage, ImportIntake.decide("songs.zip", ImportLimits.MAX_STAGED_ARCHIVE_SIZE, zipHead))
        assertEquals(Decision.LeaveUnread(isTooLarge = true), ImportIntake.decide("songs.zip", ImportLimits.MAX_STAGED_ARCHIVE_SIZE + 1, zipHead))
    }

    @Test
    fun `a picture and a document keep their limits whatever their bytes`() {
        assertEquals(Decision.ReadWhole(ImportLimits.MAX_PICKED_IMAGE_FILE_SIZE), ImportIntake.decide("Photo.JPEG", 10, zipHead))
        for (name in listOf("song.pdf", "song.DOCX", "song.doc")) {
            assertEquals(Decision.ReadWhole(ImportLimits.MAX_DOCUMENT_FILE_SIZE), ImportIntake.decide(name, 10, zipHead))
        }
    }

    @Test
    fun `a staged archive costs the selection nothing`() {
        val budget = ImportBudget()
        val staged = ImportedFile(name = "0.zip", bytes = ByteArray(0), staged = StagedFile(name = "s-1-1.zip", size = 1, sha256Hex = null))

        val archives = List(3) { index -> budget.take("$index.zip", 40L shl 20, zipHead, readBytes = { error("read") }, stage = { staged }) }
        val song = budget.take("a.cho", ImportLimits.MAX_TEXT_FILE_SIZE, textHead, readBytes = { ByteArray(ImportLimits.MAX_TEXT_FILE_SIZE.toInt()) }, stage = { error("staged") })

        archives.forEach { assertSame(staged, it) }
        assertFalse(song!!.isTooLarge)
    }

    @Test
    fun `what is read whole is read as the budget reads it`() {
        val budget = ImportBudget()
        val archive = ImportLimits.MAX_ARCHIVE_SIZE - 10
        budget.read(name = "songs.zip", size = archive) { ByteArray(archive.toInt()) }

        val tooLarge = budget.take("photo.png", 11, ByteArray(8), readBytes = { error("read") }, stage = { error("staged") })
        val photo = budget.take("photo.png", 10, ByteArray(8), readBytes = { ByteArray(10) }, stage = { error("staged") })
        val text = budget.take("riverlight.songsheet", textHead.size.toLong(), textHead, readBytes = { textHead }, stage = { error("staged") })

        assertTrue(tooLarge!!.isTooLarge)
        assertEquals(10, photo!!.bytes.size)
        assertTrue(text!!.isTooLarge.not() && text.bytes.isEmpty())
    }

    @Test
    fun `what is left unread is not read`() {
        val budget = ImportBudget()

        val song = budget.take("huge.txt", ImportLimits.MAX_TEXT_FILE_SIZE + 1, textHead, readBytes = { error("read") }, stage = { error("staged") })
        val video = budget.take("video.mp4", 100, ByteArray(100), readBytes = { error("read") }, stage = { error("staged") })

        assertEquals(ImportedFile.unread("huge.txt", isTooLarge = true), song)
        assertEquals(ImportedFile.unread("video.mp4"), video)
    }

    @Test
    fun `an unreadable file is left out`() {
        assertNull(ImportBudget().take("a.cho", 10, textHead, readBytes = { null }, stage = { error("staged") }))
        assertNull(ImportBudget().take("a.zip", 10, zipHead, readBytes = { error("read") }, stage = { null }))
    }

    @Test
    fun `a song read whole keeps its bytes`() {
        val file = ImportBudget().take("a.cho", textHead.size.toLong(), textHead, readBytes = { textHead }, stage = { error("staged") })

        assertContentEquals(textHead, file!!.bytes)
    }
}
