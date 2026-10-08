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

import com.pandulapeter.campfire.data.model.domain.UserPreferences

/**
 * The part of [UserPreferences] every device connected to one cloud folder shares: how each song of the library is
 * played when it is opened from the library rather than from a setlist, and the shape the player chose for each chord
 * ([chords], [UserPreferences.chordVoicings]). A setlist's own overrides are in its file and travel with it, and
 * everything else in the preferences - the folded sections included - is one reader's own.
 */
internal data class SyncedPreferences(
    val transpositions: Map<String, Int> = emptyMap(),
    val tempos: Map<String, Int> = emptyMap(),
    val capos: Map<String, Int> = emptyMap(),
    val chords: Map<String, Map<String, String>> = emptyMap(),
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
        chordVoicings = preferences.chordVoicings.flattened().updated(from = since.chords.flattened(), to = chords.flattened()).nested(),
    )

    /**
     * These preferences with every song under the name [spelling] gives it. Where two names of one song both hold a
     * value for one field, the one under a name [isPreferred] says yes to wins, and failing that the first in sort order.
     */
    fun respelled(spelling: (String) -> String, isPreferred: (String) -> Boolean = { false }) = SyncedPreferences(
        transpositions = transpositions.respelled(spelling, isPreferred),
        tempos = tempos.respelled(spelling, isPreferred),
        capos = capos.respelled(spelling, isPreferred),
        chords = chords,
    )

    private fun Map<String, Int>.respelled(spelling: (String) -> String, isPreferred: (String) -> Boolean) =
        entries.groupBy { spelling(it.key) }.mapValues { (_, entries) ->
            (entries.firstOrNull { isPreferred(it.key) } ?: entries.minBy { it.key }).value
        }

    private fun <T> Map<String, T>.updated(from: Map<String, T>, to: Map<String, T>): Map<String, T> {
        val result = toMutableMap()
        (from.keys + to.keys).filter { this[it] == from[it] }.forEach { key ->
            val value = to[key]
            if (value == null) result -= key else result[key] = value
        }
        return result
    }

    /** The chords by instrument and chord as one map, so that they are settled value by value like the songs' fields. */
    private fun Map<String, Map<String, String>>.flattened() = entries.flatMap { (instrument, shapes) ->
        shapes.map { (chord, shape) -> "$instrument$KEY_SEPARATOR$chord" to shape }
    }.toMap()

    private fun Map<String, String>.nested() = entries.groupBy({ it.key.substringBefore(KEY_SEPARATOR) }) {
        it.key.substringAfter(KEY_SEPARATOR) to it.value
    }.mapValues { (_, shapes) -> shapes.toMap() }

    companion object {
        fun of(preferences: UserPreferences) = SyncedPreferences(
            transpositions = preferences.transpositions,
            tempos = preferences.tempos,
            capos = preferences.capos,
            chords = preferences.chordVoicings,
        )

        /** No instrument id has one. */
        private const val KEY_SEPARATOR = '\u0000'
    }
}
