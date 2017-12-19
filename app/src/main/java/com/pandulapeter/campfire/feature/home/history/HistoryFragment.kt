package com.pandulapeter.campfire.feature.home.history

import com.pandulapeter.campfire.HistoryBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.feature.home.playlist.PlaylistViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListFragment
import javax.inject.Inject

/**
 * Allows the user to see the history of the songs they opened.
 *
 * Controlled by [PlaylistViewModel].
 */
class HistoryFragment : SongListFragment<HistoryBinding, HistoryViewModel>(R.layout.fragment_history) {
    @Inject lateinit var historyRepository: HistoryRepository

    override fun getRecyclerView() = binding.recyclerView

    override fun createViewModel() = HistoryViewModel(callbacks, userPreferenceRepository, songInfoRepository, downloadedSongRepository, playlistRepository, historyRepository)

    override fun onStart() {
        super.onStart()
        historyRepository.subscribe(viewModel)
    }

    override fun onStop() {
        super.onStop()
        historyRepository.unsubscribe(viewModel)
    }
}