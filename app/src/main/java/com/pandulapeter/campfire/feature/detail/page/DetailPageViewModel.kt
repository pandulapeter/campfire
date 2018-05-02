package com.pandulapeter.campfire.feature.detail.page

import android.databinding.ObservableField
import android.databinding.ObservableFloat
import android.databinding.ObservableInt
import com.pandulapeter.campfire.data.model.local.SongDetailMetadata
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class DetailPageViewModel(
    val song: Song,
    private val initialTextSize: Int,
    private val onDataLoaded: () -> Unit
) : CampfireViewModel(), SongDetailRepository.Subscriber {

    private val songDetailRepository by inject<SongDetailRepository>()
    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val songParser by inject<SongParser>()
    private var rawText = ""
    val text = ObservableField<CharSequence>("")
    val state = ObservableField<StateLayout.State>(StateLayout.State.LOADING)
    val textSize = ObservableFloat(preferenceDatabase.fontSize * initialTextSize)
    val transposition = ObservableInt() //TODO: Implement transposition.

    init {
        transposition.onPropertyChanged {
            var modifiedValue = it
            while (modifiedValue > 6) {
                modifiedValue -= 12
            }
            while (modifiedValue < -6) {
                modifiedValue += 12
            }
            if (modifiedValue != it) {
                transposition.set(modifiedValue)
            }
            refreshText()
        }
    }

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
            rawText = songDetail.text
            refreshText {
                state.set(StateLayout.State.NORMAL)
                onDataLoaded()
            }
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

    fun updateTextSize() = textSize.set(preferenceDatabase.fontSize * initialTextSize)

    fun refreshText(onDone: () -> Unit = {}) {
//        async(UI) {
        text.set(
//                async(CommonPool) {
            songParser.parseSong(rawText, preferenceDatabase.shouldShowChords, preferenceDatabase.shouldUseGermanNotation, transposition.get())
//                }.await()
        )
        onDone()
//        }
    }
}