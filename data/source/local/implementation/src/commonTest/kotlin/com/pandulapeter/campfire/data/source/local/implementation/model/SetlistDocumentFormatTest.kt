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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A setlist file can carry fields a later version, or its user, added, and changing the setlist here must not drop them. */
internal class SetlistDocumentFormatTest {

    @Test
    fun fieldsThisVersionDoesNotKnowSurviveARewrite() {
        val text = """{"title":"Summer","venue":{"city":"Pécs"},"songs":[{"file":"a.cho","note":"capo 2"},{"file":"b.cho"}]}"""

        val setlist = SetlistDocumentFormat.decode(text).toModel("summer.setlist.json")
        val changed = setlist.copy(isArchived = true, entries = setlist.entries.reversed())
        val rewritten = Json.parseToJsonElement(SetlistDocumentFormat.encode(changed.toDocument())).jsonObject

        assertEquals(JsonObject(mapOf("city" to JsonPrimitive("Pécs"))), rewritten["venue"])
        val songs = rewritten.getValue("songs").jsonArray.map { it.jsonObject }
        assertEquals(listOf("b.cho", "a.cho"), songs.map { it.getValue("file").jsonPrimitive.content })
        assertEquals(JsonPrimitive("capo 2"), songs[1]["note"])
        assertNull(songs[0]["note"])
    }

    @Test
    fun aDocumentWithNothingUnknownIsWrittenAsBefore() {
        // What the serializer alone wrote, which is what every setlist file in the field looks like.
        val serializer = Json { prettyPrint = true }
        listOf(
            SetlistDocument(title = "Summer", priority = 3, songs = listOf(SetlistSongDocument(file = "a.cho", transposition = 2))),
            SetlistDocument(title = "Empty"),
        ).forEach { document -> assertEquals(serializer.encodeToString(document), SetlistDocumentFormat.encode(document)) }
    }

    @Test
    fun aKnownFieldIsNeverKeptAsUnknown() {
        val document = SetlistDocumentFormat.decode("""{"title":"Summer","isArchived":null,"songs":[{"file":"a.cho","transposition":1}]}""")

        assertTrue(document.unknownFields.isEmpty())
        assertTrue(document.songs.single().unknownFields.isEmpty())
    }
}
