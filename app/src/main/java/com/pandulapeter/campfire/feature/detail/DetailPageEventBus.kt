package com.pandulapeter.campfire.feature.detail

import com.pandulapeter.campfire.data.repository.shared.Repository

class DetailPageEventBus : Repository<DetailPageEventBus.Subscriber>() {

    fun notifyTranspositionChanged(songId: String, value: Int) = subscribers.forEach { it.onTranspositionChanged(songId, value) }

    interface Subscriber {

        fun onTranspositionChanged(songId: String, value: Int)
    }
}