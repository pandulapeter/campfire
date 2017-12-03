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
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.Repository
import com.pandulapeter.campfire.util.dimension
import dagger.android.support.DaggerAppCompatDialogFragment
import javax.inject.Inject


/**
 * A bottom sheet that allows the user to set the positionSource of the avatar image (gallery or camera).
 */
class SongOptionsBottomSheetFragment : DaggerAppCompatDialogFragment(), AlertDialogFragment.OnDialogItemsSelectedListener, Repository.Subscriber {
    @Inject lateinit var playlistRepository: PlaylistRepository
    private lateinit var binding: SongOptionsBottomSheetBinding
    private lateinit var songInfo: SongInfo
    private val behavior: BottomSheetBehavior<*> by lazy { ((binding.root.parent as View).layoutParams as CoordinatorLayout.LayoutParams).behavior as BottomSheetBehavior<*> }

    override fun onCreateDialog(savedInstanceState: Bundle?) = context?.let {
        val dialog = BottomSheetDialog(it, theme)
        binding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.fragment_song_options_bottom_sheet, null, false)
        songInfo = savedInstanceState?.let { savedInstanceState.getParcelable(SONG_INFO) as SongInfo } ?: arguments?.getParcelable(SONG_INFO) as SongInfo
        binding.songInfo = songInfo
        dialog.setContentView(binding.root)
        binding.removeDownload.setOnClickListener {
            AlertDialogFragment.show(childFragmentManager,
                R.string.remove_download_confirmation_title,
                R.string.remove_download_confirmation_message,
                R.string.remove_download_confirmation_remove,
                R.string.remove_download_confirmation_cancel)
        }
        binding.newPlaylist.setOnClickListener { invokeAndClose { invokeAndClose { getSongActionListener()?.onSongAction(songInfo, SongAction.NewPlaylist) } } }
        dialog
    } ?: super.onCreateDialog(savedInstanceState)

    override fun onStart() {
        super.onStart()
        playlistRepository.subscribe(this)
    }

    override fun onStop() {
        super.onStop()
        playlistRepository.unsubscribe(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SONG_INFO, songInfo)
    }

    override fun onPositiveButtonSelected() = invokeAndClose { getSongActionListener()?.onSongAction(songInfo, SongOptionsBottomSheetFragment.SongAction.RemoveFromDownloads) }

    override fun onUpdate() {
        context?.let { context ->
            binding.playlistContainer.removeAllViews()
            val height = context.dimension(R.dimen.touch_target)
            val padding = context.dimension(R.dimen.content_padding)
            playlistRepository.getPlaylists().forEach { playlist ->
                binding.playlistContainer.addView(AppCompatCheckBox(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(padding, padding, padding, padding)
                    text = (playlist as? Playlist.Custom)?.name ?: getString(R.string.home_favorites)
                    //TODO: isChecked =
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

        fun onSongAction(songInfo: SongInfo, songAction: SongAction)
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
        private const val SONG_INFO = "song_info"

        fun show(fragmentManager: FragmentManager, songInfo: SongInfo) {
            SongOptionsBottomSheetFragment().apply { arguments = Bundle().apply { putParcelable(SONG_INFO, songInfo) } }.let { it.show(fragmentManager, it.tag) }
        }
    }
}