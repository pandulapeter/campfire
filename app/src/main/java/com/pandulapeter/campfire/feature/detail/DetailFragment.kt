package com.pandulapeter.campfire.feature.detail

import android.animation.Animator
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
import com.pandulapeter.campfire.databinding.FragmentDetailBinding
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.util.*


class DetailFragment : TopLevelFragment<FragmentDetailBinding, DetailViewModel>(R.layout.fragment_detail) {

    companion object {
        const val TRANSITION_DELAY = 50L
        private var Bundle.songId by BundleArgumentDelegate.String("songId")
        private var Bundle.playlistId by BundleArgumentDelegate.String("playlistId")

        fun newInstance(songId: String, playlistId: String = "") = DetailFragment().withArguments {
            it.songId = songId
            it.playlistId = playlistId
        }
    }

    override val viewModel by lazy { DetailViewModel(arguments.songId) }
    private val drawablePlayToPause by lazy { context.animatedDrawable(R.drawable.avd_play_to_pause_24dp) }
    private val drawablePauseToPlay by lazy { context.animatedDrawable(R.drawable.avd_pause_to_play_24dp) }

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

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)
        defaultToolbar.updateToolbarTitle("Title", "Subtitle")
        if (savedInstanceState == null) {
            mainActivity.updateMainToolbarButton(true)
        }
        mainActivity.updateFloatingActionButtonDrawable(context.drawable(R.drawable.ic_play_24dp))
        mainActivity.autoScrollControl.visibleOrGone = false
        binding.textView.text = "Song: ${arguments.songId}\nPlaylist: ${arguments.playlistId}"
        (view?.parent as? ViewGroup)?.run {
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
        onDataLoaded()
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
        mainActivity.updateFloatingActionButtonDrawable(context.drawable(R.drawable.ic_play_24dp))
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
        R.id.play_in_youtube -> consume { showSnackbar(R.string.work_in_progress) } //consumeAndCloseDrawer(binding.drawerLayout) { viewModel.onPlayOnYouTubeClicked() }
        R.id.share -> consume { showSnackbar(R.string.work_in_progress) } //consumeAndCloseDrawer(binding.drawerLayout) { binding.coordinatorLayout.showSnackbar(R.string.work_in_progress) }
        else -> super.onNavigationItemSelected(menuItem)
    }

    override fun onFloatingActionButtonPressed() = toggleAutoScroll()

    private fun onDataLoaded() {
        mainActivity.enableFloatingActionButton()
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