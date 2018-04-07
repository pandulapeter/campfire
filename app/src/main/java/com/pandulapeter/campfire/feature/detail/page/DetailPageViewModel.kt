package com.pandulapeter.campfire.feature.detail.page

import android.databinding.ObservableField
import com.pandulapeter.campfire.data.model.local.SongDetailMetadata
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import org.koin.android.ext.android.inject

class DetailPageViewModel(val song: Song) : CampfireViewModel(), SongDetailRepository.Subscriber {

    private val songDetailRepository by inject<SongDetailRepository>()
    val text = ObservableField("Loading...")

    override fun subscribe() {
        songDetailRepository.subscribe(this)
        songDetailRepository.getSongDetail(song)
    }

    override fun unsubscribe() = songDetailRepository.unsubscribe(this)

    override fun onSongDetailRepositoryUpdated(downloadedSongs: List<SongDetailMetadata>) = Unit

    override fun onSongDetailRepositoryDownloadSuccess(songDetail: SongDetail) {
        if (songDetail.id == song.id) {
            text.set(songDetail.text)
        }
    }

    override fun onSongDetailRepositoryDownloadQueueChanged(songIds: List<String>) = Unit

    override fun onSongDetailRepositoryDownloadError(song: Song) {
        if (song.id == this.song.id) {
            text.set("Error")
        }
    }
}