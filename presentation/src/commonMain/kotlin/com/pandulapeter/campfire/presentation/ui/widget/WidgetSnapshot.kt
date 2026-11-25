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

import com.pandulapeter.campfire.data.model.song.SongLanguage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the home screen widgets show, written by the app as `snapshot.json` and read by the widgets alone: they never
 * read the library, parse ChordPro, or start Koin. Not a stored document: the widgets ship with the app that writes it,
 * so it has no version and a reader ignores keys it does not know. But an updated app's widgets read the file the old
 * one wrote until the app is next opened, so a key added later has a default here and is optional in the iOS widget
 * extension, which decodes the same JSON in Swift (`app/ios`'s `CampfireWidgets`); a renamed or retyped key is renamed
 * there too.
 *
 * @property songs Every song of the library, filters left out, in the songs screen's order.
 * @property setlists Every setlist, in the setlists screen's order, the archived ones last.
 * @property areSongsEnabled The Features switch: off, both widgets say so instead of drawing from a library the app hides.
 * @property areSetlistsEnabled The Setlists switch, off with the songs too.
 * @property homeLink The app link that opens the app on its home screen.
 * @property setlistsLink The app link that opens the setlists screen.
 * @property strings Every text the widgets show, already in the app's language rather than the system's.
 * @property tags Every tag of the library, as the songs screen's filter lists them, for a random picks widget's filter.
 * @property languages Every language of the library, [SongLanguage.UNKNOWN] for the songs that name none, for the same.
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
    val theme: Theme? = null,
    val tags: List<String> = emptyList(),
    val languages: List<Language> = emptyList(),
) {

    /**
     * A song, with the app link ([link]) that opens its details screen, its [tags] as the file writes them, and its
     * [languages] as lowercase ISO codes, empty where it names none. [cover] is the file name of its cover's thumbnail in
     * the covers folder beside the snapshot ([widgetCoverName]), null where it has none to show: no cover, the Cover art
     * switch off, or a cover that could not be had.
     */
    @Serializable
    data class Song(
        val fileName: String,
        val title: String,
        val artist: String,
        val link: String,
        val tags: List<String> = emptyList(),
        val languages: List<String> = emptyList(),
        val cover: String? = null,
    )

    /** A language of the library by its [code], and its [name] in the app's language. */
    @Serializable
    data class Language(
        val code: String,
        val name: String,
    )

    /**
     * A setlist.
     *
     * @property date Its day as `yyyy-MM-dd`, which the widgets count days to.
     * @property formattedDate The same day as the app writes it in its language.
     * @property songFileNames Its songs the library holds, in order: [songs] names each.
     * @property link The app link that opens its first song read from it, or for a setlist with none, the setlists screen
     *   with it brought into sight.
     * @property songLinks The app link of each of [songFileNames], opening the setlist at that song; empty in a snapshot
     *   written before it was part of one.
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
        val songLinks: List<String> = emptyList(),
    )

    /**
     * The widgets' texts. A counted one is a [Plural]; [isRightToLeft] is whether the app's language is written from the
     * right. The setup screens' texts, from [setUpWidget] on, are empty in a snapshot written before they were part of
     * it ([hasSetupTexts]).
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
        val noMatchingSongs: String = "",
        val setUpWidget: String = "",
        val done: String = "",
        val title: String = "",
        val tags: String = "",
        val languages: String = "",
        val randomPicksFilterDescription: String = "",
        val pickCount: String = "",
    ) {

        /** Whether the setup screens' texts are there to show. */
        val hasSetupTexts get() = setUpWidget.isNotEmpty()
    }

    /**
     * How the widgets are painted: in the app's theme rather than the system's, so that they look like the app.
     *
     * @property isDark The app's dark half (true) or its light half (false), or null for whichever the system is in.
     * @property light The light half of the app's palette, its sepia included; null, with [dark], where the app takes the
     *   system's own colors (Android's from the wallpaper), which the widgets then take from the system too.
     */
    @Serializable
    data class Theme(
        val isDark: Boolean?,
        val light: Palette?,
        val dark: Palette?,
    )

    /** The few colors a widget is drawn in, each as `0xAARRGGBB`. */
    @Serializable
    data class Palette(
        val background: Long,
        val accent: Long,
        val text: Long,
        val secondaryText: Long,
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
