package com.pandulapeter.campfire.feature.home.playlist

import android.os.Bundle
import com.pandulapeter.campfire.feature.home.shared.songList.SongListFragment
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.withArguments

class PlaylistFragment : SongListFragment<PlaylistViewModel>() {

    companion object {
        private var Bundle?.playlistId by BundleArgumentDelegate.String("playlistId")

        fun newInstance(playlistId: String) = PlaylistFragment().withArguments { it.playlistId = playlistId }
    }

    override val viewModel by lazy {
        PlaylistViewModel(
            context = mainActivity,
            playlistId = arguments.playlistId,
            openLibrary = { mainActivity.openLibraryScreen() },
            updateToolbar = { defaultToolbar.updateToolbarTitle(it) })
    }
}