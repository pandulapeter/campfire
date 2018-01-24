package com.pandulapeter.campfire.data.repository.shared

import com.pandulapeter.campfire.data.model.History
import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.model.SongInfo

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

    class DownloadSuccessful(val songId: String, val song: String) : UpdateType()

    class DownloadFailed(val songId: String) : UpdateType()

    // HistoryRepository
    object HistoryUpdated : UpdateType()

    class ItemAddedToHistory(val history: History, val position: Int) : UpdateType()

    class ItemRemovedFromHistory(val songId: String) : UpdateType()

    object HistoryCleared : UpdateType()

    // LanguageRepository
    class LanguagesUpdated(val languageFilters: Map<Language, Boolean>) : UpdateType()

    object LanguageFilterChanged : UpdateType()

    // PlaylistRepository
    class PlaylistsUpdated(val playlists: List<Playlist>) : UpdateType()

    class NewPlaylistsCreated(val playlists: Playlist) : UpdateType()

    class SongAddedToPlaylist(val playlistId: Int, val songId: String, val position: Int) : UpdateType()

    class SongRemovedFromPlaylist(val playlistId: Int, val songId: String, val position: Int) : UpdateType()

    class PlaylistRenamed(val playlistId: Int, val title: String) : UpdateType()

    class PlaylistDeleted(val position: Int) : UpdateType()

    // SongInfoRepository
    class LibraryCacheUpdated(val songInfos: List<SongInfo>) : UpdateType()

    class LoadingStateChanged(val isLoading: Boolean) : UpdateType()

    // UserPreferenceRepository
    object NavigationItemUpdated : UpdateType()

    object IsSortedByTitleUpdated : UpdateType()

    object ShouldShowDownloadedOnlyUpdated : UpdateType()

    object ShouldHideExplicitUpdated : UpdateType()

    object ShouldHideWorkInProgressUpdated : UpdateType()

    class ShouldShowChords(val shouldShowChords: Boolean) : UpdateType()

    object SearchQueryUpdated : UpdateType()

    // Other
    class EditModeChanged(val playlistId: Int, val isInEditMode: Boolean) : UpdateType()

    // ScrollManager
    class ScrollStarted(val songId: String) : UpdateType()

    class ContentScrolled(val songId: String, val scrollSpeed: Int) : UpdateType()

    class ContentEndReached(val songId: String) : UpdateType()
}