package com.pandulapeter.campfire.ioc

import com.pandulapeter.campfire.feature.home.HomeActivity
import com.pandulapeter.campfire.feature.home.library.LibraryFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class CampfireModule {

    @ContributesAndroidInjector
    abstract fun contributeHomeActivity(): HomeActivity

    @ContributesAndroidInjector
    abstract fun contributeLibraryFragment(): LibraryFragment
}