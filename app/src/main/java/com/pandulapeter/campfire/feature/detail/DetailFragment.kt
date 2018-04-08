package com.pandulapeter.campfire.feature.detail

import android.animation.Animator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.ChangeImageTransform
import android.transition.ChangeTransform
import android.transition.TransitionSet
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.HistoryItem
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.databinding.FragmentDetailBinding
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.*
import org.koin.android.ext.android.inject
import java.net.URLEncoder


class DetailFragment : TopLevelFragment<FragmentDetailBinding, DetailViewModel>(R.layout.fragment_detail) {

    companion object {
        const val TRANSITION_DELAY = 50L
        private var Bundle.lastSongId by BundleArgumentDelegate.String("lastSongId")
        private var Bundle.songs by BundleArgumentDelegate.ParcelableArrayList("songs")
        private var Bundle.index by BundleArgumentDelegate.Int("index")
        private var Bundle.shouldShowManagePlaylist by BundleArgumentDelegate.Boolean("shouldShowManagePlaylist")

        fun newInstance(songs: List<Song>, index: Int, shouldShowManagePlaylist: Boolean) = DetailFragment().withArguments {
            it.songs = ArrayList(songs)
            it.index = index
            it.shouldShowManagePlaylist = shouldShowManagePlaylist
        }
    }

    override val viewModel by lazy { DetailViewModel(songs[arguments.index].id) }
    private val historyRepository by inject<HistoryRepository>()
    private val songRepository by inject<SongRepository>()
    private val firstTimeUserExperienceManager by inject<FirstTimeUserExperienceManager>()
    private val songs by lazy { arguments?.songs?.filterIsInstance<Song>() ?: listOf() }
    private val drawablePlayToPause by lazy { mainActivity.animatedDrawable(R.drawable.avd_play_to_pause_24dp) }
    private val drawablePauseToPlay by lazy { mainActivity.animatedDrawable(R.drawable.avd_pause_to_play_24dp) }
    private val multiWindowFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
    } else {
        Intent.FLAG_ACTIVITY_NEW_TASK
    }
    private var lastSongId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun createTransition(delay: Long) = TransitionSet()
            .addTransition(FadeInTransition())
            .addTransition(ChangeBounds())
            .addTransition(ChangeTransform())
            .addTransition(ChangeImageTransform())
            .apply {
                ordering = TransitionSet.ORDERING_TOGETHER
                startDelay = delay
            }
        sharedElementEnterTransition = createTransition(TRANSITION_DELAY)
        sharedElementReturnTransition = createTransition(0)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)
        defaultToolbar.updateToolbarTitle(songs[arguments?.index ?: 0].title, songs[arguments?.index ?: 0].artist)
        if (savedInstanceState == null) {
            mainActivity.updateMainToolbarButton(true)
        }
        mainActivity.updateFloatingActionButtonDrawable(mainActivity.drawable(R.drawable.ic_play_24dp))
        mainActivity.autoScrollControl.visibleOrGone = false
        if (savedInstanceState != null) {
            lastSongId = savedInstanceState.lastSongId
        }
        (view.parent as? ViewGroup)?.run {
            viewTreeObserver?.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    viewTreeObserver?.removeOnPreDrawListener(this)
                    startPostponedEnterTransition()
                    return true
                }
            })
        }
        mainActivity.toolbarContext.let { context ->
            mainActivity.updateToolbarButtons(
                listOf(
                    context.createToolbarButton(R.drawable.ic_playlist_border_24dp) { showSnackbar(R.string.work_in_progress) },
                    context.createToolbarButton(R.drawable.ic_song_options_24dp) { mainActivity.openSecondaryNavigationDrawer() })
            )
        }
        mainActivity.enableSecondaryNavigationDrawer(R.menu.detail)
        binding.viewPager.adapter = DetailPagerAdapter(childFragmentManager, songs)
        binding.viewPager.addPageScrollListener(
            onPageSelected = {
                mainActivity.expandAppBar()
                mainActivity.disableFloatingActionButton()
                viewModel.songId.set(songs[it].id)
            },
            onPageScrollStateChanged = {
                mainActivity.disableFloatingActionButton()
            }
        )
    }

    override fun onPause() {
        super.onPause()
        if (mainActivity.autoScrollControl.visibleOrInvisible) {
            toggleAutoScroll()
        }
    }

    override fun onResume() {
        super.onResume()
        (mainActivity.autoScrollControl.tag as? Animator)?.let {
            it.cancel()
            mainActivity.autoScrollControl.tag = null
        }
        mainActivity.autoScrollControl.visibleOrInvisible = false
        mainActivity.updateFloatingActionButtonDrawable(mainActivity.drawable(R.drawable.ic_play_24dp))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.lastSongId = lastSongId
    }

    override fun onBackPressed() = if (mainActivity.autoScrollControl.visibleOrInvisible) {
        toggleAutoScroll()
        true
    } else super.onBackPressed()

    override fun onDrawerStateChanged(state: Int) {
        if (mainActivity.autoScrollControl.visibleOrInvisible) {
            toggleAutoScroll()
        }
    }

    override fun onNavigationItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
        R.id.transpose_higher -> consume { }//detailEventBus.transposeSong(viewModel.getSelectedSongId(), 1) }
        R.id.transpose_lower -> consume { }//detailEventBus.transposeSong(viewModel.getSelectedSongId(), -1) }
        R.id.play_in_youtube -> consumeAndCloseDrawer {
            "${songs[arguments?.index ?: 0].title} - ${songs[arguments?.index ?: 0].artist}".let {
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
        }
        R.id.share -> consumeAndCloseDrawer { showSnackbar(R.string.work_in_progress) } //consumeAndCloseDrawer(binding.drawerLayout) { binding.coordinatorLayout.showSnackbar(R.string.work_in_progress) }
        else -> super.onNavigationItemSelected(menuItem)
    }

    override fun onFloatingActionButtonPressed() = toggleAutoScroll()

    fun onDataLoaded(songId: String) {
        if (songs.indexOfFirst { it.id == songId } == binding.viewPager.currentItem) {
            mainActivity.enableFloatingActionButton()
            historyRepository.addHistoryItem(HistoryItem(songId, System.currentTimeMillis()))
            if (lastSongId != songId) {
                analyticsManager.onSongOpened(songId)
                songRepository.onSongOpened(songId)
                lastSongId = songId
            }
            if (!firstTimeUserExperienceManager.playlistSwipeCompleted) {
                if (binding.viewPager.currentItem != arguments.index) {
                    firstTimeUserExperienceManager.playlistSwipeCompleted = true
                    hideSnackbar()
                } else if (songs.size > 1) {
                    showHint(
                        message = R.string.detail_swipe_hint,
                        action = { firstTimeUserExperienceManager.playlistSwipeCompleted = true }
                    )
                }
            }
        }
    }

    private fun getYouTubeIntent(packageName: String, query: String) = Intent(Intent.ACTION_SEARCH).apply {
        `package` = packageName
        flags = multiWindowFlags
    }.putExtra("query", query)

    private inline fun consumeAndCloseDrawer(crossinline action: () -> Unit) = consume {
        mainActivity.closeSecondaryNavigationDrawer()
        action()
    }

    private fun toggleAutoScroll() = mainActivity.autoScrollControl.run {
        if (tag == null) {
            val drawable = if (visibleOrInvisible) drawablePauseToPlay else drawablePlayToPause
            mainActivity.updateFloatingActionButtonDrawable(drawable)
            animatedVisibilityEnd = !animatedVisibilityEnd
            drawable?.start()
        }
    }
}