package com.pandulapeter.campfire.data.repository.shared

import com.pandulapeter.campfire.data.model.History
import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.feature.home.HomeViewModel

/**
 * Represents all the possible [Repository] update events.
 */
sealed class UpdateType {

    // DownloadedSongRepository
    class DownloadedSongsUpdated(val downloadedSongIds: List<String>) : UpdateType()

    class SongRemovedFromDownloads(val songId: String) : UpdateType()

    class SongAddedToDownloads(val songId: String) : UpdateType()

    object AllDownloadsRemoved : UpdateType()

    class DownloadStarted(val songId: String) : UpdateType()

    class DownloadSuccessful(val songId: String) : UpdateType()

    class DownloadFailed(val songId: String) : UpdateType()
    
    // HistoryRepository
    class HistoryUpdated(val historyIds: List<History>) : UpdateType()

    class ItemAddedToHistory(val history: History, val position: Int) : UpdateType()

    class ItemRemovedFromHistory(val songId: String) : UpdateType()

    object HistoryCleared : UpdateType()

    // LanguageRepository
    class LanguagesUpdated(val languageFilters: Map<Language, Boolean>) : UpdateType()

    class LanguageFilterChanged(val language: Language, val isEnabled: Boolean) : UpdateType()

    // PlaylistRepository
    class PlaylistsUpdated(val playlists: List<Playlist>) : UpdateType()

    class NewPlaylistsCreated(val playlists: Playlist) : UpdateType()

    class SongAddedToPlaylist(val playlistId: Int, val songId: String, val position: Int) : UpdateType()

    class SongRemovedFromPlaylist(val playlistId: Int, val songId: String, val position: Int) : UpdateType()

    class PlaylistRenamed(val playlistId: Int, val title: String) : UpdateType()

    class PlaylistDeleted(val playlistId: Int, val position: Int) : UpdateType()

    class PlaylistSongOrderUpdated(val playlistId: Int, val songIds: List<String>) : UpdateType()

    // SongInfoRepository
    class LibraryCacheUpdated(val songInfos: List<SongInfo>) : UpdateType()

    class LoadingStateChanged(val isLoading: Boolean) : UpdateType()

    // UserPreferenceRepository
    class NavigationItemUpdated(val homeNavigationItem: HomeViewModel.HomeNavigationItem) : UpdateType()

    class IsSortedByTitleUpdated(val isSortedByTitle: Boolean) : UpdateType()

    class ShouldShowDownloadedOnlyUpdated(val shouldShowDownloadedOnly: Boolean) : UpdateType()

    class ShouldHideExplicitUpdated(val shouldHideExplicit: Boolean) : UpdateType()

    class ShouldHideWorkInProgressUpdated(val shouldHideWorkInProgress: Boolean) : UpdateType()

    class SearchQueryUpdated(val searchQuery: String) : UpdateType()

    // Other
    class EditModeChanged(val playlistId: Int, val isInEditMode: Boolean) : UpdateType()
}