package com.pandulapeter.campfire.shared.ui.navigation

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
     * Full screen pager of the given songs. When opened from a setlist, [setlistId] is set so that transpositions are
     * stored per setlist.
     */
    data class SongDetails(
        val songIds: List<String>,
        val setlistId: String?,
        val initialIndex: Int
    ) : CampfireDestination {

        override val contentKey get() = "songDetails|$setlistId|$initialIndex|${songIds.joinToString(separator = ",")}"
    }
}
