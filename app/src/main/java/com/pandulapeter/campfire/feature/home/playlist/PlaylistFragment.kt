package com.pandulapeter.campfire.feature.home.playlist

import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import com.pandulapeter.campfire.PlaylistBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.FirstTimeUserExperienceRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.feature.MainActivity
import com.pandulapeter.campfire.feature.MainViewModel
import com.pandulapeter.campfire.feature.home.HomeFragment
import com.pandulapeter.campfire.feature.home.HomeViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListFragment
import com.pandulapeter.campfire.feature.shared.AlertDialogFragment
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.setArguments
import javax.inject.Inject


/**
 * Displays the list of all songs the user marked as favorite. All of these items are also downloads.
 * They can be deleted from the list using the swipe-to-dismiss gesture. The list can also be re-
 * organized.
 *
 * Controlled by [PlaylistViewModel].
 */
class PlaylistFragment : SongListFragment<PlaylistBinding, PlaylistViewModel>(R.layout.fragment_playlist), AlertDialogFragment.OnDialogItemsSelectedListener {
    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var firstTimeUserExperienceRepository: FirstTimeUserExperienceRepository
    @Inject lateinit var appShortcutManager: AppShortcutManager

    override fun createViewModel() = PlaylistViewModel(userPreferenceRepository, songInfoRepository, downloadedSongRepository, appShortcutManager, playlistRepository, getString(R.string.home_favorites), arguments.playlistId)

    override fun getRecyclerView() = binding.recyclerView

    //TODO: Add empty state placeholder.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.isInEditMode.onPropertyChanged {
            if (it) {
                if (viewModel.adapterItemCount > 0 && firstTimeUserExperienceRepository.shouldShowPlaylistHint) {
                    binding.root.showFirstTimeUserExperienceSnackbar(R.string.playlist_hint) {
                        firstTimeUserExperienceRepository.shouldShowPlaylistHint = false
                    }
                }
            } else {
                dismissHintSnackbar()
                hideKeyboard(activity?.currentFocus)
            }
        }
        viewModel.shouldShowDeleteConfirmation.onEventTriggered {
            AlertDialogFragment.show(childFragmentManager,
                R.string.playlist_delete_confirmation_title,
                R.string.playlist_delete_confirmation_message,
                R.string.playlist_delete_confirmation_delete,
                R.string.playlist_delete_confirmation_cancel)
        }
        // Setup swipe-to-dismiss and drag-to-rearrange functionality.
        //TODO: Change the elevation of the card that's being dragged.
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {

            override fun getMovementFlags(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?) =
                if (viewModel.isInEditMode.get()) makeMovementFlags(if (viewModel.adapter.items.size > 1) ItemTouchHelper.UP or ItemTouchHelper.DOWN else 0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) else 0

            override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?) = consume {
                viewHolder?.adapterPosition?.let { originalPosition ->
                    target?.adapterPosition?.let { targetPosition ->
                        viewModel.swapSongsInPlaylist(originalPosition, targetPosition)
                    }
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                viewHolder?.adapterPosition?.let { position ->
                    val songInfo = viewModel.adapter.items[position].songInfo
                    viewModel.removeSongFromPlaylist(songInfo.id)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
        // Set up list item click listeners.
        viewModel.adapter.itemClickListener = { position ->
            (activity as? MainActivity)?.setNavigationItem(MainViewModel.MainNavigationItem.Detail(viewModel.adapter.items[position].songInfo.id, arguments.playlistId))
        }
        viewModel.adapter.downloadActionClickListener = { position ->
            viewModel.adapter.items[position].let { viewModel.downloadSong(it.songInfo) }
        }
        viewModel.adapter.dragHandleTouchListener = { position ->
            if (viewModel.isInEditMode.get()) {
                itemTouchHelper.startDrag(binding.recyclerView.findViewHolderForAdapterPosition(position))
            }
        }
        viewModel.shouldShowWorkInProgressSnackbar.onEventTriggered { binding.root.showSnackbar(R.string.work_in_progress) }
    }

    override fun onStart() {
        super.onStart()
        playlistRepository.subscribe(viewModel)
    }

    override fun onStop() {
        super.onStop()
        playlistRepository.unsubscribe(viewModel)
    }

    override fun onPositiveButtonSelected() {
        (parentFragment as HomeFragment).setCheckedItem(HomeViewModel.HomeNavigationItem.Library)
        viewModel.deletePlaylist()
    }

    companion object {
        private var Bundle?.playlistId by BundleArgumentDelegate.Int("playlist_id")

        fun newInstance(playlistId: Int) = PlaylistFragment().setArguments { it.playlistId = playlistId } as PlaylistFragment
    }
}