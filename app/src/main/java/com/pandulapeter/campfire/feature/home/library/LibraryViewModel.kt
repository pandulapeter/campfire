package com.pandulapeter.campfire.feature.home.library

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.LanguageRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
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
                       private val languageRepository: LanguageRepository) : SongListViewModel(homeCallbacks, songInfoRepository) {
    val isSearchInputVisible = ObservableBoolean(songInfoRepository.query.isNotEmpty())
    val query = ObservableField(songInfoRepository.query)
    val shouldShowViewOptions = ObservableBoolean(false)
    val isLoading = ObservableBoolean(songInfoRepository.isLoading)
    val shouldShowErrorSnackbar = ObservableBoolean(false)
    val shouldShowDownloadedOnly = ObservableBoolean(songInfoRepository.shouldShowDownloadedOnly)
    val isSortedByTitle = ObservableBoolean(songInfoRepository.isSortedByTitle)
    val languageFilters = ObservableField(HashMap<Language, ObservableBoolean>())

    init {
        isSearchInputVisible.onPropertyChanged { if (it) query.set("") else songInfoRepository.query = "" }
        query.onPropertyChanged { songInfoRepository.query = it }
        shouldShowDownloadedOnly.onPropertyChanged { songInfoRepository.shouldShowDownloadedOnly = it }
        isSortedByTitle.onPropertyChanged { songInfoRepository.isSortedByTitle = it }
    }

    override fun getAdapterItems(): List<SongInfoViewModel> {
        val downloadedSongs = songInfoRepository.getDownloadedSongs()
        val downloadedSongIds = downloadedSongs.map { it.id }
        return songInfoRepository.getLibrarySongs().filterByQuery(query.get().trim()).filterByLanguages().map { songInfo ->
            SongInfoViewModel(
                songInfo,
                downloadedSongIds.contains(songInfo.id),
                downloadedSongs.firstOrNull { songInfo.id == it.id }?.version?.compareTo(songInfo.version ?: 0) ?: 0 < 0)
        }
    }

    override fun onUpdate() {
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
        super.onUpdate()
    }

    fun forceRefresh() = songInfoRepository.updateDataSet { shouldShowErrorSnackbar.set(true) }

    fun showOrHideSearchInput() = isSearchInputVisible.toggle()

    fun addSongToFavorites(id: String, position: Int? = null) = songInfoRepository.addSongToFavorites(id, position)

    fun addOrRemoveSongFromDownloads(songInfo: SongInfo) =
        if (songInfoRepository.isSongDownloaded(songInfo.id)) {
            songInfoRepository.removeSongFromDownloads(songInfo.id)
        } else {
            songInfoRepository.addSongToDownloads(songInfo)
        }

    fun showViewOptions() = shouldShowViewOptions.set(true)

    fun isHeader(position: Int) = position == 0 ||
        if (isSortedByTitle.get()) {
            adapter.items[position].songInfo.title[0] != adapter.items[position - 1].songInfo.title[0]
        } else {
            adapter.items[position].songInfo.artist[0] != adapter.items[position - 1].songInfo.artist[0]
        }

    fun getHeaderTitle(position: Int) = (if (isSortedByTitle.get()) adapter.items[position].songInfo.title[0] else adapter.items[position].songInfo.artist[0]).toString()


    //TODO: Handle special characters, prioritize results that begin with the query.
    private fun List<SongInfo>.filterByQuery(query: String) = if (isSearchInputVisible.get()) {
        filter {
            it.title.contains(query, true) || it.artist.contains(query, true)
        }
    } else {
        this
    }

    private fun List<SongInfo>.filterByLanguages() = filter {
        languageRepository.isLanguageFilterEnabled(it.language.mapToLanguage())
    }
}