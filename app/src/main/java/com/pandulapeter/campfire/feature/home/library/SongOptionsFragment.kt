package com.pandulapeter.campfire.feature.home.library

import android.app.Dialog
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.BottomSheetDialogFragment
import android.support.design.widget.CoordinatorLayout
import android.support.v4.app.FragmentManager
import android.view.LayoutInflater
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SongOptionsBinding
import com.pandulapeter.campfire.data.model.SongInfo


/**
 * A bottom sheet that allows the user to set the positionSource of the avatar image (gallery or camera).
 */
class SongOptionsFragment : BottomSheetDialogFragment(), AlertDialogFragment.OnDialogItemsSelectedListener {
    private lateinit var binding: SongOptionsBinding
    private lateinit var songInfo: SongInfo
    private val behavior: BottomSheetBehavior<*> by lazy { ((binding.root.parent as View).layoutParams as CoordinatorLayout.LayoutParams).behavior as BottomSheetBehavior<*> }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        binding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.fragment_song_options, null, false)
        songInfo = savedInstanceState?.let { savedInstanceState.getParcelable(SONG_INFO) as SongInfo } ?: arguments?.getParcelable(SONG_INFO) as SongInfo
        binding.songInfo = songInfo
        dialog.setContentView(binding.root)
        binding.removeDownload.setOnClickListener {
            AlertDialogFragment.show(childFragmentManager,
                R.string.home_remove_download_confirmation_title,
                R.string.home_remove_download_confirmation_message,
                R.string.home_remove_download_confirmation_remove,
                R.string.home_remove_download_confirmation_cancel)
        }
        binding.newPlaylist.setOnClickListener { invokeAndClose { invokeAndClose { getSongActionListener()?.onSongAction(songInfo, SongAction.NewPlaylist) } } }
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SONG_INFO, songInfo)
    }

    override fun onPositiveButtonSelected() = invokeAndClose { getSongActionListener()?.onSongAction(songInfo, SongOptionsFragment.SongAction.RemoveFromDownloads) }

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
            SongOptionsFragment().apply { arguments = Bundle().apply { putParcelable(SONG_INFO, songInfo) } }.let { it.show(fragmentManager, it.tag) }
        }
    }
}