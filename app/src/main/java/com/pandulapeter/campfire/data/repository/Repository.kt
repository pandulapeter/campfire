package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.Language
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.feature.home.HomeViewModel
import kotlin.reflect.KClass

/**
 * Base class for all repositories.
 */
abstract class Repository<T> {
    protected abstract var dataSet: T
    private var subscribers = mutableSetOf<Subscriber>()

    fun subscribe(subscriber: Subscriber) {
        if (!subscribers.contains(subscriber)) {
            subscribers.add(subscriber)
        }
        subscriber.onUpdate(UpdateType.InitialUpdate(this::class))
    }

    fun unsubscribe(subscriber: Subscriber) {
        if (subscribers.contains(subscriber)) {
            subscribers.remove(subscriber)
        }
    }

    protected fun notifySubscribers(updateType: UpdateType = UpdateType.Unspecified) = subscribers.forEach { it.onUpdate(updateType) }

    /**
     * Implemented by classes who want to observe changes in a repository.
     */
    interface Subscriber {

        fun onUpdate(updateType: UpdateType)
    }

    /**
     * Represents all the possible update events.
     */
    sealed class UpdateType {
        // General
        object Unspecified : UpdateType()

        class InitialUpdate(val repositoryClass: KClass<out Repository<*>>) : UpdateType()

        // DownloadedSongRepository
        class DownloadedSongsUpdated(val donloadedSongIds: List<String>) : UpdateType()

        // HistoryRepository
        class HistoryUpdated(val historyIds: List<String>) : UpdateType()

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
}