package com.pandulapeter.campfire.feature.home.history

import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.shared.ElevationItemTouchHelperCallback
import com.pandulapeter.campfire.feature.home.shared.songList.SongListFragment
import com.pandulapeter.campfire.feature.home.shared.songList.SongListItemViewModel
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.feature.shared.dialog.BaseDialogFragment
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.visibleOrGone
import com.pandulapeter.campfire.util.visibleOrInvisible
import org.koin.android.ext.android.inject

class HistoryFragment : SongListFragment<HistoryViewModel>(), BaseDialogFragment.OnDialogItemSelectedListener {

    companion object {
        private const val DIALOG_ID_DELETE_ALL_CONFIRMATION = 5
    }

    private val firstTimeUserExperienceManager by inject<FirstTimeUserExperienceManager>()
    override val viewModel by lazy { HistoryViewModel(mainActivity) { mainActivity.openLibraryScreen() } }
    private val deleteAllButton by lazy {
        mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_delete_24dp) {
            AlertDialogFragment.show(
                DIALOG_ID_DELETE_ALL_CONFIRMATION,
                childFragmentManager,
                R.string.are_you_sure,
                R.string.history_clear_confirmation_message,
                R.string.history_clear_confirmation_clear,
                R.string.cancel
            )
        }.apply { visibleOrInvisible = false }
    }
    override val canScrollToolbar get() = viewModel.shouldShowDeleteAll.get()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        analyticsManager.onTopLevelScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_HISTORY)
        binding.swipeRefreshLayout.isEnabled = false
        defaultToolbar.updateToolbarTitle(R.string.home_history)
        mainActivity.updateToolbarButtons(listOf(deleteAllButton))
        viewModel.shouldShowDeleteAll.onPropertyChanged(this) {
            deleteAllButton.visibleOrGone = it
            showHintIfNeeded()
        }
        ItemTouchHelper(object : ElevationItemTouchHelperCallback((mainActivity.dimension(R.dimen.content_padding)).toFloat(), 0, 0) {

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                viewHolder.adapterPosition.let { position ->
                    if (position != RecyclerView.NO_POSITION && viewModel.adapter.items[position] is SongListItemViewModel.SongViewModel) {
                        return ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                    }
                }
                return 0
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                viewHolder.adapterPosition.let { position ->
                    if (position != RecyclerView.NO_POSITION) {
                        viewModel.deleteSongPermanently()
                        firstTimeUserExperienceManager.historyCompleted = true
                        val song = (viewModel.adapter.items[position] as SongListItemViewModel.SongViewModel).song
                        showSnackbar(
                            message = getString(R.string.history_song_removed_message, song.title),
                            actionText = R.string.undo,
                            action = { viewModel.cancelDeleteSong() },
                            dismissAction = { viewModel.deleteSongPermanently() }
                        )
                        viewModel.deleteSongTemporarily(song.id)
                    }
                }
            }
        }).attachToRecyclerView(binding.recyclerView)
    }

    override fun onResume() {
        super.onResume()
        showHintIfNeeded()
    }

    override fun onPositiveButtonSelected(id: Int) {
        if (id == DIALOG_ID_DELETE_ALL_CONFIRMATION) {
            viewModel.deleteAllSongs()
            showSnackbar(R.string.history_all_songs_removed_message)
        }
    }

    private fun showHintIfNeeded() {
        if (!firstTimeUserExperienceManager.historyCompleted && !isSnackbarVisible() && viewModel.shouldShowDeleteAll.get()) {
            showHint(
                message = R.string.history_hint,
                action = { firstTimeUserExperienceManager.historyCompleted = true }
            )
        }
    }
}