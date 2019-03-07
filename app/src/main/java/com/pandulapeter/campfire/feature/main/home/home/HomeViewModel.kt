package com.pandulapeter.campfire.feature.main.home.home

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
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
import com.pandulapeter.campfire.feature.main.shared.recycler.RecyclerAdapter
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.CollectionItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.HeaderItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.ItemViewModel
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.SongItemViewModel
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.feature.shared.widget.SearchControlsViewModel
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.UI
import com.pandulapeter.campfire.util.WORKER
import com.pandulapeter.campfire.util.mutableLiveDataOf
import com.pandulapeter.campfire.util.normalize
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.coroutines.CoroutineContext

class HomeViewModel(
    private val context: Context,
    private val analyticsManager: AnalyticsManager,
    private val preferenceDatabase: PreferenceDatabase,
    private val collectionRepository: CollectionRepository,
    private val songRepository: SongRepository,
    private val songDetailRepository: SongDetailRepository,
    private val playlistRepository: PlaylistRepository,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker), CollectionRepository.Subscriber, SongRepository.Subscriber, SongDetailRepository.Subscriber, PlaylistRepository.Subscriber {

    var isDetailScreenOpen = false
    val isSearchToggleVisible = mutableLiveDataOf(false)
    private val newText = context.getString(R.string.new_tag)
    private var coroutine: CoroutineContext? = null
    private var collections = sequenceOf<Collection>()
    private var songs = sequenceOf<Song>()
    var isTextInputVisible = false
    var randomCollections = listOf<Collection>()
    var randomSongs = listOf<Song>()
    var displayedRandomSongs = listOf<Song>()
    var firstRandomSongIndex = 0
    val searchControlsViewModel = SearchControlsViewModel(
        preferenceDatabase,
        SearchControlsViewModel.Type.HOME,
        interactionBlocker
    )
    val shouldOpenSecondaryNavigationDrawer = MutableLiveData<Boolean?>()
    val state = mutableLiveDataOf(StateLayout.State.LOADING)
    val isLoading = mutableLiveDataOf(true)
    val shouldShowUpdateErrorSnackbar = MutableLiveData<Boolean?>()
    val downloadSongError = MutableLiveData<Song?>()
    val buttonText = mutableLiveDataOf(R.string.try_again)
    val shouldScrollToTop = MutableLiveData<Boolean?>()
    val items = MutableLiveData<List<ItemViewModel>?>()
    val changeEvent = MutableLiveData<Pair<Int, RecyclerAdapter.Payload>?>()
    private var lastErrorTimestamp = 0L
    private var isFirstLoadingDone = false
    var shouldSearchInSongs = preferenceDatabase.isSearchInSongsEnabled
        set(value) {
            if (field != value) {
                field = value
                updateAdapterItems(shouldRefreshRandom = false, shouldScrollToTop = true)
                trackSearchEvent()
            }
        }
    var shouldSearchInCollections = preferenceDatabase.isSearchInCollectionsEnabled
        set(value) {
            if (field != value) {
                field = value
                updateAdapterItems(shouldRefreshRandom = false, shouldScrollToTop = true)
                trackSearchEvent()
            }
        }
    var shouldShowSongOfTheDay = preferenceDatabase.shouldShowSongOfTheDay
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowSongOfTheDay = value
                updateAdapterItems(shouldRefreshRandom = true, shouldScrollToTop = true)
            }
        }
    var shouldShowNewCollections = preferenceDatabase.shouldShowNewCollections
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowNewCollections = value
                updateAdapterItems(shouldRefreshRandom = true, shouldScrollToTop = true)
            }
        }
    var shouldShowNewSongs = preferenceDatabase.shouldShowNewSongs
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowNewSongs = value
                updateAdapterItems(shouldRefreshRandom = true, shouldScrollToTop = true)
            }
        }
    var shouldShowRandomCollections = preferenceDatabase.shouldShowRandomCollections
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowRandomCollections = value
                updateAdapterItems(shouldRefreshRandom = true, shouldScrollToTop = true)
            }
        }
    var shouldShowRandomSongs = preferenceDatabase.shouldShowRandomSongs
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowRandomSongs = value
                updateAdapterItems(shouldRefreshRandom = true, shouldScrollToTop = true)
            }
        }
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
                updateAdapterItems(shouldRefreshRandom = true, shouldScrollToTop = true)
            }
        }
    var languages = MutableLiveData<List<Language>?>()
    val isSwipeRefreshEnabled = mutableLiveDataOf(true)
    val shouldShowEraseButton = mutableLiveDataOf(false) { isSwipeRefreshEnabled.value = !it }
    val shouldEnableEraseButton = mutableLiveDataOf(false)
    var query = ""
        set(value) {
            if (field != value) {
                field = value
                updateAdapterItems(shouldRefreshRandom = false, shouldScrollToTop = true)
                trackSearchEvent()
                shouldEnableEraseButton.value = query.isNotEmpty()
            }
        }

    override fun subscribe() {
        collectionRepository.subscribe(this)
        songRepository.subscribe(this)
        songDetailRepository.subscribe(this)
        playlistRepository.subscribe(this)
        isDetailScreenOpen = false
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
        this.isLoading.value = isLoading
        if (collections.toList().isEmpty() && isLoading) {
            state.value = StateLayout.State.LOADING
        }
    }

    override fun onCollectionRepositoryUpdateError() = onError()

    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        songs = data.asSequence()
        updateAdapterItems(false)
    }

    override fun onSongRepositoryLoadingStateChanged(isLoading: Boolean) {
        this.isLoading.value = isLoading
        if (songs.toList().isEmpty() && isLoading) {
            state.value = StateLayout.State.LOADING
        }
    }

    override fun onSongRepositoryUpdateError() = onError()

    override fun onSongDetailRepositoryUpdated(downloadedSongs: List<SongDetailMetadata>) {
        if (songs.toList().isNotEmpty()) {
            updateAdapterItems(false)
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
            updateAdapterItems(false)
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

    override fun onCleared() {
        super.onCleared()
        coroutine?.cancel()
    }

    private fun onError() {
        if (System.currentTimeMillis() - lastErrorTimestamp > 200) {
            if (collections.toList().isEmpty() || songs.toList().isEmpty()) {
                analyticsManager.onConnectionError(true, AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
                state.value = StateLayout.State.ERROR
            } else {
                analyticsManager.onConnectionError(false, AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
                shouldShowUpdateErrorSnackbar.value = true
            }
        }
        lastErrorTimestamp = System.currentTimeMillis()
    }

    private fun onListUpdated(items: List<ItemViewModel>) {
        state.value = if (items.isEmpty()) StateLayout.State.ERROR else StateLayout.State.NORMAL
        if (collections.toList().isNotEmpty()) {
            buttonText.value = if (isTextInputVisible) 0 else R.string.filters
        }
    }

    fun onActionButtonClicked() {
        shouldOpenSecondaryNavigationDrawer.value = true
    }

    fun updateData() {
        randomCollections = listOf()
        randomSongs = listOf()
        collectionRepository.updateData()
        songRepository.updateData()
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
        changeEvent.value = position to RecyclerAdapter.Payload.BookmarkedStateChanged(collection.isBookmarked ?: false)
        updateAdapterItems(false)
    }

    fun downloadSong(song: Song) = songDetailRepository.getSongDetail(song, true)

    fun updateAdapterItems(shouldRefreshRandom: Boolean, shouldScrollToTop: Boolean = false) {
        if (collectionRepository.isCacheLoaded() && songRepository.isCacheLoaded() && collections.toList().isNotEmpty() && songs.toList().isNotEmpty()) {
            coroutine?.cancel()
            coroutine = launch(UI) {
                withContext(WORKER) {
                    if (shouldRefreshRandom) {
                        randomSongs = listOf()
                        randomCollections = listOf()
                    }
                    if (randomCollections.isEmpty()) {
                        randomCollections = collections
                            .filterExplicitCollections()
                            .filterCollectionsByLanguage()
                            .toList()
                            .shuffled()
                    }
                    if (randomSongs.isEmpty()) {
                        randomSongs = songs
                            .filterExplicitSongs()
                            .filterSongsByLanguage()
                            .toList()
                            .shuffled()
                    }
                    createViewModels()
                }.let {
                    this@HomeViewModel.shouldScrollToTop.value = shouldScrollToTop
                    items.value = it
                    onListUpdated(it)
                    coroutine = null
                    if (!isFirstLoadingDone) {
                        languages.value = collectionRepository.languages.union(songRepository.languages).toList()
                        isFirstLoadingDone = true
                    }
                }
            }
        }
    }

    private fun createViewModels() = mutableListOf<ItemViewModel>().apply {
        if (isTextInputVisible && query.isNotEmpty()) {
            // Search in songs.
            val matchingSongs = songs
                .filterSongsByQuery()
                .filterExplicitSongs()
                .filterSongsByLanguage()
                .take(SEARCH_SONG_LIMIT)
                .map { SongItemViewModel(context, songDetailRepository, playlistRepository, it) }
                .toList()

            // Search in collections.
            val matchingCollections = collections
                .filterCollectionsByQuery()
                .filterExplicitCollections()
                .filterCollectionsByLanguage()
                .take(SEARCH_COLLECTION_LIMIT)
                .map { CollectionItemViewModel(it, newText) }
                .toList()

            // Add results.
            if (matchingSongs.isNotEmpty()) {
                if (shouldSearchInCollections) {
                    add(HeaderItemViewModel(R.string.main_songs))
                }
                addAll(matchingSongs)
            }
            if (matchingCollections.isNotEmpty()) {
                if (shouldSearchInSongs) {
                    add(HeaderItemViewModel(R.string.main_collections))
                }
                addAll(matchingCollections)
            }
        } else {

            // Add the Song of the day module.
            val songOfTheDay = if (shouldShowSongOfTheDay) songs
                .filterExplicitSongs()
                .filterSongsByLanguage()
                .toList()
                .let {
                    if (it.isNotEmpty()) {
                        var today = Calendar.getInstance().run { get(Calendar.DAY_OF_YEAR) + get(Calendar.YEAR) }
                        while (today >= it.size) {
                            today -= it.size
                        }
                        return@let it[today]
                    }
                    return@let null
                }
            else null
            songOfTheDay?.also {
                add(HeaderItemViewModel(R.string.home_song_of_the_day))
                add(SongItemViewModel(context, songDetailRepository, playlistRepository, it))
            }

            // Add the New Collections module.
            val newCollections = if (shouldShowNewCollections) collections
                .filterExplicitCollections()
                .filterCollectionsByLanguage()
                .toList()
                .takeLast(NEW_COLLECTION_COUNT)
                .asReversed() else listOf()
            newCollections
                .map { CollectionItemViewModel(it, newText) }
                .let {
                    if (it.isNotEmpty()) {
                        add(HeaderItemViewModel(R.string.home_new_collections))
                        addAll(it)
                    }
                }

            // Add the New Songs module.
            val newSongs = if (shouldShowNewSongs) songs
                .filterExplicitSongs()
                .filterSongsByLanguage()
                .toList()
                .filterNot { it.id == songOfTheDay?.id }
                .takeLast(NEW_SONG_COUNT)
                .asReversed() else listOf()
            newSongs
                .map { SongItemViewModel(context, songDetailRepository, playlistRepository, it) }
                .let {
                    if (it.isNotEmpty()) {
                        add(HeaderItemViewModel(R.string.home_new_songs))
                        addAll(it)
                    }
                }

            // Add the Random Collections module.
            if (shouldShowRandomCollections) {
                var totalRandomCollectionCount: Int
                randomCollections
                    .filterNot { newCollections.contains(it) }
                    .apply { totalRandomCollectionCount = size }
                    .take(RANDOM_COLLECTION_COUNT)
                    .map { CollectionItemViewModel(it, newText) }
                    .let {
                        if (it.isNotEmpty()) {
                            add(
                                HeaderItemViewModel(
                                    R.string.home_random_collections,
                                    if (totalRandomCollectionCount > RANDOM_COLLECTION_COUNT) ::refreshRandomCollections else null
                                )
                            )
                            addAll(it)
                        }
                    }
            }

            // Add the Random Songs module.
            firstRandomSongIndex = if (shouldShowRandomSongs) size - 1 else Int.MAX_VALUE
            if (shouldShowRandomSongs) {
                var totalRandomSongCount: Int
                displayedRandomSongs = randomSongs
                    .filterNot { it.id == songOfTheDay?.id }
                    .filterNot { newSongs.contains(it) }
                    .apply { totalRandomSongCount = size }
                    .take(RANDOM_SONG_COUNT)
                displayedRandomSongs
                    .map { SongItemViewModel(context, songDetailRepository, playlistRepository, it) }
                    .let {
                        if (it.isNotEmpty()) {
                            add(HeaderItemViewModel(R.string.home_random_songs, if (totalRandomSongCount > RANDOM_SONG_COUNT) ::refreshRandomSongs else null))
                            addAll(it)
                        }
                    }
            }
        }
    }

    private fun trackSearchEvent() {
        if (query.isNotEmpty()) {
            analyticsManager.onHomeSearchQueryChanged(query, shouldSearchInSongs, shouldSearchInCollections)
        }
    }

    private fun refreshRandomCollections() {
        randomCollections = listOf()
        updateAdapterItems(shouldRefreshRandom = false, shouldScrollToTop = false)
    }


    private fun refreshRandomSongs() {
        randomSongs = listOf()
        updateAdapterItems(shouldRefreshRandom = false, shouldScrollToTop = false)
    }

    private fun Sequence<Song>.filterSongsByQuery() = if (shouldSearchInSongs) {
        query.trim().normalize().let { query ->
            filter {
                it.getNormalizedTitle().contains(query, true) || it.getNormalizedArtist().contains(query, true)
            }
                .sortedByDescending { it.getNormalizedTitle().contains(query, true) }
                .sortedByDescending { it.getNormalizedArtist().startsWith(query, true) }
                .sortedByDescending { it.getNormalizedTitle().startsWith(query, true) }
        }
    } else sequenceOf()

    private fun Sequence<Collection>.filterCollectionsByQuery() = if (shouldSearchInCollections) {
        query.trim().normalize().let { query ->
            filter {
                it.getNormalizedTitle().contains(query, true) || it.getNormalizedDescription().contains(query, true)
            }
                .sortedByDescending { it.getNormalizedTitle().contains(query, true) }
                .sortedByDescending { it.getNormalizedDescription().startsWith(query, true) }
                .sortedByDescending { it.getNormalizedTitle().startsWith(query, true) }
        }
    } else sequenceOf()

    private fun Sequence<Collection>.filterCollectionsByLanguage() = filter { collection ->
        var shouldFilter = false
        collection.language?.forEach { language ->
            if (!disabledLanguageFilters.contains(language)) {
                shouldFilter = true
            }
        }
        shouldFilter
    }

    private fun Sequence<Song>.filterSongsByLanguage() = filter { !disabledLanguageFilters.contains(it.language) }

    private fun Sequence<Collection>.filterExplicitCollections() = if (!shouldShowExplicit) filter { it.isExplicit != true } else this

    private fun Sequence<Song>.filterExplicitSongs() = if (!shouldShowExplicit) filter { it.isExplicit != true } else this

    companion object {
        const val NEW_COLLECTION_COUNT = 3
        const val NEW_SONG_COUNT = 5
        const val RANDOM_COLLECTION_COUNT = 3
        const val RANDOM_SONG_COUNT = 10
        const val SEARCH_SONG_LIMIT = 6
        const val SEARCH_COLLECTION_LIMIT = 6
    }
}