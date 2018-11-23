package com.pandulapeter.campfire.feature.detail

import android.animation.Animator
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.transition.*
import android.view.*
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.SharedElementCallback
import androidx.viewpager.widget.ViewPager
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.HistoryItem
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.databinding.FragmentDetailBinding
import com.pandulapeter.campfire.feature.main.options.about.AboutViewModel
import com.pandulapeter.campfire.feature.shared.deprecated.OldTopLevelFragment
import com.pandulapeter.campfire.feature.shared.dialog.PlaylistChooserBottomSheetFragment
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.*
import org.koin.android.ext.android.inject
import java.net.URLEncoder


class DetailFragment : OldTopLevelFragment<FragmentDetailBinding, DetailViewModel>(R.layout.fragment_detail), DetailPageEventBus.Subscriber {

    companion object {
        const val TRANSITION_DELAY = 20L
        const val TRANSITION_DURATION = 150L
        private const val FONT_SIZE_MAX = 1.8f
        private const val FONT_SIZE_MIN = 0.8f
        private var Bundle.lastSongId by BundleArgumentDelegate.String("lastSongId")
        private var Bundle.songs by BundleArgumentDelegate.ParcelableArrayList<Song>("songs")
        private var Bundle.index by BundleArgumentDelegate.Int("index")
        private var Bundle.shouldShowManagePlaylist by BundleArgumentDelegate.Boolean("shouldShowManagePlaylist")
        private var Bundle.hasNoTransition by BundleArgumentDelegate.Boolean("hasNoTransition")

        fun newInstance(songs: List<Song>, index: Int, shouldShowManagePlaylist: Boolean, hasNoTransition: Boolean) = DetailFragment().withArguments {
            it.songs = ArrayList(songs)
            it.index = index
            it.shouldShowManagePlaylist = shouldShowManagePlaylist
            it.hasNoTransition = hasNoTransition
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
    private val pagerAdapter by lazy { DetailPagerAdapter(childFragmentManager, songs) }
    private val historyRepository by inject<HistoryRepository>()
    private val songRepository by inject<SongRepository>()
    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val firstTimeUserExperienceManager by inject<FirstTimeUserExperienceManager>()
    private val detailEventBus by inject<DetailEventBus>()
    private val detailPageEventBus by inject<DetailPageEventBus>()
    private val songs by lazy { arguments?.songs ?: listOf<Song>() }
    private val drawablePlayToPause by lazy { getCampfireActivity().animatedDrawable(R.drawable.avd_play_to_pause_24dp) }
    private val drawablePauseToPlay by lazy { getCampfireActivity().animatedDrawable(R.drawable.avd_pause_to_play_24dp) }
    private val addedToPlaylist by lazy { getCampfireActivity().animatedDrawable(R.drawable.avd_added_to_playlists_24dp) }
    private val removedFromPlaylist by lazy { getCampfireActivity().animatedDrawable(R.drawable.avd_removed_from_playlists_24dp) }
    private val transposeHigher by lazy { getCampfireActivity().secondaryNavigationMenu.findItem(R.id.transpose_higher) }
    private val transposeLower by lazy { getCampfireActivity().secondaryNavigationMenu.findItem(R.id.transpose_lower) }
    private var isPinchHintVisible = false
    private val transposeContainer by lazy { getCampfireActivity().secondaryNavigationMenu.findItem(R.id.transpose_container) }
    private val playlistButton: ToolbarButton by lazy {
        getCampfireActivity().toolbarContext.createToolbarButton(if (viewModel.isSongInAnyPlaylists()) R.drawable.ic_playlist_24dp else R.drawable.ic_playlist_border_24dp) {
            if (viewModel.areThereMoreThanOnePlaylists()) {
                if (!getCampfireActivity().isUiBlocked) {
                    viewModel.songId.get()?.let { PlaylistChooserBottomSheetFragment.show(childFragmentManager, it, AnalyticsManager.PARAM_VALUE_SCREEN_SONG_DETAIL) }
                }
            } else {
                viewModel.toggleFavoritesState()
            }
        }
    }
    private val multiWindowFlags =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT else Intent.FLAG_ACTIVITY_NEW_TASK
    private var lastSongId = ""
    private val isAutoScrollActive get() = getCampfireActivity().autoScrollControl.visibleOrInvisible
    private val isSongLoaded get() = getCampfireActivity().isFloatingActionButtonEnabled()

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
                duration = TRANSITION_DURATION
            }
        sharedElementEnterTransition = createTransition(TRANSITION_DELAY)
        sharedElementReturnTransition = createTransition(0)
        setEnterSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                sharedElements[names[0]] = binding.sharedElement
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)
        analyticsManager.onSongDetailScreenOpened(songs.size)
        getCampfireActivity().updateFloatingActionButtonDrawable(getCampfireActivity().drawable(R.drawable.ic_play_24dp))
        getCampfireActivity().autoScrollControl.visibleOrGone = false
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
                            getCampfireActivity().isUiBlocked = false
                            detailEventBus.notifyTransitionEnd()
                            transition?.removeListener(this)
                        }

                        override fun onTransitionCancel(transition: Transition?) {
                            getCampfireActivity().isUiBlocked = false
                            detailEventBus.notifyTransitionEnd()
                            transition?.removeListener(this)
                        }
                    })
                    startPostponedEnterTransition()
                    return true
                }
            })
        }
        getCampfireActivity().enableSecondaryNavigationDrawer(R.menu.detail)
        initializeCompoundButton(R.id.should_show_chords) { preferenceDatabase.shouldShowChords }
        updateTransposeControls()
        viewModel.songId.onPropertyChanged(this) {
            getCampfireActivity().lastSongId = it
            getCampfireActivity().updateToolbarTitleView(inflateToolbarTitle(getCampfireActivity().toolbarContext), toolbarWidth)
            detailEventBus.notifyTransitionEnd()
            onTranspositionChanged(it, preferenceDatabase.getTransposition(it))
        }
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.addPageScrollListener(
            onPageSelected = {
                getCampfireActivity().disableFloatingActionButton()
                if (isAutoScrollActive) {
                    getCampfireActivity().updateFloatingActionButtonDrawable(drawablePauseToPlay?.apply { start() })
                }
                viewModel.songId.set(songs[it].id)
            },
            onPageScrollStateChanged = {
                if (pagerAdapter.count > 1 && it == ViewPager.SCROLL_STATE_DRAGGING && isAutoScrollActive) {
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
        getCampfireActivity().updateToolbarButtons(
            mutableListOf(getCampfireActivity().toolbarContext.createToolbarButton(R.drawable.ic_song_options_24dp) { getCampfireActivity().openSecondaryNavigationDrawer() }).apply {
                if (arguments?.shouldShowManagePlaylist == true) {
                    add(0, playlistButton)
                }
            }
        )
        if (arguments?.hasNoTransition == true) {
            binding.root.postDelayed({
                if (isAdded) {
                    detailEventBus.notifyTransitionEnd()
                }
            }, 200)
        }
        binding.sharedElement.detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector?): Boolean {
                if (isSongLoaded) {
                    detector?.let {
                        preferenceDatabase.fontSize = Math.max(FONT_SIZE_MIN, Math.min(FONT_SIZE_MAX, preferenceDatabase.fontSize * Math.round(it.scaleFactor * 50) / 50f))
                        detailEventBus.notifyTextSizeChanged()
                        if (!firstTimeUserExperienceManager.fontSizePinchCompleted) {
                            firstTimeUserExperienceManager.fontSizePinchCompleted = true
                            if (isPinchHintVisible) {
                                hideSnackbar()
                                isPinchHintVisible = false
                            }
                        }

                    }
                    return true
                } else {
                    return false
                }
            }

            override fun onScaleEnd(detector: ScaleGestureDetector?) = analyticsManager.onPinchToZoomUsed(Math.round(preferenceDatabase.fontSize * 10) / 10f)
        })
        binding.root.post(object : Runnable {
            override fun run() {
                if (isAdded) {
                    viewModel.songId.get()?.let { songId ->
                        getCampfireActivity().autoScrollSpeed.let {
                            if (it >= 0)
                                detailEventBus.notifyScroll(songId, it)
                        }
                    }
                }
                binding.root.postOnAnimation(this)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        detailPageEventBus.subscribe(this)
        (getCampfireActivity().autoScrollControl.tag as? Animator)?.let {
            it.cancel()
            getCampfireActivity().autoScrollControl.tag = null
        }
        getCampfireActivity().autoScrollControl.visibleOrInvisible = false
        getCampfireActivity().updateFloatingActionButtonDrawable(getCampfireActivity().drawable(R.drawable.ic_play_24dp))
        showHintIfNeeded()
        getCampfireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        if (isAutoScrollActive) {
            toggleAutoScroll()
        }
        super.onPause()
        detailPageEventBus.unsubscribe(this)
        getCampfireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.lastSongId = lastSongId
    }

    override fun onBackPressed() = if (isAutoScrollActive) {
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
        if (isAutoScrollActive) {
            toggleAutoScroll()
        }
    }

    override fun onNavigationItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
        R.id.should_show_chords -> consumeAndUpdateBoolean(menuItem, {
            analyticsManager.onShouldShowChordsToggled(it, AnalyticsManager.PARAM_VALUE_SCREEN_SONG_DETAIL)
            preferenceDatabase.shouldShowChords = it
            updateTransposeControls()
            detailEventBus.notifyShouldShowChordsChanged()
        }, { preferenceDatabase.shouldShowChords })
        R.id.transpose_higher -> consume { viewModel.songId.get()?.let { detailEventBus.notifyTranspositionChanged(it, 1) } }
        R.id.transpose_lower -> consume { viewModel.songId.get()?.let { detailEventBus.notifyTranspositionChanged(it, -1) } }
        R.id.play_in_youtube -> consumeAndCloseDrawer {
            if (!getCampfireActivity().isUiBlocked) {
                getCampfireActivity().isUiBlocked = true
                val song = songs[arguments?.index ?: 0]
                analyticsManager.onPlayOriginalSelected(song.id)
                "${song.title} - ${song.artist}".let {
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
        }
        R.id.report -> consumeAndCloseDrawer {
            if (!getCampfireActivity().isUiBlocked) {
                val song = songs[binding.viewPager.currentItem]
                analyticsManager.onReportAProblemSelected(song.id)
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
                    getCampfireActivity().isUiBlocked = true
                } catch (exception: ActivityNotFoundException) {
                    showSnackbar(R.string.options_about_error)
                }
            }
        }
        else -> super.onNavigationItemSelected(menuItem)
    }

    override fun onFloatingActionButtonPressed() = toggleAutoScroll()

    override fun onTranspositionChanged(songId: String, value: Int) {
        if (songId == viewModel.songId.get()) {
            transposeContainer.title = if (value == 0) getString(R.string.detail_transpose) else getString(
                R.string.detail_transpose_value,
                if (value < 0) "$value" else "+$value"
            )
        }
    }

    fun onDataLoaded(songId: String) {
        if (songId == viewModel.songId.get()) {
            getCampfireActivity().enableFloatingActionButton()
            historyRepository.addHistoryItem(HistoryItem(songId, System.currentTimeMillis()))
            if (lastSongId != songId) {
                analyticsManager.onSongVisualized(songId)
                songRepository.onSongOpened(songId)
                lastSongId = songId
                preferenceDatabase.songsOpened++
            }
            binding.root.postDelayed({ if (isAdded) showHintIfNeeded() }, 300)
        }
    }

    fun notifyTransitionEnd() = detailEventBus.notifyTransitionEnd()

    private fun getYouTubeIntent(packageName: String, query: String) = Intent(Intent.ACTION_SEARCH).apply {
        `package` = packageName
        flags = multiWindowFlags
    }.putExtra("query", query)

    private inline fun consumeAndCloseDrawer(crossinline action: () -> Unit) = consume {
        getCampfireActivity().closeSecondaryNavigationDrawer()
        action()
    }

    private fun toggleAutoScroll() = getCampfireActivity().autoScrollControl.run {
        if (tag == null) {
            analyticsManager.onAutoScrollToggled(!visibleOrInvisible)
            val drawable = if (visibleOrInvisible) drawablePauseToPlay else drawablePlayToPause
            getCampfireActivity().updateFloatingActionButtonDrawable(drawable)
            animatedVisibilityEnd = !animatedVisibilityEnd
            drawable?.start()
        }
    }

    private fun updateTransposeControls() {
        preferenceDatabase.shouldShowChords.let {
            transposeHigher.isEnabled = it
            transposeLower.isEnabled = it
        }
    }

    private fun showHintIfNeeded() {
        fun showPinchHint() {
            if (!firstTimeUserExperienceManager.fontSizePinchCompleted && !isSnackbarVisible() && isSongLoaded) {
                isPinchHintVisible = true
                showHint(
                    message = R.string.detail_pinch_hint,
                    action = {
                        firstTimeUserExperienceManager.fontSizePinchCompleted = true
                        isPinchHintVisible = false
                    }
                )
            }
        }

        fun showSwipeHint() {
            if (!firstTimeUserExperienceManager.playlistPagerSwipeCompleted && !isSnackbarVisible() && songs.size > 1) {
                showHint(
                    message = R.string.detail_swipe_hint,
                    action = {
                        firstTimeUserExperienceManager.playlistPagerSwipeCompleted = true
                        binding.root.postDelayed({ if (isAdded) showPinchHint() }, 300)
                    }
                )
            } else {
                showPinchHint()
            }
        }

        if (isSongLoaded) {
            if (firstTimeUserExperienceManager.playlistPagerSwipeCompleted) {
                showPinchHint()
            } else {
                if (binding.viewPager.currentItem != arguments?.index ?: 0) {
                    firstTimeUserExperienceManager.playlistPagerSwipeCompleted = true
                    hideSnackbar()
                    binding.root.postDelayed({
                        if (isAdded) {
                            showPinchHint()
                        }
                    }, 300)
                } else {
                    showSwipeHint()
                }
            }
        }
    }
}