package com.pandulapeter.campfire.feature.home.managedownloads

import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import com.pandulapeter.campfire.ManageDownloadsBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListFragment
import com.pandulapeter.campfire.feature.shared.AlertDialogFragment
import com.pandulapeter.campfire.util.onEventTriggered

/**
 * Allows the user to delete downloaded songs.
 *
 * Controlled by [ManageDownloadsViewModel].
 */
class ManageDownloadsFragment : SongListFragment<ManageDownloadsBinding, ManageDownloadsViewModel>(R.layout.fragment_manage_downloads), AlertDialogFragment.OnDialogItemsSelectedListener {

    override fun getRecyclerView() = binding.recyclerView

    override fun createViewModel() = ManageDownloadsViewModel(callbacks, userPreferenceRepository, songInfoRepository, downloadedSongRepository, playlistRepository)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.shouldShowConfirmationDialog.onEventTriggered {
            AlertDialogFragment.show(childFragmentManager,
                R.string.manage_downloads_delete_all_confirmation_title,
                R.string.manage_downloads_delete_all_confirmation_message,
                R.string.manage_downloads_delete_all_confirmation_clear,
                R.string.manage_downloads_delete_all_confirmation_cancel)
        }
        // Set up swipe-to-dismiss functionality.
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                viewHolder?.adapterPosition?.let { position ->
                    val songInfo = viewModel.adapter.items[position].songInfo
                    viewModel.removeSongFromDownloads(songInfo.id)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }

    override fun onPositiveButtonSelected() = viewModel.deleteAllDownloads()
}