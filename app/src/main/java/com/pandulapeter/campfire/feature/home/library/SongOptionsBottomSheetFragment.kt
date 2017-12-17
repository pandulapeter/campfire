package com.pandulapeter.campfire.feature.home.library

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.BottomSheetDialog
import android.support.design.widget.CoordinatorLayout
import android.support.v4.app.FragmentManager
import android.support.v4.view.ViewCompat
import android.support.v4.widget.NestedScrollView
import android.support.v7.widget.AppCompatCheckBox
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SongOptionsBottomSheetBinding
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.Repository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.shared.NewPlaylistDialogFragment
import com.pandulapeter.campfire.util.dimension
import dagger.android.support.DaggerAppCompatDialogFragment
import javax.inject.Inject


/**
 * A bottom sheet that allows the user to set the positionSource of the avatar image (gallery or camera).
 */
class SongOptionsBottomSheetFragment : DaggerAppCompatDialogFragment(), AlertDialogFragment.OnDialogItemsSelectedListener, Repository.Subscriber {
    @Inject lateinit var songInfoRepository: SongInfoRepository
    @Inject lateinit var playlistRepository: PlaylistRepository
    private lateinit var binding: SongOptionsBottomSheetBinding
    private lateinit var songId: String
    private val behavior: BottomSheetBehavior<*> by lazy { ((binding.root.parent as View).layoutParams as CoordinatorLayout.LayoutParams).behavior as BottomSheetBehavior<*> }
    private val finalToolbarElevation by lazy { context?.dimension(R.dimen.bottom_sheet_toolbar_elevation) ?: 0 }
    private val finalToolbarMargin by lazy { context?.dimension(R.dimen.bottom_sheet_toolbar_margin) ?: 0 }
    private val initialToolbarContainerPadding by lazy { context?.dimension(R.dimen.content_padding) ?: 0 }
    private var scrollViewOffset = 0
    private var shouldTransformTopToAppBar = false

    override fun onCreateDialog(savedInstanceState: Bundle?) = context?.let { context ->
        val dialog = BottomSheetDialog(context, theme)
        binding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.fragment_song_options_bottom_sheet, null, false)
        songId = savedInstanceState?.let { savedInstanceState.songId } ?: arguments.songId
        dialog.setContentView(binding.root)
        binding.close.setOnClickListener { dismiss() }
        binding.removeDownload.setOnClickListener {
            AlertDialogFragment.show(childFragmentManager,
                R.string.remove_download_confirmation_title,
                R.string.remove_download_confirmation_message,
                R.string.remove_download_confirmation_remove,
                R.string.remove_download_confirmation_cancel)
        }
        binding.newPlaylist.setOnClickListener { NewPlaylistDialogFragment.show(childFragmentManager) }
        behavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {

            override fun onSlide(bottomSheet: View, slideOffset: Float) = updateSlideState(slideOffset)

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    dismiss()
                }
            }
        })
        binding.nestedScrollView.setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
            scrollViewOffset = scrollY
        }
        dialog
    } ?: super.onCreateDialog(savedInstanceState)

    override fun onStart() {
        super.onStart()
        songInfoRepository.subscribe(this)
        playlistRepository.subscribe(this)
    }

    override fun onResume() {
        super.onResume()
        checkIfToolbarTransformationIsNeeded()
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

    override fun onPositiveButtonSelected() = invokeAndClose { songInfoRepository.removeSongFromDownloads(songId) }

    override fun onUpdate(updateType: Repository.UpdateType) {
        when (updateType) {
            is Repository.UpdateType.InitialUpdate -> {
                when (updateType.repositoryClass) {
                    SongInfoRepository::class -> {
                        binding.songInfo = songInfoRepository.getLibrarySongs().first { it.id == songId }
                    }
                    PlaylistRepository::class -> {
                        refreshPlaylistCheckboxes()
                    }
                }
            }
            is Repository.UpdateType.PlaylistAddedOrRemoved -> {
                refreshPlaylistCheckboxes()
            }
        }
    }

    private fun updateSlideState(slideOffset: Float) = Math.max(0f, 2 * slideOffset - 1).let { closenessToTop ->
        if (shouldTransformTopToAppBar) {
            binding.close.alpha = closenessToTop
            binding.close.translationX = -(1 - closenessToTop) * finalToolbarMargin / 4
            binding.toolbar.translationX = closenessToTop * finalToolbarMargin
            if (scrollViewOffset == 0) {
                ViewCompat.setElevation(binding.fakeAppBar, closenessToTop * finalToolbarElevation)
                binding.background.alpha = closenessToTop
                binding.toolbarContainer.setPadding(0, Math.round((1 - closenessToTop) * initialToolbarContainerPadding), 0, 0)
            }
        }
    }

    private fun refreshPlaylistCheckboxes() {
        context?.let { context ->
            binding.playlistContainer.removeAllViews()
            val height = context.dimension(R.dimen.touch_target)
            val padding = context.dimension(R.dimen.content_padding)
            playlistRepository.getPlaylists().forEach { playlist ->
                binding.playlistContainer.addView(AppCompatCheckBox(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(padding, padding, padding, padding)
                    text = (playlist as? Playlist.Custom)?.title ?: getString(R.string.home_favorites)
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
            checkIfToolbarTransformationIsNeeded()
        }
    }

    private fun checkIfToolbarTransformationIsNeeded() {
        binding.root.post {
            val screenHeight = activity?.window?.decorView?.height ?: 0
            if (binding.root.height.toFloat() > screenHeight * 0.8f) {
                val layoutParams = binding.root.layoutParams
                layoutParams.height = screenHeight
                binding.root.layoutParams = layoutParams
                shouldTransformTopToAppBar = true
                updateSlideState(0f)
            }
        }
    }

    private fun invokeAndClose(action: () -> Unit) {
        action()
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    companion object {
        private const val SONG_ID = "song_id"
        private var Bundle?.songId: String
            get() = this?.getString(SONG_ID) ?: ""
            set(value) {
                this?.putString(SONG_ID, value)
            }

        fun show(fragmentManager: FragmentManager, songId: String) {
            SongOptionsBottomSheetFragment().apply { arguments = Bundle().apply { putString(SONG_ID, songId) } }.let { it.show(fragmentManager, it.tag) }
        }
    }
}