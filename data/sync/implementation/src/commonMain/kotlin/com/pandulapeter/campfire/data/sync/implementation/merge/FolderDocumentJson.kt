/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.merge

import com.pandulapeter.campfire.data.model.JsonBounds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The bytes of a folder document, and the three-way merge of its JSON tree, which know nothing of what it holds. */
internal object FolderDocumentJson {

    private val json = Json { prettyPrint = true }

    /**
     * Null for a document that is not a JSON object, or is past [JsonBounds], which the run then replaces, keeping this
     * device's values, as if the folder had not changed it. Any other object is returned whatever its shape, so that the
     * codec's `isNewerFormat` sees it.
     */
    fun decode(bytes: ByteArray): JsonObject? {
        val text = bytes.decodeToString()
        // The document comes from a folder anyone can edit by hand, and the parser would overflow the stack or the heap
        // with an `Error` past these bounds. The budget is the shared one, not lower: a refused document is replaced
        // with this device's values, so a real one refused for its size would overwrite every other device's.
        if (!JsonBounds.isWithin(text, maxTokens = JsonBounds.MAX_TOKENS)) return null
        return try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (exception: IllegalArgumentException) {
            null
        }
    }

    /** With every object's keys sorted, so that the same values are always the same bytes, wherever written. */
    fun encode(document: JsonObject) = json.encodeToString(JsonElement.serializer(), document.sorted()).encodeToByteArray()

    private fun JsonElement.sorted(): JsonElement =
        if (this is JsonObject) JsonObject(entries.sortedBy { it.key }.associate { (key, value) -> key to value.sorted() }) else this

    /**
     * Settles [local] and [remote] against [base], what both sides held after the last run - null where no run has
     * synced the document yet, which only ever adds. It goes down the tree and decides every value on its own, so two
     * devices that changed different entries, or different fields of one, both keep their change. Where both sides
     * changed the same value, a change beats a removal, as an edit beats a deletion for a file; two different changes
     * keep this device's, since it is the one in front of the user. Nothing here can keep both, unlike a file: the
     * values are small enough that losing one costs a tap, and a run that wants a clock to decide has none it can trust.
     */
    fun merge(base: JsonObject?, local: JsonObject, remote: JsonObject?): JsonObject =
        (mergeValue(base, local, remote) as? JsonObject) ?: JsonObject(emptyMap())

    private fun mergeValue(base: JsonElement?, local: JsonElement?, remote: JsonElement?): JsonElement? = when {
        local == remote -> local
        local == base -> remote
        remote == base -> local
        local.isObjectOrRemovedFrom(base) && remote.isObjectOrRemovedFrom(base) -> {
            val baseObject = base as? JsonObject
            val localObject = local as? JsonObject ?: JsonObject(emptyMap())
            val remoteObject = remote as? JsonObject ?: JsonObject(emptyMap())
            JsonObject(
                (localObject.keys + remoteObject.keys).mapNotNull { key ->
                    mergeValue(baseObject?.get(key), localObject[key], remoteObject[key])?.let { key to it }
                }.toMap(),
            )
        }
        else -> local ?: remote
    }

    /**
     * An object, or one the base held and this side removed whole, which is merged as an empty object: an entry reset on
     * one side and changed on the other keeps only the other side's change, rather than everything the other side holds
     * for it. Without a base object a removal cannot be told from never having had it, so the merge only adds.
     */
    private fun JsonElement?.isObjectOrRemovedFrom(base: JsonElement?) = this is JsonObject || (this == null && base is JsonObject)
}
