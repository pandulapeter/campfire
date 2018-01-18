package com.pandulapeter.campfire.feature.detail.songPage

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SongPageBinding
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import org.koin.android.ext.android.inject


/**
 * Displays lyrics and chords to a single song.
 *
 * Controlled by [SongPageViewModel].
 */
class SongPageFragment : CampfireFragment<SongPageBinding, SongPageViewModel>(R.layout.fragment_song_page) {
    private val songInfoRepository by inject<SongInfoRepository>()
    private val downloadedSongRepository by inject<DownloadedSongRepository>()
    private val userPreferenceRepository by inject<UserPreferenceRepository>()
    override val viewModel by lazy {
        SongPageViewModel(
            arguments.songId,
            analyticsManager,
            songInfoRepository,
            downloadedSongRepository,
            userPreferenceRepository
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        downloadedSongRepository.subscribe(viewModel)
        viewModel.loadSong()
    }

    override fun onStart() {
        super.onStart()
        downloadedSongRepository.subscribe(viewModel)
    }

    override fun onStop() {
        super.onStop()
        downloadedSongRepository.unsubscribe(viewModel)
    }

    companion object {
        private const val SONG_ID = "song_id"
        private val Bundle?.songId
            get() = this?.getString(SONG_ID) ?: ""

        fun newInstance(songId: String) = SongPageFragment().apply {
            arguments = Bundle().apply {
                putString(SONG_ID, songId)
            }
        }
    }
}