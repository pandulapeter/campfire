package com.pandulapeter.campfire.feature.shared

import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import dagger.android.support.DaggerAppCompatDialogFragment
import javax.inject.Inject

/**
 * Allows the user to create a new playlist.
 *
 * TODO: Rewrite this class.
 */
class NewPlaylistDialogFragment : DaggerAppCompatDialogFragment() {

    @Inject lateinit var playlistRepository: PlaylistRepository

    override fun onCreateDialog(savedInstanceState: Bundle?) = context?.let { context ->
        AlertDialog.Builder(context)
            .setTitle("Work in progress")
            .setMessage("Add new playlist?")
            .setPositiveButton("OK", { _, _ ->
                playlistRepository.createNewPlaylist("Random ${System.currentTimeMillis()}")
            })
            .setNegativeButton("Cancel", null)
            .create()
    } ?: super.onCreateDialog(savedInstanceState)

    companion object {
        fun show(fragmentManager: FragmentManager) {
            NewPlaylistDialogFragment().let { it.show(fragmentManager, it.tag) }
        }
    }
}