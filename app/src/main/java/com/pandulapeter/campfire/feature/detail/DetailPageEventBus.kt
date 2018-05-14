package com.pandulapeter.campfire.feature.detail

import com.pandulapeter.campfire.data.repository.shared.BaseRepository

class DetailPageEventBus : BaseRepository<DetailPageEventBus.Subscriber>() {

    fun notifyTranspositionChanged(songId: String, value: Int) = subscribers.forEach { it.onTranspositionChanged(songId, value) }

    interface Subscriber {

        fun onTranspositionChanged(songId: String, value: Int)
    }
}