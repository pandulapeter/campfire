package com.pandulapeter.campfire.feature.shared.dialog

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import com.pandulapeter.campfire.NewPlaylistBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.showKeyboard
import org.koin.android.ext.android.inject

class NewPlaylistDialogFragment : BaseDialogFragment() {

    companion object {
        fun show(fragmentManager: FragmentManager) {
            NewPlaylistDialogFragment().let { it.show(fragmentManager, it.tag) }
        }
    }

    private val playlistRepository by inject<PlaylistRepository>()
    private val positiveButton by lazy { (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE) }
    private val binding by lazy { DataBindingUtil.inflate<NewPlaylistBinding>(LayoutInflater.from(context), R.layout.dialog_new_playlist, null, false) }

    override fun AlertDialog.Builder.createDialog(arguments: Bundle): AlertDialog = setView(binding.root)
        .setTitle(R.string.home_new_playlist)
        .setPositiveButton(R.string.ok, { _, _ -> onOkButtonPressed() })
        .setNegativeButton(R.string.cancel, null)
        .create()

    override fun onStart() {
        super.onStart()
        dialog.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        binding.root.post {
            showKeyboard(binding.inputField)
            binding.inputField.setSelection(binding.inputField.text?.length ?: 0)
        }
        positiveButton.isEnabled = binding.inputField.text.isTextValid()
        binding.inputField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                consume { onOkButtonPressed() }
            } else false
        }
    }

    override fun onStop() {
        super.onStop()
        hideKeyboard(activity?.currentFocus)
    }

    //TODO: Check that there are no playlists with duplicate names.
    private fun CharSequence?.isTextValid() = this?.trim()?.isEmpty() == false

    private fun onOkButtonPressed() {
        binding.inputField.text?.let {
            if (it.isTextValid()) {
                playlistRepository.createNewPlaylist(it.toString().trim())
                dismiss()
            }
        }
    }
}