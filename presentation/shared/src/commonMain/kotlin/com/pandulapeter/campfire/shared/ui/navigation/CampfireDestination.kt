package com.pandulapeter.campfire.shared.ui.navigation

import androidx.navigation3.runtime.NavKey

/**
 * The keys of the Navigation 3 back stack. Top level destinations are the tabs of the navigation bar / rail,
 * [SongDetails] is pushed on top of them.
 */
sealed interface CampfireDestination : NavKey {

    sealed interface TopLevel : CampfireDestination {

        val index: Int
            get() = entries.indexOf(this)

        companion object {
            val entries: List<TopLevel> get() = listOf(Songs, Setlists, Settings)
        }
    }

    data object Songs : TopLevel

    data object Setlists : TopLevel

    data object Settings : TopLevel

    /**
     * Full screen pager of the given songs. When opened from a setlist, [setlistId] is set so that transpositions are
     * stored per setlist.
     */
    data class SongDetails(
        val songIds: List<String>,
        val setlistId: String?,
        val initialIndex: Int
    ) : CampfireDestination
}
