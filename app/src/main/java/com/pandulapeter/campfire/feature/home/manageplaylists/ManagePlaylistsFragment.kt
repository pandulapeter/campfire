package com.pandulapeter.campfire.feature.home.manageplaylists

import com.pandulapeter.campfire.ManagePlaylistsBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment

/**
 * Allows the user to rearrange or delete playlists.
 *
 * Controlled by [ManagePlaylistsViewModel].
 */
class ManagePlaylistsFragment : HomeFragment<ManagePlaylistsBinding, ManagePlaylistsViewModel>(R.layout.fragment_manage_playlists) {

    override fun createViewModel() = ManagePlaylistsViewModel()
}