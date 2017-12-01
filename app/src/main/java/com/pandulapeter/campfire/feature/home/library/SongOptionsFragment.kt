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
    var inputChoiceListener: ((SongAction) -> Unit) = {}
    var data: SongInfo? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        binding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.fragment_song_options, null, false)
        binding.songInfo = data
        dialog.setContentView(binding.root)
        val params = (binding.root.parent as View).layoutParams as CoordinatorLayout.LayoutParams
        val behavior = params.behavior
        if (behavior != null && behavior is BottomSheetBehavior<*>) {
            binding.removeDownload.setOnClickListener {
                inputChoiceListener(SongAction.REMOVE_FROM_DOWNLOADS)
                behavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }
        return dialog
    }

    /**
     * Marks the possible actions the user can do with a song.
     */
    sealed class SongAction {
        object REMOVE_FROM_DOWNLOADS : SongAction()
        class ADD_TO_PLAYLIST(val playlistId: String) : SongAction()
    }

    companion object {
        fun newInstance(songInfo: SongInfo) = SongOptionsFragment().apply { data = songInfo }
    }
}