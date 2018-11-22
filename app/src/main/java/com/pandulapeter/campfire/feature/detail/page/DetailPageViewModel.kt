package com.pandulapeter.campfire.feature.detail.page

import androidx.databinding.ObservableField
import androidx.databinding.ObservableFloat
import androidx.databinding.ObservableInt
import com.pandulapeter.campfire.data.model.local.SongDetailMetadata
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.feature.detail.DetailPageEventBus
import com.pandulapeter.campfire.feature.detail.page.parsing.SongParser
import com.pandulapeter.campfire.feature.shared.OldCampfireViewModel
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.UI
import com.pandulapeter.campfire.util.WORKER
import com.pandulapeter.campfire.util.onPropertyChanged
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DetailPageViewModel(
    val song: Song,
    private val initialTextSize: Int,
    private val songParser: SongParser,
    private val onDataLoaded: () -> Unit
) : OldCampfireViewModel(), SongDetailRepository.Subscriber {

    private val songDetailRepository by inject<SongDetailRepository>()
    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val detailPageEventBus by inject<DetailPageEventBus>()
    private val analyticsManager by inject<AnalyticsManager>()
    private var rawText = ""
    val text = ObservableField<CharSequence>("")
    val state = ObservableField<StateLayout.State>(StateLayout.State.LOADING)
    val textSize = ObservableFloat(preferenceDatabase.fontSize * initialTextSize)
    val transposition = ObservableInt(preferenceDatabase.getTransposition(song.id))

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
            } else {
                refreshText()
                analyticsManager.onTranspositionChanged(song.id, modifiedValue)
                preferenceDatabase.setTransposition(song.id, modifiedValue)
                detailPageEventBus.notifyTranspositionChanged(song.id, modifiedValue)
            }
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
                detailPageEventBus.notifyTranspositionChanged(song.id, transposition.get())
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
            analyticsManager.onConnectionError(true, song.id)
            state.set(StateLayout.State.ERROR)
        }
    }

    fun loadData() = songDetailRepository.getSongDetail(song)

    fun updateTextSize() = textSize.set(preferenceDatabase.fontSize * initialTextSize)

    fun refreshText(onDone: () -> Unit = {}) {
        GlobalScope.launch(WORKER) {
            val parsed = songParser.parseSong(rawText, preferenceDatabase.shouldShowChords, preferenceDatabase.shouldUseGermanNotation, transposition.get())
            launch(UI) {
                text.set(parsed)
                onDone()
            }
        }
    }
}