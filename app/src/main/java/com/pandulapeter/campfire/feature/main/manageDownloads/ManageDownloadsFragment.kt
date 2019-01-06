package com.pandulapeter.campfire.feature.main.manageDownloads

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
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.visibleOrGone
import com.pandulapeter.campfire.util.visibleOrInvisible
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class ManageDownloadsFragment : BaseSongListFragment<ManageDownloadsViewModel>(), BaseDialogFragment.OnDialogItemSelectedListener {

    override val viewModel by viewModel<ManageDownloadsViewModel>()
    private val firstTimeUserExperienceManager by inject<FirstTimeUserExperienceManager>()
    private val deleteAllButton by lazy {
        getCampfireActivity()!!.toolbarContext.createToolbarButton(R.drawable.ic_delete) {
            AlertDialogFragment.show(
                DIALOG_ID_DELETE_ALL_CONFIRMATION,
                childFragmentManager,
                R.string.are_you_sure,
                R.string.manage_downloads_delete_all_confirmation_message,
                R.string.manage_downloads_delete_all_confirmation_clear,
                R.string.cancel
            )
        }.apply { visibleOrInvisible = false }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        analyticsManager.onTopLevelScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_DOWNLOADS)
        binding.swipeRefreshLayout.isEnabled = false
        updateToolbarTitle(viewModel.songCount.value ?: 0)
        getCampfireActivity()?.updateToolbarButtons(listOf(deleteAllButton))
        viewModel.shouldOpenSongs.observeAndReset { getCampfireActivity()?.openSongsScreen() }
        viewModel.shouldShowDeleteAll.observe { deleteAllButton.visibleOrGone = it }
        viewModel.state.observe { updateToolbarTitle(viewModel.songCount.value ?: 0) }
        viewModel.songCount.observe {
            updateToolbarTitle(it)
            showHintIfNeeded()
        }
        ItemTouchHelper(object :
            ElevationItemTouchHelperCallback((requireContext().dimension(R.dimen.content_padding)).toFloat(), 0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                viewHolder.adapterPosition.let { position ->
                    if (position != RecyclerView.NO_POSITION && viewModel.adapter.items[position] is SongItemViewModel && !binding.recyclerView.isAnimating) {
                        return ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                    }
                }
                return 0
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                viewHolder.adapterPosition.let { position ->
                    if (position != RecyclerView.NO_POSITION) {
                        analyticsManager.onSwipeToDismissUsed(AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_DOWNLOADS)
                        viewModel.deleteSongPermanently()
                        firstTimeUserExperienceManager.manageDownloadsCompleted = true
                        val song = (viewModel.adapter.items[position] as SongItemViewModel).song
                        showSnackbar(
                            message = getString(R.string.manage_downloads_song_deleted_message, song.title),
                            actionText = R.string.undo,
                            action = {
                                analyticsManager.onUndoButtonPressed(AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_DOWNLOADS)
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
            analyticsManager.onDeleteAllButtonPressed(AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_DOWNLOADS, viewModel.adapter.itemCount)
            viewModel.deleteAllSongs()
            showSnackbar(R.string.manage_downloads_delete_all_message)
        }
    }

    private fun updateToolbarTitle(songCount: Int) = topLevelBehavior.defaultToolbar.updateToolbarTitle(
        R.string.main_manage_downloads,
        if (songCount == 0) {
            getString(if (viewModel.state.value == StateLayout.State.LOADING) R.string.loading else R.string.manage_downloads_no_downloads)
        } else {
            resources.getQuantityString(R.plurals.playlist_song_count, songCount, songCount)
        }
    )

    private fun showHintIfNeeded() {
        if (!firstTimeUserExperienceManager.manageDownloadsCompleted && !isSnackbarVisible() && (viewModel.songCount.value ?: 0) > 0) {
            showHint(
                message = R.string.manage_downloads_hint,
                action = { firstTimeUserExperienceManager.manageDownloadsCompleted = true }
            )
        }
    }

    companion object {
        private const val DIALOG_ID_DELETE_ALL_CONFIRMATION = 4
    }
}