package com.pandulapeter.campfire.feature.detail

import com.pandulapeter.campfire.data.repository.shared.BaseRepository

class DetailEventBus : BaseRepository<DetailEventBus.Subscriber>() {

    fun notifyTransitionEnd() = subscribers.forEach { it.onTransitionEnd() }

    fun notifyTextSizeChanged() = subscribers.forEach { it.onTextSizeChanged() }

    fun notifyScroll(songId: String, speed: Int) = subscribers.forEach { it.scroll(songId, speed) }

    fun notifyTranspositionChanged(songId: String, value: Int) = subscribers.forEach { it.onTranspositionChanged(songId, value) }

    interface Subscriber {

        fun onTransitionEnd()

        fun onTextSizeChanged()

        fun onTranspositionChanged(songId: String, value: Int)

        fun scroll(songId: String, speed: Int)
    }
}