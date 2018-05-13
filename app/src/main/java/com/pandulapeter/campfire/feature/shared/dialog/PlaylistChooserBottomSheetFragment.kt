package com.pandulapeter.campfire.feature.shared.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.databinding.DataBindingUtil
import android.graphics.Color
import android.os.Bundle
import android.support.annotation.StyleRes
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.BottomSheetDialog
import android.support.design.widget.CoordinatorLayout
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import android.support.v4.widget.NestedScrollView
import android.support.v7.app.AppCompatDialogFragment
import android.support.v7.widget.AppCompatCheckBox
import android.view.*
import com.pandulapeter.campfire.PlaylistChooserBottomSheetBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.util.*
import org.koin.android.ext.android.inject

class PlaylistChooserBottomSheetFragment : AppCompatDialogFragment() {

    companion object {
        private var Bundle?.songId by BundleArgumentDelegate.String("songId")

        fun show(fragmentManager: FragmentManager, songId: String) {
            PlaylistChooserBottomSheetFragment().withArguments { it.songId = songId }.run { (this as DialogFragment).show(fragmentManager, tag) }
        }
    }

    private val songInfoRepository by inject<SongRepository>()
    private val playlistRepository by inject<PlaylistRepository>()
    private lateinit var binding: PlaylistChooserBottomSheetBinding
    private lateinit var viewModel: PlaylistChooserBottomSheetViewModel
    private val behavior: BottomSheetBehavior<*> by lazy { ((binding.root.parent as View).layoutParams as CoordinatorLayout.LayoutParams).behavior as BottomSheetBehavior<*> }
    private val checkBoxHeight by lazy { context?.dimension(R.dimen.touch_target) ?: 0 }
    private val contentPadding by lazy { context?.dimension(R.dimen.content_padding) ?: 0 }
    private val contentBottomMargin by lazy { context?.dimension(R.dimen.list_fab_content_bottom_margin) ?: 0 }
    private var shouldTransformTopToAppBar = false
    private var scrollViewOffset = 0
    private var originalStatusBarColor = 0
    private val updatedStatusBarColor by lazy { context?.obtainColor(android.R.attr.colorPrimary) }
    private val headerContext by lazy { binding.container?.toolbar?.context }

    override fun onCreateDialog(savedInstanceState: Bundle?) = context?.let { context ->
        CustomWidthBottomSheetDialog(context, theme).apply {
            binding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.fragment_playlist_chooser_bottom_sheet, null, false)
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
            setContentView(binding.root)
            viewModel.shouldDismissDialog.onEventTriggered(this@PlaylistChooserBottomSheetFragment) { dismiss() }
            viewModel.shouldShowNewPlaylistDialog.onEventTriggered(this@PlaylistChooserBottomSheetFragment) { NewPlaylistDialogFragment.show(childFragmentManager) }
            viewModel.shouldUpdatePlaylists.onEventTriggered(this@PlaylistChooserBottomSheetFragment) { refreshPlaylistCheckboxes() }
            behavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {

                @SuppressLint("ResourceAsColor")
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    if (shouldTransformTopToAppBar) {
                        viewModel.updateSlideState(slideOffset, scrollViewOffset)
                        activity?.window?.run {
                            if (slideOffset == 1f) {
                                if (statusBarColor != updatedStatusBarColor) {
                                    clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                                    originalStatusBarColor = statusBarColor
                                    statusBarColor = updatedStatusBarColor ?: Color.BLACK
                                }
                            } else {
                                if (statusBarColor != originalStatusBarColor) {
                                    addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                                    statusBarColor = originalStatusBarColor
                                }
                            }
                        }
                    }
                }

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        dismiss()
                    }
                }
            })
            binding.container?.nestedScrollView?.setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
                scrollViewOffset = scrollY
            }
        }
    } ?: super.onCreateDialog(savedInstanceState)

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

    override fun onDismiss(dialog: DialogInterface?) {
        super.onDismiss(dialog)
        if (shouldTransformTopToAppBar) {
            activity?.window?.run {
                addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                statusBarColor = originalStatusBarColor
            }
        }
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
                            playlistRepository.addSongToPlaylist(playlist.id, viewModel.songId)
                        } else {
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
            if (binding.root.height > screenHeight - (binding.container?.fakeAppBar?.height ?: 0) && (dialog as CustomWidthBottomSheetDialog).isFullWidth) {
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

    private class CustomWidthBottomSheetDialog(context: Context, @StyleRes theme: Int) : BottomSheetDialog(context, theme) {
        private val width = context.dimension(R.dimen.playlist_chooser_bottom_sheet_width)
        val isFullWidth = width == 0

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            if (!isFullWidth) {
                window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                window.setGravity(Gravity.BOTTOM)
            }
        }
    }
}