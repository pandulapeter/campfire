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
import com.pandulapeter.campfire.feature.home.shared.songInfoList.SongInfoListFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.integration.DeepLinkManager
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.performAfterExpand
import com.pandulapeter.campfire.util.setArguments
import org.koin.android.ext.android.inject


/**
 * Displays the list of all songs the user marked as favorite. All of these items are also downloads.
 * They can be deleted from the list using the swipe-to-dismiss gesture. The list can also be re-
 * organized.
 *
 * Controlled by [PlaylistViewModel].
 */
class PlaylistFragment : SongInfoListFragment<PlaylistBinding, PlaylistViewModel>(R.layout.fragment_playlist), AlertDialogFragment.OnDialogItemsSelectedListener {
    private val playlistRepository by inject<PlaylistRepository>()
    private val firstTimeUserExperienceRepository by inject<FirstTimeUserExperienceRepository>()
    private val appShortcutManager by inject<AppShortcutManager>()
    private val deepLinkManager by inject<DeepLinkManager>()

    override fun createViewModel() = PlaylistViewModel(analyticsManager, deepLinkManager, songInfoRepository, downloadedSongRepository, appShortcutManager, playlistRepository, getString(R.string.home_favorites), arguments.playlistId)

    override fun getAppBarLayout() = binding.appBarLayout

    override fun getRecyclerView() = binding.recyclerView

    override fun getCoordinatorLayout() = binding.coordinatorLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.isInEditMode.onPropertyChanged(this) {
            if (it) {
                if (viewModel.adapter.items.isNotEmpty() && firstTimeUserExperienceRepository.shouldShowPlaylistHint) {
                    binding.coordinatorLayout.showFirstTimeUserExperienceSnackbar(R.string.playlist_hint) {
                        firstTimeUserExperienceRepository.shouldShowPlaylistHint = false
                    }
                }
            } else {
                dismissHintSnackbar()
                hideKeyboard(activity?.currentFocus)
            }
        }
        viewModel.shouldShowDeleteConfirmation.onEventTriggered(this) {
            AlertDialogFragment.show(childFragmentManager,
                R.string.playlist_delete_confirmation_title,
                R.string.playlist_delete_confirmation_message,
                R.string.playlist_delete_confirmation_delete,
                R.string.cancel)
        }
        // Setup swipe-to-dismiss and drag-to-rearrange functionality.
        //TODO: Change the elevation of the card that's being dragged.
        //TODO: Re-ordering is glitchy.
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
                    firstTimeUserExperienceRepository.shouldShowPlaylistHint = false
                    dismissHintSnackbar()
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
        // Set up list item click listeners.
        viewModel.adapter.itemClickListener = { position ->
            binding.appBarLayout.performAfterExpand(
                onExpanded = { if (isAdded) (activity as? MainActivity)?.setNavigationItem(MainViewModel.MainNavigationItem.Detail(viewModel.adapter.items[position].songInfo.id, arguments.playlistId)) },
                connectedView = binding.recyclerView)
        }
        viewModel.adapter.downloadActionClickListener = { position ->
            viewModel.adapter.items[position].let { viewModel.downloadSong(it.songInfo) }
        }
        viewModel.adapter.dragHandleTouchListener = { position ->
            if (viewModel.isInEditMode.get()) {
                itemTouchHelper.startDrag(binding.recyclerView.findViewHolderForAdapterPosition(position))
            }
        }
        //TODO: Implement playlist sharing.
        viewModel.shouldShowWorkInProgressSnackbar.onEventTriggered(this) { binding.coordinatorLayout.showSnackbar(R.string.work_in_progress) }
    }

    override fun onStart() {
        super.onStart()
        playlistRepository.subscribe(viewModel)
    }

    override fun onStop() {
        super.onStop()
        playlistRepository.unsubscribe(viewModel)
    }

    override fun onBackPressed(): Boolean {
        if (viewModel.isInEditMode.get() && viewModel.shouldAllowDeleteButton) {
            viewModel.isInEditMode.set(false)
            return true
        }
        return false
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