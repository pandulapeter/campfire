package com.pandulapeter.campfire.data.repository.shared

import android.support.annotation.CallSuper

abstract class Repository<T> {

    protected var subscribers = mutableSetOf<T>()

    @CallSuper
    open fun subscribe(subscriber: T) {
        if (!subscribers.contains(subscriber)) {
            subscribers.add(subscriber)
        }
    }

    fun unsubscribe(subscriber: T) {
        if (subscribers.contains(subscriber)) {
            subscribers.remove(subscriber)
        }
    }
}