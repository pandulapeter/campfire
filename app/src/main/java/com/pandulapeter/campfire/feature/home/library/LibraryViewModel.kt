package com.pandulapeter.campfire.feature.home.library

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.*
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.home.shared.songInfoList.SongInfoListAdapter
import com.pandulapeter.campfire.feature.home.shared.songInfoList.SongInfoListViewModel
import com.pandulapeter.campfire.feature.home.shared.songInfoList.SongInfoViewModel
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.networking.AnalyticsManager
import com.pandulapeter.campfire.util.mapToLanguage
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.replaceSpecialCharacters
import com.pandulapeter.campfire.util.toggle

/**
 * Handles events and logic for [LibraryFragment].
 */
class LibraryViewModel(
    context: Context?,
    analyticsManager: AnalyticsManager,
    songInfoRepository: SongInfoRepository,
    downloadedSongRepository: DownloadedSongRepository,
    appShortcutManager: AppShortcutManager,
    private val userPreferenceRepository: UserPreferenceRepository,
    playlistRepository: PlaylistRepository,
    private val languageRepository: LanguageRepository
) : SongInfoListViewModel(context, analyticsManager, songInfoRepository, downloadedSongRepository, playlistRepository) {
    val isSearchInputVisible = ObservableBoolean(userPreferenceRepository.searchQuery.isNotEmpty())
    val searchQuery = ObservableField(userPreferenceRepository.searchQuery)
    val shouldShowViewOptions = ObservableBoolean(false)
    val isLoading = ObservableBoolean(songInfoRepository.isLoading)
    val shouldShowErrorSnackbar = ObservableBoolean(false)
    val shouldShowDownloadedOnly = ObservableBoolean(userPreferenceRepository.shouldShowDownloadedOnly)
    val shouldShowExplicit = ObservableBoolean(userPreferenceRepository.shouldShowExplicit)
    val shouldShowWorkInProgress = ObservableBoolean(userPreferenceRepository.shouldShowWorkInProgress)
    val isSortedByTitle = ObservableBoolean(userPreferenceRepository.isSortedByTitle)
    val languageFilters = ObservableField(HashMap<Language, ObservableBoolean>())
    val shouldAllowToolbarScrolling = ObservableBoolean()
    val shouldShowPlaceholderButton = ObservableBoolean(true)
    val filteredItemCount = ObservableField("")
    val isLibraryNotEmpty = ObservableBoolean(songInfoRepository.getLibrarySongs().isNotEmpty())
    val placeholderText = ObservableInt(R.string.library_placeholder_loading)
    val placeholderButtonText = ObservableInt(R.string.try_again)
    private var itemCountWithoutSearchFilter = 0

    init {
        isSearchInputVisible.onPropertyChanged {
            if (it) searchQuery.set("") else userPreferenceRepository.searchQuery = ""
            updateShouldAllowToolbarScrolling(adapter.items.isNotEmpty())
        }
        searchQuery.onPropertyChanged {
            updatePlaceholderState()
            userPreferenceRepository.searchQuery = it
        }
        shouldShowDownloadedOnly.onPropertyChanged { userPreferenceRepository.shouldShowDownloadedOnly = it }
        shouldShowExplicit.onPropertyChanged { userPreferenceRepository.shouldShowExplicit = it }
        shouldShowWorkInProgress.onPropertyChanged { userPreferenceRepository.shouldShowWorkInProgress = it }
        isSortedByTitle.onPropertyChanged { userPreferenceRepository.isSortedByTitle = it }
        isLoading.onPropertyChanged { if (it || songInfoRepository.getLibrarySongs().isEmpty()) updatePlaceholderState() }
        isSearchInputVisible.onPropertyChanged { updatePlaceholderState() }
        appShortcutManager.onLibraryOpened()
        updatePlaceholderState()
    }

    override fun getAdapterItems(): List<SongInfoViewModel> {
        val librarySongs = songInfoRepository.getLibrarySongs()
        val preFilteredItems = librarySongs
            .asSequence()
            .filterWorkInProgress()
            .filterExplicit()
            .filterByLanguages()
            .filterDownloaded()
            .toList()
        val filteredItems = preFilteredItems
            .asSequence()
            .filterByQuery()
            .sort()
            .toList()
        itemCountWithoutSearchFilter = preFilteredItems.size
        isLibraryNotEmpty.set(librarySongs.isNotEmpty())
        filteredItemCount.set(if (filteredItems.size == librarySongs.size) "${filteredItems.size}" else "${filteredItems.size} / ${librarySongs.size}")
        return filteredItems.map { songInfo ->
            val isDownloaded = downloadedSongRepository.isSongDownloaded(songInfo.id)
            val isSongNew = false //TODO: Check if the song is new.
            SongInfoViewModel(
                songInfo = songInfo,
                isSongDownloaded = isDownloaded,
                isSongLoading = downloadedSongRepository.isSongLoading(songInfo.id),
                isSongOnAnyPlaylist = playlistRepository.isSongInAnyPlaylist(songInfo.id),
                shouldShowDragHandle = false,
                shouldShowPlaylistButton = true,
                shouldShowDownloadButton = !isDownloaded || isSongNew,
                alertText = if (isDownloaded) {
                    if (downloadedSongRepository.getDownloadedSong(songInfo.id)?.version ?: 0 != songInfo.version ?: 0) updateString else null
                } else {
                    if (isSongNew) newString else null
                }
            )
        }
    }

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            UpdateType.LanguageFilterChanged,
            is UpdateType.DownloadedSongsUpdated,
            is UpdateType.LibraryCacheUpdated,
            is UpdateType.PlaylistsUpdated,
            UpdateType.HistoryUpdated,
            UpdateType.IsSortedByTitleUpdated,
            UpdateType.ShouldShowDownloadedOnlyUpdated,
            UpdateType.ShouldHideExplicitUpdated,
            UpdateType.ShouldHideWorkInProgressUpdated,
            UpdateType.SearchQueryUpdated,
            UpdateType.AllDownloadsRemoved -> super.onUpdate(updateType)
            is UpdateType.LoadingStateChanged -> isLoading.set(updateType.isLoading)
            is UpdateType.SongAddedToDownloads -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let {
                if (it != -1) adapter.notifyItemChanged(
                    it,
                    SongInfoListAdapter.Payload.SONG_DOWNLOADED
                )
            }
            is UpdateType.SongRemovedFromDownloads -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let {
                if (it != -1) adapter.notifyItemChanged(
                    it,
                    SongInfoListAdapter.Payload.SONG_DOWNLOAD_DELETED
                )
            }
            is UpdateType.DownloadStarted -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let {
                if (it != -1) adapter.notifyItemChanged(
                    it,
                    SongInfoListAdapter.Payload.DOWNLOAD_STARTED
                )
            }
            is UpdateType.DownloadSuccessful -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let {
                if (it != -1) adapter.notifyItemChanged(
                    it,
                    SongInfoListAdapter.Payload.DOWNLOAD_SUCCESSFUL
                )
            }
            is UpdateType.DownloadFailed -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let {
                if (it != -1) adapter.notifyItemChanged(
                    it,
                    SongInfoListAdapter.Payload.DOWNLOAD_FAILED
                )
            }
            is UpdateType.SongAddedToPlaylist -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let {
                if (it != -1 && !adapter.items[it].isSongOnAnyPlaylist) adapter.notifyItemChanged(
                    it,
                    SongInfoListAdapter.Payload.SONG_IS_IN_A_PLAYLIST
                )
            }
            is UpdateType.SongRemovedFromPlaylist -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let {
                if (it != -1 && !playlistRepository.isSongInAnyPlaylist(
                        updateType.songId
                    )) adapter.notifyItemChanged(it, SongInfoListAdapter.Payload.SONG_IS_NOT_IN_A_PLAYLISTS)
            }
            is UpdateType.LanguagesUpdated -> {
                languageFilters.get().clear()
                updateType.languageFilters.forEach { (language, isEnabled) ->
                    languageFilters.get()[language] = ObservableBoolean(isEnabled).apply {
                        onPropertyChanged { languageRepository.setLanguageFilterEnabled(language, it) }
                    }
                }
                languageFilters.notifyChange()
            }
        }
    }

    override fun onUpdateDone(items: List<SongInfoViewModel>, updateType: UpdateType) {
        super.onUpdateDone(items, updateType)
        updateShouldAllowToolbarScrolling(items.isNotEmpty())
        updatePlaceholderState()
    }

    fun forceRefresh() {
        if (!isLoading.get()) {
            updatePlaceholderState()
            songInfoRepository.updateDataSet { if (adapter.itemCount > 0) shouldShowErrorSnackbar.set(true) }
        }
    }

    fun onPlaceholderButtonClicked() = when (placeholderButtonText.get()) {
        R.string.try_again -> forceRefresh()
        R.string.library_view_options -> showViewOptions()
        else -> Unit
    }

    fun showOrHideSearchInput() = isSearchInputVisible.toggle()

    fun showViewOptions() = shouldShowViewOptions.set(true)

    fun isHeader(position: Int) = position == 0 ||
            if (isSortedByTitle.get()) {
                adapter.items[position].songInfo.titleWithSpecialCharactersRemoved[0] != adapter.items[position - 1].songInfo.titleWithSpecialCharactersRemoved[0]
            } else {
                adapter.items[position].songInfo.artistWithSpecialCharactersRemoved[0] != adapter.items[position - 1].songInfo.artistWithSpecialCharactersRemoved[0]
            }

    fun getHeaderTitle(position: Int) =
        (if (isSortedByTitle.get()) adapter.items[position].songInfo.titleWithSpecialCharactersRemoved[0] else adapter.items[position].songInfo.artistWithSpecialCharactersRemoved[0]).toString().toUpperCase()

    private fun updatePlaceholderState() {
        if (shouldShowPlaceholder.get()) {
            val isLibraryInitialized = songInfoRepository.getLibrarySongs().isNotEmpty()
            placeholderText.set(
                when {
                    isLoading.get() && !isLibraryInitialized -> R.string.library_placeholder_loading
                    !isLibraryInitialized -> R.string.library_placeholder_loading_failed
                    itemCountWithoutSearchFilter > 0 -> R.string.library_placeholder_search
                    else -> R.string.library_placeholder_filters
                }
            )
            shouldShowPlaceholderButton.set((!isLoading.get() && !isLibraryInitialized) || placeholderText.get() == R.string.library_placeholder_filters)
            placeholderButtonText.set(if (placeholderText.get() == R.string.library_placeholder_filters) R.string.library_view_options else R.string.try_again)
        }
    }

    private fun updateShouldAllowToolbarScrolling(isAdapterNotEmpty: Boolean) =
        shouldAllowToolbarScrolling.set(if (isSearchInputVisible.get()) false else isAdapterNotEmpty)

    private fun Sequence<SongInfo>.filterByLanguages() = filter { languageRepository.isLanguageFilterEnabled(it.language.mapToLanguage()) }

    private fun Sequence<SongInfo>.filterDownloaded() =
        if (shouldShowDownloadedOnly.get()) filter { downloadedSongRepository.isSongDownloaded(it.id) } else this

    //TODO: Prioritize results that begin with the searchQuery.
    private fun Sequence<SongInfo>.filterByQuery() = if (isSearchInputVisible.get()) {
        searchQuery.get().trim().replaceSpecialCharacters().let { query ->
            filter { it.titleWithSpecialCharactersRemoved.contains(query, true) || it.artistWithSpecialCharactersRemoved.contains(query, true) }
        }
    } else this

    private fun Sequence<SongInfo>.sort() =
        sortedBy { if (isSortedByTitle.get()) it.titleWithSpecialCharactersRemoved else it.artistWithSpecialCharactersRemoved }

    private fun Sequence<SongInfo>.filterWorkInProgress() = if (!shouldShowWorkInProgress.get()) filter { it.version ?: 0 >= 0 } else this

    private fun Sequence<SongInfo>.filterExplicit() = if (!shouldShowExplicit.get()) filter { it.isExplicit != true } else this
}