package com.pandulapeter.campfire.ioc.activity

import com.pandulapeter.campfire.feature.home.HomeActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class ActivityModule {

    @ContributesAndroidInjector
    abstract fun contributeHomeActivity(): HomeActivity
}