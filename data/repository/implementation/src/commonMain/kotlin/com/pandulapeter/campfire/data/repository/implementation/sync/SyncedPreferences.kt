/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.domain.MetronomeSettings
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The part of [UserPreferences] every device connected to one cloud folder shares: how each song of the library is
 * played when it is opened from the library rather than from a setlist. A setlist's own overrides are in its file and
 * travel with it, and everything else in the preferences - the folded sections included - is one reader's own.
 */
internal data class SyncedPreferences(
    val transpositions: Map<String, Int> = emptyMap(),
    val tempos: Map<String, Int> = emptyMap(),
    val capos: Map<String, Int> = emptyMap(),
) {

    /**
     * [preferences] with what changed from [since] to this applied, value by value. A value that is no longer what it
     * was in [since] was changed on this device while the run was merging, and is kept: it differs from what the run
     * leaves behind as the last synced document, so the next run carries it.
     */
    fun applyTo(preferences: UserPreferences, since: SyncedPreferences) = preferences.copy(
        transpositions = preferences.transpositions.updated(from = since.transpositions, to = transpositions),
        tempos = preferences.tempos.updated(from = since.tempos, to = tempos),
        capos = preferences.capos.updated(from = since.capos, to = capos),
    )

    private fun Map<String, Int>.updated(from: Map<String, Int>, to: Map<String, Int>): Map<String, Int> {
        val result = toMutableMap()
        (from.keys + to.keys).filter { this[it] == from[it] }.forEach { key ->
            val value = to[key]
            if (value == null) result -= key else result[key] = value
        }
        return result
    }

    companion object {
        fun of(preferences: UserPreferences) = SyncedPreferences(
            transpositions = preferences.transpositions,
            tempos = preferences.tempos,
            capos = preferences.capos,
        )
    }
}

/**
 * `preferences.json`, the document at the top of the cloud folder that carries [SyncedPreferences], and the three-way
 * merge every run settles it with.
 *
 * The document is kept as a JSON tree rather than decoded into a class, so that it can grow without an older version
 * losing what a newer one wrote: a song's entry may gain a field, and settings that have nothing to do with the songs
 * may join [SONGS] at the top level, and a version that does not know them passes them through untouched. This
 * version only ever writes the paths it owns ([SONG_FIELDS] under each song of [SONGS]); everything else in the
 * document is carried over from the last synced one ([localDocument]), so to the merge it is simply unchanged here.
 *
 * ```json
 * {
 *   "version": 1,
 *   "songs": {
 *     "green_day-good_riddance.cho": { "transposition": 2, "capo": 1, "tempo": 92 }
 *   }
 * }
 * ```
 *
 * Songs are keyed by file name, as the library keys them, and an entry left with no fields is dropped, so the
 * document only ever names songs that something is set for.
 */
internal object SyncedPreferencesDocument {

    const val FILE_NAME = "preferences.json"

    private const val VERSION = "version"
    private const val FORMAT_VERSION = 1
    private const val SONGS = "songs"
    private const val TRANSPOSITION = "transposition"
    private const val TEMPO = "tempo"
    private const val CAPO = "capo"
    private val SONG_FIELDS = listOf(TRANSPOSITION, TEMPO, CAPO)

    /** The furthest a transposition can sensibly be from the key the song is written in: an octave either way. */
    private val TRANSPOSITION_RANGE = -11..11

    private val json = Json { prettyPrint = true }

    /** Null for a document that is not a JSON object, which the run then replaces as if there were none. */
    fun decode(bytes: ByteArray): JsonObject? = try {
        json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
    } catch (exception: IllegalArgumentException) {
        null
    }

    /** With every object's keys sorted, so that the same preferences are always the same bytes, wherever written. */
    fun encode(document: JsonObject) = json.encodeToString(JsonElement.serializer(), document.sorted()).encodeToByteArray()

    private fun JsonElement.sorted(): JsonElement =
        if (this is JsonObject) JsonObject(entries.sortedBy { it.key }.associate { (key, value) -> key to value.sorted() }) else this

    /**
     * What this device holds, as a document: [base] - the document the last run left behind, or null where there was
     * none - with this version's own fields replaced by [preferences]. Everything else [base] carries stays as it was,
     * since this device has no opinion on it.
     */
    fun localDocument(base: JsonObject?, preferences: SyncedPreferences): JsonObject {
        val baseSongs = base?.get(SONGS) as? JsonObject
        val names = baseSongs?.keys.orEmpty() + preferences.transpositions.keys + preferences.tempos.keys + preferences.capos.keys
        val songs = names.mapNotNull { name ->
            val fields = (baseSongs?.get(name) as? JsonObject).orEmpty() - SONG_FIELDS.toSet() + listOfNotNull(
                preferences.transpositions[name]?.let { TRANSPOSITION to JsonPrimitive(it) },
                preferences.tempos[name]?.let { TEMPO to JsonPrimitive(it) },
                preferences.capos[name]?.let { CAPO to JsonPrimitive(it) },
            )
            fields.takeIf { it.isNotEmpty() }?.let { name to JsonObject(it) }
        }.toMap()
        return JsonObject(base.orEmpty() + (VERSION to (base?.get(VERSION) ?: JsonPrimitive(FORMAT_VERSION))) + (SONGS to JsonObject(songs)))
    }

    /** The values of [document] this version understands; one that is not a value it could have written is left out. */
    fun preferencesOf(document: JsonObject): SyncedPreferences {
        val songs = (document[SONGS] as? JsonObject).orEmpty()
        fun field(name: String, range: IntRange) = songs.mapNotNull { (song, entry) ->
            ((entry as? JsonObject)?.get(name) as? JsonPrimitive)
                ?.takeUnless { it.isString }
                ?.intOrNull
                ?.takeIf { it in range }
                ?.let { song to it }
        }.toMap()
        return SyncedPreferences(
            transpositions = field(TRANSPOSITION, TRANSPOSITION_RANGE).filterValues { it != 0 },
            tempos = field(TEMPO, MetronomeSettings.TEMPO_RANGE),
            capos = field(CAPO, Song.CAPO_RANGE),
        )
    }

    /**
     * [document] without the songs [isKept] says no to, whatever is set for them: a song deleted from the library takes
     * its preferences with it, on this device and in the cloud folder alike.
     */
    fun withSongsWhere(document: JsonObject, isKept: (String) -> Boolean): JsonObject {
        val songs = document[SONGS] as? JsonObject ?: return document
        return JsonObject(document + (SONGS to JsonObject(songs.filterKeys(isKept))))
    }

    /**
     * Settles [local] and [remote] against [base], what both sides held after the last run - null where no run has
     * synced the document yet, which only ever adds. It goes down the tree and decides every value on its own, so two
     * devices that changed different songs, or different fields of one song, both keep their change. Where both sides
     * changed the same value, a change beats a removal, as an edit beats a deletion for a file; two different changes
     * keep this device's, since it is the one in front of the user. Nothing here can keep both, unlike a file: the
     * values are small enough that losing one costs a tap, and a run that wants a clock to decide has none it can trust.
     */
    fun merge(base: JsonObject?, local: JsonObject, remote: JsonObject?): JsonObject {
        val merged = (mergeValue(base, local, remote) as? JsonObject) ?: JsonObject(emptyMap())
        // Each side can take a different field off one song, which leaves an entry neither of them holds any more.
        return withSongsWhere(merged) { (merged[SONGS] as? JsonObject)?.get(it) != JsonObject(emptyMap()) }
    }

    private fun mergeValue(base: JsonElement?, local: JsonElement?, remote: JsonElement?): JsonElement? = when {
        local == remote -> local
        local == base -> remote
        remote == base -> local
        local is JsonObject && remote is JsonObject -> {
            val baseObject = base as? JsonObject
            JsonObject(
                (local.keys + remote.keys).mapNotNull { key ->
                    mergeValue(baseObject?.get(key), local[key], remote[key])?.let { key to it }
                }.toMap(),
            )
        }
        else -> local ?: remote
    }
}
