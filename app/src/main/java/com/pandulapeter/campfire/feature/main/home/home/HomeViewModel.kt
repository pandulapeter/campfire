package com.pandulapeter.campfire.feature.main.home.home

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import android.support.v7.widget.RecyclerView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Language
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.model.local.SongDetailMetadata
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.main.collections.CollectionListItemViewModel
import com.pandulapeter.campfire.feature.main.shared.baseSongList.SongListItemViewModel
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.swap
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.cancel
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.koin.android.ext.android.inject
import kotlin.coroutines.experimental.CoroutineContext

class HomeViewModel(
    private val onDataLoaded: (languages: List<Language>) -> Unit,
    private val openSecondaryNavigationDrawer: () -> Unit,
    private val context: Context
) : CampfireViewModel(), CollectionRepository.Subscriber, SongRepository.Subscriber, SongDetailRepository.Subscriber, PlaylistRepository.Subscriber {

    private val analyticsManager by inject<AnalyticsManager>()
    private val preferenceDatabase by inject<PreferenceDatabase>()
    val collectionRepository by inject<CollectionRepository>()
    private val songRepository by inject<SongRepository>()
    private val songDetailRepository by inject<SongDetailRepository>()
    private val playlistRepository by inject<PlaylistRepository>()
    private val newText = context.getString(R.string.new_tag)
    private var coroutine: CoroutineContext? = null
    private var collections = sequenceOf<Collection>()
    private var songs = sequenceOf<Song>()
    private var randomCollections = listOf<Collection>()
    private var randomSongs = listOf<Song>()
    private var displayedRandomCollections = listOf<Collection>()
    var displayedRandomSongs = listOf<Song>()
    var firstRandomSongIndex = 0
    val adapter = HomeAdapter()
    val state = ObservableField<StateLayout.State>(StateLayout.State.LOADING)
    val isLoading = ObservableBoolean()
    val shouldShowUpdateErrorSnackbar = ObservableBoolean()
    val downloadSongError = ObservableField<Song?>()
    val placeholderText = ObservableInt(R.string.home_initializing_error)
    val buttonText = ObservableInt(R.string.try_again)
    val buttonIcon = ObservableInt()
    private var isFirstLoadingDone = false
    var shouldShowExplicit = preferenceDatabase.shouldShowExplicit
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowExplicit = value
                updateAdapterItems(true)
            }
        }
    var disabledLanguageFilters = preferenceDatabase.disabledLanguageFilters
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.disabledLanguageFilters = value
                updateAdapterItems(true)
            }
        }
    var languages = mutableListOf<Language>()

    override fun subscribe() {
        collectionRepository.subscribe(this)
        songRepository.subscribe(this)
        songDetailRepository.subscribe(this)
        playlistRepository.subscribe(this)
    }

    override fun unsubscribe() {
        songRepository.unsubscribe(this)
        collectionRepository.unsubscribe(this)
        songDetailRepository.unsubscribe(this)
        playlistRepository.unsubscribe(this)
    }

    override fun onCollectionsUpdated(data: List<Collection>) {
        collections = data.asSequence()
        updateAdapterItems(false)
    }

    override fun onCollectionsLoadingStateChanged(isLoading: Boolean) {
        this.isLoading.set(isLoading)
        if (collections.toList().isEmpty() && isLoading) {
            state.set(StateLayout.State.LOADING)
        }
    }

    override fun onCollectionRepositoryUpdateError() = onError()

    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        songs = data.asSequence()
        updateAdapterItems(false)
    }

    override fun onSongRepositoryLoadingStateChanged(isLoading: Boolean) {
        this.isLoading.set(isLoading)
        if (songs.toList().isEmpty() && isLoading) {
            state.set(StateLayout.State.LOADING)
        }
    }

    override fun onSongRepositoryUpdateError() = onError()


    override fun onSongDetailRepositoryUpdated(downloadedSongs: List<SongDetailMetadata>) {
        if (songs.toList().isNotEmpty()) {
            updateAdapterItems(false)
        }
    }

    override fun onSongDetailRepositoryDownloadSuccess(songDetail: SongDetail) {
        adapter.items.indexOfLast { it is SongListItemViewModel.SongViewModel && it.song.id == songDetail.id }.let { index ->
            if (index != RecyclerView.NO_POSITION && (adapter.items[index] as? SongListItemViewModel.SongViewModel)?.song?.version == songDetail.version) {
                adapter.notifyItemChanged(
                    index,
                    HomeAdapter.Payload.DownloadStateChanged(SongListItemViewModel.SongViewModel.DownloadState.Downloaded.UpToDate)
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
                        HomeAdapter.Payload.DownloadStateChanged(SongListItemViewModel.SongViewModel.DownloadState.Downloading)
                    )
                }
            }
        }
    }

    override fun onSongDetailRepositoryDownloadError(song: Song) {
        analyticsManager.onConnectionError(!songDetailRepository.isSongDownloaded(song.id), song.id)
        downloadSongError.set(song)
        adapter.items.indexOfLast { it is SongListItemViewModel.SongViewModel && it.song.id == song.id }.let { index ->
            if (index != RecyclerView.NO_POSITION) {
                adapter.notifyItemChanged(
                    index,
                    HomeAdapter.Payload.DownloadStateChanged(
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
        if (songs.toList().isNotEmpty()) {
            updateAdapterItems(false)
        }
    }

    override fun onSongAddedToPlaylistForTheFirstTime(songId: String) {
        adapter.items.indexOfLast { it is SongListItemViewModel.SongViewModel && it.song.id == songId }.let { index ->
            if (index != RecyclerView.NO_POSITION) {
                adapter.notifyItemChanged(
                    index,
                    HomeAdapter.Payload.IsSongInAPlaylistChanged(true)
                )
            }
        }
    }

    override fun onSongRemovedFromAllPlaylists(songId: String) {
        adapter.items.indexOfLast { it is SongListItemViewModel.SongViewModel && it.song.id == songId }.let { index ->
            if (index != RecyclerView.NO_POSITION) {
                adapter.notifyItemChanged(
                    index,
                    HomeAdapter.Payload.IsSongInAPlaylistChanged(false)
                )
            }
        }
    }

    override fun onPlaylistOrderChanged(playlists: List<Playlist>) = Unit

    private fun onError() {
        if (collections.toList().isEmpty() || songs.toList().isEmpty()) {
            analyticsManager.onConnectionError(true, AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
            state.set(StateLayout.State.ERROR)
        } else {
            analyticsManager.onConnectionError(false, AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
            shouldShowUpdateErrorSnackbar.set(true)
        }
    }

    private fun onListUpdated(items: List<HomeItemViewModel>) {
        state.set(if (items.isEmpty()) StateLayout.State.ERROR else StateLayout.State.NORMAL)
        if (collections.toList().isNotEmpty()) {
            placeholderText.set(R.string.home_placeholder)
            buttonText.set(R.string.filters)
            buttonIcon.set(R.drawable.ic_filter_and_sort_24dp)
        }
    }

    fun onActionButtonClicked() {
        if (buttonIcon.get() == 0) {
            updateData()
        } else {
            openSecondaryNavigationDrawer()
        }
    }

    fun updateData() {
        randomCollections = listOf()
        randomSongs = listOf()
        collectionRepository.updateData()
        songRepository.updateData()
    }

    fun restoreToolbarButtons() {
        if (languages.isNotEmpty()) {
            onDataLoaded(languages)
        }
    }

    fun areThereMoreThanOnePlaylists() = playlistRepository.cache.size > 1

    fun toggleFavoritesState(songId: String) {
        if (playlistRepository.isSongInPlaylist(Playlist.FAVORITES_ID, songId)) {
            analyticsManager.onSongPlaylistStateChanged(
                songId,
                playlistRepository.getPlaylistCountForSong(songId) - 1,
                AnalyticsManager.PARAM_VALUE_SCREEN_HOME,
                playlistRepository.cache.size > 1
            )
            playlistRepository.removeSongFromPlaylist(Playlist.FAVORITES_ID, songId)
        } else {
            analyticsManager.onSongPlaylistStateChanged(
                songId,
                playlistRepository.getPlaylistCountForSong(songId) + 1,
                AnalyticsManager.PARAM_VALUE_SCREEN_HOME,
                playlistRepository.cache.size > 1
            )
            playlistRepository.addSongToPlaylist(Playlist.FAVORITES_ID, songId)
        }
    }


    fun onBookmarkClicked(position: Int, collection: Collection) {
        collectionRepository.toggleBookmarkedState(collection.id)
        analyticsManager.onCollectionBookmarkedStateChanged(
            collection.id,
            collection.isBookmarked == true,
            AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTIONS
        )
        adapter.notifyItemChanged(position, HomeAdapter.Payload.BookmarkedStateChanged(collection.isBookmarked ?: false))
        updateAdapterItems(false)
    }

    fun downloadSong(song: Song) = songDetailRepository.getSongDetail(song, true)

    private fun updateAdapterItems(shouldRefreshRandom: Boolean) {
        if (collectionRepository.isCacheLoaded() && songRepository.isCacheLoaded() && collections.toList().isNotEmpty() && songs.toList().isNotEmpty()) {
            if (!isFirstLoadingDone) {
                languages.swap(collectionRepository.languages.union(songRepository.languages).toList())
                onDataLoaded(languages)
                isFirstLoadingDone = true
            }
            if (shouldRefreshRandom || randomCollections.isEmpty() || randomSongs.isEmpty()) {
                randomCollections = collections
                    .filterExplicitCollections()
                    .filterCollectionsByLanguage()
                    .toList()
                    .shuffled()
                randomSongs = songs
                    .filterExplicitSongs()
                    .filterSongsByLanguage()
                    .toList()
                    .shuffled()
            }
            coroutine?.cancel()
            coroutine = launch(UI) {
                withContext(CommonPool) { createViewModels() }.let {
                    adapter.shouldScrollToTop = false
                    adapter.items = it
                    onListUpdated(it)
                }
                coroutine = null
            }
        }
    }

    private fun createViewModels() = mutableListOf<HomeItemViewModel>().apply {
        val newCollections = collections
            .filterExplicitCollections()
            .filterCollectionsByLanguage()
            .toList()
            .takeLast(3)
            .asReversed()
        newCollections
            .map { CollectionListItemViewModel.CollectionViewModel(it, newText) }
            .let {
                if (it.isNotEmpty()) {
                    add(HomeHeaderViewModel(context.getString(R.string.home_new_collections)))
                    addAll(it)
                }
            }
        val newSongs = songs
            .filterExplicitSongs()
            .filterSongsByLanguage()
            .toList()
            .takeLast(5)
            .asReversed()
        newSongs
            .map { SongListItemViewModel.SongViewModel(context, songDetailRepository, playlistRepository, it) }
            .let {
                if (it.isNotEmpty()) {
                    add(HomeHeaderViewModel(context.getString(R.string.home_new_songs)))
                    addAll(it)
                }
            }
        displayedRandomCollections = randomCollections
            .filter { !newCollections.contains(it) }
            .take(3)
        displayedRandomCollections
            .map { CollectionListItemViewModel.CollectionViewModel(it, newText) }
            .let {
                if (it.isNotEmpty()) {
                    add(HomeHeaderViewModel(context.getString(R.string.home_random_collections)))
                    addAll(it)
                }
            }
        firstRandomSongIndex = size - 1
        displayedRandomSongs = randomSongs
            .filter { !newSongs.contains(it) }
            .take(10)
        displayedRandomSongs
            .map { SongListItemViewModel.SongViewModel(context, songDetailRepository, playlistRepository, it) }
            .let {
                if (it.isNotEmpty()) {
                    add(HomeHeaderViewModel(context.getString(R.string.home_random_songs)))
                    addAll(it)
                }
            }
    }

    private fun Sequence<Collection>.filterCollectionsByLanguage() = filter {
        var shouldFilter = false
        it.language?.forEach {
            if (!disabledLanguageFilters.contains(it)) {
                shouldFilter = true
            }
        }
        shouldFilter
    }

    private fun Sequence<Song>.filterSongsByLanguage() = filter { !disabledLanguageFilters.contains(it.language) }

    private fun Sequence<Collection>.filterExplicitCollections() = if (!shouldShowExplicit) filter { it.isExplicit != true } else this

    private fun Sequence<Song>.filterExplicitSongs() = if (!shouldShowExplicit) filter { it.isExplicit != true } else this
}