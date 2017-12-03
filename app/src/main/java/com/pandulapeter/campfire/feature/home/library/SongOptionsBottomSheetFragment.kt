package com.pandulapeter.campfire.feature.home.library

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.BottomSheetDialog
import android.support.design.widget.CoordinatorLayout
import android.support.v4.app.FragmentManager
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

    override fun onCreateDialog(savedInstanceState: Bundle?) = context?.let {
        val dialog = BottomSheetDialog(it, theme)
        binding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.fragment_song_options_bottom_sheet, null, false)
        songId = savedInstanceState?.let { savedInstanceState.songId } ?: arguments.songId
        dialog.setContentView(binding.root)
        binding.removeDownload.setOnClickListener {
            AlertDialogFragment.show(childFragmentManager,
                R.string.remove_download_confirmation_title,
                R.string.remove_download_confirmation_message,
                R.string.remove_download_confirmation_remove,
                R.string.remove_download_confirmation_cancel)
        }
        binding.newPlaylist.setOnClickListener { invokeAndClose { invokeAndClose { getSongActionListener()?.onSongAction(songId, SongAction.NewPlaylist) } } }
        dialog
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

    override fun onPositiveButtonSelected() = invokeAndClose { getSongActionListener()?.onSongAction(songId, SongOptionsBottomSheetFragment.SongAction.RemoveFromDownloads) }

    override fun onUpdate(updateType: Repository.UpdateType) {

        binding.songInfo = songInfoRepository.getLibrarySongs().first { it.id == songId }

        context?.let { context ->
            binding.playlistContainer.removeAllViews()
            val height = context.dimension(R.dimen.touch_target)
            val padding = context.dimension(R.dimen.content_padding)
            playlistRepository.getPlaylists().forEach { playlist ->
                binding.playlistContainer.addView(AppCompatCheckBox(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(padding, padding, padding, padding)
                    text = (playlist as? Playlist.Custom)?.name ?: getString(R.string.home_favorites)
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
        }
    }

    private fun invokeAndClose(action: () -> Unit) {
        action()
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun getSongActionListener(): SongActionListener? {
        parentFragment?.let {
            if (it is SongActionListener) {
                return it
            }
        }
        return null
    }

    interface SongActionListener {

        fun onSongAction(songId: String, songAction: SongAction)
    }

    /**
     * Marks the possible actions the user can do with a song.
     */
    sealed class SongAction {
        object RemoveFromDownloads : SongAction()
        object NewPlaylist : SongAction()
        class AddToPlaylist(val id: String) : SongAction()
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