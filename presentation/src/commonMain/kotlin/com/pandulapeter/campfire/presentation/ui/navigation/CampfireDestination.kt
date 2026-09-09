package com.pandulapeter.campfire.presentation.ui.navigation

import androidx.navigation3.runtime.NavKey

/**
 * The keys of the Navigation 3 back stack. Top level destinations are the tabs of the navigation bar / rail,
 * [SongDetails] is pushed on top of them.
 */
sealed interface CampfireDestination : NavKey {

    /**
     * The identifier Navigation 3 uses to persist the state of the entry. It must be a type that can be stored in
     * an Android Bundle, so the destinations themselves cannot be used.
     */
    val contentKey: String

    sealed interface TopLevel : CampfireDestination {

        val index: Int
            get() = entries.indexOf(this)

        companion object {
            val entries: List<TopLevel> get() = listOf(Songs, Setlists, Settings)

            /**
             * Maps a [NavEntry][androidx.navigation3.runtime.NavEntry] content key back to the destination it
             * belongs to, since the entries only expose their content keys.
             */
            fun fromContentKey(contentKey: Any?): TopLevel? = entries.firstOrNull { it.contentKey == contentKey }
        }
    }

    data object Songs : TopLevel {
        override val contentKey = "songs"
    }

    data object Setlists : TopLevel {
        override val contentKey = "setlists"
    }

    data object Settings : TopLevel {
        override val contentKey = "settings"
    }

    /**
     * Full screen pager of the given songs, identified by their file names. When opened from a setlist,
     * [setlistFileName] is set so that transpositions are stored in that setlist rather than in the preferences.
     */
    data class SongDetails(
        val songFileNames: List<String>,
        val setlistFileName: String?,
        val initialIndex: Int
    ) : CampfireDestination {

        override val contentKey get() = "songDetails|$setlistFileName|$initialIndex|${songFileNames.joinToString(separator = ",")}"
    }

    /**
     * The raw text of one song, opened over whatever was showing.
     *
     * @param shouldStartInsideFirstSection Where the caret goes: a song that has just been created opens inside the
     *   empty verse its template ends with, an existing one at the top of its text.
     */
    data class SongEditor(
        val fileName: String,
        val shouldStartInsideFirstSection: Boolean = false
    ) : CampfireDestination {

        // The flag is deliberately not part of this: it says how to open the editor, not which editor it is.
        override val contentKey get() = "songEditor|$fileName"
    }
}
