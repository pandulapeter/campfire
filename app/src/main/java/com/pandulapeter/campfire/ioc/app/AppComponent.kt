package com.pandulapeter.campfire.ioc.app

import com.pandulapeter.campfire.CampfireApplication
import com.pandulapeter.campfire.ioc.activity.ActivityModule
import com.pandulapeter.campfire.ioc.module.NetworkModule
import com.pandulapeter.campfire.ioc.module.StorageModule
import dagger.Component
import dagger.android.AndroidInjector
import dagger.android.support.AndroidSupportInjectionModule
import javax.inject.Singleton

@Singleton
@Component(modules = arrayOf(
    AndroidSupportInjectionModule::class,
    AppModule::class,
    ActivityModule::class,
    StorageModule::class,
    NetworkModule::class))
interface AppComponent : AndroidInjector<CampfireApplication> {

    @Component.Builder
    abstract class Builder : AndroidInjector.Builder<CampfireApplication>()
}