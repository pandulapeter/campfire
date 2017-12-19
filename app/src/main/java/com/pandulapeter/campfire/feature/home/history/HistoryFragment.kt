package com.pandulapeter.campfire.feature.home.history

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.HistoryBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListFragment
import com.pandulapeter.campfire.feature.shared.AlertDialogFragment
import com.pandulapeter.campfire.util.onEventTriggered
import javax.inject.Inject

/**
 * Allows the user to see the history of the songs they opened.
 *
 * Controlled by [HistoryViewModel].
 */
class HistoryFragment : SongListFragment<HistoryBinding, HistoryViewModel>(R.layout.fragment_history), AlertDialogFragment.OnDialogItemsSelectedListener {

    @Inject lateinit var historyRepository: HistoryRepository

    override fun getRecyclerView() = binding.recyclerView

    override fun createViewModel() = HistoryViewModel(callbacks, userPreferenceRepository, songInfoRepository, downloadedSongRepository, playlistRepository, historyRepository)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.shouldShowConfirmationDialog.onEventTriggered {
            AlertDialogFragment.show(childFragmentManager,
                R.string.history_clear_confirmation_title,
                R.string.history_clear_confirmation_message,
                R.string.history_clear_confirmation_clear,
                R.string.history_clear_confirmation_cancel)
        }
    }

    override fun onStart() {
        super.onStart()
        historyRepository.subscribe(viewModel)
    }

    override fun onStop() {
        super.onStop()
        historyRepository.unsubscribe(viewModel)
    }

    override fun onPositiveButtonSelected() = viewModel.clearHistory()
}