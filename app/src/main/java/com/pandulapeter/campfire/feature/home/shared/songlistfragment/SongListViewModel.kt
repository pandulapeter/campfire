package com.pandulapeter.campfire.feature.home.shared.songlistfragment

import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.Subscriber
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragmentViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoAdapter

/**
 * Parent class for view models that display lists of songs.
 *
 * Handles events and logic for subclasses of [SongListFragment].
 */
abstract class SongListViewModel(homeCallbacks: HomeFragment.HomeCallbacks?,
                                 protected val songInfoRepository: SongInfoRepository) : HomeFragmentViewModel(homeCallbacks), Subscriber {
    val adapter = SongInfoAdapter()
}