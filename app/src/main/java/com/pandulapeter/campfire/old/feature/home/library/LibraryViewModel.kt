package com.pandulapeter.campfire.old.feature.home.library

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.old.data.model.SongInfo
import com.pandulapeter.campfire.old.data.repository.*
import com.pandulapeter.campfire.old.data.repository.shared.UpdateType
import com.pandulapeter.campfire.old.feature.home.shared.songInfoList.SongInfoListAdapter
import com.pandulapeter.campfire.old.feature.home.shared.songInfoList.SongInfoListViewModel
import com.pandulapeter.campfire.old.feature.home.shared.songInfoList.SongInfoViewModel
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.networking.AnalyticsManager
import com.pandulapeter.campfire.old.util.mapToLanguage
import com.pandulapeter.campfire.old.util.onPropertyChanged
import com.pandulapeter.campfire.old.util.toggle
import com.pandulapeter.campfire.util.normalize

/**
 * Handles events and logic for [LibraryFragment].
 */
class LibraryViewModel(
    context: Context?,
    analyticsManager: AnalyticsManager,
    songInfoRepository: SongInfoRepository,
    downloadedSongRepository: DownloadedSongRepository,
    appShortcutManager: AppShortcutManager,
    userPreferenceRepository: UserPreferenceRepository,
    playlistRepository: PlaylistRepository,
    private val languageRepository: LanguageRepository
) : SongInfoListViewModel(context, analyticsManager, songInfoRepository, downloadedSongRepository, playlistRepository, userPreferenceRepository) {
    val isSearchInputVisible = ObservableBoolean(userPreferenceRepository.searchQuery.isNotEmpty())
    val searchQuery = ObservableField(userPreferenceRepository.searchQuery)
    val shouldShowViewOptions = ObservableBoolean(false)
    val isLoading = ObservableBoolean(songInfoRepository.isLoading)
    val shouldShowErrorSnackbar = ObservableBoolean(false)
    val shouldShowDownloadedOnly = ObservableBoolean(userPreferenceRepository.shouldShowDownloadedOnly)
    val shouldShowExplicit = ObservableBoolean(userPreferenceRepository.shouldShowExplicit)
    val shouldShowWorkInProgress = ObservableBoolean(userPreferenceRepository.shouldShowWorkInProgress)
    val sortingMode = ObservableField<SortingMode>(SortingMode.fromIntValue(userPreferenceRepository.isSortedByTitle))
    val languageFilters = ObservableField(HashMap<Language, ObservableBoolean>())
    val shouldShowPlaceholderButton = ObservableBoolean(true)
    val isLibraryNotEmpty = ObservableBoolean(songInfoRepository.getLibrarySongs().isNotEmpty())
    val placeholderText = ObservableInt(R.string.library_placeholder_loading)
    val placeholderButtonText = ObservableInt(R.string.try_again)
    private var itemCountWithoutSearchFilter = 0

    init {
        isSearchInputVisible.onPropertyChanged {
            if (it) searchQuery.set("") else userPreferenceRepository.searchQuery = ""
        }
        searchQuery.onPropertyChanged {
            updatePlaceholderState()
            userPreferenceRepository.searchQuery = it
        }
        shouldShowDownloadedOnly.onPropertyChanged { userPreferenceRepository.shouldShowDownloadedOnly = it }
        shouldShowExplicit.onPropertyChanged { userPreferenceRepository.shouldShowExplicit = it }
        shouldShowWorkInProgress.onPropertyChanged { userPreferenceRepository.shouldShowWorkInProgress = it }
        sortingMode.onPropertyChanged { userPreferenceRepository.isSortedByTitle = it.intValue }
        isLoading.onPropertyChanged { if (it || songInfoRepository.getLibrarySongs().isEmpty()) updatePlaceholderState() }
        isSearchInputVisible.onPropertyChanged { updatePlaceholderState() }
        appShortcutManager.onLibraryOpened()
        updatePlaceholderState()
    }

    override fun getAdapterItems(): List<SongInfoViewModel> {
        val librarySongs = songInfoRepository.getLibrarySongs()
        isLibraryNotEmpty.set(librarySongs.isNotEmpty())
        val preFilteredItems = librarySongs
            .asSequence()
            .filterWorkInProgress()
            .filterExplicit()
            .filterByLanguages()
            .filterDownloaded()
        itemCountWithoutSearchFilter = preFilteredItems.toList().size
        return preFilteredItems
            .filterByQuery()
            .sort()
            .map { songInfo ->
                SongInfoViewModel(
                    songInfo = songInfo,
                    downloadState = downloadedSongRepository.getSongDownloadedState(songInfo.id),
                    isSongOnAnyPlaylist = playlistRepository.isSongInAnyPlaylist(songInfo.id),
                    updateText = updateString,
                    newText = newString
                )
            }.toList()
    }

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            UpdateType.LanguageFilterChanged,
            is UpdateType.DownloadedSongsUpdated,
            is UpdateType.LibraryCacheUpdated,
            is UpdateType.PlaylistsUpdated,
            UpdateType.HistoryUpdated,
            UpdateType.SortingModeUpdated,
            UpdateType.ShouldShowDownloadedOnlyUpdated,
            UpdateType.ShouldHideExplicitUpdated,
            UpdateType.ShouldHideWorkInProgressUpdated,
            UpdateType.SearchQueryUpdated,
            UpdateType.AllDownloadsRemoved -> super.onUpdate(updateType)
            is UpdateType.LoadingStateChanged -> isLoading.set(updateType.isLoading)
            is UpdateType.Download -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let {
                if (it != -1) adapter.notifyItemChanged(it, SongInfoListAdapter.Payload.DownloadStateChanged(downloadedSongRepository.getSongDownloadedState(updateType.songId)))
            }
            is UpdateType.SongAddedToPlaylist -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let {
                if (it != -1 && !adapter.items[it].isSongOnAnyPlaylist) adapter.notifyItemChanged(it, SongInfoListAdapter.Payload.IsSongInAPlaylistChanged(true))
            }
            is UpdateType.SongRemovedFromPlaylist -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let {
                if (it != -1 && !playlistRepository.isSongInAnyPlaylist(updateType.songId)) adapter.notifyItemChanged(
                    it,
                    SongInfoListAdapter.Payload.IsSongInAPlaylistChanged(false)
                )
            }
            is UpdateType.LanguagesUpdated -> {
                languageFilters.get()?.clear()
                updateType.languageFilters.forEach { (language, isEnabled) ->
                    languageFilters.get()?.set(language, ObservableBoolean(isEnabled).apply {
                        onPropertyChanged { languageRepository.setLanguageFilterEnabled(language, it) }
                    })
                }
                languageFilters.notifyChange()
            }
        }
    }

    override fun onUpdateDone(items: List<SongInfoViewModel>, updateType: UpdateType) {
        super.onUpdateDone(items, updateType)
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
        R.string.library_filters -> showViewOptions()
        else -> Unit
    }

    fun showOrHideSearchInput() = isSearchInputVisible.toggle()

    fun showViewOptions() = shouldShowViewOptions.set(true)

    fun isHeader(position: Int) = when (sortingMode.get()) {
        LibraryViewModel.SortingMode.TITLE -> position == 0 || adapter.items[position].songInfo.titleWithSpecialCharactersRemoved[0] != adapter.items[position - 1].songInfo.titleWithSpecialCharactersRemoved[0]
        LibraryViewModel.SortingMode.ARTIST -> position == 0 || adapter.items[position].songInfo.artistWithSpecialCharactersRemoved[0] != adapter.items[position - 1].songInfo.artistWithSpecialCharactersRemoved[0]
        else -> false
    }

    fun getHeaderTitle(position: Int) = when (sortingMode.get()) {
        LibraryViewModel.SortingMode.TITLE -> adapter.items[position].songInfo.titleWithSpecialCharactersRemoved[0].toString().toUpperCase()
        LibraryViewModel.SortingMode.ARTIST -> adapter.items[position].songInfo.artistWithSpecialCharactersRemoved[0].toString().toUpperCase()
        else -> ""
    }

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
            placeholderButtonText.set(if (placeholderText.get() == R.string.library_placeholder_filters) R.string.library_filters else R.string.try_again)
        }
    }

    private fun Sequence<SongInfo>.filterByLanguages() = filter { languageRepository.isLanguageFilterEnabled(it.language.mapToLanguage()) }

    private fun Sequence<SongInfo>.filterDownloaded() =
        if (shouldShowDownloadedOnly.get()) filter { downloadedSongRepository.isSongDownloaded(it.id) } else this

    //TODO: Prioritize results that begin with the searchQuery.
    private fun Sequence<SongInfo>.filterByQuery() = if (isSearchInputVisible.get()) {
        searchQuery.get()?.trim()?.normalize()?.let { query ->
            filter { it.titleWithSpecialCharactersRemoved.contains(query, true) || it.artistWithSpecialCharactersRemoved.contains(query, true) }
        } ?: this
    } else this

    private fun Sequence<SongInfo>.sort() = when (sortingMode.get()) {
        SortingMode.POPULARITY -> sortedBy { it.artistWithSpecialCharactersRemoved }.sortedBy { it.titleWithSpecialCharactersRemoved }.sortedByDescending { it.popularity }
        SortingMode.TITLE -> sortedBy { it.artistWithSpecialCharactersRemoved }.sortedBy { it.titleWithSpecialCharactersRemoved }
        SortingMode.ARTIST -> sortedBy { it.titleWithSpecialCharactersRemoved }.sortedBy { it.artistWithSpecialCharactersRemoved }
        else -> this
    }

    private fun Sequence<SongInfo>.filterWorkInProgress() = if (!shouldShowWorkInProgress.get()) filter { it.version ?: 0 >= 0 } else this

    private fun Sequence<SongInfo>.filterExplicit() = if (!shouldShowExplicit.get()) filter { it.isExplicit != true } else this

    enum class SortingMode(val intValue: Int) {
        TITLE(0),
        ARTIST(1),
        POPULARITY(2);

        companion object {
            fun fromIntValue(value: Int) = SortingMode.values().find { it.intValue == value } ?: TITLE
        }
    }
}