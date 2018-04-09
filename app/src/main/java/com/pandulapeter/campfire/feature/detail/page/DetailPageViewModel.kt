package com.pandulapeter.campfire.feature.detail.page

import android.databinding.ObservableField
import com.pandulapeter.campfire.data.model.local.SongDetailMetadata
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import org.koin.android.ext.android.inject

class DetailPageViewModel(val song: Song, private val onDataLoaded: () -> Unit) : CampfireViewModel(), SongDetailRepository.Subscriber {

    private val songDetailRepository by inject<SongDetailRepository>()
    val text = ObservableField("")
    val state = ObservableField<StateLayout.State>(StateLayout.State.LOADING)

    override fun subscribe() {
        songDetailRepository.subscribe(this)
        if (text.get().isNullOrEmpty()) {
            loadData()
        }
    }

    override fun unsubscribe() = songDetailRepository.unsubscribe(this)

    override fun onSongDetailRepositoryUpdated(downloadedSongs: List<SongDetailMetadata>) = Unit

    override fun onSongDetailRepositoryDownloadSuccess(songDetail: SongDetail) {
        if (songDetail.id == song.id) {
            text.set(songDetail.text)
            state.set(StateLayout.State.NORMAL)
            onDataLoaded()
        }
    }

    override fun onSongDetailRepositoryDownloadQueueChanged(songIds: List<String>) {
        if (songIds.contains(song.id) && text.get().isNullOrEmpty()) {
            state.set(StateLayout.State.LOADING)
        }
    }

    override fun onSongDetailRepositoryDownloadError(song: Song) {
        if (song.id == this.song.id && text.get().isNullOrEmpty()) {
            state.set(StateLayout.State.ERROR)
        }
    }

    fun loadData() = songDetailRepository.getSongDetail(song)
}