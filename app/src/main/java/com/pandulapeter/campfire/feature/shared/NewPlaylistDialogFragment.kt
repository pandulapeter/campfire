package com.pandulapeter.campfire.feature.shared

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import com.pandulapeter.campfire.NewPlaylistBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.showKeyboard
import dagger.android.support.DaggerAppCompatDialogFragment
import javax.inject.Inject

/**
 * Allows the user to create a new playlist.
 */
class NewPlaylistDialogFragment : DaggerAppCompatDialogFragment() {

    @Inject lateinit var playlistRepository: PlaylistRepository
    private lateinit var binding: NewPlaylistBinding
    private val positiveButton by lazy { (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE) }

    override fun onCreateDialog(savedInstanceState: Bundle?) = context?.let { context ->
        binding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.dialog_new_playlist, null, false)
        binding.inputField.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(text: Editable?) {
                positiveButton.isEnabled = text.isTextValid()
            }

            override fun beforeTextChanged(text: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit

            override fun onTextChanged(text: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
        })
        AlertDialog.Builder(context, R.style.AlertDialog)
            .setView(binding.root)
            .setTitle(R.string.home_new_playlist)
            .setPositiveButton(R.string.ok, { _, _ -> onOkButtonPressed() })
            .setNegativeButton(R.string.cancel, null)
            .create()
    } ?: super.onCreateDialog(savedInstanceState)

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
    //TODO: Display error messages below the input field.
    private fun CharSequence?.isTextValid() = !isNullOrBlank()

    private fun onOkButtonPressed() {
        binding.inputField.text?.let {
            if (it.isTextValid()) {
                playlistRepository.createNewPlaylist(it.toString().trim())
                dismiss()
            }
        }
    }

    companion object {
        fun show(fragmentManager: FragmentManager) {
            NewPlaylistDialogFragment().let { it.show(fragmentManager, it.tag) }
        }
    }
}