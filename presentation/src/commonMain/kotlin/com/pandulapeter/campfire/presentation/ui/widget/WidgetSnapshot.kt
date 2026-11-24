/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.widget

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the home screen widgets show, written by the app as `snapshot.json` and read by the widgets alone: they never
 * read the library, parse ChordPro, or start Koin. Not a stored document: the widgets always ship with the app that
 * writes it, so it has no version and a reader ignores keys it does not know. The iOS widget extension decodes the same
 * JSON in Swift (`app/ios`'s `CampfireWidgets`), so a renamed or retyped key is renamed there too.
 *
 * @property songs Every song of the library, filters left out, in the songs screen's order.
 * @property setlists Every setlist, in the setlists screen's order, the archived ones last.
 * @property areSongsEnabled The Features switch: off, both widgets say so instead of drawing from a library the app hides.
 * @property areSetlistsEnabled The Setlists switch, off with the songs too.
 * @property homeLink The app link that opens the app on its home screen.
 * @property setlistsLink The app link that opens the setlists screen.
 * @property strings Every text the widgets show, already in the app's language rather than the system's.
 */
@Serializable
data class WidgetSnapshot(
    val songs: List<Song>,
    val setlists: List<Setlist>,
    val areSongsEnabled: Boolean,
    val areSetlistsEnabled: Boolean,
    val homeLink: String,
    val setlistsLink: String,
    val strings: Strings,
) {

    /** A song, with the app link ([link]) that opens its details screen. */
    @Serializable
    data class Song(
        val fileName: String,
        val title: String,
        val artist: String,
        val link: String,
    )

    /**
     * A setlist.
     *
     * @property date Its day as `yyyy-MM-dd`, which the widgets count days to.
     * @property formattedDate The same day as the app writes it in its language.
     * @property songFileNames Its songs the library holds, in order: [songs] names each.
     * @property link The app link that opens its first song read from it, or for a setlist with none, the setlists screen
     *   with it brought into sight.
     */
    @Serializable
    data class Setlist(
        val fileName: String,
        val title: String,
        val date: String,
        val formattedDate: String,
        val isArchived: Boolean,
        val songFileNames: List<String>,
        val link: String,
    )

    /**
     * The widgets' texts. A counted one is a [Plural]; [isRightToLeft] is whether the app's language is written from the
     * right.
     */
    @Serializable
    data class Strings(
        val isRightToLeft: Boolean,
        val randomPicks: String,
        val drawNewPicks: String,
        val emptyLibrary: String,
        val songsOff: String,
        val setlistsOff: String,
        val nextGig: String,
        val nextGigDescription: String,
        val noUpcomingSetlist: String,
        val setlistMissing: String,
        val chooseSetlist: String,
        val today: String,
        val tomorrow: String,
        val yesterday: String,
        val inDays: Plural,
        val daysAgo: Plural,
        val songCount: Plural,
    )

    /**
     * A counted text, as the app's language reads each count from 0 to [byCount]'s last index (its plural rule worked
     * out by the app, so that no widget needs one): a larger count reads as the one with the same last two digits past
     * 100, which every language of the app's rule agrees with for a count of days or songs.
     */
    @Serializable
    data class Plural(val byCount: List<String>) {

        /** The text for [count], its `%1$d` filled in. */
        fun format(count: Int): String {
            val index = if (count < byCount.size) count else PERIODIC_START + count % PERIOD
            return byCount.getOrElse(index.coerceAtLeast(0)) { byCount.lastOrNull().orEmpty() }.replace(COUNT_PLACEHOLDER, count.toString())
        }

        companion object {

            /** How many counts [byCount] holds, from 0. */
            internal const val SIZE = 200
            private const val PERIODIC_START = 100
            private const val PERIOD = 100
            private const val COUNT_PLACEHOLDER = "%1\$d"
        }
    }

    /** The snapshot as `snapshot.json` holds it. */
    fun encode() = JSON.encodeToString(serializer(), this)

    companion object {

        private val JSON = Json { ignoreUnknownKeys = true }

        /** The snapshot [json] holds, or null where it is not one (a file cut short, or one from no known version). */
        fun decode(json: String) = try {
            JSON.decodeFromString(serializer(), json)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
