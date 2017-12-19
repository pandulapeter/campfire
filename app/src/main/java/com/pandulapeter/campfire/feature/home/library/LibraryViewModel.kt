package com.pandulapeter.campfire.feature.home.library

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.LanguageRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.Repository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel
import com.pandulapeter.campfire.util.mapToLanguage
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.toggle

/**
 * Handles events and logic for [LibraryFragment].
 */
class LibraryViewModel(homeCallbacks: HomeFragment.HomeCallbacks?,
                       songInfoRepository: SongInfoRepository,
                       userPreferenceRepository: UserPreferenceRepository,
                       private val downloadedSongRepository: DownloadedSongRepository,
                       private val playlistRepository: PlaylistRepository,
                       private val languageRepository: LanguageRepository) : SongListViewModel(homeCallbacks, userPreferenceRepository, songInfoRepository) {
    val isSearchInputVisible = ObservableBoolean(userPreferenceRepository.searchQuery.isNotEmpty())
    val query = ObservableField(userPreferenceRepository.searchQuery)
    val shouldShowViewOptions = ObservableBoolean(false)
    val isLoading = ObservableBoolean(songInfoRepository.isLoading)
    val shouldShowErrorSnackbar = ObservableBoolean(false)
    val shouldShowDownloadedOnly = ObservableBoolean(userPreferenceRepository.shouldShowDownloadedOnly)
    val isSortedByTitle = ObservableBoolean(userPreferenceRepository.isSortedByTitle)
    val languageFilters = ObservableField(HashMap<Language, ObservableBoolean>())
    val filteredItemCount = ObservableField("")
    val shouldDisplaySubtitle = userPreferenceRepository.shouldShowSongCount

    init {
        isSearchInputVisible.onPropertyChanged { if (it) query.set("") else userPreferenceRepository.searchQuery = "" }
        query.onPropertyChanged { userPreferenceRepository.searchQuery = it }
        shouldShowDownloadedOnly.onPropertyChanged { userPreferenceRepository.shouldShowDownloadedOnly = it }
        isSortedByTitle.onPropertyChanged { userPreferenceRepository.isSortedByTitle = it }
    }

    override fun getAdapterItems(): List<SongInfoViewModel> {
        val downloadedSongs = downloadedSongRepository.getDownloadedSongs()
        val downloadedSongIds = downloadedSongs.map { it.id }
        val librarySongs = songInfoRepository.getLibrarySongs()
            .filterWorkInProgress()
            .filterExplicit()
        val filteredItems = librarySongs
            .filterByLanguages()
            .filterDownloaded()
            .filterByQuery()
            .sort()
            .map { songInfo ->
                val isDownloaded = downloadedSongIds.contains(songInfo.id)
                SongInfoViewModel(
                    songInfo = songInfo,
                    isDownloaded = isDownloaded,
                    primaryActionDrawable = if (isDownloaded) {
                        if (playlistRepository.isSongInAnyPlaylist(songInfo.id)) R.drawable.ic_playlist_24dp else R.drawable.ic_playlist_border_24dp
                    } else {
                        R.drawable.ic_download_24dp
                    },
                    primaryActionContentDescription = if (isDownloaded) R.string.manage_playlists else R.string.download,
                    alertText = if (isDownloaded) {
                        if (downloadedSongs.firstOrNull { songInfo.id == it.id }?.version ?: 0 != songInfo.version ?: 0) R.string.new_version_available else null
                    } else {
                        null //TODO: or "new"
                    })
            }
        filteredItemCount.set(if (filteredItems.size == librarySongs.size) "${filteredItems.size}" else "${filteredItems.size} / ${librarySongs.size}")
        return filteredItems
    }

    override fun onUpdate(updateType: Repository.UpdateType) {
        isLoading.set(songInfoRepository.isLoading)
        languageRepository.getLanguages().let { languages ->
            if (languages != languageFilters.get().keys.toList()) {
                languageFilters.get().clear()
                languages.forEach { language ->
                    languageFilters.get().put(
                        language,
                        ObservableBoolean(languageRepository.isLanguageFilterEnabled(language)).apply {
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
            adapter.items[position].songInfo.title[0] != adapter.items[position - 1].songInfo.title[0]
        } else {
            adapter.items[position].songInfo.artist[0] != adapter.items[position - 1].songInfo.artist[0]
        }

    fun getHeaderTitle(position: Int) = (if (isSortedByTitle.get()) adapter.items[position].songInfo.title[0] else adapter.items[position].songInfo.artist[0]).toString()

    private fun List<SongInfo>.filterByLanguages() = filter { languageRepository.isLanguageFilterEnabled(it.language.mapToLanguage()) }

    private fun List<SongInfo>.filterDownloaded() = if (shouldShowDownloadedOnly.get()) filter { downloadedSongRepository.isSongDownloaded(it.id) } else this

    //TODO: Handle special characters, prioritize results that begin with the searchQuery.
    private fun List<SongInfo>.filterByQuery() = if (isSearchInputVisible.get()) {
        val query = query.get().trim()
        filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    } else this

    //TODO: Handle special characters.
    private fun List<SongInfo>.sort() = sortedBy { if (isSortedByTitle.get()) it.title else it.artist }
}