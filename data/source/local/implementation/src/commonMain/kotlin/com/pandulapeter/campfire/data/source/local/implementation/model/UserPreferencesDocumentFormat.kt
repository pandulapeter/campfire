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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

/** What [UserPreferencesDocumentFormat.decode] made of a text. */
internal data class DecodedUserPreferencesDocument(
    val document: UserPreferencesDocument,
    /**
     * False where something in the text had to be left out to get a document at all. What was left out is lost by the
     * next save, which is why the caller keeps a copy of a text that was not intact.
     */
    val isIntact: Boolean,
)

/**
 * How `preferences.json` is written and read. Reading is per field: the document holds every setting and every saved
 * transposition, and one value of the wrong shape - a hand edit, a field whose type a later version changed - is no
 * reason to lose the rest of them.
 */
internal object UserPreferencesDocumentFormat {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        // A null where a value belongs says nothing, so the field falls back on its default like a missing one does.
        coerceInputValues = true
    }

    fun encode(document: UserPreferencesDocument) = json.encodeToString(document)

    /** Never throws: a text that is not a JSON object at all decodes to the defaults, and is not intact. */
    fun decode(text: String) = try {
        DecodedUserPreferencesDocument(document = json.decodeFromString(text), isIntact = true)
    } catch (_: IllegalArgumentException) {
        DecodedUserPreferencesDocument(document = decodeFieldByField(text), isIntact = false)
    }

    private fun decodeFieldByField(text: String): UserPreferencesDocument {
        val fields = parseObject(text) ?: return UserPreferencesDocument()
        return json.decodeFromJsonElement(JsonObject(fields.mapNotNull { (key, value) -> readableField(key, value) }.toMap()))
    }

    private fun parseObject(text: String) = try {
        json.parseToJsonElement(text) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }

    /**
     * The field if a document holding nothing else decodes, which asks the serializer itself instead of repeating
     * the type of every field here - a field added to the document later is covered without being named. The
     * transpositions and the folded sections are the fields looked into, because they are the ones that are a
     * collection of the user's own choices: a single entry of the wrong shape costs that entry and not the map.
     */
    private fun readableField(key: String, value: JsonElement): Pair<String, JsonElement>? {
        val field = when {
            key == TRANSPOSITIONS_KEY && value is JsonObject -> JsonObject(value.filterValues { it is JsonPrimitive && it.intOrNull != null })
            key == FOLDED_SECTIONS_KEY && value is JsonObject -> JsonObject(
                value.filterValues { it is JsonArray }.mapValues { (_, keys) -> JsonArray((keys as JsonArray).filter { it is JsonPrimitive && it.isString }) }
            )

            else -> value
        }
        return try {
            json.decodeFromJsonElement<UserPreferencesDocument>(JsonObject(mapOf(key to field)))
            key to field
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private const val TRANSPOSITIONS_KEY = "transpositions"
    private const val FOLDED_SECTIONS_KEY = "foldedSections"
}
