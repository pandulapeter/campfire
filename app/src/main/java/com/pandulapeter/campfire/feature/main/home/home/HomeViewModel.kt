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
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.normalize
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.onTextChanged
import com.pandulapeter.campfire.util.swap
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.cancel
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.koin.android.ext.android.inject
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext

class HomeViewModel(
    private val onDataLoaded: (languages: List<Language>) -> Unit,
    private val openSecondaryNavigationDrawer: () -> Unit,
    val toolbarTextInputView: ToolbarTextInputView,
    private val updateSearchToggleDrawable: (Boolean) -> Unit,
    private val context: Context
) : CampfireViewModel(), CollectionRepository.Subscriber, SongRepository.Subscriber, SongDetailRepository.Subscriber, PlaylistRepository.Subscriber {

    companion object {
        const val NEW_COLLECTION_COUNT = 3
        const val NEW_SONG_COUNT = 5
        const val RANDOM_COLLECTION_COUNT = 3
        const val RANDOM_SONG_COUNT = 10
        const val SEARCH_SONG_LIMIT = 6
        const val SEARCH_COLLECTION_LIMIT = 6
    }

    private val analyticsManager by inject<AnalyticsManager>()
    private val preferenceDatabase by inject<PreferenceDatabase>()
    val collectionRepository by inject<CollectionRepository>()
    private val songRepository by inject<SongRepository>()
    private val songDetailRepository by inject<SongDetailRepository>()
    private val playlistRepository by inject<PlaylistRepository>()
    var isDetailScreenOpen = false
    private val newText = context.getString(R.string.new_tag)
    private var coroutine: CoroutineContext? = null
    private var collections = sequenceOf<Collection>()
    private var songs = sequenceOf<Song>()
    var randomCollections = listOf<Collection>()
    var randomSongs = listOf<Song>()
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
    private var lastErrorTimestamp = 0L
    private var isFirstLoadingDone = false
    var shouldShowSongOfTheDay = preferenceDatabase.shouldShowSongOfTheDay
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowSongOfTheDay = value
                updateAdapterItems(true, true)
            }
        }
    var shouldShowNewCollections = preferenceDatabase.shouldShowNewCollections
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowNewCollections = value
                updateAdapterItems(true, true)
            }
        }
    var shouldShowNewSongs = preferenceDatabase.shouldShowNewSongs
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowNewSongs = value
                updateAdapterItems(true, true)
            }
        }
    var shouldShowRandomCollections = preferenceDatabase.shouldShowRandomCollections
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowRandomCollections = value
                updateAdapterItems(true, true)
            }
        }
    var shouldShowRandomSongs = preferenceDatabase.shouldShowRandomSongs
        set(value) {
            if (field != value) {
                field = value
                preferenceDatabase.shouldShowRandomSongs = value
                updateAdapterItems(true, true)
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
                updateAdapterItems(true, true)
            }
        }
    var languages = mutableListOf<Language>()
    val isSwipeRefreshEnabled = ObservableBoolean(true)
    val shouldShowEraseButton = ObservableBoolean().apply {
        onPropertyChanged {
            isSwipeRefreshEnabled.set(!it)
        }
    }
    val shouldEnableEraseButton = ObservableBoolean()
    var query = ""
        set(value) {
            if (field != value) {
                field = value
                updateAdapterItems(false, true)
                if (value.isNotEmpty()) {
                    analyticsManager.onHomeSearchQueryChanged(query)
                }
                shouldEnableEraseButton.set(query.isNotEmpty())
            }
        }

    init {
        toolbarTextInputView.apply {
            textInput.onTextChanged { if (isTextInputVisible) query = it }
        }
    }

    fun toggleTextInputVisibility() {
        toolbarTextInputView.run {
            if (title.tag == null) {
                val shouldScrollToTop = !query.isEmpty()
                animateTextInputVisibility(!isTextInputVisible)
                if (isTextInputVisible) {
                    textInput.setText("")
                }
                updateSearchToggleDrawable(toolbarTextInputView.isTextInputVisible)
                if (shouldScrollToTop) {
                    updateAdapterItems(false, !isTextInputVisible)
                }
                buttonText.set(if (toolbarTextInputView.isTextInputVisible) 0 else R.string.filters)
            }
            shouldShowEraseButton.set(isTextInputVisible)
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
        if (System.currentTimeMillis() - lastErrorTimestamp > 200) {
            if (collections.toList().isEmpty() || songs.toList().isEmpty()) {
                analyticsManager.onConnectionError(true, AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
                state.set(StateLayout.State.ERROR)
            } else {
                analyticsManager.onConnectionError(false, AnalyticsManager.PARAM_VALUE_SCREEN_HOME)
                shouldShowUpdateErrorSnackbar.set(true)
            }
        }
        lastErrorTimestamp = System.currentTimeMillis()
    }

    private fun onListUpdated(items: List<HomeItemViewModel>) {
        state.set(if (items.isEmpty()) StateLayout.State.ERROR else StateLayout.State.NORMAL)
        if (collections.toList().isNotEmpty()) {
            placeholderText.set(R.string.home_placeholder)
            buttonText.set(if (toolbarTextInputView.isTextInputVisible) 0 else R.string.filters)
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

    private fun updateAdapterItems(shouldRefreshRandom: Boolean, shouldScrollToTop: Boolean = false) {
        if (collectionRepository.isCacheLoaded() && songRepository.isCacheLoaded() && collections.toList().isNotEmpty() && songs.toList().isNotEmpty()) {
            coroutine?.cancel()
            coroutine = launch(UI) {
                withContext(CommonPool) {
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
                    adapter.shouldScrollToTop = shouldScrollToTop
                    adapter.items = it
                    onListUpdated(it)
                    coroutine = null
                    if (!isFirstLoadingDone) {
                        languages.swap(collectionRepository.languages.union(songRepository.languages).toList())
                        onDataLoaded(languages)
                        isFirstLoadingDone = true
                    }
                }
            }
        }
    }

    private fun createViewModels() = mutableListOf<HomeItemViewModel>().apply {
        if (toolbarTextInputView.isTextInputVisible && query.isNotEmpty()) {
            // Search in songs.
            val matchingSongs = songs
                .filterSongsByQuery()
                .filterExplicitSongs()
                .filterSongsByLanguage()
                .take(SEARCH_SONG_LIMIT)
                .map { SongListItemViewModel.SongViewModel(context, songDetailRepository, playlistRepository, it) }
                .toList()

            // Search in collections.
            val matchingCollections = collections
                .filterCollectionsByQuery()
                .filterExplicitCollections()
                .filterCollectionsByLanguage()
                .take(SEARCH_COLLECTION_LIMIT)
                .map { CollectionListItemViewModel.CollectionViewModel(it, newText) }
                .toList()

            // Add results.
            if (matchingSongs.isNotEmpty()) {
                add(HomeHeaderViewModel(context.getString(R.string.main_songs)))
                addAll(matchingSongs)
            }
            if (matchingCollections.isNotEmpty()) {
                add(HomeHeaderViewModel(context.getString(R.string.main_collections)))
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
                add(HomeHeaderViewModel(context.getString(R.string.home_song_of_the_day)))
                add(SongListItemViewModel.SongViewModel(context, songDetailRepository, playlistRepository, it))
            }

            // Add the New Collections module.
            val newCollections = if (shouldShowNewCollections) collections
                .filterExplicitCollections()
                .filterCollectionsByLanguage()
                .toList()
                .takeLast(NEW_COLLECTION_COUNT)
                .asReversed() else listOf()
            newCollections
                .map { CollectionListItemViewModel.CollectionViewModel(it, newText) }
                .let {
                    if (it.isNotEmpty()) {
                        add(HomeHeaderViewModel(context.getString(R.string.home_new_collections)))
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
                .map { SongListItemViewModel.SongViewModel(context, songDetailRepository, playlistRepository, it) }
                .let {
                    if (it.isNotEmpty()) {
                        add(HomeHeaderViewModel(context.getString(R.string.home_new_songs)))
                        addAll(it)
                    }
                }

            // Add the Random Collections module.
            if (shouldShowRandomCollections) {
                var totalRandomCollectionCount = 0
                randomCollections
                    .filterNot { newCollections.contains(it) }
                    .apply { totalRandomCollectionCount = size }
                    .take(RANDOM_COLLECTION_COUNT)
                    .map { CollectionListItemViewModel.CollectionViewModel(it, newText) }
                    .let {
                        if (it.isNotEmpty()) {
                            add(
                                HomeHeaderViewModel(
                                    context.getString(R.string.home_random_collections),
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
                var totalRandomSongCount = 0
                displayedRandomSongs = randomSongs
                    .filterNot { it.id == songOfTheDay?.id }
                    .filterNot { newSongs.contains(it) }
                    .apply { totalRandomSongCount = size }
                    .take(RANDOM_SONG_COUNT)
                displayedRandomSongs
                    .map { SongListItemViewModel.SongViewModel(context, songDetailRepository, playlistRepository, it) }
                    .let {
                        if (it.isNotEmpty()) {
                            add(HomeHeaderViewModel(context.getString(R.string.home_random_songs), if (totalRandomSongCount > RANDOM_SONG_COUNT) ::refreshRandomSongs else null))
                            addAll(it)
                        }
                    }
            }
        }
    }

    private fun refreshRandomCollections() {
        randomCollections = listOf()
        updateAdapterItems(false, false)
    }


    private fun refreshRandomSongs() {
        randomSongs = listOf()
        updateAdapterItems(false, false)
    }

    private fun Sequence<Song>.filterSongsByQuery() =
        query.trim().normalize().let { query ->
            filter {
                it.getNormalizedTitle().contains(query, true) || it.getNormalizedArtist().contains(query, true)
            }
                .sortedByDescending { it.getNormalizedTitle().contains(query, true) }
                .sortedByDescending { it.getNormalizedArtist().startsWith(query, true) }
                .sortedByDescending { it.getNormalizedTitle().startsWith(query, true) }
        }

    private fun Sequence<Collection>.filterCollectionsByQuery() =
        query.trim().normalize().let { query ->
            filter {
                it.getNormalizedTitle().contains(query, true) || it.getNormalizedDescription().contains(query, true)
            }
                .sortedByDescending { it.getNormalizedTitle().contains(query, true) }
                .sortedByDescending { it.getNormalizedDescription().startsWith(query, true) }
                .sortedByDescending { it.getNormalizedTitle().startsWith(query, true) }
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