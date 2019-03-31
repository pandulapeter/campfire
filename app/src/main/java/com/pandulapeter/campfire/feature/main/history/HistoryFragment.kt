package com.pandulapeter.campfire.feature.main.history

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.main.shared.ElevationItemTouchHelperCallback
import com.pandulapeter.campfire.feature.main.shared.baseSongList.BaseSongListFragment
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.SongItemViewModel
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.feature.shared.dialog.BaseDialogFragment
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.visibleOrGone
import com.pandulapeter.campfire.util.visibleOrInvisible
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class HistoryFragment : BaseSongListFragment<HistoryViewModel>(), BaseDialogFragment.OnDialogItemSelectedListener {

    private val firstTimeUserExperienceManager by inject<FirstTimeUserExperienceManager>()
    override val viewModel by viewModel<HistoryViewModel>()
    private val deleteAllButton by lazy {
        getCampfireActivity()!!.toolbarContext.createToolbarButton(R.drawable.ic_delete) {
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        analyticsManager.onTopLevelScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_HISTORY)
        binding.swipeRefreshLayout.isEnabled = false
        topLevelBehavior.defaultToolbar.updateToolbarTitle(R.string.main_history)
        getCampfireActivity()?.updateToolbarButtons(listOf(deleteAllButton))
        viewModel.shouldShowDeleteAll.observe {
            deleteAllButton.visibleOrGone = it
            showHintIfNeeded()
        }
        viewModel.shouldOpenSongs.observeAndReset { getCampfireActivity()?.openSongsScreen() }
        ItemTouchHelper(object : ElevationItemTouchHelperCallback((requireContext().dimension(R.dimen.content_padding)).toFloat(), 0, 0) {

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun getSwipeDirs(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                viewHolder.adapterPosition.let { position ->
                    if (position != RecyclerView.NO_POSITION && recyclerAdapter?.items?.get(position) is SongItemViewModel && !binding.recyclerView.isAnimating) {
                        return ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                    }
                }
                return 0
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                viewHolder.adapterPosition.let { position ->
                    if (position != RecyclerView.NO_POSITION) {
                        analyticsManager.onSwipeToDismissUsed(AnalyticsManager.PARAM_VALUE_SCREEN_HISTORY)
                        viewModel.deleteSongPermanently()
                        firstTimeUserExperienceManager.historyCompleted = true
                        val song = (recyclerAdapter?.items?.get(position) as SongItemViewModel).song
                        showSnackbar(
                            message = getString(R.string.history_song_removed_message, song.title),
                            actionText = R.string.undo,
                            action = {
                                analyticsManager.onUndoButtonPressed(AnalyticsManager.PARAM_VALUE_SCREEN_HISTORY)
                                viewModel.cancelDeleteSong()
                            },
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
            analyticsManager.onDeleteAllButtonPressed(AnalyticsManager.PARAM_VALUE_SCREEN_HISTORY, recyclerAdapter?.itemCount ?: 0)
            viewModel.deleteAllSongs()
            showSnackbar(R.string.history_all_songs_removed_message)
        }
    }

    private fun showHintIfNeeded() {
        if (!firstTimeUserExperienceManager.historyCompleted && !isSnackbarVisible() && viewModel.shouldShowDeleteAll.value == true) {
            showHint(
                message = R.string.history_hint,
                action = { firstTimeUserExperienceManager.historyCompleted = true }
            )
        }
    }

    companion object {
        private const val DIALOG_ID_DELETE_ALL_CONFIRMATION = 5
    }
}