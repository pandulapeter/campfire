package com.pandulapeter.campfire.feature.main.shared.baseSongList

import android.content.Context
import androidx.annotation.CallSuper
import androidx.databinding.ObservableBoolean
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.model.local.SongDetailMetadata
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.main.shared.recycler.RecyclerAdapter
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.CollectionItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.ItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.SongItemViewModel
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.UI
import com.pandulapeter.campfire.util.WORKER
import com.pandulapeter.campfire.util.mutableLiveDataOf
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

abstract class BaseSongListViewModel(
    protected val context: Context,
    protected val songRepository: SongRepository,
    protected val songDetailRepository: SongDetailRepository,
    val preferenceDatabase: PreferenceDatabase,
    val playlistRepository: PlaylistRepository,
    protected val analyticsManager: AnalyticsManager,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker), SongRepository.Subscriber, SongDetailRepository.Subscriber, PlaylistRepository.Subscriber {

    val isSwipeRefreshEnabled = ObservableBoolean(true)
    abstract val screenName: String
    private var coroutine: CoroutineContext? = null
    protected var songs = sequenceOf<Song>()
    val collection = MutableLiveData<CollectionItemViewModel?>()
    val shouldShowUpdateErrorSnackbar = MutableLiveData<Boolean?>()
    val downloadSongError = MutableLiveData<Song?>()
    val isLoading = mutableLiveDataOf(false)
    val state = mutableLiveDataOf(StateLayout.State.LOADING)
    open val placeholderText = R.string.campfire
    val buttonText = MutableLiveData<Int>()
    open val buttonIcon = 0
    var isDetailScreenOpen = false
    open val cardTransitionName = ""
    open val imageTransitionName = ""
    val shouldScrollToTop = MutableLiveData<Boolean?>()
    val items = MutableLiveData<List<ItemViewModel>?>()
    val changeEvent = MutableLiveData<Pair<Int, RecyclerAdapter.Payload>?>()

    @CallSuper
    override fun subscribe() {
        songRepository.subscribe(this)
        songDetailRepository.subscribe(this)
        playlistRepository.subscribe(this)
        isDetailScreenOpen = false
    }

    @CallSuper
    override fun unsubscribe() {
        songRepository.unsubscribe(this)
        songDetailRepository.unsubscribe(this)
        playlistRepository.unsubscribe(this)
    }

    @CallSuper
    override fun onCleared() {
        super.onCleared()
        coroutine?.cancel()
    }

    @CallSuper
    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        songs = data.asSequence()
        updateAdapterItems(true)
    }

    override fun onSongRepositoryLoadingStateChanged(isLoading: Boolean) {
        this.isLoading.value = isLoading
        if (songs.toList().isEmpty() && isLoading) {
            state.value = StateLayout.State.LOADING
        }
    }

    override fun onSongRepositoryUpdateError() {
        if (songs.toList().isEmpty()) {
            analyticsManager.onConnectionError(true, screenName)
            state.value = StateLayout.State.ERROR
        } else {
            analyticsManager.onConnectionError(false, screenName)
            shouldShowUpdateErrorSnackbar.value = true
        }
    }

    override fun onSongDetailRepositoryUpdated(downloadedSongs: List<SongDetailMetadata>) {
        if (songs.toList().isNotEmpty()) {
            updateAdapterItems()
        }
    }

    override fun onSongDetailRepositoryDownloadSuccess(songDetail: SongDetail) {
        items.value.orEmpty().let { items ->
            items.indexOfLast { it is SongItemViewModel && it.song.id == songDetail.id }.let { index ->
                if (index != RecyclerView.NO_POSITION && (items[index] as? SongItemViewModel)?.song?.version == songDetail.version) {
                    changeEvent.value = index to RecyclerAdapter.Payload.DownloadStateChanged(SongItemViewModel.DownloadState.Downloaded.UpToDate)
                }
            }
        }
    }

    override fun onSongDetailRepositoryDownloadQueueChanged(songIds: List<String>) {
        songIds.forEach { songId ->
            items.value.orEmpty().let { items ->
                items.indexOfLast { it is SongItemViewModel && it.song.id == songId }.let { index ->
                    if (index != RecyclerView.NO_POSITION) {
                        changeEvent.value = index to RecyclerAdapter.Payload.DownloadStateChanged(SongItemViewModel.DownloadState.Downloading)
                    }
                }
            }
        }
    }

    override fun onSongDetailRepositoryDownloadError(song: Song) {
        analyticsManager.onConnectionError(!songDetailRepository.isSongDownloaded(song.id), song.id)
        downloadSongError.value = song
        items.value.orEmpty().let { items ->
            items.indexOfLast { it is SongItemViewModel && it.song.id == song.id }.let { index ->
                if (index != RecyclerView.NO_POSITION) {
                    changeEvent.value = index to RecyclerAdapter.Payload.DownloadStateChanged(
                        when {
                            songDetailRepository.isSongDownloaded(song.id) -> SongItemViewModel.DownloadState.Downloaded.Outdated
                            song.isNew -> SongItemViewModel.DownloadState.NotDownloaded.New
                            else -> SongItemViewModel.DownloadState.NotDownloaded.Old
                        }
                    )
                }
            }
        }
    }

    override fun onPlaylistsUpdated(playlists: List<Playlist>) {
        if (songs.toList().isNotEmpty()) {
            updateAdapterItems()
        }
    }

    override fun onSongAddedToPlaylistForTheFirstTime(songId: String) {
        items.value.orEmpty().let { items ->
            items.indexOfLast { it is SongItemViewModel && it.song.id == songId }.let { index ->
                if (index != RecyclerView.NO_POSITION) {
                    changeEvent.value = index to RecyclerAdapter.Payload.IsSongInAPlaylistChanged(true)
                }
            }
        }
    }

    override fun onSongRemovedFromAllPlaylists(songId: String) {
        items.value.orEmpty().let { items ->
            items.indexOfLast { it is SongItemViewModel && it.song.id == songId }.let { index ->
                if (index != RecyclerView.NO_POSITION) {
                    changeEvent.value = index to RecyclerAdapter.Payload.IsSongInAPlaylistChanged(false)
                }
            }
        }
    }

    override fun onPlaylistOrderChanged(playlists: List<Playlist>) = Unit

    abstract fun onActionButtonClicked()

    fun updateData() = songRepository.updateData()

    fun downloadSong(song: Song) = songDetailRepository.getSongDetail(song, true)

    fun areThereMoreThanOnePlaylists() = playlistRepository.cache.size > 1

    fun toggleFavoritesState(songId: String) {
        if (playlistRepository.isSongInPlaylist(Playlist.FAVORITES_ID, songId)) {
            analyticsManager.onSongPlaylistStateChanged(
                songId,
                playlistRepository.getPlaylistCountForSong(songId) - 1,
                screenName,
                playlistRepository.cache.size > 1
            )
            playlistRepository.removeSongFromPlaylist(Playlist.FAVORITES_ID, songId)
        } else {
            analyticsManager.onSongPlaylistStateChanged(
                songId,
                playlistRepository.getPlaylistCountForSong(songId) + 1,
                screenName,
                playlistRepository.cache.size > 1
            )
            playlistRepository.addSongToPlaylist(Playlist.FAVORITES_ID, songId)
        }
    }

    protected open fun canUpdateUI() = true

    protected abstract fun Sequence<Song>.createViewModels(): List<ItemViewModel>

    protected open fun onListUpdated(items: List<ItemViewModel>) {
        state.value = if (items.isEmpty()) StateLayout.State.ERROR else StateLayout.State.NORMAL
    }

    fun updateAdapterItems(shouldScrollToTop: Boolean = false) {
        if (canUpdateUI() && playlistRepository.isCacheLoaded() && songRepository.isCacheLoaded() && songDetailRepository.isCacheLoaded()) {
            coroutine?.cancel()
            coroutine = launch(WORKER) {
                songs.createViewModels().let {
                    launch(UI) {
                        this@BaseSongListViewModel.shouldScrollToTop.value = shouldScrollToTop
                        items.value = it
                        onListUpdated(it)
                    }
                }
                coroutine = null
            }
        }
    }
}