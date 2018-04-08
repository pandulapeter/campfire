package com.pandulapeter.campfire.old.feature.home

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.google.gson.annotations.SerializedName
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.old.data.model.Playlist
import com.pandulapeter.campfire.old.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.old.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.old.data.repository.shared.Subscriber
import com.pandulapeter.campfire.old.data.repository.shared.UpdateType
import com.pandulapeter.campfire.old.feature.home.managePlaylists.ManagePlaylistsFragment
import com.pandulapeter.campfire.old.feature.home.playlist.PlaylistFragment
import com.pandulapeter.campfire.old.feature.home.shared.homeChild.HomeChildFragment
import com.pandulapeter.campfire.old.feature.shared.CampfireViewModel
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
            is UpdateType.Download.Successful -> hasDownloads.set(true)
            UpdateType.AllDownloadsRemoved -> hasDownloads.set(false)
            is UpdateType.PlaylistsUpdated -> playlists.set(updateType.playlists)
            is UpdateType.PlaylistsOrderUpdated -> playlists.set(updateType.playlists)
            is UpdateType.NewPlaylistsCreated -> playlists.set(playlists.get()?.toMutableList()?.apply { add(updateType.playlists) })
            is UpdateType.PlaylistRenamed -> playlists.get()?.find { it.id == updateType.playlistId }?.let { oldPlaylist ->
                val newPlaylist = Playlist(oldPlaylist.id, updateType.title, oldPlaylist.songIds)
                playlists.set(playlists.get()?.toMutableList()?.apply {
                    val position = indexOf(oldPlaylist)
                    removeAt(position)
                    add(position, newPlaylist)
                })
            }
            is UpdateType.PlaylistDeleted -> playlists.set(playlists.get()?.toMutableList()?.apply { removeAt(updateType.position) })
        }
    }

    /**
     * Marks the possible screens the user can reach using the side navigation on the home screen.
     */
    sealed class HomeNavigationItem(val stringValue: String) {
        //TODO: Scroll position, edit mode state, etc are lost when restoring instance state.
        abstract fun getFragment(): HomeChildFragment<*, *>


        class Playlist(@SerializedName("id") val id: Int) : HomeNavigationItem(VALUE_PLAYLIST + id) {
            override fun getFragment() = PlaylistFragment.newInstance(id)
        }

        object ManagePlaylists : HomeNavigationItem(VALUE_MANAGE_PLAYLISTS) {
            override fun getFragment() = ManagePlaylistsFragment()
        }

        companion object {
            private const val VALUE_PLAYLIST = "playlist_"
            private const val VALUE_MANAGE_PLAYLISTS = "manage_playlists"

            fun fromStringValue(string: String?) = when (string) {
                VALUE_MANAGE_PLAYLISTS -> ManagePlaylists
                else -> Playlist(Integer.parseInt(string?.removePrefix(VALUE_PLAYLIST)))
            }
        }
    }
}