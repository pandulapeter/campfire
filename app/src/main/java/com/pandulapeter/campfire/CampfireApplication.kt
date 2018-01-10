package com.pandulapeter.campfire

import com.pandulapeter.campfire.inversionOfControl.app.DaggerAppComponent
import dagger.android.AndroidInjector
import dagger.android.support.DaggerApplication

/**
 * Custom Application class for initializing dependency injection.
 *
 * TODO: Introduce Firebase crash reporting.
 */
class CampfireApplication : DaggerApplication() {

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> = DaggerAppComponent.builder().create(this)
}