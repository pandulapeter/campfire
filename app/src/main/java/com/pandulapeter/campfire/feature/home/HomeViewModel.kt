package com.pandulapeter.campfire.feature.home

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.google.gson.annotations.SerializedName
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

/**
 * Handles events and logic for [HomeActivity].
 */
class HomeViewModel(downloadedSongRepository: DownloadedSongRepository,
                    playlistRepository: PlaylistRepository,
                    private val userPreferenceRepository: UserPreferenceRepository) : CampfireViewModel(), Subscriber {
    val playlists = ObservableField<List<Playlist>>(playlistRepository.getPlaylists())
    val hasDownloads = ObservableBoolean(downloadedSongRepository.getDownloadedSongIds().isNotEmpty())
    var navigationItem: NavigationItem = userPreferenceRepository.navigationItem
        set(value) {
            field = value
            userPreferenceRepository.navigationItem = value
        }

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.InitialUpdate -> if (updateType.repositoryClass == PlaylistRepository::class) {
                playlists.notifyChange()
            }
            is UpdateType.PlaylistsUpdated -> playlists.set(updateType.playlists)
            is UpdateType.DownloadedSongsUpdated -> hasDownloads.set(updateType.downloadedSongIds.isNotEmpty())
            is UpdateType.DownloadSuccessful -> hasDownloads.set(true)
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