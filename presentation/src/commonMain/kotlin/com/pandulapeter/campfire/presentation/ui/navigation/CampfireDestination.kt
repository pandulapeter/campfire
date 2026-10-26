/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * The keys of the Navigation 3 back stack. Top level destinations are the tabs of the navigation bar / rail,
 * [SongDetails] is pushed on top of them.
 *
 * Serializable because the whole stack is written into the view model's saved state: a process the system killed in
 * the background comes back on the screen it was showing, and with it the state Navigation 3 saved for each entry -
 * the text typed into an editor included, which would otherwise be left in the saved state with no entry to claim it.
 */
@Serializable
sealed interface CampfireDestination : NavKey {

    /**
     * The identifier Navigation 3 uses to persist the state of the entry. It must be a type that can be stored in
     * an Android Bundle, so the destinations themselves cannot be used.
     */
    val contentKey: String

    @Serializable
    sealed interface TopLevel : CampfireDestination {

        companion object {
            val entries: List<TopLevel> get() = listOf(Songs, Setlists, Metronome, Tuner, Chords, Scales, Settings)

            /**
             * The top level screens that are tools to reach for while playing rather than places the library is read in,
             * in the order the chrome lists them: where a navigation bar has no room for every top level screen, these are
             * the ones it offers behind one Tools item (see `navigationBarItems`).
             */
            val tools: List<TopLevel> get() = listOf(Metronome, Tuner, Chords, Scales)

            /** The top level screens the navigation chrome offers, which leaves out those of the features switched off. */
            internal fun entries(features: FeatureSwitches) = entries.filter { CampfireDestination.isEnabled(it, features) }

            /**
             * The screen at the bottom of every back stack, which the app opens on and every Back ends on: the first
             * top level screen switched on, so the songs while they are on, and otherwise the first tool. Settings,
             * which no switch takes, is the last one left.
             */
            internal fun home(features: FeatureSwitches) = entries(features).first()

            /**
             * Maps a [NavEntry][androidx.navigation3.runtime.NavEntry] content key back to the destination it
             * belongs to, since the entries only expose their content keys.
             */
            fun fromContentKey(contentKey: Any?): TopLevel? = entries.firstOrNull { it.contentKey == contentKey }
        }
    }

    companion object {

        /**
         * Whether [destination] belongs to a feature that is switched on: the songs screen, a song, and its editor to the
         * songs, the setlists screen and a song read from a setlist to the setlists too, the metronome screen to the
         * metronome, the tuner screen to the tuner, the chords screen to the chord diagrams, the scales screen to the
         * scales. Everything else is always there.
         */
        internal fun isEnabled(destination: CampfireDestination, features: FeatureSwitches) = when (destination) {
            Songs, is SongEditor -> features.areSongsEnabled
            Setlists -> features.areSetlistsEnabled
            Metronome -> features.isMetronomeEnabled
            Tuner -> features.isTunerEnabled
            Chords -> features.isChordLibraryEnabled
            Scales -> features.areScalesEnabled
            is SongDetails -> if (destination.setlistFileName == null) features.areSongsEnabled else features.areSetlistsEnabled
            else -> true
        }
    }

    @Serializable
    data object Songs : TopLevel {
        override val contentKey = "songs"
    }

    @Serializable
    data object Setlists : TopLevel {
        override val contentKey = "setlists"
    }

    @Serializable
    data object Metronome : TopLevel {
        override val contentKey = "metronome"
    }

    @Serializable
    data object Tuner : TopLevel {
        override val contentKey = "tuner"
    }

    /** Every chord the app can draw, browsed by instrument, tuning, key and name, see `ChordsScreen`. */
    @Serializable
    data object Chords : TopLevel {
        override val contentKey = "chords"
    }

    /** Every scale the app knows on the player's instrument, browsed by key and family, see `ScalesScreen`. */
    @Serializable
    data object Scales : TopLevel {
        override val contentKey = "scales"
    }

    @Serializable
    data object Settings : TopLevel {
        override val contentKey = "settings"
    }

    /**
     * Full screen pager of the given songs, identified by their file names. When opened from a setlist,
     * [setlistFileName] is set so that transpositions are stored in that setlist rather than in the preferences.
     *
     * @param songFileNames Names each song once, since the pager is keyed by file name.
     * @param id What the entry is to Navigation 3, generated once per push and kept by [copy]. The file names cannot be
     *   that: renaming a song from this screen rewrites them in place, and an entry keyed on them would be a new
     *   screen sliding in over itself with its scroll position gone. Always written into the saved back stack, since
     *   a default that is left out would be generated anew on the way back in and orphan the entry's saved state.
     * @param importPreview Set for a preview of songs an import has not written yet, which names no file
     *   ([songFileNames] is empty) and is never restored, like [ImportReport]. A preview of one song is rewritten in place
     *   into that song's own screen once it is imported, which the [id] it keeps is what lets it do without a transition.
     */
    @Serializable
    data class SongDetails(
        val songFileNames: List<String>,
        val setlistFileName: String?,
        val initialIndex: Int,
        @OptIn(ExperimentalSerializationApi::class)
        @EncodeDefault
        val id: String = Uuid.random().toString(),
        val importPreview: ImportPreview? = null,
    ) : CampfireDestination {

        override val contentKey get() = "songDetails|$id"
    }

    /**
     * The raw text of one song, opened over whatever was showing.
     *
     * @param fileName The song's file, or [NEW_SONG_FILE_NAME] for a pasted, dropped or shared song that has no file
     *   until its first Save.
     * @param shouldStartInsideFirstSection Where the caret goes: a song that has just been created opens inside the
     *   empty verse its template ends with, an existing one at the top of its text.
     * @param entryKey What the entry is to Navigation 3 in place of [fileName], given to the new song's editor: its
     *   first Save rewrites [fileName] in place, and an entry keyed on it would be a new editor sliding in over itself.
     */
    @Serializable
    data class SongEditor(
        val fileName: String,
        val shouldStartInsideFirstSection: Boolean = false,
        val entryKey: String? = null,
    ) : CampfireDestination {

        // The flag is deliberately not part of this: it says how to open the editor, not which editor it is.
        override val contentKey get() = "$CONTENT_KEY_PREFIX${entryKey ?: fileName}"

        /** Whether this is the editor of a song that has no file yet, see [NEW_SONG_FILE_NAME]. */
        val isNewSong get() = fileName == NEW_SONG_FILE_NAME

        companion object {
            private const val CONTENT_KEY_PREFIX = "songEditor|"

            /**
             * The name the editor, its draft and the unsaved changes question know a song by that has no file yet. No
             * song of the library can have it, since every song file ends in one of the song extensions.
             */
            const val NEW_SONG_FILE_NAME = ":new-song:"

            /** The editor of a song that has no file yet, keyed by an id of its own, see [entryKey]. */
            fun newSong() = SongEditor(fileName = NEW_SONG_FILE_NAME, entryKey = Uuid.random().toString())
        }
    }

    /**
     * What an import that went anywhere but the one way a snackbar is enough for has to say, see
     * `ImportReportState.importReport`: a screen rather than a run of dialogs, so that it has a place on the back stack
     * that a song opened from it returns to, and room for every file a large import is about. There is only ever one,
     * and it is about whatever import the view model holds a report of, so it carries nothing of its own.
     */
    @Serializable
    data object ImportReport : CampfireDestination {
        override val contentKey = "importReport"
    }

    /**
     * The onboarding, one screen whose pages are a pager of its own: the page it is on is `Navigator.onboardingStep`,
     * which a back steps through before it leaves the screen, the way the settings screen's tabs are. What each page
     * shows is read from the preferences, so it carries nothing else.
     *
     * @param isOpenedFromSettings Whether it was opened from the settings screen rather than by a first run, which is
     *   what gives it a Close button instead of Skip and finishes it with Done rather than Start playing.
     */
    @Serializable
    data class Onboarding(
        val isOpenedFromSettings: Boolean,
    ) : CampfireDestination {
        override val contentKey get() = "onboarding"
    }
}
