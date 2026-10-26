/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipWriter
import kotlinx.coroutines.runBlocking
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What comes out of an archive, against the archives the desktops actually produce. */
class ArchiveLocalSourceTest {

    private val archiveLocalSource = ArchiveLocalSourceImpl(Logger.Standard)

    @Test
    fun `documents retain their bytes and docx is not unpacked as a nested archive`() = runBlocking {
        val word = ZipWriter.write(listOf(ZipEntry("word/document.xml", "<document/>".encodeToByteArray())))
        val pdf = "%PDF-1.7".encodeToByteArray()
        val archive = ZipWriter.write(listOf(ZipEntry("sheets/song.docx", word), ZipEntry("sheets/song.pdf", pdf)))
        val files = archiveLocalSource.unpack(archive, ImportLimits.MAX_IMPORT_SIZE)
        assertEquals(listOf("song.docx", "song.pdf"), files.map { it.name })
        assertContentEquals(word, files[0].bytes)
        assertContentEquals(pdf, files[1].bytes)
    }

    @Test
    fun `strips backslash paths the way Windows PowerShell writes them`() = runBlocking {
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("songs\\", ByteArray(0)),
                ZipEntry("songs\\a.cho", "{title: A}".encodeToByteArray()),
            ),
        )

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("a.cho"), files.map { it.name })
    }

    @Test
    fun `unpacks every file with its path stripped`() = runBlocking {
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("songs/a.cho", "{title: A}".encodeToByteArray()),
                ZipEntry("setlists/s.setlist.json", "{}".encodeToByteArray()),
            ),
        )

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("a.cho", "s.setlist.json"), files.map { it.name })
        assertContentEquals("{title: A}".encodeToByteArray(), files.first().bytes)
    }

    @Test
    fun `leaves out what the archiving tool wrote for itself`() = runBlocking {
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("a.cho", "{title: A}".encodeToByteArray()),
                // Every entry the macOS Finder adds to an archive of two songs: an AppleDouble carrying the
                // extended attributes of each file, under the extension of the file it belongs to, and the
                // directory's own listing state.
                ZipEntry("__MACOSX/._a.cho", byteArrayOf(0, 5, 22, 7, 0, 2)),
                ZipEntry("b.cho", "{title: B}".encodeToByteArray()),
                ZipEntry("__MACOSX/._b.cho", byteArrayOf(0, 5, 22, 7, 0, 2)),
                ZipEntry(".DS_Store", byteArrayOf(0, 0, 0, 1)),
                // What a folder's own bookkeeping holds is not part of the library either, however it is named, while a
                // path written through the current directory is an ordinary one.
                ZipEntry(".Trash/old.cho", "{title: Old}".encodeToByteArray()),
                ZipEntry(".dropbox.cache/c.cho", "{title: C}".encodeToByteArray()),
                ZipEntry("./songs/d.cho", "{title: D}".encodeToByteArray()),
            ),
        )

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("a.cho", "b.cho", "d.cho"), files.map { it.name })
    }

    @Test
    fun `unpacks the archives inside an archive`() = runBlocking {
        val inner = ZipWriter.write(listOf(ZipEntry("songs/b.cho", "{title: B}".encodeToByteArray())))
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("a.cho", "{title: A}".encodeToByteArray()),
                ZipEntry("more.zip", inner),
            ),
        )

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("a.cho", "b.cho"), files.map { it.name })
    }

    @Test
    fun `unpacks an archive inside an archive by its bytes under any name but a document's`() = runBlocking {
        val inner = ZipWriter.write(listOf(ZipEntry("songs/b.cho", "{title: B}".encodeToByteArray())))
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("a.cho", "{title: A}".encodeToByteArray()),
                ZipEntry("backup.txt", inner),
                ZipEntry("library.json", inner),
                ZipEntry("sheet.docx", inner),
            ),
        )

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("a.cho", "b.cho", "b.cho", "sheet.docx"), files.map { it.name })
        assertContentEquals(inner, files.last().bytes)
    }

    @Test
    fun `unpacks an archive nested under an unknown name by its bytes`() = runBlocking {
        val inner = ZipWriter.write(listOf(ZipEntry("in.cho", "{title: In}".encodeToByteArray())))
        val names = listOf("folder/backup.bak", "folder/Songs.backup", "folder/x.onsong", "folder/backup")
        val archive = ZipWriter.write(names.map { ZipEntry(it, inner) })

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(List(names.size) { "in.cho" }, files.map { it.name })
        assertTrue(files.all { it.bytes.isNotEmpty() })
    }

    @Test
    fun `a chord sheet under an unknown name inside an archive is read`() = runBlocking {
        val sheet = "G         D\nDown by the river light\n".encodeToByteArray()
        val archive = ZipWriter.write(listOf(ZipEntry("songs/x.songsheet", sheet), ZipEntry("songs/Riverlight", sheet)))

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("x.songsheet", "Riverlight"), files.map { it.name })
        assertTrue(files.all { it.isPlainTextByContent && it.bytes.contentEquals(sheet) })
    }

    @Test
    fun `a JSON document of songs next to a song yields every song, and a setlist is left as it is`() = runBlocking {
        val sheet = "G         D\\nDown by the river light\\nC         G\\nWe sang until morning"
        val songs = "[{\"title\": \"One\", \"content\": \"$sheet\"}, {\"title\": \"Two\", \"content\": \"$sheet\"}]"
        val setlist = "{\"title\": \"Gig\", \"songs\": []}".encodeToByteArray()
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("a.cho", "{title: A}".encodeToByteArray()),
                ZipEntry("songs.json", songs.encodeToByteArray()),
                ZipEntry("gig.setlist.json", setlist),
            ),
        )

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("a.cho", "One.cho", "Two.cho", "gig.setlist.json"), files.map { it.name })
        assertContentEquals(setlist, files.last().bytes)
    }

    @Test
    fun `an OpenSongApp backup yields its songs, without its sets and its database`() = runBlocking {
        val song = "<song><title>Riverlight</title><lyrics>[V1]\n.G           D\n Down by the river</lyrics></song>"
        val set = "<set name=\"Sunday\"><slide_groups><slide_group name=\"Riverlight\" type=\"song\"/></slide_groups></set>"
        val backup = ZipWriter.write(
            listOf(
                ZipEntry("MAIN/Riverlight", song.encodeToByteArray()),
                ZipEntry("Sets/Sunday", set.encodeToByteArray()),
                ZipEntry("Songs.db", byteArrayOf(0x53, 0x51, 0x4C, 0, 1, 2)),
            ),
        )
        val archive = ZipWriter.write(listOf(ZipEntry("OpenSongBackup_2026.osb", backup)))

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("Riverlight.cho", "Sunday", "Songs.db"), files.map { it.name })
        assertTrue("[G]Down by the [D]river" in files.first().bytes.decodeToString())
        assertTrue(files.last().bytes.isEmpty())
    }

    @Test
    fun `a loose extensionless OpenSong file is its song`() = runBlocking {
        val song = "<song><title>Riverlight</title><lyrics>[V1]\n Down by the river</lyrics></song>"

        val files = archiveLocalSource.songsIn(ImportedFile("Riverlight", song.encodeToByteArray(), isPlainTextByContent = true))

        assertEquals(listOf("Riverlight.cho"), files?.map { it.name })
        assertEquals(null, archiveLocalSource.songsIn(ImportedFile("notes", "<p>Hello</p>".encodeToByteArray(), isPlainTextByContent = true)))
    }

    @Test
    fun `OnSong files come out as ChordPro songs named by the files they came in`() = runBlocking {
        val sheet = "G" + " ".repeat(11) + "D\nDown by the river"
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("Riverlight.onsong", "Riverlight\nThe Invented Band\n\n$sheet".encodeToByteArray()),
                ZipEntry("Meadow.onsong", "Title: Meadow\n\n$sheet".encodeToByteArray()),
            ),
        )

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("Riverlight.cho", "Meadow.cho"), files.map { it.name })
        assertTrue(files.all { "[G]Down by the [D]river" in it.bytes.decodeToString() })
        assertTrue("{title: Meadow}" in files.last().bytes.decodeToString())
    }

    @Test
    fun `an ABC file inside an archive comes out as one ChordPro song per tune`() = runBlocking {
        val tunes = "X:1\nT:Lantern Reel\nK:G\n\"G\"GABc|\n\nX:2\nT:Meadow Jig\nK:D\n\"D\"DEF|"
        val archive = ZipWriter.write(listOf(ZipEntry("folk/tunes.abc", tunes.encodeToByteArray())))

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("Lantern Reel.cho", "Meadow Jig.cho"), files.map { it.name })
        assertTrue(files.all { "{start_of_abc}" in it.bytes.decodeToString() && "{start_of_grid}" in it.bytes.decodeToString() })
    }

    @Test
    fun `a loose ABC file is its tunes, by its name or by its content, and a photo renamed abc is left alone`() = runBlocking {
        val tune = "X:1\nT:Lantern Reel\nK:G\n\"G\"GABc|"

        assertEquals(listOf("Lantern Reel.cho"), archiveLocalSource.songsIn(ImportedFile("tunes.abc", tune.encodeToByteArray()))?.map { it.name })
        assertEquals(
            listOf("Lantern Reel.cho"),
            archiveLocalSource.songsIn(ImportedFile("tunes.abc2", tune.encodeToByteArray(), isPlainTextByContent = true))?.map { it.name },
        )
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0, 0)
        assertEquals(null, archiveLocalSource.songsIn(ImportedFile("photo.abc", png)))
    }

    @Test
    fun `a nested docx is still a document`() = runBlocking {
        val word = ZipWriter.write(listOf(ZipEntry("word/document.xml", "<document/>".encodeToByteArray())))
        val archive = ZipWriter.write(listOf(ZipEntry("folder/sheet.docx", word)))

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("sheet.docx"), files.map { it.name })
        assertContentEquals(word, files.single().bytes)
    }

    @Test
    fun `a nested unknown file that is not an archive is reported skipped and costs nothing`() = runBlocking {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + ByteArray(2000)
        val song = "{title: A}".encodeToByteArray()
        val archive = ZipWriter.write(listOf(ZipEntry("x.songsheet", png), ZipEntry("a.cho", song)))

        val files = archiveLocalSource.unpack(archive = archive, maxSize = song.size.toLong())

        assertEquals(listOf("a.cho", "x.songsheet"), files.map { it.name })
        assertContentEquals(song, files.first().bytes)
        assertEquals(ImportedFile.unread("x.songsheet"), files.last())
    }

    @Test
    fun `a zip over the text limit under a text name is unpacked, a text over it is too large`() = runBlocking {
        val large = ByteArray(ImportLimits.MAX_TEXT_FILE_SIZE.toInt() + 1) { 'a'.code.toByte() }
        val backup = ZipWriter.write(listOf(ZipEntry("in.cho", "{title: In}".encodeToByteArray()), ZipEntry("padding.cho", large)))
        val archive = ZipWriter.write(listOf(ZipEntry("backup.txt", backup), ZipEntry("huge.txt", large)))

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("in.cho", "padding.cho", "huge.txt"), files.map { it.name })
        assertFalse(files.first().isTooLarge)
        assertTrue(files[1].isTooLarge)
        assertTrue(files.last().isTooLarge)
    }

    @Test
    fun `reads a SongbookPro backup inside an archive as its songs and sets, without its bookkeeping`() = runBlocking {
        val data = """1.0
            {"songs": [{"Id": 1, "name": "A", "content": "[C]La"}],
            "sets": [{"details": {"Id": 1, "name": "Gig"}, "contents": [{"Order": 0, "SongId": 1, "ItemType": 1}]}], "folders": []}"""
        val backup = ZipWriter.write(
            listOf(
                ZipEntry("settings.hive", byteArrayOf(1, 2, 3)),
                ZipEntry("dataFile.hash", "9ed4cb9934f75a0e".encodeToByteArray()),
                ZipEntry("dataFile.txt", data.encodeToByteArray()),
            ),
        )
        val archive = ZipWriter.write(listOf(ZipEntry("b.cho", "{title: B}".encodeToByteArray()), ZipEntry("Library.sbpbackup", backup)))

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("b.cho", "A.cho", "Gig.setlist.json"), files.map { it.name })
        assertEquals("{title: A}\n[C]La\n", files[1].bytes.decodeToString())
    }

    @Test
    fun `reports a SongbookPro backup whose library cannot be read as one file, without its bookkeeping`() = runBlocking {
        val backup = ZipWriter.write(
            listOf(
                ZipEntry("settings.hive", byteArrayOf(1, 2, 3)),
                ZipEntry("dataFile.hash", "9ed4cb9934f75a0e".encodeToByteArray()),
                ZipEntry("dataFile.txt", "{\"library\": {\"songs\": []}}".encodeToByteArray()),
            ),
        )

        val files = archiveLocalSource.unpack(archive = backup, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("dataFile.txt"), files.map { it.name })
        assertTrue(files.single().bytes.isEmpty())
    }

    @Test
    fun `reports what it did not read instead of failing`() = runBlocking {
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("a.cho", "{title: A}".encodeToByteArray()),
                ZipEntry("notes.png", byteArrayOf(37, 80, 68, 70)),
                ZipEntry("b.cho", "{title: B}".encodeToByteArray()),
            ),
        )

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("a.cho", "b.cho", "notes.png"), files.map { it.name })
        assertEquals(0, files.last().bytes.size)
        assertFalse(files.last().isTooLarge)
    }

    @Test
    fun `stops at the size it was given`() = runBlocking {
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("a.cho", ByteArray(600) { 'a'.code.toByte() }),
                ZipEntry("b.cho", ByteArray(600) { 'b'.code.toByte() }),
            ),
        )

        val files = archiveLocalSource.unpack(archive = archive, maxSize = 1000)

        assertEquals(listOf("a.cho", "b.cho"), files.map { it.name })
        assertEquals(600, files[0].bytes.size)
        assertTrue(files[1].isTooLarge)
    }

    @Test
    fun `counts the damaged entries of a nested archive against the whole import`() = runBlocking {
        val damagedSize = 100
        val damaged = ZipWriter.write(listOf(ZipEntry("x.cho", ByteArray(damagedSize) { 'x'.code.toByte() })))
        // The first byte of the stored content, after the 30 byte local header and the name: the checksum now fails.
        damaged[30 + "x.cho".length] = 'y'.code.toByte()
        val good = ZipWriter.write(listOf(ZipEntry("song.cho", ByteArray(20) { 's'.code.toByte() })))
        val archive = ZipWriter.write(listOf(ZipEntry("a.zip", damaged), ZipEntry("b.zip", good)))

        val files = archiveLocalSource.unpack(archive = archive, maxSize = damaged.size + good.size + damagedSize + 10L)

        assertTrue(files.single { it.name == "song.cho" }.isTooLarge)
    }

    @Test
    fun `reports an archive inside it that cannot be read`() = runBlocking {
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("a.cho", "{title: A}".encodeToByteArray()),
                ZipEntry("more.zip", byteArrayOf(1, 2, 3)),
            ),
        )

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("a.cho", "more.zip"), files.map { it.name })
        assertEquals(ImportedFile.unread("more.zip"), files.last())
    }

    @Test
    fun `undated packs of the same files are identical whenever and wherever they are made`() = runBlocking {
        val files = mapOf(
            "songs/a.cho" to "{title: A}".encodeToByteArray(),
            "setlists/s.setlist.json" to "{}".encodeToByteArray(),
        )
        val defaultTimeZone = TimeZone.getDefault()
        val packs = try {
            listOf("Pacific/Kiritimati", "America/Adak").map { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                archiveLocalSource.pack(files = files, isDated = false)
            }
        } finally {
            TimeZone.setDefault(defaultTimeZone)
        }

        assertContentEquals(packs[0], packs[1])
        assertEquals(listOf("a.cho", "s.setlist.json"), archiveLocalSource.unpack(archive = packs[0], maxSize = ImportLimits.MAX_IMPORT_SIZE).map { it.name })
    }
}
