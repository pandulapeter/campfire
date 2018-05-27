package com.pandulapeter.campfire.feature.shared.dialog

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import android.support.v4.widget.NestedScrollView
import android.support.v7.widget.AppCompatCheckBox
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.pandulapeter.campfire.PlaylistChooserBottomSheetBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.*
import org.koin.android.ext.android.inject


class PlaylistChooserBottomSheetFragment : BaseBottomSheetDialogFragment<PlaylistChooserBottomSheetBinding>(R.layout.fragment_playlist_chooser_bottom_sheet) {

    companion object {
        private var Bundle?.songId by BundleArgumentDelegate.String("songId")
        private var Bundle?.screenName by BundleArgumentDelegate.String("screenName")

        fun show(fragmentManager: FragmentManager, songId: String, screenName: String) {
            PlaylistChooserBottomSheetFragment().withArguments {
                it.songId = songId
                it.screenName = screenName
            }.run { (this as DialogFragment).show(fragmentManager, tag) }
        }
    }

    private val songInfoRepository by inject<SongRepository>()
    private val playlistRepository by inject<PlaylistRepository>()
    private val analyticsManager by inject<AnalyticsManager>()
    private lateinit var viewModel: PlaylistChooserBottomSheetViewModel
    private val checkBoxHeight by lazy { context?.dimension(R.dimen.touch_target) ?: 0 }
    private val contentPadding by lazy { context?.dimension(R.dimen.content_padding) ?: 0 }
    private val contentBottomMargin by lazy { context?.dimension(R.dimen.list_fab_content_bottom_margin) ?: 0 }
    private var shouldTransformTopToAppBar = false
    private var scrollViewOffset = 0
    private val headerContext by lazy { binding.container?.toolbar?.context }

    override fun initializeDialog(context: Context, savedInstanceState: Bundle?) {
        viewModel = PlaylistChooserBottomSheetViewModel(
            savedInstanceState?.let { savedInstanceState.songId } ?: arguments.songId,
            headerContext?.obtainColor(android.R.attr.textColorPrimary) ?: Color.BLACK,
            context.obtainColor(android.R.attr.textColorPrimary),
            headerContext?.obtainColor(android.R.attr.textColorSecondary) ?: Color.BLACK,
            context.obtainColor(android.R.attr.textColorSecondary),
            context.dimension(R.dimen.content_padding),
            context.dimension(R.dimen.bottom_sheet_toolbar_elevation),
            context.dimension(R.dimen.bottom_sheet_toolbar_margin)
        )
        binding.viewModel = viewModel
        viewModel.shouldDismissDialog.onEventTriggered(this@PlaylistChooserBottomSheetFragment) { dismiss() }
        viewModel.shouldShowNewPlaylistDialog.onEventTriggered(this@PlaylistChooserBottomSheetFragment) {
            NewPlaylistDialogFragment.show(
                childFragmentManager,
                AnalyticsManager.PARAM_VALUE_BOTTOM_SHEET
            )
        }
        viewModel.shouldUpdatePlaylists.onEventTriggered(this@PlaylistChooserBottomSheetFragment) { refreshPlaylistCheckboxes() }
        binding.container?.nestedScrollView?.setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
            scrollViewOffset = scrollY
        }
    }

    override fun onDialogCreated() {
        behavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (shouldTransformTopToAppBar) {
                    viewModel.updateSlideState(slideOffset, scrollViewOffset)
                    updateBackgroundDim()
                }
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    dismiss()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        songInfoRepository.subscribe(viewModel)
        playlistRepository.subscribe(viewModel)
        if (shouldTransformTopToAppBar) {
            updateBackgroundDim()
        }
    }

    override fun onStop() {
        super.onStop()
        songInfoRepository.unsubscribe(viewModel)
        playlistRepository.unsubscribe(viewModel)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.songId = viewModel.songId
    }

    private fun refreshPlaylistCheckboxes() {
        context?.let { context ->
            binding.container?.playlistContainer?.removeAllViews()
            playlistRepository.cache.sortedBy { it.order }.forEach { playlist ->
                binding.container?.playlistContainer?.addView(AppCompatCheckBox(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(contentPadding, contentPadding, contentPadding, contentPadding)
                    text = playlist.title ?: getString(R.string.home_favorites)
                    isChecked = playlistRepository.isSongInPlaylist(playlist.id, viewModel.songId)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            arguments?.screenName?.let {
                                analyticsManager.onSongPlaylistStateChanged(
                                    viewModel.songId,
                                    playlistRepository.getPlaylistCountForSong(viewModel.songId) + 1,
                                    it,
                                    true
                                )
                            }
                            playlistRepository.addSongToPlaylist(playlist.id, viewModel.songId)
                        } else {
                            arguments?.screenName?.let {
                                analyticsManager.onSongPlaylistStateChanged(
                                    viewModel.songId,
                                    playlistRepository.getPlaylistCountForSong(viewModel.songId) - 1,
                                    it,
                                    true
                                )
                            }
                            playlistRepository.removeSongFromPlaylist(playlist.id, viewModel.songId)
                        }
                    }
                }, ViewGroup.LayoutParams.MATCH_PARENT, checkBoxHeight)
            }
            binding.container?.newPlaylist?.visibility = if (playlistRepository.cache.size < Playlist.MAXIMUM_PLAYLIST_COUNT) View.VISIBLE else View.GONE
            checkIfToolbarTransformationIsNeeded()
        }
    }

    private fun checkIfToolbarTransformationIsNeeded() {
        binding.root.post {
            val screenHeight = activity?.window?.decorView?.height ?: 0
            if (binding.root.height > screenHeight - (binding.container?.fakeAppBar?.height ?: 0) && isFullWidth) {
                val layoutParams = binding.root.layoutParams
                layoutParams.height = screenHeight
                binding.root.layoutParams = layoutParams
                shouldTransformTopToAppBar = true
                binding.container?.contentContainer?.setPadding(0, 0, 0, contentBottomMargin)
                if (shouldTransformTopToAppBar) {
                    viewModel.updateSlideState(if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) 1f else 0f, scrollViewOffset)
                }
            }
            behavior.peekHeight = Math.min(binding.root.height, screenHeight / 2)
        }
    }

    private fun updateBackgroundDim() {
        dialog.window?.let {
            it.attributes = it.attributes.apply {
                dimAmount = (1f - viewModel.containerAlpha.get()) * 0.6f
                flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            }
        }
    }
}