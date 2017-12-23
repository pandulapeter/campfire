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

/**
 * Handles events and logic for [HomeActivity].
 */
class HomeViewModel(private val downloadedSongRepository: DownloadedSongRepository,
                    private val userPreferenceRepository: UserPreferenceRepository) : CampfireViewModel(), Subscriber {
    val playlists = ObservableField<List<Playlist>>()
    val isLibraryReady = ObservableBoolean()
    val hasDownloads = ObservableBoolean()
    var navigationItem: NavigationItem = userPreferenceRepository.navigationItem
        set(value) {
            field = value
            userPreferenceRepository.navigationItem = value
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
    sealed class NavigationItem {
        object Library : NavigationItem()
        object Collections : NavigationItem()
        object History : NavigationItem()
        object Settings : NavigationItem()
        class Playlist(@SerializedName("id") val id: Int) : NavigationItem()
        object ManagePlaylists : NavigationItem()
        object ManageDownloads : NavigationItem()
    }
}