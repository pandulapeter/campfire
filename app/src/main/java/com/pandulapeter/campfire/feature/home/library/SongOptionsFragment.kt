package com.pandulapeter.campfire.feature.home.library

import android.app.Dialog
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.BottomSheetDialogFragment
import android.support.design.widget.CoordinatorLayout
import android.view.LayoutInflater
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SongOptionsBinding
import com.pandulapeter.campfire.data.model.SongInfo


/**
 * A bottom sheet that allows the user to set the positionSource of the avatar image (gallery or camera).
 */
class SongOptionsFragment : BottomSheetDialogFragment() {
    private lateinit var binding: SongOptionsBinding
    private lateinit var songInfo: SongInfo
    private val behavior: BottomSheetBehavior<*> by lazy { ((binding.root.parent as View).layoutParams as CoordinatorLayout.LayoutParams).behavior as BottomSheetBehavior<*> }
    private var songActionListener: SongActionListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        binding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.fragment_song_options, null, false)
        songInfo = savedInstanceState?.let { savedInstanceState.getParcelable(SONG_INFO) as SongInfo } ?: arguments?.getParcelable(SONG_INFO) as SongInfo
        binding.songInfo = songInfo
        dialog.setContentView(binding.root)
        parentFragment.let {
            if (it is SongActionListener) {
                songActionListener = it
            }
        }
        binding.removeDownload.setOnClickListener { invokeAndClose { songActionListener?.onSongAction(songInfo, SongAction.RemoveFromDownloads) } }
        binding.newPlaylist.setOnClickListener { invokeAndClose { songActionListener?.onSongAction(songInfo, SongAction.NewPlaylist) } }
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SONG_INFO, songInfo)
    }

    private fun invokeAndClose(action: () -> Unit) {
        action()
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
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

        fun newInstance(songInfo: SongInfo) = SongOptionsFragment().apply { arguments = Bundle().apply { putParcelable(SONG_INFO, songInfo) } }
    }
}