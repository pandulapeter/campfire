package com.pandulapeter.campfire.feature.home.library

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.LanguageRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel
import com.pandulapeter.campfire.util.mapToLanguage
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.toggle

/**
 * Handles events and logic for [LibraryFragment].
 */
class LibraryViewModel(
    homeCallbacks: HomeFragment.HomeCallbacks?,
    userPreferenceRepository: UserPreferenceRepository,
    songInfoRepository: SongInfoRepository,
    downloadedSongRepository: DownloadedSongRepository,
    playlistRepository: PlaylistRepository,
    private val languageRepository: LanguageRepository) : SongListViewModel(homeCallbacks, userPreferenceRepository, songInfoRepository, downloadedSongRepository, playlistRepository) {
    val isSearchInputVisible = ObservableBoolean(userPreferenceRepository.searchQuery.isNotEmpty())
    val searchQuery = ObservableField(userPreferenceRepository.searchQuery)
    val shouldShowViewOptions = ObservableBoolean(false)
    val isLoading = ObservableBoolean(songInfoRepository.isLoading)
    val shouldShowErrorSnackbar = ObservableBoolean(false)
    val shouldShowDownloadedOnly = ObservableBoolean(userPreferenceRepository.shouldShowDownloadedOnly)
    val isSortedByTitle = ObservableBoolean(userPreferenceRepository.isSortedByTitle)
    val languageFilters = ObservableField(HashMap<Language, ObservableBoolean>())
    val filteredItemCount = ObservableField("")
    val shouldDisplaySubtitle = userPreferenceRepository.shouldShowSongCount

    init {
        isSearchInputVisible.onPropertyChanged { if (it) searchQuery.set("") else userPreferenceRepository.searchQuery = "" }
        searchQuery.onPropertyChanged { userPreferenceRepository.searchQuery = it }
        shouldShowDownloadedOnly.onPropertyChanged { userPreferenceRepository.shouldShowDownloadedOnly = it }
        isSortedByTitle.onPropertyChanged { userPreferenceRepository.isSortedByTitle = it }
    }

    override fun getAdapterItems(): List<SongInfo> {
        val librarySongs = songInfoRepository.getLibrarySongs()
            .filterWorkInProgress()
            .filterExplicit()
        val filteredItems = librarySongs
            .filterByLanguages()
            .filterDownloaded()
            .filterByQuery()
            .sort()
        filteredItemCount.set(if (filteredItems.size == librarySongs.size) "${filteredItems.size}" else "${filteredItems.size} / ${librarySongs.size}")
        return filteredItems
    }

    override fun onUpdate(updateType: UpdateType) {
        isLoading.set(songInfoRepository.isLoading)
        languageRepository.getLanguages().let { languages ->
            if (languages != languageFilters.get().keys.toList()) {
                languageFilters.get().clear()
                languages.forEach { language ->
                    languageFilters.get().put(language, ObservableBoolean(languageRepository.isLanguageFilterEnabled(language)).apply {
                        onPropertyChanged { languageRepository.setLanguageFilterEnabled(language, it) }
                    })
                }
                languageFilters.notifyChange()
            }
        }
        super.onUpdate(updateType)
    }

    fun forceRefresh() = songInfoRepository.updateDataSet { shouldShowErrorSnackbar.set(true) }

    fun showOrHideSearchInput() = isSearchInputVisible.toggle()

    fun showViewOptions() = shouldShowViewOptions.set(true)

    fun isHeader(position: Int) = position == 0 ||
        if (isSortedByTitle.get()) {
            adapter.items[position].songInfo.title.replaceSpecialCharacters()[0] != adapter.items[position - 1].songInfo.title.replaceSpecialCharacters()[0]
        } else {
            adapter.items[position].songInfo.artist.replaceSpecialCharacters()[0] != adapter.items[position - 1].songInfo.artist.replaceSpecialCharacters()[0]
        }

    fun getHeaderTitle(position: Int) = (if (isSortedByTitle.get()) adapter.items[position].songInfo.title.replaceSpecialCharacters()[0] else adapter.items[position].songInfo.artist.replaceSpecialCharacters()[0]).toString()

    private fun List<SongInfo>.filterByLanguages() = filter { languageRepository.isLanguageFilterEnabled(it.language.mapToLanguage()) }

    private fun List<SongInfo>.filterDownloaded() = if (shouldShowDownloadedOnly.get()) filter { downloadedSongRepository.isSongDownloaded(it.id) } else this

    //TODO: Prioritize results that begin with the searchQuery.
    private fun List<SongInfo>.filterByQuery() = if (isSearchInputVisible.get()) {
        val query = searchQuery.get().trim().replaceSpecialCharacters()
        filter { it.title.replaceSpecialCharacters().contains(query, true) || it.artist.replaceSpecialCharacters().contains(query, true) }
    } else this

    private fun List<SongInfo>.sort() = sortedBy { if (isSortedByTitle.get()) it.title.replaceSpecialCharacters() else it.artist.replaceSpecialCharacters() }

    private fun String.replaceSpecialCharacters() = this
        .replace("á", "a", true)
        .replace("ă", "a", true)
        .replace("â", "a", true)
        .replace("é", "e", true)
        .replace("í", "i", true)
        .replace("î", "i", true)
        .replace("ó", "o", true)
        .replace("ö", "o", true)
        .replace("ő", "o", true)
        .replace("ú", "u", true)
        .replace("ș", "s", true)
        .replace("ț", "t", true)
        .replace("ű", "u", true)
}