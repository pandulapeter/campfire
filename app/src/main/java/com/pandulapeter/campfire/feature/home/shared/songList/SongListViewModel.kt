package com.pandulapeter.campfire.feature.home.shared.songList

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import android.support.annotation.CallSuper
import android.support.v7.widget.RecyclerView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.model.local.SongDetailMetadata
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.home.collections.CollectionListItemViewModel
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancel
import org.koin.android.ext.android.inject
import kotlin.coroutines.experimental.CoroutineContext

abstract class SongListViewModel(protected val context: Context) : CampfireViewModel(), SongRepository.Subscriber, SongDetailRepository.Subscriber, PlaylistRepository.Subscriber {

    protected val songRepository by inject<SongRepository>()
    protected val songDetailRepository by inject<SongDetailRepository>()
    protected val preferenceDatabase by inject<PreferenceDatabase>()
    protected val playlistRepository by inject<PlaylistRepository>()
    val shouldUpdateScrollState = ObservableBoolean()
    private var coroutine: CoroutineContext? = null
    protected var librarySongs = sequenceOf<Song>()
    val collection = ObservableField<CollectionListItemViewModel.CollectionViewModel?>()
    val adapter = SongListAdapter()
    val shouldShowUpdateErrorSnackbar = ObservableBoolean()
    val downloadSongError = ObservableField<Song?>()
    val isLoading = ObservableBoolean()
    val state = ObservableField<StateLayout.State>(StateLayout.State.LOADING)
    val placeholderText = ObservableInt(R.string.library_initializing_error)
    val buttonText = ObservableInt(R.string.try_again)
    val buttonIcon = ObservableInt()
    var isDetailScreenOpen = false
    open val cardTransitionName = ""
    open val imageTransitionName = ""

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
    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        librarySongs = data.asSequence()
        updateAdapterItems(true)
    }

    override fun onSongRepositoryLoadingStateChanged(isLoading: Boolean) {
        this.isLoading.set(isLoading)
        if (librarySongs.toList().isEmpty() && isLoading) {
            state.set(StateLayout.State.LOADING)
        }
    }

    override fun onSongRepositoryUpdateError() {
        if (librarySongs.toList().isEmpty()) {
            state.set(StateLayout.State.ERROR)
        } else {
            shouldShowUpdateErrorSnackbar.set(true)
        }
    }

    override fun onSongDetailRepositoryUpdated(downloadedSongs: List<SongDetailMetadata>) {
        if (librarySongs.toList().isNotEmpty()) {
            updateAdapterItems()
        }
    }

    override fun onSongDetailRepositoryDownloadSuccess(songDetail: SongDetail) {
        adapter.items.indexOfLast { it is SongListItemViewModel.SongViewModel && it.song.id == songDetail.id }.let { index ->
            if (index != RecyclerView.NO_POSITION && (adapter.items[index] as? SongListItemViewModel.SongViewModel)?.song?.version == songDetail.version) {
                adapter.notifyItemChanged(
                    index,
                    SongListAdapter.Payload.DownloadStateChanged(SongListItemViewModel.SongViewModel.DownloadState.Downloaded.UpToDate)
                )
            }
        }
    }

    override fun onSongDetailRepositoryDownloadQueueChanged(songIds: List<String>) {
        songIds.forEach { songId ->
            adapter.items.indexOfLast { it is SongListItemViewModel.SongViewModel && it.song.id == songId }.let { index ->
                if (index != RecyclerView.NO_POSITION) {
                    adapter.notifyItemChanged(
                        index,
                        SongListAdapter.Payload.DownloadStateChanged(SongListItemViewModel.SongViewModel.DownloadState.Downloading)
                    )
                }
            }
        }
    }

    override fun onSongDetailRepositoryDownloadError(song: Song) {
        downloadSongError.set(song)
        adapter.items.indexOfLast { it is SongListItemViewModel.SongViewModel && it.song.id == song.id }.let { index ->
            if (index != RecyclerView.NO_POSITION) {
                adapter.notifyItemChanged(
                    index,
                    SongListAdapter.Payload.DownloadStateChanged(
                        when {
                            songDetailRepository.isSongDownloaded(song.id) -> SongListItemViewModel.SongViewModel.DownloadState.Downloaded.Deprecated
                            song.isNew -> SongListItemViewModel.SongViewModel.DownloadState.NotDownloaded.New
                            else -> SongListItemViewModel.SongViewModel.DownloadState.NotDownloaded.Old
                        }
                    )
                )
            }
        }
    }

    override fun onPlaylistsUpdated(playlists: List<Playlist>) {
        if (librarySongs.toList().isNotEmpty()) {
            updateAdapterItems()
        }
    }

    override fun onSongAddedToPlaylistForTheFirstTime(songId: String) {
        adapter.items.indexOfLast { it is SongListItemViewModel.SongViewModel && it.song.id == songId }.let { index ->
            if (index != RecyclerView.NO_POSITION) {
                adapter.notifyItemChanged(
                    index,
                    SongListAdapter.Payload.IsSongInAPlaylistChanged(true)
                )
            }
        }
    }

    override fun onSongRemovedFromAllPlaylists(songId: String) {
        adapter.items.indexOfLast { it is SongListItemViewModel.SongViewModel && it.song.id == songId }.let { index ->
            if (index != RecyclerView.NO_POSITION) {
                adapter.notifyItemChanged(
                    index,
                    SongListAdapter.Payload.IsSongInAPlaylistChanged(false)
                )
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
            playlistRepository.removeSongFromPlaylist(Playlist.FAVORITES_ID, songId)
        } else {
            playlistRepository.addSongToPlaylist(Playlist.FAVORITES_ID, songId)
        }
    }

    protected open fun canUpdateUI() = true

    protected abstract fun Sequence<Song>.createViewModels(): List<SongListItemViewModel>

    @CallSuper
    protected open fun onListUpdated(items: List<SongListItemViewModel>) {
        state.set(if (items.isEmpty()) StateLayout.State.ERROR else StateLayout.State.NORMAL)
        shouldUpdateScrollState.set(true)
    }

    protected fun updateAdapterItems(shouldScrollToTop: Boolean = false) {
        if (canUpdateUI() && playlistRepository.isCacheLoaded() && songRepository.isCacheLoaded() && songDetailRepository.isCacheLoaded()) {
            coroutine?.cancel()
            coroutine = async(UI) {
                async(CommonPool) { librarySongs.createViewModels() }.await().let {
                    adapter.shouldScrollToTop = shouldScrollToTop
                    adapter.items = it
                    onListUpdated(it)
                }
                coroutine = null
            }
        }
    }
}