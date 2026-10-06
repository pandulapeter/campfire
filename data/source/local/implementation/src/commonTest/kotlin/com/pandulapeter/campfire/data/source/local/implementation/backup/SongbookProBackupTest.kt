/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.backup

import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocumentFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** SongbookPro's backup, in the shape its version 1.0 `dataFile.txt` has been seen written. */
internal class SongbookProBackupTest {

    @Test
    fun songsGetAHeaderFromWhatSongbookProKeepsBesideTheirText() {
        val files = read(
            """{"Id": 1, "name": "Amazing Grace", "author": "John Newton", "Capo": "2", "key": "5", "TempoInt": "72",
            "timeSig": "3/4", "Duration": "217", "Copyright": "Public Domain", "Url": "https://example.com/grace", "Deleted": "False",
            "_folders": "[1]", "content": "{c: Verse 1}\nAmazing[D] Grace\n"}""",
            folders = """[{"Id": 1, "Name": "Hymns", "Deleted": 0}]""",
        )

        assertEquals(listOf("Amazing Grace.cho"), files.map { it.name })
        assertEquals(
            """
            {title: Amazing Grace}
            {artist: John Newton}
            {key: D}
            {capo: 2}
            {tempo: 72}
            {time: 3/4}
            {duration: 3:37}
            {copyright: Public Domain}
            {meta: link https://example.com/grace}
            {tag: Hymns}
            {c: Verse 1}
            Amazing[D] Grace

            """.trimIndent(),
            files.single().bytes.decodeToString(),
        )
    }

    @Test
    fun whatTheTextDeclaresItselfIsNotDeclaredAgain() {
        val files = read("""{"Id": 1, "name": "Title", "key": 3, "Capo": 0, "TempoInt": 0, "content": "{t: Own title}\n{key: Am}\n[Am]La"}""")

        assertEquals("{t: Own title}\n{key: Am}\n[Am]La\n", files.single().bytes.decodeToString())
    }

    @Test
    fun deletedSongsAndSongsWithoutTextAreLeftOutAndRepeatedTitlesAreNumbered() {
        val files = read(
            """{"Id": 1, "name": "Song", "content": "a"}""",
            """{"Id": 2, "name": "Song", "content": "b"}""",
            """{"Id": 3, "name": "Gone", "content": "c", "Deleted": "True"}""",
            """{"Id": 4, "name": "Picture", "content": ""}""",
            """{"Id": 5, "name": ".hidden/one", "content": "d"}""",
        )

        assertEquals(listOf("Song.cho", "Song 2.cho", "hidden one.cho"), files.map { it.name })
    }

    @Test
    fun setsBecomeSetlistsOfTheSongsInOrderWithTheCapoWhereItDiffers() {
        val files = read(
            """{"Id": 1, "name": "First", "Capo": 2, "content": "a"}""",
            """{"Id": 2, "name": "Second", "Capo": 0, "content": "b"}""",
            sets = """[{"details": {"Id": 7, "name": "", "date": "2021-08-08T00:00:00.000", "Deleted": 0}, "contents": [
                {"Order": 1, "SongId": 1, "Capo": 2, "ItemType": 1, "Deleted": 0},
                {"Order": 0, "SongId": 2, "Capo": 3, "ItemType": 1, "Deleted": 0},
                {"Order": 2, "SongId": 99, "Capo": 0, "ItemType": 1, "Deleted": 0},
                {"Order": 3, "SongId": 1, "Capo": 0, "ItemType": 1, "Deleted": 1}
            ]}]""",
        )

        val setlist = files.single { it.name.endsWith(".setlist.json") }
        assertEquals("2021-08-08.setlist.json", setlist.name)
        val document = SetlistDocumentFormat.decode(setlist.bytes.decodeToString())
        assertEquals("2021-08-08", document.title)
        assertEquals("2021-08-08", document.date)
        assertEquals(listOf("Second.cho" to 3, "First.cho" to null), document.songs.map { it.file to it.capo })
    }

    @Test
    fun anArchiveWithoutSongbookProsDocumentIsNotASongbookProLibrary() {
        assertNull(SongbookProBackup.read(listOf(ImportedFile("song.cho", "{title: A}".encodeToByteArray()))))
        assertNull(SongbookProBackup.read(listOf(ImportedFile(SongbookProBackup.DATA_FILE_NAME, "Just some notes".encodeToByteArray()))))
    }

    private fun read(vararg songs: String, folders: String = "[]", sets: String = "[]") = SongbookProBackup.read(
        listOf(
            ImportedFile(
                name = SongbookProBackup.DATA_FILE_NAME,
                bytes = "1.0\n{\"songs\": [${songs.joinToString()}], \"sets\": $sets, \"folders\": $folders}".encodeToByteArray(),
            ),
        ),
    )!!
}
