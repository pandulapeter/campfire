package com.pandulapeter.campfire.feature.detail.songPage

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SongPageBinding
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.detail.DetailEventBus
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.onEventTriggered
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
    private val detailEventBus by inject<DetailEventBus>()
    private var smoothScrollHolder = 0
    override val viewModel by lazy {
        SongPageViewModel(
            arguments.songId,
            analyticsManager,
            songInfoRepository,
            downloadedSongRepository,
            detailEventBus,
            userPreferenceRepository
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        downloadedSongRepository.subscribe(viewModel)
        viewModel.loadSong()
        viewModel.scrollSpeed.onEventTriggered(this) {
            smoothScrollHolder += it
            while (smoothScrollHolder > 10) {
                smoothScrollHolder -= 10
                binding.nestedScrollView.scrollBy(0, 1)
            }
            if (isScrolledToBottom()) {
                detailEventBus.onContentEndReached(viewModel.songId)
            }
        }
        viewModel.shouldScrollToTop.onEventTriggered(this) {
            //TODO: Does not always work.
            if (isScrolledToBottom()) {
                binding.nestedScrollView.smoothScrollTo(0, 0)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        downloadedSongRepository.subscribe(viewModel)
        detailEventBus.subscribe(viewModel)
    }

    override fun onStop() {
        super.onStop()
        downloadedSongRepository.unsubscribe(viewModel)
        detailEventBus.unsubscribe(viewModel)
    }

    private fun isScrolledToBottom() = binding.container.bottom <= binding.nestedScrollView.height + binding.nestedScrollView.scrollY


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