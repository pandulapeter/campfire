package com.pandulapeter.campfire.data.repository.shared

import com.pandulapeter.campfire.data.model.History
import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.feature.home.HomeViewModel
import kotlin.reflect.KClass

/**
 * Represents all the possible [Repository] update events.
 */
sealed class UpdateType {

    // General
    object Unspecified : UpdateType()

    class InitialUpdate(val repositoryClass: KClass<out Repository>) : UpdateType()

    // DownloadedSongRepository
    class DownloadedSongsUpdated(val donloadedSongIds: List<String>) : UpdateType()

    class DownloadStarted(val songId: String) : UpdateType()

    class DownloadFinished(val songId: String) : UpdateType()

    // HistoryRepository
    class HistoryUpdated(val historyIds: List<History>) : UpdateType()

    // LanguageRepository
    class LanguageFilterChanged(val language: Language, val isEnabled: Boolean) : UpdateType()

    // PlaylistRepository
    class PlaylistsUpdated(val playlists: List<Playlist>) : UpdateType()

    // SongInfoRepository
    class LoadingStateChanged(val isLoading: Boolean) : UpdateType()

    class LibraryCacheUpdated(val songInfos: List<SongInfo>) : UpdateType()

    // UserPreferenceRepository
    class NavigationItemUpdated(val navigationItem: HomeViewModel.NavigationItem) : UpdateType()

    class IsSortedByTitleUpdated(val isSortedByTitle: Boolean) : UpdateType()

    class ShouldShowDownloadedOnlyUpdated(val shouldShowDownloadedOnly: Boolean) : UpdateType()

    class ShouldHideExplicitUpdated(val shouldHideExplicit: Boolean) : UpdateType()

    class ShouldHideWorkInProgressUpdated(val shouldHideWorkInProgress: Boolean) : UpdateType()

    class ShouldShowSongCountUpdated(val shouldShowSongCount: Boolean) : UpdateType()

    class SearchQueryUpdated(val searchQuery: String) : UpdateType()
}