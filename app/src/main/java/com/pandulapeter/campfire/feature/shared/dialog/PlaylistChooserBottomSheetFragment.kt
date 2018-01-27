package com.pandulapeter.campfire.feature.shared.dialog

import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.annotation.StyleRes
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.BottomSheetDialog
import android.support.design.widget.CoordinatorLayout
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import android.support.v4.view.ViewCompat
import android.support.v4.widget.NestedScrollView
import android.support.v7.app.AppCompatDialogFragment
import android.support.v7.widget.AppCompatCheckBox
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.PlaylistChooserBottomSheetBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.setArguments
import org.koin.android.ext.android.inject


/**
 * A bottom sheet that allows the user to set the positionSource of the avatar image (gallery or camera).
 */
class PlaylistChooserBottomSheetFragment : AppCompatDialogFragment(), Subscriber {
    private val songInfoRepository by inject<SongInfoRepository>()
    private val playlistRepository by inject<PlaylistRepository>()
    private lateinit var binding: PlaylistChooserBottomSheetBinding
    private lateinit var songId: String
    private val behavior: BottomSheetBehavior<*> by lazy { ((binding.root.parent as View).layoutParams as CoordinatorLayout.LayoutParams).behavior as BottomSheetBehavior<*> }
    private val finalToolbarElevation by lazy { context?.dimension(R.dimen.bottom_sheet_toolbar_elevation) ?: 0 }
    private val finalToolbarMargin by lazy { context?.dimension(R.dimen.bottom_sheet_toolbar_margin) ?: 0 }
    private val initialToolbarContainerPadding by lazy { context?.dimension(R.dimen.content_padding) ?: 0 }
    private var scrollViewOffset = 0
    private var shouldTransformTopToAppBar = false

    override fun onCreateDialog(savedInstanceState: Bundle?) = activity?.let { context ->
        CustomWidthBottomSheetDialog(context, theme).apply {
            binding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.fragment_playlist_chooser_bottom_sheet, null, false)
            songId = savedInstanceState?.let { savedInstanceState.songId } ?: arguments.songId
            setContentView(binding.root)
            binding.container?.close?.setOnClickListener { dismiss() }
            binding.container?.newPlaylist?.setOnClickListener { NewPlaylistDialogFragment.show(childFragmentManager) }
            behavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) = updateSlideState(slideOffset)

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) dismiss()
                }
            })
            binding.container?.nestedScrollView?.setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
                scrollViewOffset = scrollY
            }
        }
    } ?: super.onCreateDialog(savedInstanceState)

    override fun onStart() {
        super.onStart()
        songInfoRepository.subscribe(this)
        playlistRepository.subscribe(this)
    }

    override fun onStop() {
        super.onStop()
        songInfoRepository.unsubscribe(this)
        playlistRepository.unsubscribe(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.songId = songId
    }

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.LibraryCacheUpdated -> binding.songInfo = songInfoRepository.getLibrarySongs().first { it.id == songId }
            is UpdateType.PlaylistsUpdated,
            is UpdateType.NewPlaylistsCreated,
            is UpdateType.PlaylistRenamed,
            is UpdateType.PlaylistDeleted -> refreshPlaylistCheckboxes()
        }
    }

    private fun updateSlideState(slideOffset: Float) = Math.max(0f, 2 * slideOffset - 1).let { closenessToTop ->
        if (shouldTransformTopToAppBar && (dialog as CustomWidthBottomSheetDialog).isFullWidth) {
            binding.container?.close?.alpha = closenessToTop
            binding.container?.close?.translationX = -(1 - closenessToTop) * finalToolbarMargin / 4
            binding.container?.toolbar?.translationX = closenessToTop * finalToolbarMargin
            if (scrollViewOffset == 0) {
                ViewCompat.setElevation(binding.container?.fakeAppBar, closenessToTop * finalToolbarElevation)
                binding.container?.background?.alpha = closenessToTop
                binding.container?.toolbarContainer?.setPadding(0, Math.round((1 - closenessToTop) * initialToolbarContainerPadding), 0, 0)
            }
        }
    }

    private fun refreshPlaylistCheckboxes() {
        context?.let { context ->
            binding.container?.playlistContainer?.removeAllViews()
            val height = context.dimension(R.dimen.touch_target)
            val padding = context.dimension(R.dimen.content_padding)
            val playlists = playlistRepository.getPlaylists()
            playlists.forEach { playlist ->
                binding.container?.playlistContainer?.addView(AppCompatCheckBox(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(padding, padding, padding, padding)
                    text = playlist.title ?: getString(R.string.home_favorites)
                    isChecked = playlistRepository.isSongInPlaylist(playlist.id, songId)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            playlistRepository.addSongToPlaylist(playlist.id, songId)
                        } else {
                            playlistRepository.removeSongFromPlaylist(playlist.id, songId)
                        }
                    }
                }, ViewGroup.LayoutParams.MATCH_PARENT, height)
            }
            binding.container?.newPlaylist?.visibility = if (playlists.size < Playlist.MAXIMUM_PLAYLIST_COUNT) View.VISIBLE else View.GONE
            checkIfToolbarTransformationIsNeeded()
        }
    }

    //TODO: Overlap glitch when having too many items and quickly resizing the screen.
    //TODO: Method is not being called after small changes to multi window size.
    private fun checkIfToolbarTransformationIsNeeded() {
        binding.root.post {
            val screenHeight = activity?.window?.decorView?.height ?: 0
            if (binding.root.height > screenHeight - (binding.container?.fakeAppBar?.height ?: 0) && (dialog as CustomWidthBottomSheetDialog).isFullWidth) {
                val layoutParams = binding.root.layoutParams
                layoutParams.height = screenHeight
                binding.root.layoutParams = layoutParams
                shouldTransformTopToAppBar = true
                binding.container?.contentContainer?.setPadding(0, 0, 0, context?.dimension(R.dimen.list_fab_content_bottom_margin) ?: 0)
                updateSlideState(if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) 1f else 0f)
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

    companion object {
        private var Bundle?.songId by BundleArgumentDelegate.String("song_id")

        fun show(fragmentManager: FragmentManager, songId: String) {
            PlaylistChooserBottomSheetFragment().setArguments { it.songId = songId }.run { (this as DialogFragment).show(fragmentManager, tag) }
        }
    }
}