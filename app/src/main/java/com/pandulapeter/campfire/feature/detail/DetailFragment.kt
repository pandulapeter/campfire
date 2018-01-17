package com.pandulapeter.campfire.feature.detail

import android.content.Intent
import android.os.Bundle
import android.support.v4.view.GravityCompat
import android.support.v4.view.ViewPager
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import com.pandulapeter.campfire.DetailBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.FirstTimeUserExperienceRepository
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.MainActivity
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.addDrawerListener
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.disableScrollbars
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.performAfterExpand
import com.pandulapeter.campfire.util.setArguments
import com.pandulapeter.campfire.util.setupWithBackingField
import com.pandulapeter.campfire.util.toggle
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
    private var isBackAnimationInProgress = false
    private lateinit var transposeHigherMenuItem: MenuItem
    private lateinit var transposeLowerMenuItem: MenuItem

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Setup the view pager.
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {

            override fun onPageScrollStateChanged(state: Int) = binding.appBarLayout.setExpanded(true, true)

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
            binding.coordinatorLayout.showFirstTimeUserExperienceSnackbar(R.string.detail_swipe_hint) {
                firstTimeUserExperienceRepository.shouldShowDetailSwipeHint = false
            }
        }
        // Set up the side navigation drawer.
        transposeHigherMenuItem = binding.navigationView.menu.findItem(R.id.transpose_higher)
        transposeLowerMenuItem = binding.navigationView.menu.findItem(R.id.transpose_lower)
        updateTransposeSectionState(viewModel.shouldShowChords.get())
        viewModel.shouldShowChords.onPropertyChanged(this) { updateTransposeSectionState(it) }
        (binding.navigationView.menu.findItem(R.id.show_chords).actionView as CompoundButton).setupWithBackingField(viewModel.shouldShowChords)
        binding.drawerLayout.addDrawerListener(onDrawerStateChanged = { binding.appBarLayout.setExpanded(true, true) })
        binding.navigationView.disableScrollbars()
        binding.navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.show_chords -> consume { viewModel.shouldShowChords.toggle() }
                R.id.play_on_youtube -> consume { viewModel.onPlayOnYouTubeClicked() }
                else -> false
            }
        }
        viewModel.shouldShowSongOptions.onEventTriggered(this) { binding.drawerLayout.openDrawer(GravityCompat.END) }
        viewModel.youTubeSearchQuery.onEventTriggered(this) {
            //TODO: Handle the case if no YouTube app is installed.
            startActivity(Intent(Intent.ACTION_SEARCH).apply {
                `package` = "com.google.android.youtube"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }.putExtra("query", it))
        }
        viewModel.shouldNavigateBack.onEventTriggered(this) {
            if (!isBackAnimationInProgress) {
                isBackAnimationInProgress = true
                binding.appBarLayout.performAfterExpand(
                    onExpanded = { (activity as? MainActivity)?.navigateBack() },
                    onInterrupted = {
                        viewModel.adapter.getItemAt(binding.viewPager.currentItem).stopScroll()
                        isBackAnimationInProgress = false
                        viewModel.shouldNavigateBack.set(true)
                    },
                    connectedView = binding.viewPager
                )
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
    }

    override fun onStop() {
        super.onStop()
        playlistRepository.unsubscribe(viewModel)
        downloadedSongRepository.unsubscribe(viewModel)
    }

    private fun updateTransposeSectionState(isEnabled: Boolean) {
        transposeHigherMenuItem.isEnabled = isEnabled
        transposeLowerMenuItem.isEnabled = isEnabled
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