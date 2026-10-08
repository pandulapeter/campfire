/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.domain.MetronomeSettings
import com.pandulapeter.campfire.data.model.domain.Song
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * `preferences.json`, the document at the top of the cloud folder that carries [SyncedPreferences], and the three-way
 * merge every run settles it with.
 *
 * The document is kept as a JSON tree rather than decoded into a class, so that it can grow without an older version
 * losing what a newer one wrote: a song's entry may gain a field, and settings that have nothing to do with the songs
 * may join [SONGS] at the top level, and a version that does not know them passes them through untouched. This
 * version only ever writes the paths it owns ([TRANSPOSITION], [TEMPO] and [CAPO] under each song of [SONGS], and the
 * shape of each chord under its instrument in [CHORDS]), and of those only the values it can read; everything else in
 * the document is carried over from the last synced one ([localDocument]), so to the merge it is simply unchanged here.
 *
 * ```json
 * {
 *   "version": 1,
 *   "songs": {
 *     "green_day-good_riddance.cho": { "transposition": 2, "capo": 1, "tempo": 92 }
 *   },
 *   "chords": {
 *     "guitar": { "F:0.4.7": "x x 3 2 1 1" }
 *   }
 * }
 * ```
 *
 * A chord is keyed by its notes rather than by its name (`C#:0.3.7.10` is every spelling of C#m7), and its shape is
 * whatever the app wrote, which this version does not need to understand to keep: an instrument it does not know is
 * kept as it is, for the version that does. A chord's choice belongs to no song, so none is ever dropped with one.
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
    private const val CHORDS = "chords"

    /** The furthest a transposition can sensibly be from the key the song is written in: an octave either way. */
    private val TRANSPOSITION_RANGE = -11..11

    private val json = Json { prettyPrint = true }

    /**
     * Null for a document that is not a JSON object, which the run then replaces, keeping this device's values, as if the
     * folder had not changed it. Any other object is returned whatever its shape, so that [isNewerFormat] sees it.
     */
    fun decode(bytes: ByteArray): JsonObject? = try {
        json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
    } catch (exception: IllegalArgumentException) {
        null
    }

    /**
     * Whether [document] holds what every document this version writes does, a [SONGS] object. One that does not is
     * replaced like one that does not decode.
     */
    fun isReadable(document: JsonObject) = document[SONGS] is JsonObject

    /**
     * Whether [document] declares a later format than this version writes. Fields a later version adds pass through on
     * their own, so it only bumps [VERSION] for a change that cannot be passed through - which this version must not
     * merge or write over.
     */
    fun isNewerFormat(document: JsonObject) =
        ((document[VERSION] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: FORMAT_VERSION) > FORMAT_VERSION

    /** With every object's keys sorted, so that the same preferences are always the same bytes, wherever written. */
    fun encode(document: JsonObject) = json.encodeToString(JsonElement.serializer(), document.sorted()).encodeToByteArray()

    private fun JsonElement.sorted(): JsonElement =
        if (this is JsonObject) JsonObject(entries.sortedBy { it.key }.associate { (key, value) -> key to value.sorted() }) else this

    /**
     * What this device holds, as a document: [base] - the document the last run left behind, or null where there was
     * none - with the values of this version's own fields that it reads ([preferencesOf]) replaced by [preferences].
     * Everything else [base] carries stays as it was, since this device has no opinion on it - a value of one of those
     * fields that this version cannot read included, unless [preferences] sets that field, since this device never
     * held it and so cannot have removed it.
     */
    fun localDocument(base: JsonObject?, preferences: SyncedPreferences): JsonObject {
        val baseSongs = base?.get(SONGS) as? JsonObject
        val readable = base?.let(::preferencesOf) ?: SyncedPreferences()
        val names = baseSongs?.keys.orEmpty() + preferences.transpositions.keys + preferences.tempos.keys + preferences.capos.keys
        val songs = names.mapNotNull { name ->
            val understood = listOfNotNull(
                TRANSPOSITION.takeIf { name in readable.transpositions },
                TEMPO.takeIf { name in readable.tempos },
                CAPO.takeIf { name in readable.capos },
            )
            val fields = (baseSongs?.get(name) as? JsonObject).orEmpty() - understood.toSet() + listOfNotNull(
                preferences.transpositions[name]?.let { TRANSPOSITION to JsonPrimitive(it) },
                preferences.tempos[name]?.let { TEMPO to JsonPrimitive(it) },
                preferences.capos[name]?.let { CAPO to JsonPrimitive(it) },
            )
            fields.takeIf { it.isNotEmpty() }?.let { name to JsonObject(it) }
        }.toMap()
        val baseChords = base?.get(CHORDS) as? JsonObject
        val chords = (baseChords?.keys.orEmpty() + preferences.chords.keys).mapNotNull { instrument ->
            val baseShapes = (baseChords?.get(instrument) as? JsonObject).orEmpty()
            val fields = baseShapes.filterValues { !it.isReadableShape } + preferences.chords[instrument].orEmpty().mapValues { JsonPrimitive(it.value) }
            fields.takeIf { it.isNotEmpty() }?.let { instrument to JsonObject(it) }
        }.toMap()
        // Written only once there is something in it, so that a document from before the chords is left as it was.
        val chordsMember = if (chords.isEmpty() && baseChords == null) emptyMap() else mapOf(CHORDS to JsonObject(chords))
        return JsonObject(
            base.orEmpty() +
                (VERSION to (base?.get(VERSION) ?: JsonPrimitive(FORMAT_VERSION))) +
                (SONGS to JsonObject(songs)) +
                chordsMember,
        )
    }

    /** A shape this version reads, which is a string: anything else in [CHORDS] is passed through. */
    private val JsonElement.isReadableShape get() = this is JsonPrimitive && isString

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
            chords = (document[CHORDS] as? JsonObject).orEmpty().mapNotNull { (instrument, shapes) ->
                (shapes as? JsonObject)
                    ?.filterValues { it.isReadableShape && (it as JsonPrimitive).content.isNotBlank() }
                    ?.mapValues { (it.value as JsonPrimitive).content }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { instrument to it }
            }.toMap(),
        )
    }

    /** The names of the songs [document] holds an entry for. */
    fun songNamesOf(document: JsonObject?) = (document?.get(SONGS) as? JsonObject)?.keys.orEmpty()

    /**
     * [document] with every song under the name [spelling] gives it. Two entries of one song are collapsed field by
     * field: the fields of the one already under that name win, and among the others those of the first in sort order.
     */
    fun withSongsSpelled(document: JsonObject, spelling: (String) -> String): JsonObject {
        val songs = document[SONGS] as? JsonObject ?: return document
        val respelled = songs.entries.groupBy { spelling(it.key) }.mapValues { (name, entries) ->
            entries.singleOrNull()?.value ?: JsonObject(
                entries
                    .sortedWith(compareBy<Map.Entry<String, JsonElement>> { it.key == name }.thenByDescending { it.key })
                    .fold(emptyMap<String, JsonElement>()) { fields, entry -> fields + (entry.value as? JsonObject).orEmpty() },
            )
        }
        return JsonObject(document + (SONGS to JsonObject(respelled)))
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
        val withoutEmptySongs = withSongsWhere(merged) { (merged[SONGS] as? JsonObject)?.get(it) != JsonObject(emptyMap()) }
        val chords = withoutEmptySongs[CHORDS] as? JsonObject ?: return withoutEmptySongs
        // The same for an instrument each side took a different shape off. Kept out because [localDocument] never writes
        // one, so the next run would read it as a change and upload the document again; the chords member itself stays,
        // since that one [localDocument] does write once a base had it.
        return JsonObject(withoutEmptySongs + (CHORDS to JsonObject(chords.filterValues { it != JsonObject(emptyMap()) })))
    }

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
     * An object, or one the base held and this side removed whole, which is merged as an empty object: a song reset on
     * one side and changed on the other keeps only the other side's change, rather than everything the other side
     * holds for it. Without a base object a removal cannot be told from never having had it, so the merge only adds.
     */
    private fun JsonElement?.isObjectOrRemovedFrom(base: JsonElement?) = this is JsonObject || (this == null && base is JsonObject)
}
