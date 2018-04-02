package com.pandulapeter.campfire.feature.shared

import android.content.ComponentCallbacks
import android.content.res.Configuration

abstract class CampfireViewModel : ComponentCallbacks {

    var componentCallbacks: ComponentCallbacks? = null

    override fun onLowMemory() {
        componentCallbacks?.onLowMemory()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        componentCallbacks?.onConfigurationChanged(newConfig)
    }

    open fun subscribe() = Unit

    open fun unsubscribe() = Unit
}