package com.pandulapeter.campfire.feature.home.library

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.R
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
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoAdapter
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel
import com.pandulapeter.campfire.util.mapToLanguage
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.replaceSpecialCharacters
import com.pandulapeter.campfire.util.toggle

/**
 * Handles events and logic for [LibraryFragment].
 */
class LibraryViewModel(
    homeCallbacks: HomeFragment.HomeCallbacks?,
    userPreferenceRepository: UserPreferenceRepository,
    songInfoRepository: SongInfoRepository,
    downloadedSongRepository: DownloadedSongRepository,
    private val playlistRepository: PlaylistRepository,
    private val languageRepository: LanguageRepository) : SongListViewModel(homeCallbacks, userPreferenceRepository, songInfoRepository, downloadedSongRepository) {
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

    override fun getAdapterItems(): List<SongInfoViewModel> {
        val librarySongs = songInfoRepository.getLibrarySongs()
            .filterWorkInProgress()
            .filterExplicit()
        val filteredItems = librarySongs
            .filterByLanguages()
            .filterDownloaded()
            .filterByQuery()
            .sort()
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
                    if (downloadedSongRepository.getDownloadedSong(songInfo.id)?.version ?: 0 != songInfo.version ?: 0) R.string.new_version_available else null
                } else {
                    if (isSongNew) R.string.library_new else null
                })
        }
    }

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.LanguageFilterChanged,
            is UpdateType.DownloadedSongsUpdated,
            is UpdateType.LibraryCacheUpdated,
            is UpdateType.PlaylistsUpdated,
            is UpdateType.HistoryUpdated,
            is UpdateType.IsSortedByTitleUpdated,
            is UpdateType.ShouldShowDownloadedOnlyUpdated,
            is UpdateType.SearchQueryUpdated,
            is UpdateType.AllDownloadsRemoved -> super.onUpdate(updateType)
            is UpdateType.LoadingStateChanged -> isLoading.set(updateType.isLoading)
            is UpdateType.SongAddedToDownloads -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoAdapter.SONG_DOWNLOADED) }
            is UpdateType.SongRemovedFromDownloads -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoAdapter.SONG_DOWNLOAD_DELETED) }
            is UpdateType.DownloadStarted -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoAdapter.DOWNLOAD_STARTED) }
            is UpdateType.DownloadSuccessful -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoAdapter.DOWNLOAD_SUCCESSFUL) }
            is UpdateType.DownloadFailed -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoAdapter.DOWNLOAD_FAILED) }
            is UpdateType.SongAddedToPlaylist -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, SongInfoAdapter.SONG_IS_IN_A_PLAYLIST) }
            is UpdateType.SongRemovedFromPlaylist -> adapter.items.indexOfFirst { it.songInfo.id == updateType.songId }.let { if (it != -1) adapter.notifyItemChanged(it, if (playlistRepository.isSongInAnyPlaylist(updateType.songId)) SongInfoAdapter.SONG_IS_IN_A_PLAYLIST else SongInfoAdapter.SONG_IS_NOT_IN_A_PLAYLISTS) }
            is UpdateType.LanguagesUpdated -> {
                languageFilters.get().clear()
                updateType.languageFilters.forEach { (language, isEnabled) ->
                    languageFilters.get().put(language, ObservableBoolean(isEnabled).apply {
                        onPropertyChanged { languageRepository.setLanguageFilterEnabled(language, it) }
                    })
                }
                languageFilters.notifyChange()
            }
        }
    }

    fun forceRefresh() = songInfoRepository.updateDataSet { shouldShowErrorSnackbar.set(true) }

    fun showOrHideSearchInput() = isSearchInputVisible.toggle()

    fun showViewOptions() = shouldShowViewOptions.set(true)

    fun isHeader(position: Int) = position == 0 ||
        if (isSortedByTitle.get()) {
            adapter.items[position].songInfo.titleWithSpecialCharactersRemoved[0] != adapter.items[position - 1].songInfo.titleWithSpecialCharactersRemoved[0]
        } else {
            adapter.items[position].songInfo.artistWithSpecialCharactersRemoved[0] != adapter.items[position - 1].songInfo.artistWithSpecialCharactersRemoved[0]
        }

    fun getHeaderTitle(position: Int) = (if (isSortedByTitle.get()) adapter.items[position].songInfo.titleWithSpecialCharactersRemoved[0] else adapter.items[position].songInfo.artistWithSpecialCharactersRemoved[0]).toString().toUpperCase()

    private fun List<SongInfo>.filterByLanguages() = filter { languageRepository.isLanguageFilterEnabled(it.language.mapToLanguage()) }

    private fun List<SongInfo>.filterDownloaded() = if (shouldShowDownloadedOnly.get()) filter { downloadedSongRepository.isSongDownloaded(it.id) } else this

    //TODO: Prioritize results that begin with the searchQuery.
    private fun List<SongInfo>.filterByQuery() = if (isSearchInputVisible.get()) {
        searchQuery.get().trim().replaceSpecialCharacters().let { query ->
            filter { it.titleWithSpecialCharactersRemoved.contains(query, true) || it.artistWithSpecialCharactersRemoved.contains(query, true) }
        }
    } else this

    private fun List<SongInfo>.sort() = sortedBy { if (isSortedByTitle.get()) it.titleWithSpecialCharactersRemoved else it.artistWithSpecialCharactersRemoved }
}