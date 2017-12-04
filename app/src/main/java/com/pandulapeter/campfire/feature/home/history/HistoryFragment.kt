package com.pandulapeter.campfire.feature.home.history

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.HistoryBinding
import com.pandulapeter.campfire.feature.home.playlist.PlaylistViewModel
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment

/**
 * Allows the user to see the history of the songs they opened.
 *
 * Controlled by [PlaylistViewModel].
 */
class HistoryFragment : HomeFragment<HistoryBinding, HistoryViewModel>(R.layout.fragment_history) {

    override fun createViewModel() = HistoryViewModel(callbacks)
}