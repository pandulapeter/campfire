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

import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipWriter
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OpenSong's sets, as OpenSongApp exports and backs them up and as OpenSong keeps them in its folders, read into memory
 * and from the staging folder alike (see [ArchiveUnpacks]): each set a setlist pointing at the archive's own songs, and
 * at the rest by title through the import.
 */
class OpenSongSetImportTest {

    private val unpacks = ArchiveUnpacks()

    @Test
    fun `a set bundle points at its own songs by folder even where two share a name`() = runBlocking {
        val bundle = ZipWriter.write(
            listOf(
                ZipEntry("World Night.osts", set(name = "World Night", *WORLD_NIGHT).encodeToByteArray()),
                ZipEntry("Main__River Song.ost", song("River Song", "Anna Bell").encodeToByteArray()),
                // The bundle keeps only a song's innermost folder: this one is in Hymns/Evening/.
                ZipEntry("Evening__River Song.ost", song("River Song", "Tom Gray").encodeToByteArray()),
                ZipEntry("Hymns__morning light.ost", song("morning light", "Choir").encodeToByteArray()),
                ZipEntry("Main__Morning Light.ost", song("Morning Light", "Duo").encodeToByteArray()),
            ),
        )

        val files = unpacks.unpack(bundle)

        val setlist = files.single { it.name == "World Night.setlist.json" }
        val artists = entriesOf(setlist).map { entry -> files.firstOrNull { it.name == entry }?.bytes?.decodeToString()?.let(ARTIST::find)?.groupValues?.get(1) }
        assertEquals(listOf("Tom Gray", "Anna Bell", "Duo", "Choir", null), artists)
        assertEquals(mapOf("Nowhere.cho" to "Nowhere"), setlist.setlistSongTitles)
        assertEquals("World Night", titleOf(setlist))
        assertTrue(files.any { it.name == "World Night: Welcome" && it.bytes.isEmpty() })
    }

    @Test
    fun `a sets backup names every song by title with two of one title kept apart`() = runBlocking {
        val backup = ZipWriter.write(
            listOf(
                ZipEntry("World Night", set(name = "World Night", *WORLD_NIGHT).encodeToByteArray()),
                ZipEntry("Services__Sunday", set(name = null, """<slide_group name="Psalm" type="song" path="Main/"/>""").encodeToByteArray()),
            ),
        )

        val files = unpacks.unpack(backup)

        val worldNight = files.single { it.name == "World Night.setlist.json" }
        assertEquals(listOf("River Song.cho", "River Song 2.cho", "Morning Light.cho", "morning light 2.cho", "Nowhere.cho"), entriesOf(worldNight))
        assertEquals("River Song", worldNight.setlistSongTitles["River Song 2.cho"])
        val sunday = files.single { it.name == "Sunday.setlist.json" }
        assertEquals("Sunday", titleOf(sunday))
        assertEquals(mapOf("Psalm.cho" to "Psalm"), sunday.setlistSongTitles)
    }

    @Test
    fun `OpenSong's own folders zipped up give their sets and songs`() = runBlocking {
        val folders = ZipWriter.write(
            listOf(
                ZipEntry("OpenSong/Songs/Main/River Song", song("River Song", "Anna Bell").encodeToByteArray()),
                ZipEntry("OpenSong/Songs/Hymns/Evening/River Song", song("River Song", "Tom Gray").encodeToByteArray()),
                ZipEntry(
                    "OpenSong/Sets/Gig",
                    set(
                        name = "Gig",
                        """<slide_group name="River Song" type="song" path="Main/"/>""",
                        """<slide_group name="River Song" type="song" path="Hymns/Evening/"/>""",
                    ).encodeToByteArray(),
                ),
            ),
        )

        val files = unpacks.unpack(folders)

        assertEquals(listOf("River Song.cho", "River Song 2.cho", "Gig.setlist.json"), files.map { it.name })
        assertEquals(listOf("River Song.cho", "River Song 2.cho"), entriesOf(files.last()))
        assertTrue("{artist: Tom Gray}" in files[1].bytes.decodeToString())
    }

    @Test
    fun `a set on its own is a setlist whose songs are found by title`() = runBlocking {
        val text = set(name = "World Night", *WORLD_NIGHT)

        listOf(ImportedFile("World Night.osts", text.encodeToByteArray()), ImportedFile("World Night", text.encodeToByteArray(), isPlainTextByContent = true)).forEach { file ->
            val files = unpacks.archiveLocalSource.songsIn(file).orEmpty()

            assertEquals(listOf("World Night.setlist.json", "World Night: Welcome"), files.map { it.name })
            assertEquals(5, files.first().setlistSongTitles.size)
        }
    }

    @Test
    fun `a set picked on its own points at the library's songs of its titles`() = runBlocking {
        ImportApp().use { app ->
            app.importPicked(name = "river.cho", bytes = "{title: River Song}\n{artist: Anna Bell}\n[A]Water".encodeToByteArray())

            val outcome = app.importPicked(name = "World Night.osts", bytes = set(name = "World Night", *WORLD_NIGHT).encodeToByteArray())

            val written = Json.parseToJsonElement(app.libraryText("setlists/world_night.setlist.json")).jsonObject
            val entries = written.getValue("songs").jsonArray.map { it.jsonObject.getValue("file").jsonPrimitive.content }
            // Both of the set's River Songs are the library's only one, which a setlist names once.
            assertEquals(listOf("anna_bell-river_song.cho", "Morning Light.cho", "morning light 2.cho", "Nowhere.cho"), entries)
            assertEquals(listOf("World Night: Welcome"), outcome.plan?.skippedFileNames)
        }
    }

    private fun entriesOf(setlist: ImportedFile) = document(setlist).getValue("songs").jsonArray.map { it.jsonObject.getValue("file").jsonPrimitive.content }

    private fun titleOf(setlist: ImportedFile) = document(setlist).getValue("title").jsonPrimitive.content

    private fun document(setlist: ImportedFile): JsonObject = Json.parseToJsonElement(setlist.bytes.decodeToString()).jsonObject

    private fun set(name: String?, vararg groups: String) =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<set${name?.let { " name=\"$it\"" }.orEmpty()}>\n<slide_groups>\n${groups.joinToString("\n")}\n</slide_groups>\n</set>"

    private fun song(title: String, author: String) =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<song><title>$title</title><author>$author</author><lyrics>[V1]\n.G\n Water</lyrics></song>"

    private companion object {
        val WORLD_NIGHT = arrayOf(
            """<slide_group name="River Song" type="song" path="Hymns/Evening/"/>""",
            """<slide_group name="River Song" type="song" path="Main/"/>""",
            """<slide_group name="Morning Light" type="song" path="Main/"/>""",
            """<slide_group name="morning light" type="song" path="Hymns/"/>""",
            """<slide_group name="# Note # - Welcome" type="custom"><title>Welcome</title><notes>Tuning</notes></slide_group>""",
            """<slide_group name="Nowhere" type="song" path="Main/"/>""",
        )
        val ARTIST = Regex("""\{artist: ([^\}]*)\}""")
    }
}
