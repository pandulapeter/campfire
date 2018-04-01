package com.pandulapeter.campfire.old.feature.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.view.GravityCompat
import android.support.v4.view.ViewPager
import android.support.v4.widget.DrawerLayout
import android.view.View
import com.pandulapeter.campfire.DetailBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.old.data.repository.*
import com.pandulapeter.campfire.old.feature.MainActivity
import com.pandulapeter.campfire.old.feature.shared.CampfireFragment
import com.pandulapeter.campfire.old.util.*
import com.pandulapeter.campfire.util.*
import org.koin.android.ext.android.inject
import java.net.URLEncoder


/**
 * Displays a horizontal pager with the songs that are in the currently selected playlist (or a
 * single song if no playlist is available).
 *
 * Controlled by [DetailViewModel].
 */
class DetailFragment : CampfireFragment<DetailBinding, DetailViewModel>(R.layout.fragment_detail_old) {
    private val songInfoRepository by inject<SongInfoRepository>()
    private val historyRepository by inject<HistoryRepository>()
    private val downloadedSongRepository by inject<DownloadedSongRepository>()
    private val userPreferenceRepository by inject<UserPreferenceRepository>()
    private val playlistRepository by inject<PlaylistRepository>()
    private val firstTimeUserExperienceRepository by inject<FirstTimeUserExperienceRepository>()
    private val detailEventBus by inject<DetailEventBus>()
    private val transposeContainer by lazy { binding.navigationView.menu.findItem(R.id.transpose_container) }
    private val transposeHigher by lazy { binding.navigationView.menu.findItem(R.id.transpose_higher) }
    private val transposeLower by lazy { binding.navigationView.menu.findItem(R.id.transpose_lower) }
    private val multiWindowFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
    } else {
        Intent.FLAG_ACTIVITY_NEW_TASK
    }
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
                //TODO: Weird behavior when scrolling from the first or the last page.
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
        transposeContainer.isVisible = userPreferenceRepository.shouldShowChords
        binding.drawerLayout.addDrawerListener(onDrawerStateChanged = {
            binding.appBarLayout.setExpanded(true, true)
            viewModel.isAutoScrollStarted.set(false)
        })
        context?.let { binding.drawerLayout.setStatusBarBackgroundColor(it.color(R.color.primary)) }
        binding.navigationView.disableScrollbars()
        binding.navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.transpose_higher -> consume { detailEventBus.transposeSong(viewModel.getSelectedSongId(), 1) }
                R.id.transpose_lower -> consume { detailEventBus.transposeSong(viewModel.getSelectedSongId(), -1) }
                R.id.play_in_youtube -> consumeAndCloseDrawer(binding.drawerLayout) { viewModel.onPlayOnYouTubeClicked() }
                R.id.share -> consumeAndCloseDrawer(binding.drawerLayout) { binding.coordinatorLayout.showSnackbar(R.string.work_in_progress) }
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
        viewModel.playOriginalSearchQuery.onEventTriggered(this) {
            try {
                startActivity(getYouTubeIntent("com.lara.android.youtube", it))
            } catch (_: ActivityNotFoundException) {
                try {
                    startActivity(getYouTubeIntent("com.google.android.youtube", it))
                } catch (_: ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com/#q=" + URLEncoder.encode(it, "UTF-8"))).apply { flags = multiWindowFlags })
                }
            }
        }
        viewModel.shouldNavigateBack.onEventTriggered(this) { (activity as? MainActivity)?.navigateBack() }
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

    private fun getYouTubeIntent(packageName: String, query: String) = Intent(Intent.ACTION_SEARCH).apply {
        `package` = packageName
        flags = multiWindowFlags
    }.putExtra("query", query)

    private fun updateTranposeText(transposeValue: Int) {
        context?.let {
            transposeContainer.title = if (transposeValue == 0) it.getString(R.string.detail_transpose) else it.getString(
                R.string.detail_transpose_value,
                if (transposeValue < 0) "$transposeValue" else "+$transposeValue"
            )
        }
    }

    private inline fun consumeAndCloseDrawer(drawerLayout: DrawerLayout, crossinline action: () -> Unit) = consume {
        action()
        drawerLayout.closeDrawers()
    }

    companion object {
        const val NO_PLAYLIST = -1
        private var Bundle?.songId by BundleArgumentDelegate.String("song_id")
        private var Bundle?.playlistId by BundleArgumentDelegate.Int("playlist_id")

        fun newInstance(songId: String, playlistId: Int?) = DetailFragment().withArguments {
            it.songId = songId
            it.playlistId = playlistId ?: NO_PLAYLIST
        }
    }
}