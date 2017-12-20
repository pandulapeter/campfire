package com.pandulapeter.campfire.feature.home.history

import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
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
        // Set up swipe-to-dismiss functionality.
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                viewHolder?.adapterPosition?.let { position ->
                    val songInfo = viewModel.adapter.items[position].songInfo
                    viewModel.removeSongFromHistory(songInfo.id)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
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