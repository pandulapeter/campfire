package com.pandulapeter.campfire.feature.home

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.google.gson.annotations.SerializedName
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.util.onPropertyChanged

/**
 * Handles events and logic for [HomeActivity].
 */
class HomeViewModel(private val downloadedSongRepository: DownloadedSongRepository,
                    private val userPreferenceRepository: UserPreferenceRepository,
                    private val appShortcutManager: AppShortcutManager) : CampfireViewModel(), Subscriber {
    val playlists = ObservableField<List<Playlist>>()
    val isLibraryReady = ObservableBoolean()
    val hasDownloads = ObservableBoolean()
    var navigationItem: NavigationItem = userPreferenceRepository.navigationItem
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
            is UpdateType.AllDownloadsRemoved -> hasDownloads.set(false)
            is UpdateType.PlaylistsUpdated -> playlists.set(updateType.playlists)
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
    sealed class NavigationItem(val stringValue: String) {
        object Library : NavigationItem(VALUE_LIBRARY)
        object Collections : NavigationItem(VALUE_COLLECTIONS)
        object History : NavigationItem(VALUE_HISTORY)
        object Settings : NavigationItem(VALUE_SETTINGS)
        class Playlist(@SerializedName("id") val id: Int) : NavigationItem(VALUE_PLAYLIST + id)
        object ManagePlaylists : NavigationItem(VALUE_MANAGE_PLAYLISTS)
        object ManageDownloads : NavigationItem(VALUE_MANAGE_DOWNLOADS)

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