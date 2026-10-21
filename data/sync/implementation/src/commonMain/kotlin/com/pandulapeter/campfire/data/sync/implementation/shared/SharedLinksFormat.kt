/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.JsonBounds
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Reads and writes [SharedLinksDocument]s, by hand around the entries so that the members this version does not know
 * survive its edits: devices on different versions edit one list.
 */
internal object SharedLinksFormat {

    /** The document's name in the cloud folder, beside `preferences.json`. */
    const val NAME = "shared-links.json"

    /** The version this one writes. A list of a later version is left alone: neither shown nor edited. */
    const val VERSION = 1

    sealed interface Decoded {

        data class Readable(val document: SharedLinksDocument) : Decoded

        data class FromNewerVersion(val version: Int) : Decoded

        /** Not JSON, or not this shape: hand-edited, or cut short. */
        data object Unreadable : Decoded
    }

    fun decode(text: String): Decoded {
        // The registry comes from a folder anyone can edit by hand, and the parser would overflow the stack or the heap
        // with an `Error` past these bounds. An entry is about 22 tokens, so the shared budget holds far more links than
        // anyone makes, and the 3 levels the document nests are far within the depth.
        if (!JsonBounds.isWithin(text, maxTokens = JsonBounds.MAX_TOKENS)) return Decoded.Unreadable
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return Decoded.Unreadable
        val version = when (val value = root[VERSION_KEY]) {
            null -> VERSION
            else -> (value as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: return Decoded.Unreadable
        }
        if (version > VERSION) return Decoded.FromNewerVersion(version)
        val links = when (val value = root[LINKS_KEY]) {
            null -> JsonArray(emptyList())
            is JsonArray -> value
            else -> return Decoded.Unreadable
        }
        return Decoded.Readable(
            SharedLinksDocument(
                version = version,
                // An entry that is no entry names no copy that can be stopped or followed, so it is dropped.
                links = links.mapNotNull { (it as? JsonObject)?.let(::decodeEntry) },
                unknownFields = root - KNOWN_KEYS,
            ),
        )
    }

    fun encode(document: SharedLinksDocument) = json.encodeToString(JsonElement.serializer(), encodeToElement(document))

    fun encodeToElement(document: SharedLinksDocument) = JsonObject(
        document.unknownFields + mapOf(
            VERSION_KEY to JsonPrimitive(document.version),
            LINKS_KEY to JsonArray(document.links.map(::encodeEntry)),
        ),
    )

    private fun decodeEntry(element: JsonObject) = try {
        json.decodeFromJsonElement<SharedLinksEntry>(element)
            .takeIf { it.shareId.isNotEmpty() }
            ?.copy(unknownFields = element - ENTRY_KEYS)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun encodeEntry(entry: SharedLinksEntry) = JsonObject(entry.unknownFields + json.encodeToJsonElement(entry).jsonObject)

    private const val VERSION_KEY = "version"
    private const val LINKS_KEY = "links"
    private val KNOWN_KEYS = setOf(VERSION_KEY, LINKS_KEY)
    private val ENTRY_KEYS = setOf("shareId", "kind", "fileName", "title", "fileId", "rlkey", "sharedAt", "state", "built", "copyMissing")

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
    }
}
