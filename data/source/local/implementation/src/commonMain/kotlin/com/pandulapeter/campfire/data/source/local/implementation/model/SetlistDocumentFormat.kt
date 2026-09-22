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

import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * How a `*.setlist.json` file is read and written. The fields this version knows go through the serializer. Every
 * other member of the document and of each song is kept in `unknownFields` and written back after them, so a
 * field added by a later version, or by hand, outlives this version changing the setlist. The file is shared with
 * those versions through an export or a sync run.
 */
internal object SetlistDocumentFormat {

    /** Pretty printed because these files are meant to survive an export and be readable (and editable) outside the app. */
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        // A null where a value belongs says nothing, so the field falls back on its default like a missing one does.
        coerceInputValues = true
    }

    private val DOCUMENT_KEYS = SetlistDocument.serializer().descriptor.elementNames.toSet()
    private val SONG_KEYS = SetlistSongDocument.serializer().descriptor.elementNames.toSet()
    private const val SONGS_KEY = "songs"

    /** Throws on a text that is not a setlist document, as the serializer does. */
    fun decode(text: String): SetlistDocument {
        val element = json.parseToJsonElement(text)
        val document = json.decodeFromJsonElement<SetlistDocument>(element)
        // The songs decode one for one from the array, so the index is what pairs each with its own leftovers.
        val songs = (element.jsonObject[SONGS_KEY] as? JsonArray).orEmpty()
        return document.copy(
            unknownFields = element.jsonObject.without(DOCUMENT_KEYS),
            songs = document.songs.mapIndexed { index, song ->
                song.copy(unknownFields = (songs.getOrNull(index) as? JsonObject)?.without(SONG_KEYS) ?: song.unknownFields)
            },
        )
    }

    fun encode(document: SetlistDocument): String {
        val songs = JsonArray(document.songs.map { song -> JsonObject(json.encodeToJsonElement(song).jsonObject + song.unknownFields) })
        val known = json.encodeToJsonElement(document).jsonObject
        // Known first, in the serializer's order, and "songs" replaced where it stands rather than moved to the end. An
        // empty list is left out, as the serializer leaves out every field at its default.
        val withSongs = if (document.songs.isEmpty()) known else known + (SONGS_KEY to songs)
        return json.encodeToString(JsonElement.serializer(), JsonObject(withSongs + document.unknownFields))
    }

    private fun JsonObject.without(keys: Set<String>) = JsonObject(filterKeys { it !in keys })
}
