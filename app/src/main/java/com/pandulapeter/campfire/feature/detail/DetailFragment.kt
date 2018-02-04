package com.pandulapeter.campfire.feature.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v4.view.GravityCompat
import android.support.v4.view.ViewPager
import android.view.View
import com.pandulapeter.campfire.DetailBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.*
import com.pandulapeter.campfire.feature.MainActivity
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.*
import org.koin.android.ext.android.inject


/**
 * Displays a horizontal pager with the songs that are in the currently selected playlist (or a
 * single song if no playlist is available).
 *
 * Controlled by [DetailViewModel].
 */
class DetailFragment : CampfireFragment<DetailBinding, DetailViewModel>(R.layout.fragment_detail) {
    private val songInfoRepository by inject<SongInfoRepository>()
    private val historyRepository by inject<HistoryRepository>()
    private val downloadedSongRepository by inject<DownloadedSongRepository>()
    private val userPreferenceRepository by inject<UserPreferenceRepository>()
    private val playlistRepository by inject<PlaylistRepository>()
    private val firstTimeUserExperienceRepository by inject<FirstTimeUserExperienceRepository>()
    private val scrollManager by inject<ScrollManager>()
    override val viewModel by lazy {
        DetailViewModel(
            arguments.songId,
            arguments.playlistId,
            analyticsManager,
            userPreferenceRepository,
            downloadedSongRepository,
            childFragmentManager,
            playlistRepository,
            songInfoRepository,
            historyRepository
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Setup the view pager.
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {

            override fun onPageScrollStateChanged(state: Int) {
                //TODO: Wierd behavior when scrolling from the first or the last page.
                if (viewModel.adapter.count > 1) {
                    binding.appBarLayout.setExpanded(true, true)
                    viewModel.isAutoScrollStarted.set(false)
                }
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) = Unit

            override fun onPageSelected(position: Int) {
                viewModel.onPageSelected(position)
                firstTimeUserExperienceRepository.shouldShowDetailSwipeHint = false
                dismissHintSnackbar()
            }
        })
        binding.viewPager.run {
            post {
                if (savedInstanceState == null) {
                    setCurrentItem(viewModel.songIds.indexOf(arguments.songId), false)
                }
                viewModel.onPageSelected(currentItem)
            }
        }
        if (viewModel.songIds.size > 1 && firstTimeUserExperienceRepository.shouldShowDetailSwipeHint) {
            binding.coordinatorLayout.showFirstTimeUserExperienceSnackbar(R.string.detail_swipe_hint) {
                firstTimeUserExperienceRepository.shouldShowDetailSwipeHint = false
            }
        }
        // Set up the side navigation drawer.
        binding.navigationView.menu.findItem(R.id.transpose_container).isVisible = userPreferenceRepository.shouldShowChords
        binding.drawerLayout.addDrawerListener(onDrawerStateChanged = {
            binding.appBarLayout.setExpanded(true, true)
            viewModel.isAutoScrollStarted.set(false)
        })
        binding.navigationView.disableScrollbars()
        binding.navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.transpose_higher -> consume {
                    //TODO: Implement transpose feature.
                }
                R.id.transpose_lower -> consume {
                    //TODO: Implement transpose feature.
                }
                R.id.play_in_youtube -> consumeAndCloseDrawer(binding.drawerLayout) { viewModel.onPlayOnYouTubeClicked() }
                else -> false
            }
        }
        viewModel.shouldShowSongOptions.onEventTriggered(this) { binding.drawerLayout.openDrawer(GravityCompat.END) }
        viewModel.youTubeSearchQuery.onEventTriggered(this) {
            //TODO: Add support for more third party YouTube clients.
            try {
                startActivity(getYouTubeIntent("com.lara.android.youtube", it))
            } catch (_: ActivityNotFoundException) {
                try {
                    startActivity(getYouTubeIntent("com.google.android.youtube", it))
                } catch (_: ActivityNotFoundException) {
                    binding.drawerLayout.closeDrawers()
                    binding.coordinatorLayout.showSnackbar(R.string.detail_no_youtube_client_found)
                }
            }
        }
        viewModel.shouldNavigateBack.onEventTriggered(this) {
            binding.appBarLayout.performAfterExpand(binding.viewPager) { if (isAdded) (activity as? MainActivity)?.navigateBack() }
        }
        viewModel.isAutoScrollStarted.onPropertyChanged(this) {
            if (it) {
                binding.appBarLayout.setExpanded(false, true)
                scrollManager.onScrollStarted(viewModel.getSelectedSongId())
                binding.root.post(object : Runnable {
                    override fun run() {
                        if (isAdded && viewModel.isAutoScrollStarted.get()) {
                            scrollManager.performScroll(viewModel.getSelectedSongId(), viewModel.autoScrollSpeed.get() + 2)
                            binding.root.postOnAnimation(this)
                        }
                    }
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
        downloadedSongRepository.subscribe(viewModel)
        scrollManager.subscribe(viewModel)
    }

    override fun onPause() {
        super.onPause()
        viewModel.isAutoScrollStarted.set(false)
    }

    override fun onStop() {
        super.onStop()
        playlistRepository.unsubscribe(viewModel)
        downloadedSongRepository.unsubscribe(viewModel)
        scrollManager.unsubscribe(viewModel)
    }

    private fun getYouTubeIntent(packageName: String, query: String) = Intent(Intent.ACTION_SEARCH).apply {
        `package` = packageName
        flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        } else {
            Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }.putExtra("query", query)

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