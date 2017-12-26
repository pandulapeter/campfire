package com.pandulapeter.campfire.feature.detail

import android.os.Bundle
import android.support.v4.view.ViewPager
import android.view.View
import com.pandulapeter.campfire.DetailBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.FirstTimeUserExperienceRepository
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.setArguments
import javax.inject.Inject

/**
 * Displays a horizontal pager with the songs that are in the currently selected playlist (or a
 * single song if no playlist is available).
 *
 * Controlled by [DetailViewModel].
 */
class DetailFragment : CampfireFragment<DetailBinding, DetailViewModel>(R.layout.fragment_detail) {
    @Inject lateinit var songInfoRepository: SongInfoRepository
    @Inject lateinit var historyRepository: HistoryRepository
    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var firstTimeUserExperienceRepository: FirstTimeUserExperienceRepository
    override val viewModel by lazy { DetailViewModel(childFragmentManager, arguments.songId, arguments.playlistId, playlistRepository, songInfoRepository, historyRepository) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Setup the view pager.
        //TODO: Pay attention to instance state saving.
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) = Unit

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) = Unit

            override fun onPageSelected(position: Int) = viewModel.onPageSelected(position)
        })
        if (viewModel.songIds.indexOf(arguments.songId) == 0) {
            binding.viewModel?.onPageSelected(0)
        } else {
            binding.viewPager.run { post { setCurrentItem(viewModel.songIds.indexOf(arguments.songId), false) } }
        }
        if (viewModel.songIds.size > 1 && firstTimeUserExperienceRepository.shouldShowDetailSwipeHint) {
            //TODO: Also dismiss for swipes.
            //TODO: Show hint.
        }
    }

    companion object {
        private var Bundle?.songId by BundleArgumentDelegate.String("song_id")
        private var Bundle?.playlistId by BundleArgumentDelegate.Int("playlist_id")

        fun newInstance(songId: String, playlistId: Int?) = DetailFragment().setArguments {
            it.songId = songId
            it.playlistId = playlistId ?: -1
        }
    }
}