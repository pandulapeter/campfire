package com.pandulapeter.campfire.feature.detail

import android.content.Intent
import android.os.Bundle
import android.support.v4.view.GravityCompat
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
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.disableScrollbars
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.performAfterExpand
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
        // Set up the side navigation drawer.
        context?.let { binding.drawerLayout.setScrimColor(it.color(android.R.color.transparent)) }
        binding.navigationView.disableScrollbars()
        binding.navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.play_on_youtube -> consume { viewModel.onPlayOnYouTubeClicked() }
                else -> false
            }
        }
        viewModel.shouldShowSongOptions.onEventTriggered { binding.drawerLayout.openDrawer(GravityCompat.END) }
        viewModel.youTubeSearchQuery.onEventTriggered {
            //TODO: Handle the case if no YouTube app is installed.
            startActivity(Intent(Intent.ACTION_SEARCH).apply {
                `package` = "com.google.android.youtube"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }.putExtra("query", it))
        }
        viewModel.shouldNavigateBack.onEventTriggered {
            if (!isBackAnimationInProgress) {
                isBackAnimationInProgress = true
                viewModel.adapter.getItemAt(binding.viewPager.currentItem).scrollToTop(
                    onScrollCompleted = {
                        binding.appBarLayout.performAfterExpand(
                            onExpanded = { (activity as? MainActivity)?.navigateBack() },
                            onInterrupted = { isBackAnimationInProgress = false })
                    },
                    onScrollInterrupted = {
                        isBackAnimationInProgress = false
                    })
            }
        }
    }

    override fun onBackPressed(): Boolean {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
            binding.drawerLayout.closeDrawers()
            return true
        }
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