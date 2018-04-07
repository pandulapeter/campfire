package com.pandulapeter.campfire.old.feature.home.managePlaylists

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import com.pandulapeter.campfire.ManagePlaylistsBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.shared.ElevationItemTouchHelperCallback
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.old.data.repository.PlaylistRepository
import com.pandulapeter.campfire.old.feature.home.HomeViewModel
import com.pandulapeter.campfire.old.feature.home.shared.SpacesItemDecoration
import com.pandulapeter.campfire.old.feature.home.shared.homeChild.HomeChildFragment
import com.pandulapeter.campfire.old.feature.shared.dialog.NewPlaylistDialogFragment
import com.pandulapeter.campfire.old.util.onEventTriggered
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.hideKeyboard
import org.koin.android.ext.android.inject

/**
 * Allows the user to rearrange or delete playlists.
 *
 * Controlled by [ManagePlaylistsViewModel].
 */
class ManagePlaylistsFragment : HomeChildFragment<ManagePlaylistsBinding, ManagePlaylistsViewModel>(R.layout.fragment_manage_playlists_old) {
    private val firstTimeUserExperienceRepository by inject<FirstTimeUserExperienceManager>()
    private val playlistRepository by inject<PlaylistRepository>()
    private val appShortcutManager by inject<AppShortcutManager>()

    override fun createViewModel() = ManagePlaylistsViewModel(
        analyticsManager,
        appShortcutManager,
        firstTimeUserExperienceRepository,
        playlistRepository
    )

    override fun getAppBarLayout() = binding.appBarLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.run {
            adapter = viewModel.adapter
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
            addItemDecoration(SpacesItemDecoration(context.dimension(R.dimen.content_padding)))
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                    if (dy > 0) {
                        hideKeyboard(activity?.currentFocus)
                    }
                }
            })
        }
        // Setup swipe-to-dismiss and drag-to-rearrange functionality.
        val itemTouchHelper = ItemTouchHelper(object : ElevationItemTouchHelperCallback((context?.dimension(R.dimen.content_padding) ?: 0).toFloat()) {

            override fun getMovementFlags(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?) =
                if ((viewHolder?.adapterPosition ?: 0) > 0) makeMovementFlags(
                    if (viewModel.adapter.items.size > 2) ItemTouchHelper.UP or ItemTouchHelper.DOWN else 0,
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                ) else 0

            override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?) =
                consume {
                    viewHolder?.adapterPosition?.let { originalPosition ->
                        target?.adapterPosition?.let { targetPosition ->
                            if (originalPosition > 0 && targetPosition > 0) {
                                viewModel.swapSongsInPlaylist(originalPosition, targetPosition)
                            }
                        }
                    }
                }

            //TODO: Add confirmation snackbars with Undo action.
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                viewHolder?.adapterPosition?.let { position ->
                    val playlist = viewModel.adapter.items[position].playlist
                    viewModel.deletePlaylist(playlist.id)
//                    firstTimeUserExperienceRepository.shouldShowManagePlaylistsHint = false
                    dismissHintSnackbar()
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
        // Set up list item click listeners.
        viewModel.adapter.itemClickListener = { position ->
            if (isAdded) {
                //TODO: Transition glitch.
                (parentFragment as? HomeCallbacks)?.setCheckedItem(HomeViewModel.HomeNavigationItem.Playlist(viewModel.adapter.items[position].playlist.id))
            }
        }
        viewModel.adapter.dragHandleTouchListener = { position ->
            itemTouchHelper.startDrag(binding.recyclerView.findViewHolderForAdapterPosition(position))
        }
        // Display first-time user experience hint.
//        viewModel.shouldShowHintSnackbar.onPropertyChanged(this) {
//            binding.coordinatorLayout.showFirstTimeUserExperienceSnackbar(R.string.manage_playlists_hint) {
//                firstTimeUserExperienceRepository.shouldShowManagePlaylistsHint = false
//            }
//        }
        viewModel.shouldShowNewPlaylistDialog.onEventTriggered(this) {
            NewPlaylistDialogFragment.show(childFragmentManager)
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
}