package com.pandulapeter.campfire.feature.home.history

import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.library.HeaderItemDecoration
import com.pandulapeter.campfire.feature.home.shared.ElevationItemTouchHelperCallback
import com.pandulapeter.campfire.feature.home.shared.SongListFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.visibleOrInvisible
import org.koin.android.ext.android.inject

class HistoryFragment : SongListFragment<HistoryViewModel>(), AlertDialogFragment.OnDialogItemsSelectedListener {

    companion object {
        private const val DIALOG_ID_DELETE_ALL_CONFIRMATION = 5
    }

    private val firstTimeUserExperienceManager by inject<FirstTimeUserExperienceManager>()
    override val viewModel = HistoryViewModel { mainActivity.openLibraryScreen() }
    private val deleteAllButton by lazy {
        mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_delete_24dp) {
            AlertDialogFragment.show(
                DIALOG_ID_DELETE_ALL_CONFIRMATION,
                childFragmentManager,
                R.string.history_clear_confirmation_title,
                R.string.history_clear_confirmation_message,
                R.string.history_clear_confirmation_clear,
                R.string.cancel
            )
        }.apply { visibleOrInvisible = false }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeRefreshLayout.isEnabled = false
        defaultToolbar.updateToolbarTitle(R.string.home_history)
        mainActivity.updateToolbarButtons(listOf(deleteAllButton))
        viewModel.shouldShowDeleteAll.onPropertyChanged(this) {
            deleteAllButton.visibleOrInvisible = it
            if (it && !firstTimeUserExperienceManager.historyCompleted) {
                showHint(
                    message = R.string.history_hint,
                    action = { firstTimeUserExperienceManager.historyCompleted = true }
                )
            }
        }
        viewModel.shouldInvalidateItemDecorations.onEventTriggered { binding.recyclerView.invalidateItemDecorations() }
        binding.recyclerView.addItemDecoration(object : HeaderItemDecoration(mainActivity) {

            override fun isHeader(position: Int) = position >= 0 && viewModel.isHeader(position)

            override fun getHeaderTitle(position: Int) = if (position >= 0) getString(viewModel.getHeaderTitle(position)) else ""
        })
        ItemTouchHelper(object : ElevationItemTouchHelperCallback((mainActivity.dimension(R.dimen.content_padding)).toFloat(), 0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                viewHolder?.adapterPosition?.let { position ->
                    if (position != RecyclerView.NO_POSITION) {
                        firstTimeUserExperienceManager.historyCompleted = true
                        val song = viewModel.adapter.items[position].song
                        showSnackbar(
                            message = getString(R.string.history_song_removed_message, song.title),
                            actionText = R.string.undo,
                            action = { viewModel.cancelDeleteSong() },
                            dismissAction = { viewModel.deleteSongPermanently() }
                        )
                        binding.root.post { viewModel.deleteSongTemporarily(song.id) }
                    }
                }
            }
        }).attachToRecyclerView(binding.recyclerView)
    }

    override fun onPositiveButtonSelected(id: Int) {
        if (id == DIALOG_ID_DELETE_ALL_CONFIRMATION) {
            viewModel.deleteAllSongs()
            showSnackbar(R.string.history_all_songs_removed_message)
        }
    }
}