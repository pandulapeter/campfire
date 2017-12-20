package com.pandulapeter.campfire.feature.home

import android.databinding.ObservableField
import com.google.gson.annotations.SerializedName
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

/**
 * Handles events and logic for [HomeActivity].
 */
class HomeViewModel(private val userPreferenceRepository: UserPreferenceRepository,
                    private val playlistRepository: PlaylistRepository) : CampfireViewModel(), Subscriber {
    val playlists = ObservableField<List<Playlist>>()
    var navigationItem: NavigationItem = userPreferenceRepository.navigationItem
        set(value) {
            field = value
            userPreferenceRepository.navigationItem = value
        }

    override fun onUpdate(updateType: UpdateType) {
        playlists.set(playlistRepository.getPlaylists())
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
        object ManagePlaylists: NavigationItem()
        object ManageDownloads: NavigationItem()
    }
}