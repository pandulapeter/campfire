package com.pandulapeter.campfire.feature.shared.dialog

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import android.support.v4.widget.NestedScrollView
import android.support.v7.widget.AppCompatCheckBox
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.PlaylistChooserBottomSheetBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.withArguments
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

    override fun initializeDialog(context: Context, savedInstanceState: Bundle?) {
        viewModel = PlaylistChooserBottomSheetViewModel(
            savedInstanceState?.let { savedInstanceState.songId } ?: arguments.songId,
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

    override fun updateSystemWindows() {
        if (shouldTransformTopToAppBar) {
            updateBackgroundDim()
        } else {
            super.updateSystemWindows()
        }
    }

    private fun refreshPlaylistCheckboxes() {
        context?.let { context ->
            binding.container?.playlistContainer?.removeAllViews()
            playlistRepository.cache.sortedBy { it.order }.forEach { playlist ->
                binding.container?.playlistContainer?.addView(AppCompatCheckBox(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(contentPadding, contentPadding, contentPadding, contentPadding)
                    text = playlist.title ?: getString(R.string.main_favorites)
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
            val screenHeight = activity?.window?.decorView?.findViewById<View?>(android.R.id.content)?.height ?: 0
            if (binding.root.height > screenHeight - (binding.container?.fakeAppBar?.height ?: 0) && isFullWidth) {
                val layoutParams = binding.root.layoutParams
                layoutParams.height = screenHeight
                binding.root.layoutParams = layoutParams
                shouldTransformTopToAppBar = true
                binding.container?.contentContainer?.setPadding(0, 0, 0, contentBottomMargin)
                viewModel.updateSlideState(if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) 1f else 0f, scrollViewOffset)
                binding.container?.nestedScrollView?.run { setPadding(0, paddingTop, 0, 0) }
            }
            behavior.peekHeight = Math.min(binding.root.height, screenHeight / 2)
        }
    }

    private fun updateBackgroundDim() {
        dialog.window?.let {
            val newDimAmount = if (viewModel.containerAlpha.get() == 1f) 0f else 0.6f
            if (it.attributes.dimAmount != newDimAmount) {
                it.attributes = it.attributes.apply { dimAmount = newDimAmount }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_NO)
                    it.decorView.systemUiVisibility = if (newDimAmount == 0F) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
                    }
            }
        }
    }
}