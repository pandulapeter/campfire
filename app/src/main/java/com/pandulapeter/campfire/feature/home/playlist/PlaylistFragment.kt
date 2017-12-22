package com.pandulapeter.campfire.feature.home.playlist

import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import com.pandulapeter.campfire.PlaylistBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.feature.detail.DetailActivity
import com.pandulapeter.campfire.feature.home.HomeActivity
import com.pandulapeter.campfire.feature.home.HomeViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListFragment
import com.pandulapeter.campfire.feature.shared.AlertDialogFragment
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

    override fun createViewModel() = PlaylistViewModel(callbacks, userPreferenceRepository, songInfoRepository, downloadedSongRepository, playlistRepository, getString(R.string.home_favorites), arguments.playlistId)

    override fun getRecyclerView() = binding.recyclerView

    //TODO: Add empty state placeholder.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.isInEditMode.onPropertyChanged {
            if (it) {
                if (viewModel.adapterItemCount > 0) {
                    //TODO: Only show during FTUX.
                    binding.root.showInfoSnackbar(R.string.playlist_hint)
                }
            } else {
                dismissSnackbar()
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
        context?.let { context ->
            viewModel.adapter.itemClickListener = { position ->
                startActivity(DetailActivity.getStartIntent(
                    context = context,
                    currentId = viewModel.adapter.items[position].songInfo.id,
                    ids = viewModel.adapter.items.map { it.songInfo.id }))
            }
        }
        viewModel.adapter.itemDownloadActionClickListener = { position ->
            viewModel.adapter.items[position].let { viewModel.downloadSong(it.songInfo) }
        }
        viewModel.adapter.itemPrimaryActionTouchListener = { position ->
            if (viewModel.isInEditMode.get()) {
                itemTouchHelper.startDrag(binding.recyclerView.findViewHolderForAdapterPosition(position))
            }
        }
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
        (activity as HomeActivity).setCheckedItem(HomeViewModel.NavigationItem.Library)
        viewModel.deletePlaylist()
    }

    companion object {
        private var Bundle?.playlistId by BundleArgumentDelegate.Int("playlist_id")

        fun newInstance(playlistId: Int) = PlaylistFragment().setArguments { it.playlistId = playlistId }
    }
}