package com.pandulapeter.campfire.feature.home.shared

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.support.annotation.CallSuper
import com.pandulapeter.campfire.data.model.local.SongDetailMetadata
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancel
import org.koin.android.ext.android.inject
import kotlin.coroutines.experimental.CoroutineContext

abstract class SongListViewModel : CampfireViewModel(), SongRepository.Subscriber, SongDetailRepository.Subscriber {

    private val songRepository by inject<SongRepository>()
    protected val songDetailRepository by inject<SongDetailRepository>()
    private var librarySongs = sequenceOf<Song>()
    private var coroutine: CoroutineContext? = null
    val adapter = SongAdapter()
    val shouldShowUpdateErrorSnackbar = ObservableBoolean()
    val downloadSongError = ObservableField<Song?>()
    val isLoading = ObservableBoolean()

    @CallSuper
    override fun subscribe() {
        songRepository.subscribe(this)
        songDetailRepository.subscribe(this)
    }

    @CallSuper
    override fun unsubscribe() {
        songRepository.unsubscribe(this)
        songDetailRepository.unsubscribe(this)
    }

    @CallSuper
    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        librarySongs = data.asSequence()
        updateAdapterItems()
    }

    override fun onSongRepositoryLoadingStateChanged(isLoading: Boolean) = this.isLoading.set(isLoading)

    override fun onSongRepositoryUpdateError() = shouldShowUpdateErrorSnackbar.set(true)

    override fun onSongDetailRepositoryUpdated(downloadedSongs: List<SongDetailMetadata>) = updateAdapterItems()

    override fun onSongDetailRepositoryDownloadSuccess(songDetail: SongDetail) = updateAdapterItems()

    override fun onSongDetailRepositoryDownloadQueueChanged(songIds: List<String>) = updateAdapterItems()

    override fun onSongDetailRepositoryDownloadError(song: Song) = downloadSongError.set(song)

    fun updateData() = songRepository.updateData()

    fun downloadSong(song: Song) = songDetailRepository.getSongDetail(song)

    protected abstract fun Sequence<Song>.createViewModels(): List<SongViewModel>

    protected fun updateAdapterItems(shouldScrollToTop: Boolean = false) {
        coroutine?.cancel()
        coroutine = async(UI) {
            adapter.shouldScrollToTop = shouldScrollToTop
            adapter.items = async(CommonPool) { librarySongs.createViewModels() }.await()
            coroutine = null
        }
    }
}