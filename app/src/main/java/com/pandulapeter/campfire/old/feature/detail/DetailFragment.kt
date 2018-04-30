package com.pandulapeter.campfire.old.feature.detail

import android.os.Bundle
import android.support.v4.view.GravityCompat
import android.support.v4.view.ViewPager
import android.view.View
import com.pandulapeter.campfire.DetailBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.old.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.old.data.repository.PlaylistRepository
import com.pandulapeter.campfire.old.data.repository.SongInfoRepository
import com.pandulapeter.campfire.old.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.old.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.*
import org.koin.android.ext.android.inject


/**
 * Displays a horizontal pager with the songs that are in the currently selected playlist (or a
 * single song if no playlist is available).
 *
 * Controlled by [DetailViewModel].
 */
class DetailFragment : CampfireFragment<DetailBinding, DetailViewModel>(R.layout.fragment_detail_old) {
    private val songInfoRepository by inject<SongInfoRepository>()
    private val downloadedSongRepository by inject<DownloadedSongRepository>()
    private val userPreferenceRepository by inject<UserPreferenceRepository>()
    private val playlistRepository by inject<PlaylistRepository>()
    private val detailEventBus by inject<DetailEventBus>()
    private val transposeContainer by lazy { binding.navigationView.menu.findItem(R.id.transpose_container) }
    private val transposeHigher by lazy { binding.navigationView.menu.findItem(R.id.transpose_higher) }
    private val transposeLower by lazy { binding.navigationView.menu.findItem(R.id.transpose_lower) }

    override val viewModel by lazy {
        DetailViewModel(
            arguments.songId,
            arguments.playlistId,
            analyticsManager,
            userPreferenceRepository,
            downloadedSongRepository,
            childFragmentManager,
            playlistRepository,
            songInfoRepository
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Setup the view pager.
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {

            override fun onPageScrollStateChanged(state: Int) {
                if (viewModel.adapter.count > 1) {
                    binding.appBarLayout.setExpanded(true, true)
                    viewModel.isAutoScrollStarted.set(false)
                }
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) = Unit

            override fun onPageSelected(position: Int) {
                viewModel.onPageSelected(position)
//                firstTimeUserExperienceRepository.shouldShowDetailSwipeHint = false
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
//        if (viewModel.songIds.size > 1 && firstTimeUserExperienceRepository.shouldShowDetailSwipeHint) {
//            binding.coordinatorLayout.showFirstTimeUserExperienceSnackbar(R.string.detail_swipe_hint) {
//                firstTimeUserExperienceRepository.shouldShowDetailSwipeHint = false
//            }
//        }
        // Set up the side navigation drawer.
        transposeContainer.isVisible = userPreferenceRepository.shouldShowChords
        binding.drawerLayout.addDrawerListener(onDrawerStateChanged = {
            binding.appBarLayout.setExpanded(true, true)
            viewModel.isAutoScrollStarted.set(false)
        })
        binding.navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.transpose_higher -> consume { detailEventBus.transposeSong(viewModel.getSelectedSongId(), 1) }
                R.id.transpose_lower -> consume { detailEventBus.transposeSong(viewModel.getSelectedSongId(), -1) }
                R.id.play_in_youtube -> consumeAndCloseDrawer { viewModel.onPlayOnYouTubeClicked() }
                else -> false
            }
        }
        viewModel.shouldShowAutoScrollButton.onPropertyChanged(this) {
            transposeHigher.isEnabled = it
            transposeLower.isEnabled = it
        }
        viewModel.transposition.onPropertyChanged(this) { updateTranposeText(it) }
        updateTranposeText(viewModel.transposition.get())
        viewModel.shouldShowSongOptions.onEventTriggered(this) { binding.drawerLayout.openDrawer(GravityCompat.END) }
        viewModel.isAutoScrollStarted.onPropertyChanged(this) {
            if (it) {
                binding.appBarLayout.setExpanded(false, true)
                detailEventBus.onScrollStarted(viewModel.getSelectedSongId())
                binding.root.post(object : Runnable {
                    override fun run() {
                        if (isAdded && viewModel.isAutoScrollStarted.get()) {
                            detailEventBus.performScroll(viewModel.getSelectedSongId(), viewModel.autoScrollSpeed.get() + 2)
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
        detailEventBus.subscribe(viewModel)
    }

    override fun onPause() {
        super.onPause()
        viewModel.isAutoScrollStarted.set(false)
    }

    override fun onStop() {
        super.onStop()
        playlistRepository.unsubscribe(viewModel)
        downloadedSongRepository.unsubscribe(viewModel)
        detailEventBus.unsubscribe(viewModel)
    }

    private fun updateTranposeText(transposeValue: Int) {
        context?.let {
            transposeContainer.title = if (transposeValue == 0) it.getString(R.string.detail_transpose) else it.getString(
                R.string.detail_transpose_value,
                if (transposeValue < 0) "$transposeValue" else "+$transposeValue"
            )
        }
    }

    private inline fun consumeAndCloseDrawer(crossinline action: () -> Unit) = consume {
        action()
        binding.drawerLayout.closeDrawers()
    }

    companion object {
        const val NO_PLAYLIST = -1
        private var Bundle?.songId by BundleArgumentDelegate.String("song_id")
        private var Bundle?.playlistId by BundleArgumentDelegate.Int("playlist_id")
    }
}