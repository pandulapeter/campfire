package com.pandulapeter.campfire.feature.detail

import com.pandulapeter.campfire.data.repository.shared.Repository
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.detail.songPage.SongPageFragment

/**
 * Enables communication between [DetailFragment] and [SongPageFragment].
 */
class DetailEventBus : Repository() {

    fun transposeSong(songId: String, value: Int) = notifySubscribers(UpdateType.TransposeEvent(songId, value))

    fun songTransposed(songId: String, value: Int) = notifySubscribers(UpdateType.SongTransposed(songId, value))

    fun onScrollStarted(songId: String) = notifySubscribers(UpdateType.ScrollStarted(songId))

    fun performScroll(songId: String, scrollSpeed: Int) = notifySubscribers(UpdateType.ContentScrolled(songId, scrollSpeed))

    fun onContentEndReached(songId: String) = notifySubscribers(UpdateType.ContentEndReached(songId))
}