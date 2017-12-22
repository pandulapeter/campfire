package com.pandulapeter.campfire.data.repository.shared

import android.support.annotation.CallSuper

/**
 * Base class for all repositories that handles the subscription management.
 */
abstract class Repository {
    private var subscribers = mutableSetOf<Subscriber>()

    @CallSuper
    open fun subscribe(subscriber: Subscriber) {
        if (!subscribers.contains(subscriber)) {
            subscribers.add(subscriber)
        }
    }

    fun unsubscribe(subscriber: Subscriber) {
        if (subscribers.contains(subscriber)) {
            subscribers.remove(subscriber)
        }
    }

    protected fun notifySubscribers(updateType: UpdateType) = subscribers.forEach { it.onUpdate(updateType) }
}