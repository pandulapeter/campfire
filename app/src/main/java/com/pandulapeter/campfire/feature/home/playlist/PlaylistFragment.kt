package com.pandulapeter.campfire.feature.home.playlist

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.shared.songList.SongListFragment
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.onPropertyChanged
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
            openLibrary = { mainActivity.openLibraryScreen() })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeRefreshLayout.isEnabled = false
        viewModel.songCount.onPropertyChanged(this) { updateToolbarTitle(it) }
        viewModel.playlist.onPropertyChanged(this) { updateToolbarTitle() }
    }

    private fun updateToolbarTitle(songCount: Int = viewModel.songCount.get()) = defaultToolbar.updateToolbarTitle(
        viewModel.playlist.get()?.title ?: getString(R.string.home_favorites),
        if (songCount == 0) getString(R.string.manage_playlists_song_count_empty) else mainActivity.resources.getQuantityString(R.plurals.playlist_song_count, songCount, songCount)
    )
}