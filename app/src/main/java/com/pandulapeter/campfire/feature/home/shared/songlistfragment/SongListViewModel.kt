package com.pandulapeter.campfire.feature.home.shared.songlistfragment

import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.Repository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragmentViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoAdapter
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel

/**
 * Parent class for view models that display lists of songs.
 *
 * Handles events and logic for subclasses of [SongListFragment].
 */
abstract class SongListViewModel(homeCallbacks: HomeFragment.HomeCallbacks?,
                                 protected val songInfoRepository: SongInfoRepository,
                                 protected val playlistRepository: PlaylistRepository) : HomeFragmentViewModel(homeCallbacks), Repository.Subscriber {
    val adapter = SongInfoAdapter()

    abstract fun getAdapterItems(): List<SongInfoViewModel>

    override fun onUpdate() {
        adapter.items = getAdapterItems()
    }
}