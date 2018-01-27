package com.pandulapeter.campfire.feature.home.managePlaylists

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import com.pandulapeter.campfire.ManagePlaylistsBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.FirstTimeUserExperienceRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.feature.home.HomeViewModel
import com.pandulapeter.campfire.feature.home.shared.SpacesItemDecoration
import com.pandulapeter.campfire.feature.home.shared.homeChild.HomeChildFragment
import com.pandulapeter.campfire.feature.shared.dialog.NewPlaylistDialogFragment
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

/**
 * Allows the user to rearrange or delete playlists.
 *
 * Controlled by [ManagePlaylistsViewModel].
 */
class ManagePlaylistsFragment : HomeChildFragment<ManagePlaylistsBinding, ManagePlaylistsViewModel>(R.layout.fragment_manage_playlists) {
    private val firstTimeUserExperienceRepository by inject<FirstTimeUserExperienceRepository>()
    private val playlistRepository by inject<PlaylistRepository>()

    override fun createViewModel() = ManagePlaylistsViewModel(analyticsManager, firstTimeUserExperienceRepository, playlistRepository)

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
        //TODO: Implement drag to rearrange and swipe to dismiss.
        // Set up list item click listeners.
        viewModel.adapter.itemClickListener = { position ->
            if (isAdded) {
                (parentFragment as? HomeCallbacks)?.setCheckedItem(HomeViewModel.HomeNavigationItem.Playlist(viewModel.adapter.items[position].playlist.id))
            }
        }
        // Display first-time user experience hint.
        viewModel.shouldShowHintSnackbar.onPropertyChanged(this) {
            binding.coordinatorLayout.showFirstTimeUserExperienceSnackbar(R.string.manage_playlists_hint) {
                firstTimeUserExperienceRepository.shouldShowManagePlaylistsHint = false
            }
        }
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