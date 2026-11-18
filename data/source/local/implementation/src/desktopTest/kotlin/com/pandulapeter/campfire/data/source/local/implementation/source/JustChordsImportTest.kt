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
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JustChords' `.justchords` export (which OpenSongApp writes its sets as too), read as the JSON its name says it is,
 * picked on its own and inside an archive alike.
 */
class JustChordsImportTest {

    @Test
    fun `a picked JustChords export imports the songs it holds`() = runBlocking {
        ImportApp().use { app ->
            val outcome = app.importPicked(name = "World Night.justchords", bytes = EXPORT.encodeToByteArray())

            assertEquals(null, outcome.failure)
            assertEquals(setOf("songs/anna_bell-river_song.cho", "songs/tom_gray-morning_light.cho"), outcome.library.keys)
            assertTrue("{title: River Song}" in app.libraryText("songs/anna_bell-river_song.cho"))
        }
    }

    @Test
    fun `a JustChords export inside an archive is read for its songs`() = runBlocking {
        val archive = ZipWriter.write(listOf(ZipEntry("Exports/World Night.JUSTCHORDS", EXPORT.encodeToByteArray())))

        val files = ArchiveUnpacks().unpack(archive)

        assertEquals(listOf("River Song.cho", "Morning Light.cho"), files.map { it.name })
    }

    private companion object {
        val EXPORT = """
            {
              "dataVersion": 1,
              "songs": [
                {"id": "1", "title": "River Song", "artist": "Anna Bell", "rawData": "G         D\nDown by the river light\nC         G\nWe sang until morning"},
                {"id": "2", "title": "Morning Light", "artist": "Tom Gray", "rawData": "Am        F\nMorning light is breaking\nC         G\nOver the hills again"}
              ],
              "playlists": [{"title": "World Night", "songs": ["1", "2"]}]
            }
        """.trimIndent()
    }
}
