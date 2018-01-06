package com.pandulapeter.campfire.feature.detail

import android.os.Bundle
import android.support.design.widget.AppBarLayout
import android.support.v4.view.ViewPager
import android.view.View
import com.pandulapeter.campfire.DetailBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.FirstTimeUserExperienceRepository
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.MainActivity
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.onEventTriggered
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
    override val viewModel by lazy { DetailViewModel(arguments.songId, arguments.playlistId, childFragmentManager, playlistRepository, songInfoRepository, historyRepository) }
    private var isBackAnimationInProgress = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Setup the view pager.
        //TODO: Selected song index is lost when restoring the state.
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {

            override fun onPageScrollStateChanged(state: Int) = Unit

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) = Unit

            override fun onPageSelected(position: Int) {
                viewModel.onPageSelected(position)
                firstTimeUserExperienceRepository.shouldShowDetailSwipeHint = false
                dismissHintSnackbar()
            }
        })
        if (viewModel.songIds.indexOf(arguments.songId) == 0) {
            binding.viewModel?.onPageSelected(0)
        } else {
            binding.viewPager.run { post { setCurrentItem(viewModel.songIds.indexOf(arguments.songId), false) } }
        }
        if (viewModel.songIds.size > 1 && firstTimeUserExperienceRepository.shouldShowDetailSwipeHint) {
            binding.root.showFirstTimeUserExperienceSnackbar(R.string.detail_swipe_hint) {
                firstTimeUserExperienceRepository.shouldShowDetailSwipeHint = false
            }
        }
        viewModel.shouldNavigateBack.onEventTriggered {
            if (!isBackAnimationInProgress) {
                isBackAnimationInProgress = true
                viewModel.adapter.getItemAt(binding.viewPager.currentItem).scrollToTop(
                    onScrollCompleted = {
                        if (binding.appBarLayout.height - binding.appBarLayout.bottom == 0) {
                            (activity as? MainActivity)?.navigateBack()
                        } else {
                            var previousVerticalOffset = -Int.MAX_VALUE
                            binding.appBarLayout.setExpanded(true, true)
                            binding.appBarLayout.addOnOffsetChangedListener(object : AppBarLayout.OnOffsetChangedListener {
                                override fun onOffsetChanged(appBarLayout: AppBarLayout?, verticalOffset: Int) {
                                    if (verticalOffset < binding.appBarLayout.height / 10) {
                                        (activity as? MainActivity)?.navigateBack()
                                    }
                                    if (verticalOffset < previousVerticalOffset) {
                                        binding.appBarLayout.removeOnOffsetChangedListener(this)
                                        isBackAnimationInProgress = false
                                    }
                                    previousVerticalOffset = verticalOffset
                                }
                            })
                        }
                    },
                    onScrollInterrupted = {
                        isBackAnimationInProgress = false
                    })
            }
        }
    }


    override fun onBackPressed(): Boolean {
        viewModel.navigateBack()
        return true
    }

    override fun onStart() {
        super.onStart()
        playlistRepository.subscribe(viewModel)
    }

    override fun onStop() {
        super.onStop()
        playlistRepository.unsubscribe(viewModel)
    }

    companion object {
        const val NO_PLAYLIST = -1
        private var Bundle?.songId by BundleArgumentDelegate.String("song_id")
        private var Bundle?.playlistId by BundleArgumentDelegate.Int("playlist_id")

        fun newInstance(songId: String, playlistId: Int?) = DetailFragment().setArguments {
            it.songId = songId
            it.playlistId = playlistId ?: NO_PLAYLIST
        } as DetailFragment
    }
}