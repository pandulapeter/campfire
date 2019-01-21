package com.pandulapeter.campfire.feature.detail.page

import android.content.Context
import androidx.lifecycle.MediatorLiveData
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.SongDetailMetadata
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.feature.detail.DetailPageEventBus
import com.pandulapeter.campfire.feature.detail.page.parsing.SongParser
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.UI
import com.pandulapeter.campfire.util.WORKER
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.mutableLiveDataOf
import kotlinx.coroutines.launch

class DetailPageViewModel(
    val song: Song,
    context: Context,
    interactionBlocker: InteractionBlocker,
    private val songDetailRepository: SongDetailRepository,
    private val preferenceDatabase: PreferenceDatabase,
    private val detailPageEventBus: DetailPageEventBus,
    private val analyticsManager: AnalyticsManager,
    private val songParser: SongParser
) : CampfireViewModel(interactionBlocker), SongDetailRepository.Subscriber {

    private var rawText = ""
    private val initialTextSize = context.dimension(R.dimen.text_normal)
    val text = mutableLiveDataOf<CharSequence>("")
    val state = mutableLiveDataOf(StateLayout.State.LOADING)
    val textSize = mutableLiveDataOf(preferenceDatabase.fontSize * initialTextSize)
    val transposition = mutableLiveDataOf(preferenceDatabase.getTransposition(song.id))
    val onDataLoaded = MediatorLiveData<Boolean?>()

    init {
        transposition.observeForever { newValue ->
            var modifiedValue = newValue
            while (modifiedValue > 6) {
                modifiedValue -= 12
            }
            while (modifiedValue < -6) {
                modifiedValue += 12
            }
            if (modifiedValue != newValue) {
                transposition.value = modifiedValue
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
        if (text.value.isNullOrEmpty()) {
            loadData()
        }
    }

    override fun unsubscribe() = songDetailRepository.unsubscribe(this)

    override fun onSongDetailRepositoryUpdated(downloadedSongs: List<SongDetailMetadata>) = Unit

    override fun onSongDetailRepositoryDownloadSuccess(songDetail: SongDetail) {
        if (songDetail.id == song.id) {
            rawText = songDetail.text
            refreshText {
                state.value = StateLayout.State.NORMAL
                onDataLoaded.value = true
                transposition.value?.let { detailPageEventBus.notifyTranspositionChanged(song.id, it) }
            }
        }
    }

    override fun onSongDetailRepositoryDownloadQueueChanged(songIds: List<String>) {
        if (songIds.contains(song.id) && text.value.isNullOrEmpty()) {
            state.value = StateLayout.State.LOADING
        }
    }

    override fun onSongDetailRepositoryDownloadError(song: Song) {
        if (song.id == this.song.id && text.value.isNullOrEmpty()) {
            analyticsManager.onConnectionError(true, song.id)
            state.value = StateLayout.State.ERROR
        }
    }

    fun loadData() = songDetailRepository.getSongDetail(song)

    fun updateTextSize() {
        textSize.value = preferenceDatabase.fontSize * initialTextSize
    }

    private fun refreshText(onDone: () -> Unit = {}) {
        transposition.value?.let { transposition ->
            launch(WORKER) {
                val parsed = songParser.parseSong(rawText, preferenceDatabase.shouldShowChords, preferenceDatabase.shouldUseGermanNotation, transposition)
                launch(UI) {
                    text.value = parsed
                    onDone()
                }
            }
        }
    }
}