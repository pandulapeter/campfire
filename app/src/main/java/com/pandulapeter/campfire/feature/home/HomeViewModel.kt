package com.pandulapeter.campfire.feature.home

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.google.gson.annotations.SerializedName
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.home.collections.CollectionsFragment
import com.pandulapeter.campfire.feature.home.history.HistoryFragment
import com.pandulapeter.campfire.feature.home.library.LibraryFragment
import com.pandulapeter.campfire.feature.home.manageDownloads.ManageDownloadsFragment
import com.pandulapeter.campfire.feature.home.managePlaylists.ManagePlaylistsFragment
import com.pandulapeter.campfire.feature.home.playlist.PlaylistFragment
import com.pandulapeter.campfire.feature.home.settings.SettingsFragment
import com.pandulapeter.campfire.feature.home.shared.homeChild.HomeChildFragment
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.networking.AnalyticsManager
import com.pandulapeter.campfire.util.onPropertyChanged

/**
 * Handles events and logic for [HomeFragment].
 */
class HomeViewModel(
    analyticsManager: AnalyticsManager,
    private val downloadedSongRepository: DownloadedSongRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val appShortcutManager: AppShortcutManager
) : CampfireViewModel(analyticsManager), Subscriber {
    val playlists = ObservableField<List<Playlist>>()
    val isLibraryReady = ObservableBoolean()
    val hasDownloads = ObservableBoolean()
    var homeNavigationItem: HomeNavigationItem = userPreferenceRepository.navigationItem
        set(value) {
            field = value
            userPreferenceRepository.navigationItem = value
        }

    init {
        isLibraryReady.onPropertyChanged {
            if (it) {
                appShortcutManager.updateAppShortcuts()
            }
        }
    }

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.LibraryCacheUpdated -> isLibraryReady.set(updateType.songInfos.isNotEmpty())
            is UpdateType.DownloadedSongsUpdated -> hasDownloads.set(updateType.downloadedSongIds.isNotEmpty())
            is UpdateType.SongRemovedFromDownloads -> hasDownloads.set(downloadedSongRepository.getDownloadedSongIds().isNotEmpty())
            is UpdateType.SongAddedToDownloads,
            is UpdateType.DownloadSuccessful -> hasDownloads.set(true)
            UpdateType.AllDownloadsRemoved -> hasDownloads.set(false)
            is UpdateType.PlaylistsUpdated -> playlists.set(updateType.playlists)
            is UpdateType.PlaylistsOrderUpdated -> playlists.set(updateType.playlists)
            is UpdateType.NewPlaylistsCreated -> playlists.set(playlists.get().toMutableList().apply { add(updateType.playlists) })
            is UpdateType.PlaylistRenamed -> playlists.get().find { it.id == updateType.playlistId }?.let { oldPlaylist ->
                val newPlaylist = Playlist(oldPlaylist.id, updateType.title, oldPlaylist.songIds)
                playlists.set(playlists.get().toMutableList().apply {
                    val position = indexOf(oldPlaylist)
                    removeAt(position)
                    add(position, newPlaylist)
                })
            }
            is UpdateType.PlaylistDeleted -> playlists.set(playlists.get().toMutableList().apply { removeAt(updateType.position) })
        }
    }

    /**
     * Marks the possible screens the user can reach using the side navigation on the home screen.
     */
    sealed class HomeNavigationItem(val stringValue: String) {
        //TODO: Scroll position, edit mode state, etc are lost when restoring instance state.
        abstract fun getFragment(): HomeChildFragment<*, *>

        object Library : HomeNavigationItem(VALUE_LIBRARY) {
            override fun getFragment() = LibraryFragment()
        }

        object Collections : HomeNavigationItem(VALUE_COLLECTIONS) {
            override fun getFragment() = CollectionsFragment()
        }

        object History : HomeNavigationItem(VALUE_HISTORY) {
            override fun getFragment() = HistoryFragment()
        }

        object Settings : HomeNavigationItem(VALUE_SETTINGS) {
            override fun getFragment() = SettingsFragment()
        }

        class Playlist(@SerializedName("id") val id: Int) : HomeNavigationItem(VALUE_PLAYLIST + id) {
            override fun getFragment() = PlaylistFragment.newInstance(id)
        }

        object ManagePlaylists : HomeNavigationItem(VALUE_MANAGE_PLAYLISTS) {
            override fun getFragment() = ManagePlaylistsFragment()
        }

        object ManageDownloads : HomeNavigationItem(VALUE_MANAGE_DOWNLOADS) {
            override fun getFragment() = ManageDownloadsFragment()
        }

        companion object {
            private const val VALUE_LIBRARY = "library"
            private const val VALUE_COLLECTIONS = "collections"
            private const val VALUE_HISTORY = "history"
            private const val VALUE_SETTINGS = "settings"
            private const val VALUE_PLAYLIST = "playlist_"
            private const val VALUE_MANAGE_PLAYLISTS = "manage_playlists"
            private const val VALUE_MANAGE_DOWNLOADS = "manage_downloads"

            fun fromStringValue(string: String?) = when (string) {
                null, "", VALUE_LIBRARY -> Library
                VALUE_COLLECTIONS -> Collections
                VALUE_HISTORY -> History
                VALUE_SETTINGS -> Settings
                VALUE_MANAGE_PLAYLISTS -> ManagePlaylists
                VALUE_MANAGE_DOWNLOADS -> ManageDownloads
                else -> Playlist(Integer.parseInt(string.removePrefix(VALUE_PLAYLIST)))
            }
        }
    }
}