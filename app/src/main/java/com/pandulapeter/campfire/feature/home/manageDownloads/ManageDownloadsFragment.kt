package com.pandulapeter.campfire.feature.home.manageDownloads

import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.shared.ElevationItemTouchHelperCallback
import com.pandulapeter.campfire.feature.home.shared.SongListFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.visibleOrInvisible
import org.koin.android.ext.android.inject

class ManageDownloadsFragment : SongListFragment<ManageDownloadsViewModel>(), AlertDialogFragment.OnDialogItemsSelectedListener {

    companion object {
        private const val DIALOG_ID_DELETE_ALL_CONFIRMATION = 4
    }

    private val firstTimeUserExperienceManager by inject<FirstTimeUserExperienceManager>()
    override val viewModel = ManageDownloadsViewModel {
        mainActivity.openLibraryScreen()
    }
    private val deleteAllButton by lazy {
        mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_delete_24dp) {
            AlertDialogFragment.show(
                DIALOG_ID_DELETE_ALL_CONFIRMATION,
                childFragmentManager,
                R.string.manage_downloads_delete_all_confirmation_title,
                R.string.manage_downloads_delete_all_confirmation_message,
                R.string.manage_downloads_delete_all_confirmation_clear,
                R.string.cancel
            )
        }.apply { visibleOrInvisible = false }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeRefreshLayout.isEnabled = false
        updateToolbarTitle(viewModel.songCount.get())
        mainActivity.updateToolbarButtons(listOf(deleteAllButton))
        viewModel.shouldShowDeleteAll.onPropertyChanged(this) { deleteAllButton.visibleOrInvisible = it }
        viewModel.songCount.onPropertyChanged(this) {
            updateToolbarTitle(it)
            if (it > 0 && !firstTimeUserExperienceManager.manageDownloadsCompleted) {
                showHint(
                    message = R.string.manage_downloads_hint,
                    action = { firstTimeUserExperienceManager.manageDownloadsCompleted = true }
                )
            }
        }
        ItemTouchHelper(object : ElevationItemTouchHelperCallback((mainActivity.dimension(R.dimen.content_padding)).toFloat(), 0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                viewHolder?.adapterPosition?.let { position ->
                    if (position != RecyclerView.NO_POSITION) {
                        firstTimeUserExperienceManager.manageDownloadsCompleted = true
                        val song = viewModel.adapter.items[position].song
                        showSnackbar(
                            message = getString(R.string.manage_downloads_song_deleted_message, song.title),
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
            showSnackbar(R.string.manage_downloads_delete_all_message)
        }
    }

    private fun updateToolbarTitle(songCount: Int) = defaultToolbar.updateToolbarTitle(
        R.string.home_manage_downloads,
        if (songCount == 0) getString(R.string.manage_downloads_no_downloads) else mainActivity.resources.getQuantityString(R.plurals.playlist_song_count, songCount, songCount)
    )
}