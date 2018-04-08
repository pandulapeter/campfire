package com.pandulapeter.campfire.old.feature.home.playlist

import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import android.view.inputmethod.EditorInfo
import com.pandulapeter.campfire.PlaylistBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.shared.ElevationItemTouchHelperCallback
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.integration.DeepLinkManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.old.feature.MainActivity
import com.pandulapeter.campfire.old.feature.MainViewModel
import com.pandulapeter.campfire.old.feature.home.shared.songInfoList.SongInfoListFragment
import com.pandulapeter.campfire.util.*
import org.koin.android.ext.android.inject


/**
 * Displays the list of all songs the user marked as favorite. All of these items are also downloads.
 * They can be deleted from the list using the swipe-to-dismiss gesture. The list can also be re-
 * organized.
 *
 * Controlled by [PlaylistViewModel].
 */
class PlaylistFragment : SongInfoListFragment<PlaylistBinding, PlaylistViewModel>(R.layout.fragment_playlist) {
    private val firstTimeUserExperienceRepository by inject<FirstTimeUserExperienceManager>()
    private val appShortcutManager by inject<AppShortcutManager>()
    private val deepLinkManager by inject<DeepLinkManager>()

    override fun createViewModel() = PlaylistViewModel(
        context,
        analyticsManager,
        deepLinkManager,
        songInfoRepository,
        downloadedSongRepository,
        appShortcutManager,
        playlistRepository,
        userPreferenceRepository,
        getString(R.string.home_favorites),
        arguments.playlistId
    )

    override fun getAppBarLayout() = binding.appBarLayout

    override fun getRecyclerView() = binding.recyclerView

    override fun getCoordinatorLayout() = binding.coordinatorLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.isInEditMode.onPropertyChanged(this) {
            if (it) {
//                if (viewModel.adapter.items.isNotEmpty() && firstTimeUserExperienceRepository.shouldShowPlaylistHint) {
//                    binding.coordinatorLayout.showFirstTimeUserExperienceSnackbar(R.string.playlist_hint) {
//                        firstTimeUserExperienceRepository.shouldShowPlaylistHint = false
//                    }
//                }
            } else {
                dismissHintSnackbar()
                hideKeyboard(activity?.currentFocus)
            }
        }
        // Setup swipe-to-dismiss and drag-to-rearrange functionality.
        val itemTouchHelper = ItemTouchHelper(object : ElevationItemTouchHelperCallback((context?.dimension(R.dimen.content_padding) ?: 0).toFloat()) {

            override fun getMovementFlags(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?) =
                if (viewModel.isInEditMode.get()) makeMovementFlags(
                    if (viewModel.adapter.items.size > 1) ItemTouchHelper.UP or ItemTouchHelper.DOWN else 0,
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                ) else 0

            override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?) =
                consume {
                    viewHolder?.adapterPosition?.let { originalPosition ->
                        target?.adapterPosition?.let { targetPosition ->
                            viewModel.swapSongsInPlaylist(originalPosition, targetPosition)
                        }
                    }
                }

            //TODO: Add confirmation snackbars with Undo action.
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                viewHolder?.adapterPosition?.let { position ->
                    val songInfo = viewModel.adapter.items[position].songInfo
                    viewModel.removeSongFromPlaylist(songInfo.id)
//                    firstTimeUserExperienceRepository.shouldShowPlaylistHint = false
                    dismissHintSnackbar()
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        binding.input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                activity?.currentFocus?.let { hideKeyboard(it) }
            }
            false
        }
        // Set up list item click listeners.
        viewModel.adapter.itemClickListener = {
            (activity as? MainActivity)?.setNavigationItem(MainViewModel.MainNavigationItem.Detail(viewModel.adapter.items[it].songInfo.id, arguments.playlistId))
        }
        viewModel.adapter.downloadActionClickListener = { position ->
            viewModel.adapter.items[position].let { viewModel.downloadSong(it.songInfo) }
        }
        viewModel.adapter.dragHandleTouchListener = { position ->
            if (viewModel.isInEditMode.get()) {
                itemTouchHelper.startDrag(binding.recyclerView.findViewHolderForAdapterPosition(position))
            }
        }
        viewModel.shouldShowWorkInProgressSnackbar.onEventTriggered(this) { binding.coordinatorLayout.showSnackbar(R.string.work_in_progress) }
    }

    override fun onBackPressed() = if (viewModel.isInEditMode.get()) {
        viewModel.isInEditMode.set(false)
        true
    } else {
        false
    }

    companion object {
        private var Bundle?.playlistId by BundleArgumentDelegate.Int("playlist_id")

        fun newInstance(playlistId: Int) = PlaylistFragment().withArguments { it.playlistId = playlistId }
    }
}