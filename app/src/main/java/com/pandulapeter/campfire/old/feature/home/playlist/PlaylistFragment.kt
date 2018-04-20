package com.pandulapeter.campfire.old.feature.home.playlist

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import com.pandulapeter.campfire.PlaylistBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.integration.DeepLinkManager
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

        binding.input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                activity?.currentFocus?.let { hideKeyboard(it) }
            }
            false
        }
        // Set up list item click listeners.
        viewModel.adapter.downloadActionClickListener = { position ->
            viewModel.adapter.items[position].let { viewModel.downloadSong(it.songInfo) }
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