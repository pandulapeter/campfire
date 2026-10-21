/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.model

import com.pandulapeter.campfire.data.source.local.implementation.mapper.toDocument
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toModel
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A setlist file can carry fields a later version, or its user, added, and changing the setlist here must not drop them. */
internal class SetlistDocumentFormatTest {

    @Test
    fun `fields this version does not know survive a rewrite`() {
        val text = """{"title":"Summer","venue":{"city":"Pécs"},"songs":[{"file":"a.cho","note":"capo 2"},{"file":"b.cho"}]}"""

        val setlist = SetlistDocumentFormat.decode(text).toModel("summer.setlist.json", size = 0, undatedDay = DAY)
        val changed = setlist.copy(isArchived = true, entries = setlist.entries.reversed())
        val rewritten = Json.parseToJsonElement(SetlistDocumentFormat.encode(changed.toDocument())).jsonObject

        assertEquals(JsonObject(mapOf("city" to JsonPrimitive("Pécs"))), rewritten["venue"])
        val songs = rewritten.getValue("songs").jsonArray.map { it.jsonObject }
        assertEquals(listOf("b.cho", "a.cho"), songs.map { it.getValue("file").jsonPrimitive.content })
        assertEquals(JsonPrimitive("capo 2"), songs[1]["note"])
        assertNull(songs[0]["note"])
    }

    @Test
    fun `a document with nothing unknown is written as before`() {
        // What the serializer alone wrote, which is what every setlist file in the field looks like.
        val serializer = Json { prettyPrint = true }
        listOf(
            SetlistDocument(title = "Summer", date = "2026-09-28", songs = listOf(SetlistSongDocument(file = "a.cho", transposition = 2))),
            SetlistDocument(title = "Empty"),
        ).forEach { document -> assertEquals(serializer.encodeToString(document), SetlistDocumentFormat.encode(document)) }
    }

    @Test
    fun `a known field is never kept as unknown`() {
        val document = SetlistDocumentFormat.decode("""{"title":"Summer","isArchived":null,"songs":[{"file":"a.cho","transposition":1}]}""")

        assertTrue(document.unknownFields.isEmpty())
        assertTrue(document.songs.single().unknownFields.isEmpty())
    }

    @Test
    fun `the order older versions wrote is read and dropped`() {
        val document = SetlistDocumentFormat.decode("""{"title":"Summer","priority":3}""")

        assertTrue(document.unknownFields.isEmpty())
        val encoded = SetlistDocumentFormat.encode(document.toModel("summer.setlist.json", size = 0, undatedDay = DAY).toDocument())
        assertFalse("priority" in Json.parseToJsonElement(encoded).jsonObject)
    }

    @Test
    fun `a date that is not text loses only itself`() {
        listOf("20260928", "{}").forEach { date ->
            val document = SetlistDocumentFormat.decode("""{"title":"S","date":$date,"songs":[{"file":"a.cho"}]}""")

            assertNull(document.date)
            assertEquals("S", document.title)
            assertEquals(listOf("a.cho"), document.songs.map { it.file })
        }
    }

    @Test
    fun `a date that is text is kept`() = assertEquals("2026-09-28", SetlistDocumentFormat.decode("""{"title":"S","date":"2026-09-28"}""").date)

    @Test
    fun `a tempo round trips and is left out when null`() {
        val text = """{"title":"S","songs":[{"file":"a.cho","tempo":96},{"file":"b.cho"}]}"""
        val setlist = SetlistDocumentFormat.decode(text).toModel("s.setlist.json", size = 0, undatedDay = DAY)
        assertEquals(listOf(96, null), setlist.entries.map { it.tempo })

        val songs = Json.parseToJsonElement(SetlistDocumentFormat.encode(setlist.toDocument())).jsonObject.getValue("songs").jsonArray
        assertEquals(JsonPrimitive(96), songs[0].jsonObject["tempo"])
        assertFalse("tempo" in songs[1].jsonObject)
    }

    @Test
    fun `a tempo that is not one loses only itself`() {
        listOf("\"fast\"", "96.5", "0", "1000", "{}").forEach { tempo ->
            val document = SetlistDocumentFormat.decode("""{"title":"S","songs":[{"file":"a.cho","tempo":$tempo,"transposition":2}]}""")

            assertNull(document.songs.single().tempo)
            assertEquals(2, document.songs.single().transposition)
            assertEquals("S", document.title)
        }
    }

    @Test
    fun `an unknown member survives beside the tempo`() {
        val text = """{"title":"S","songs":[{"file":"a.cho","tempo":96,"capo":2}]}"""
        val rewritten = SetlistDocumentFormat.encode(SetlistDocumentFormat.decode(text).toModel("s.setlist.json", size = 0, undatedDay = DAY).toDocument())
        val song = Json.parseToJsonElement(rewritten).jsonObject.getValue("songs").jsonArray.single().jsonObject

        assertEquals(JsonPrimitive(96), song["tempo"])
        assertEquals(JsonPrimitive(2), song["capo"])
    }

    @Test
    fun `a setlist past the token budget is refused before parsing`() {
        assertFailsWith<IllegalArgumentException> {
            SetlistDocumentFormat.decode("""{"title":"x","songs":[""" + List(40_000) { """{"file":"a.cho"}""" }.joinToString(",") + "]}")
        }
    }

    @Test
    fun `a setlist nested past the parser's stack is refused rather than overflowing it`() {
        assertFailsWith<IllegalArgumentException> {
            SetlistDocumentFormat.decode("""{"title":"x","songs":[{"file":"a.cho","z":""" + "[".repeat(100_000) + "]".repeat(100_000) + "}]}")
        }
    }

    @Test
    fun `an ordinary setlist of a few hundred songs still decodes`() {
        val songs = List(500) { """{"file":"song_$it.cho","transposition":2,"tempo":96,"capo":1}""" }

        assertEquals(500, SetlistDocumentFormat.decode("""{"title":"x","songs":[${songs.joinToString(",")}]}""").songs.size)
    }

    private companion object {
        val DAY = LocalDate(2026, 1, 1)
    }
}
