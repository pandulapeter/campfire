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

import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** What a notes app keeps of its settings: the notes pinned, each note's color, and the app's theme. */
internal data class NotesSettings(
    val pinned: Set<String> = emptySet(),
    val colors: Map<String, String> = emptyMap(),
    val theme: String? = null,
)

/**
 * The folder document of an app that is not Campfire: a notes app's `settings.json`, its entries keyed by the notes of
 * [OtherAppLibrary], the device's values held in [state] (null while they cannot be read).
 *
 * ```json
 * { "version": 1, "notes": { "a.note": { "pinned": true, "color": "red" } }, "theme": "dark" }
 * ```
 *
 * The first version that synced it owned [PINNED] and [THEME]; [COLOR] came later.
 */
internal class NotesSettingsCodec(
    val state: MutableStateFlow<NotesSettings?> = MutableStateFlow(NotesSettings()),
) : MergedFolderDocumentCodec<NotesSettings> {

    override val id = "settings"

    override val fileName = FILE_NAME

    override val ownedFields = OWNED_FIELDS

    override val ownedFieldsWhenUnrecorded = setOf(PINNED, THEME)

    override val keyedBy = OtherAppLibrary.NOTES

    override fun isReadable(document: JsonObject) = document[NOTES] is JsonObject

    override fun isNewerFormat(document: JsonObject) = ((document[VERSION] as? JsonPrimitive)?.intOrNull ?: 1) > 1

    override fun keysOf(document: JsonObject?) = document?.notes?.keys.orEmpty()

    override fun withKeysSpelled(document: JsonObject, spelling: (String) -> String): JsonObject {
        val notes = document.notes ?: return document
        val respelled = notes.entries.sortedBy { it.key }.groupBy { spelling(it.key) }.mapValues { (_, entries) ->
            JsonObject(entries.fold(emptyMap<String, JsonElement>()) { fields, entry -> fields + (entry.value as? JsonObject).orEmpty() })
        }
        return JsonObject(document + (NOTES to JsonObject(respelled)))
    }

    override fun withKeysWhere(document: JsonObject, isKept: (String) -> Boolean): JsonObject {
        val notes = document.notes ?: return document
        return JsonObject(document + (NOTES to JsonObject(notes.filterKeys(isKept))))
    }

    override fun withoutFieldsNotOwnedBy(base: JsonObject, ownedByWriter: Set<String>): JsonObject {
        val noteFields = setOf(PINNED, COLOR) - ownedByWriter
        val notes = base.notes?.mapNotNull { (name, entry) ->
            ((entry as? JsonObject).orEmpty() - noteFields).takeIf { it.isNotEmpty() }?.let { name to JsonObject(it) }
        }?.toMap()
        return JsonObject(base - (setOf(THEME) - ownedByWriter) + listOfNotNull(notes?.let { NOTES to JsonObject(it) }))
    }

    override fun merge(base: JsonObject?, local: JsonObject, remote: JsonObject?): JsonObject {
        val merged = FolderDocumentJson.merge(base, local, remote)
        return withKeysWhere(merged) { merged.notes?.get(it) != JsonObject(emptyMap()) }
    }

    override fun localDocument(base: JsonObject?, values: NotesSettings): JsonObject {
        val readable = base?.let(::valuesOf) ?: NotesSettings()
        val names = keysOf(base) + values.pinned + values.colors.keys
        val notes = names.mapNotNull { name ->
            val understood = setOfNotNull(PINNED.takeIf { name in readable.pinned }, COLOR.takeIf { name in readable.colors })
            val fields = (base?.notes?.get(name) as? JsonObject).orEmpty() - understood + listOfNotNull(
                (PINNED to JsonPrimitive(true)).takeIf { name in values.pinned },
                values.colors[name]?.let { COLOR to JsonPrimitive(it) },
            )
            fields.takeIf { it.isNotEmpty() }?.let { name to JsonObject(it) }
        }.toMap()
        val theme = values.theme?.let { mapOf(THEME to JsonPrimitive(it)) } ?: base?.get(THEME)?.takeIf { readable.theme == null }
            ?.let { mapOf(THEME to it) }.orEmpty()
        return JsonObject(base.orEmpty() - THEME + (VERSION to (base?.get(VERSION) ?: JsonPrimitive(1))) + (NOTES to JsonObject(notes)) + theme)
    }

    override fun valuesOf(document: JsonObject): NotesSettings {
        val notes = document.notes.orEmpty().mapValues { (_, entry) -> (entry as? JsonObject).orEmpty() }
        return NotesSettings(
            pinned = notes.filterValues { (it[PINNED] as? JsonPrimitive)?.booleanOrNull == true }.keys,
            colors = notes.mapNotNull { (name, fields) ->
                (fields[COLOR] as? JsonPrimitive)?.takeIf { it.isString }?.let { name to it.content }
            }.toMap(),
            theme = (document[THEME] as? JsonPrimitive)?.takeIf { it.isString }?.content,
        )
    }

    override fun respelled(values: NotesSettings, spelling: (String) -> String, isPreferred: (String) -> Boolean) = NotesSettings(
        pinned = values.pinned.mapTo(mutableSetOf(), spelling),
        colors = values.colors.entries.groupBy { spelling(it.key) }.mapValues { (_, entries) ->
            (entries.firstOrNull { isPreferred(it.key) } ?: entries.minBy { it.key }).value
        },
        theme = values.theme,
    )

    override val values = state

    override suspend fun load() = state.value != null

    override suspend fun apply(values: NotesSettings, since: NotesSettings, onWritten: (before: NotesSettings, after: NotesSettings) -> Unit) {
        val before = state.value ?: return
        val pinned = before.pinned.toMutableSet().apply {
            (since.pinned + values.pinned).filter { (it in before.pinned) == (it in since.pinned) }.forEach { if (it in values.pinned) add(it) else remove(it) }
        }
        val colors = before.colors.toMutableMap().apply {
            (since.colors.keys + values.colors.keys).filter { before.colors[it] == since.colors[it] }.forEach { name ->
                val color = values.colors[name]
                if (color == null) remove(name) else put(name, color)
            }
        }
        val after = NotesSettings(pinned = pinned, colors = colors, theme = if (before.theme == since.theme) values.theme else before.theme)
        onWritten(before, after)
        state.value = after
    }

    private val JsonObject.notes get() = get(NOTES) as? JsonObject

    companion object {
        const val FILE_NAME = "settings.json"
        private const val VERSION = "version"
        private const val NOTES = "notes"
        const val PINNED = "pinned"
        const val COLOR = "color"
        const val THEME = "theme"
        val OWNED_FIELDS = setOf(PINNED, COLOR, THEME)
    }
}
