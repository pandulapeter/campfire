package com.pandulapeter.campfire.feature.home.manageDownloads

import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import com.pandulapeter.campfire.ManageDownloadsBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.FirstTimeUserExperienceRepository
import com.pandulapeter.campfire.feature.MainActivity
import com.pandulapeter.campfire.feature.MainViewModel
import com.pandulapeter.campfire.feature.home.shared.ElevationItemTouchHelperCallback
import com.pandulapeter.campfire.feature.home.shared.songInfoList.SongInfoListFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.feature.shared.dialog.PlaylistChooserBottomSheetFragment
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.performAfterExpand
import org.koin.android.ext.android.inject

/**
 * Allows the user to delete downloaded songs.
 *
 * Controlled by [ManageDownloadsViewModel].
 */
class ManageDownloadsFragment :
    SongInfoListFragment<ManageDownloadsBinding, ManageDownloadsViewModel>(R.layout.fragment_manage_downloads),
    AlertDialogFragment.OnDialogItemsSelectedListener {
    private val firstTimeUserExperienceRepository by inject<FirstTimeUserExperienceRepository>()

    override fun createViewModel() = ManageDownloadsViewModel(context, analyticsManager, songInfoRepository, downloadedSongRepository, playlistRepository)

    override fun getAppBarLayout() = binding.appBarLayout

    override fun getRecyclerView() = binding.recyclerView

    override fun getCoordinatorLayout() = binding.coordinatorLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.shouldShowConfirmationDialog.onEventTriggered(this) {
            AlertDialogFragment.show(
                childFragmentManager,
                R.string.manage_downloads_delete_all_confirmation_title,
                R.string.manage_downloads_delete_all_confirmation_message,
                R.string.manage_downloads_delete_all_confirmation_clear,
                R.string.cancel
            )
        }
        // Set up swipe-to-dismiss functionality.
        ItemTouchHelper(object : ElevationItemTouchHelperCallback(
            (context?.dimension(R.dimen.content_padding) ?: 0).toFloat(),
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                viewHolder?.adapterPosition?.let { position ->
                    val songInfo = viewModel.adapter.items[position].songInfo
                    viewModel.removeSongFromDownloads(songInfo.id)
                    firstTimeUserExperienceRepository.shouldShowManageDownloadsHint = false
                    dismissHintSnackbar()
                }
            }
        }).attachToRecyclerView(binding.recyclerView)
        // Set up list item click listeners.
        viewModel.adapter.itemClickListener = { position ->
            binding.appBarLayout.performAfterExpand(binding.recyclerView) {
                if (isAdded) (activity as? MainActivity)?.setNavigationItem(MainViewModel.MainNavigationItem.Detail(viewModel.adapter.items[position].songInfo.id))
            }
        }
        viewModel.adapter.playlistActionClickListener = { position ->
            viewModel.adapter.items[position].let { songInfoViewModel ->
                val songId = songInfoViewModel.songInfo.id
                if (playlistRepository.getPlaylists().size == 1) {
                    if (playlistRepository.isSongInPlaylist(Playlist.FAVORITES_ID, songId)) {
                        playlistRepository.removeSongFromPlaylist(Playlist.FAVORITES_ID, songId)
                    } else {
                        playlistRepository.addSongToPlaylist(Playlist.FAVORITES_ID, songId)
                    }
                } else {
                    PlaylistChooserBottomSheetFragment.show(childFragmentManager, songId)
                }
            }
        }
        // Display first-time user experience hint.
        viewModel.shouldShowHintSnackbar.onPropertyChanged(this) {
            if (firstTimeUserExperienceRepository.shouldShowManageDownloadsHint) {
                binding.coordinatorLayout.showFirstTimeUserExperienceSnackbar(R.string.manage_downloads_hint) {
                    firstTimeUserExperienceRepository.shouldShowManageDownloadsHint = false
                }
            }
        }
    }

    override fun onPositiveButtonSelected() {
        viewModel.deleteAllDownloads()
        dismissHintSnackbar()
    }
}