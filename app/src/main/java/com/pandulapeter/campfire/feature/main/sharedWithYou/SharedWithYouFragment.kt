package com.pandulapeter.campfire.feature.main.sharedWithYou

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.main.shared.baseSongList.BaseSongListFragment
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.withArguments
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class SharedWithYouFragment : BaseSongListFragment<SharedWithYouViewModel>() {

    override val shouldSendMultipleSongs = true
    override val viewModel by viewModel<SharedWithYouViewModel> { parametersOf(arguments?.songIds.orEmpty()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        analyticsManager.onTopLevelScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_MANAGE_DOWNLOADS)
        binding.swipeRefreshLayout.isEnabled = false
        updateToolbarTitle(viewModel.songCount.value ?: 0)
        getCampfireActivity()?.updateToolbarButtons(listOf())
        viewModel.shouldOpenSongs.observeAndReset { getCampfireActivity()?.openSongsScreen() }
        viewModel.state.observe { updateToolbarTitle(viewModel.songCount.value ?: 0) }
        viewModel.songCount.observe { updateToolbarTitle(it) }
    }

    private fun updateToolbarTitle(songCount: Int) = topLevelBehavior.defaultToolbar.updateToolbarTitle(
        R.string.shared_with_you,
        if (songCount == 0) {
            getString(if (viewModel.state.value == StateLayout.State.LOADING) R.string.loading else R.string.shared_with_you_empty_list)
        } else {
            resources.getQuantityString(R.plurals.playlist_song_count, songCount, songCount)
        }
    )

    companion object {
        private var Bundle.songIds by BundleArgumentDelegate.StringArrayList("songIds")

        fun newInstance(songIds: List<String>) = SharedWithYouFragment().withArguments {
            it.songIds = ArrayList(songIds)
        }
    }
}