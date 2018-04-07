package com.pandulapeter.campfire.feature.home.shared

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.support.annotation.CallSuper
import android.support.v7.widget.RecyclerView
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

    override fun onSongDetailRepositoryDownloadSuccess(songDetail: SongDetail) {
        adapter.items.indexOfLast { it.song.id == songDetail.id }.let { index ->
            if (index != RecyclerView.NO_POSITION) {
                adapter.notifyItemChanged(index, SongViewModel.DownloadState.Downloaded.UpToDate)
            }
        }
    }

    override fun onSongDetailRepositoryDownloadQueueChanged(songIds: List<String>) {
        songIds.forEach { songId ->
            adapter.items.indexOfLast { it.song.id == songId }.let { index ->
                if (index != RecyclerView.NO_POSITION) {
                    adapter.notifyItemChanged(index, SongViewModel.DownloadState.Downloading)
                }
            }
        }
    }

    override fun onSongDetailRepositoryDownloadError(song: Song) {
        downloadSongError.set(song)
        adapter.items.indexOfLast { it.song.id == song.id }.let { index ->
            if (index != RecyclerView.NO_POSITION) {
                adapter.notifyItemChanged(
                    index,
                    when {
                        songDetailRepository.isSongDownloaded(song.id) -> SongViewModel.DownloadState.Downloaded.Deprecated
                        song.isNew -> SongViewModel.DownloadState.NotDownloaded.New
                        else -> SongViewModel.DownloadState.NotDownloaded.Old
                    }
                )
            }
        }
    }

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