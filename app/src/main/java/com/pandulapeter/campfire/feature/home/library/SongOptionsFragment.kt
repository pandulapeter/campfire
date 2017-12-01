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

    var inputChoiceListener: ((SongAction) -> Unit) = {}
    private lateinit var binding: SongOptionsBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        binding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.fragment_song_options, null, false)
        arguments?.get(SONG_INFO)?.let { binding.songInfo = it as SongInfo }
        dialog.setContentView(binding.root)
        val params = (binding.root.parent as View).layoutParams as CoordinatorLayout.LayoutParams
        val behavior = params.behavior
        if (behavior != null && behavior is BottomSheetBehavior<*>) {
            binding.removeDownload.setOnClickListener {
                inputChoiceListener(SongAction.RemoveFromDownloads)
                behavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
            binding.newPlaylist.setOnClickListener {}
        }
        return dialog
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