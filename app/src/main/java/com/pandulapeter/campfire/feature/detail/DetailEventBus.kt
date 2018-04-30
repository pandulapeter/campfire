package com.pandulapeter.campfire.feature.detail

import com.pandulapeter.campfire.data.repository.shared.Repository

class DetailEventBus : Repository<DetailEventBus.Subscriber>() {

    fun notifyTransitionEnd() = subscribers.forEach { it.onTransitionEnd() }

    fun notifyTextSizeChanged() = subscribers.forEach { it.onTextSizeChanged() }

    fun notifyScroll(songId: String, speed: Int) = subscribers.forEach { it.scroll(songId, speed) }

    interface Subscriber {

        fun onTransitionEnd()

        fun onTextSizeChanged()

        fun scroll(songId: String, speed: Int)
    }
}