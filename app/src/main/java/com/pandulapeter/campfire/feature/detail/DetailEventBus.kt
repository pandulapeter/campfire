package com.pandulapeter.campfire.feature.detail

import com.pandulapeter.campfire.data.repository.shared.Repository

class DetailEventBus : Repository<DetailEventBus.Subscriber>() {

    fun notifyTransitionEnd() = subscribers.forEach { it.onTransitionEnd() }

    fun notifyTextSizeChanged() = subscribers.forEach { it.onTextSizeChanged() }

    fun notifyScroll(songId: String, speed: Int) = subscribers.forEach { it.scroll(songId, speed) }

    fun notifyShouldShowChordsChanged() = subscribers.forEach { it.onShouldShowChordsChanged() }

    fun notifyTranspositionChanged(songId: String, value: Int) = subscribers.forEach { it.onTranspositionChanged(songId, value) }

    interface Subscriber {

        fun onTransitionEnd()

        fun onTextSizeChanged()

        fun onShouldShowChordsChanged()

        fun onTranspositionChanged(songId: String, value: Int)

        fun scroll(songId: String, speed: Int)
    }
}