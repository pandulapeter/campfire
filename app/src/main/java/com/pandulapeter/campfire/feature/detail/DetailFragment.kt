package com.pandulapeter.campfire.feature.detail

import android.animation.Animator
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.app.SharedElementCallback
import android.support.v4.view.ViewPager
import android.support.v7.widget.AppCompatTextView
import android.transition.*
import android.util.Log
import android.view.*
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.HistoryItem
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.databinding.FragmentDetailBinding
import com.pandulapeter.campfire.feature.home.options.about.AboutViewModel
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.feature.shared.dialog.PlaylistChooserBottomSheetFragment
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
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

    override val viewModel by lazy {
        var isAddedToPlaylist: Boolean? = null
        DetailViewModel {
            if (isAddedToPlaylist != null && isAddedToPlaylist != it) {
                playlistButton.setImageDrawable((if (it) addedToPlaylist else removedFromPlaylist)?.apply { start() })
            }
            isAddedToPlaylist = it
        }
    }
    private val historyRepository by inject<HistoryRepository>()
    private val songRepository by inject<SongRepository>()
    private val firstTimeUserExperienceManager by inject<FirstTimeUserExperienceManager>()
    private val detailEventBus by inject<DetailEventBus>()
    private val songs by lazy { arguments?.songs?.filterIsInstance<Song>() ?: listOf() }
    private val drawablePlayToPause by lazy { mainActivity.animatedDrawable(R.drawable.avd_play_to_pause_24dp) }
    private val drawablePauseToPlay by lazy { mainActivity.animatedDrawable(R.drawable.avd_pause_to_play_24dp) }
    private val addedToPlaylist by lazy { mainActivity.animatedDrawable(R.drawable.avd_added_to_playlists_24dp) }
    private val removedFromPlaylist by lazy { mainActivity.animatedDrawable(R.drawable.avd_removed_from_playlists_24dp) }
    private val playlistButton: ToolbarButton by lazy {
        mainActivity.toolbarContext.createToolbarButton(if (viewModel.isSongInAnyPlaylists()) R.drawable.ic_playlist_24dp else R.drawable.ic_playlist_border_24dp) {
            if (viewModel.areThereMoreThanOnePlaylists()) {
                viewModel.songId.get()?.let { PlaylistChooserBottomSheetFragment.show(childFragmentManager, it) }
            } else {
                viewModel.toggleFavoritesState()
            }
        }
    }
    private val previousButton by lazy {
        mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_previous_24dp) { binding.viewPager.currentItem -= 1 }.apply {
            visibleOrGone = binding.viewPager.currentItem != 0
        }
    }
    private val nextButton by lazy {
        mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_next_24dp) { binding.viewPager.currentItem += 1 }.apply {
            visibleOrGone = binding.viewPager.currentItem != binding.viewPager.adapter.count - 1
        }
    }
    private val multiWindowFlags =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT else Intent.FLAG_ACTIVITY_NEW_TASK
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

        setEnterSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>?, sharedElements: MutableMap<String, View>?) {
//                names?.let {
//                    viewModel.songId.get()?.let { id ->
//                        names.clear()
//                        names.add(id)
//                        sharedElements?.let {
//                            sharedElements.clear()
//                            sharedElements[id] = binding.sharedElement
//                        }
//                    }
//                }
                Log.d("DEBUG_TRANSITION", "Enter")
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)
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
                    (sharedElementEnterTransition as? Transition)?.addListener(object : Transition.TransitionListener {

                        override fun onTransitionStart(transition: Transition?) = Unit

                        override fun onTransitionResume(transition: Transition?) = Unit

                        override fun onTransitionPause(transition: Transition?) = Unit

                        override fun onTransitionEnd(transition: Transition?) {
                            detailEventBus.notifyTransitionEnd()
                            transition?.removeListener(this)
                        }

                        override fun onTransitionCancel(transition: Transition?) {
                            detailEventBus.notifyTransitionEnd()
                            transition?.removeListener(this)
                        }

                    })
                    startPostponedEnterTransition()
                    return true
                }
            })
        }
        mainActivity.enableSecondaryNavigationDrawer(R.menu.detail)
        viewModel.songId.onPropertyChanged(this) {
            mainActivity.updateToolbarTitleView(inflateToolbarTitle(mainActivity.toolbarContext), toolbarWidth)
            detailEventBus.notifyTransitionEnd()
        }
        binding.viewPager.adapter = DetailPagerAdapter(childFragmentManager, songs)
        binding.viewPager.addPageScrollListener(
            onPageSelected = {
                previousButton.visibleOrGone = it != 0
                nextButton.visibleOrGone = it != binding.viewPager.adapter.count - 1
                mainActivity.disableFloatingActionButton()
                if (mainActivity.autoScrollControl.visibleOrInvisible) {
                    mainActivity.updateFloatingActionButtonDrawable(drawablePauseToPlay?.apply { start() })
                }
                viewModel.songId.set(songs[it].id)
                mainActivity.expandAppBar()
            },
            onPageScrollStateChanged = {
                if (binding.viewPager.adapter.count > 1 && it == ViewPager.SCROLL_STATE_DRAGGING && mainActivity.autoScrollControl.visibleOrInvisible) {
                    toggleAutoScroll()
                }
            }
        )
        (arguments?.index ?: 0).let {
            if (it == 0) {
                viewModel.songId.set(songs[0].id)
            } else {
                binding.viewPager.currentItem = it
            }
        }
        mainActivity.updateToolbarButtons(
            mutableListOf(mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_song_options_24dp) { mainActivity.openSecondaryNavigationDrawer() }).apply {
                if (arguments.shouldShowManagePlaylist) {
                    add(0, playlistButton)
                } else {
                    add(0, nextButton)
                    add(0, previousButton)
                }
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

    override fun inflateToolbarTitle(context: Context) = AppCompatTextView(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        binding.viewPager.currentItem.let { position ->
            updateToolbarTitle(songs[position].title, songs[position].artist)
        }
    }

    override fun onDrawerStateChanged(state: Int) {
        if (mainActivity.autoScrollControl.visibleOrInvisible) {
            toggleAutoScroll()
        }
    }

    override fun onNavigationItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
        R.id.transpose_higher -> consume { showSnackbar(R.string.work_in_progress) }//detailEventBus.transposeSong(viewModel.getSelectedSongId(), 1) }
        R.id.transpose_lower -> consume { showSnackbar(R.string.work_in_progress) }//detailEventBus.transposeSong(viewModel.getSelectedSongId(), -1) }
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
        R.id.report -> consumeAndCloseDrawer {
            val song = songs[binding.viewPager.currentItem]
            try {
                startActivity(
                    Intent.createChooser(
                        Intent().apply {
                            action = Intent.ACTION_SENDTO
                            type = "text/plain"
                            data = Uri.parse("mailto:${AboutViewModel.EMAIL_ADDRESS}?subject=${Uri.encode(getString(R.string.detail_report_subject, song.artist, song.title))}")
                        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null
                    )
                )
            } catch (exception: ActivityNotFoundException) {
                showSnackbar(R.string.options_about_error)
            }
        }
        else -> super.onNavigationItemSelected(menuItem)
    }

    override fun onFloatingActionButtonPressed() = toggleAutoScroll()

    fun onDataLoaded(songId: String) {
        if (songId == viewModel.songId.get()) {
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