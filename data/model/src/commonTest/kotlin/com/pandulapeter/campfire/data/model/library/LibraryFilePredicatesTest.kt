/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryFilePredicatesTest {

    @Test
    fun `a zip archive is recognised by its local header or by the end record of an empty one`() {
        assertTrue(LibraryFiles.isZipArchive(byteArrayOf(0x50, 0x4b, 3, 4, 20, 0)))
        assertTrue(LibraryFiles.isZipArchive(byteArrayOf(0x50, 0x4b, 5, 6)))
        assertFalse(LibraryFiles.isZipArchive(byteArrayOf(0x50, 0x4b, 7, 8)))
        assertFalse(LibraryFiles.isZipArchive(byteArrayOf(0x50, 0x4b, 3)))
        assertFalse(LibraryFiles.isZipArchive("%PDF-1.7".encodeToByteArray()))
    }

    @Test
    fun `an archive is known by its extension in any case`() {
        assertTrue(LibraryFiles.isArchiveFileName("Library.SBPBACKUP"))
        assertTrue(LibraryFiles.isArchiveFileName("set.sbp"))
        assertTrue(LibraryFiles.isArchiveFileName("Sets.OSBS"))
        assertTrue(LibraryFiles.isArchiveFileName("World Night.ossb"))
        assertTrue(LibraryFiles.isArchiveFileName("Songs.ZIP"))
        assertFalse(LibraryFiles.isArchiveFileName("song.docx"))
        assertFalse(LibraryFiles.isArchiveFileName("zip"))
    }

    @Test
    fun `an importable file is known by its extension in any case`() {
        listOf("song.CHO", "song.chordpro", "notes.txt", "Sheet.PDF", "sheet.docx", "old.doc", "backup.json", "Library.sbpbackup", "songs.zip", "Song.onsong", "Tunes.ABC", "World Night.osts")
            .forEach { assertTrue(LibraryFiles.isImportableFileName(it), it) }
        listOf("cover.jpg", "song.cho.bak", "README").forEach { assertFalse(LibraryFiles.isImportableFileName(it), it) }
    }

    @Test
    fun `a collision suffix is taken off in either shape`() {
        assertEquals("artist-title", LibraryFiles.withoutCollisionSuffix("artist-title_2"))
        assertEquals("artist-title", LibraryFiles.withoutCollisionSuffix("artist-title_12"))
        assertEquals("artist-title", LibraryFiles.withoutCollisionSuffix("artist-title (2)"))
    }

    @Test
    fun `a name with no collision suffix or nothing in front of one or has no family`() {
        assertNull(LibraryFiles.withoutCollisionSuffix("artist-title"))
        assertNull(LibraryFiles.withoutCollisionSuffix("_2"))
        assertNull(LibraryFiles.withoutCollisionSuffix("title_"))
        assertNull(LibraryFiles.withoutCollisionSuffix("title_2b"))
    }

    @Test
    fun `a library file is named by its content and an allowed extension - a picture's name being an attachment's too`() {
        val digits = "3fa9c2e81b7d4f06"

        assertTrue(LibraryFiles.isLibraryAttachmentName("$digits.pdf"))
        assertTrue(LibraryFiles.isLibraryAttachmentName("$digits.jpeg"))
        assertTrue(LibraryFiles.isLibraryAttachmentName("$digits.png"))
        assertTrue(LibraryFiles.isLibraryImageName("$digits.png"))
        assertFalse(LibraryFiles.isLibraryImageName("$digits.pdf"))
        assertFalse(LibraryFiles.isLibraryAttachmentName("$digits.exe"))
        assertFalse(LibraryFiles.isLibraryAttachmentName("$digits.html"))
        assertFalse(LibraryFiles.isLibraryAttachmentName("$digits.PDF"))
        assertFalse(LibraryFiles.isLibraryAttachmentName("$digits"))
        assertFalse(LibraryFiles.isLibraryAttachmentName("${digits.take(15)}.pdf"))
        assertFalse(LibraryFiles.isLibraryAttachmentName("Rehearsal take.pdf"))
        assertTrue(LibraryFiles.isLibraryFileName("$digits.mp3"))
        assertTrue(LibraryFiles.isLibraryFileName("$digits.webp"))
        assertTrue(LibraryFileKind.FILE.matches("$digits.mscz"))
        assertFalse(LibraryFileKind.FILE.matches("$digits.sh"))
    }

    @Test
    fun `a library file is what its name says only where its content hashes to that name`() {
        val hash = "3fa9c2e81b7d4f06" + "0".repeat(48)

        assertTrue(LibraryFiles.isLibraryFileNamedFor("3fa9c2e81b7d4f06.pdf", hash))
        assertTrue(LibraryFiles.isLibraryFileNamedFor("3fa9c2e81b7d4f06.png", hash))
        assertFalse(LibraryFiles.isLibraryFileNamedFor("3fa9c2e81b7d4f07.pdf", hash))
        assertFalse(LibraryFiles.isLibraryFileNamedFor("3fa9c2e81b7d4f06.exe", hash))
    }
}
