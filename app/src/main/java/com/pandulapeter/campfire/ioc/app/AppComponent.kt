package com.pandulapeter.campfire.ioc.app

import com.pandulapeter.campfire.CampfireApplication
import com.pandulapeter.campfire.ioc.CampfireModule
import com.pandulapeter.campfire.ioc.module.NetworkModule
import com.pandulapeter.campfire.ioc.module.RepositoryModule
import com.pandulapeter.campfire.ioc.module.StorageModule
import dagger.Component
import dagger.android.AndroidInjector
import dagger.android.support.AndroidSupportInjectionModule
import javax.inject.Singleton

@Singleton
@Component(modules = [
    AndroidSupportInjectionModule::class,
    AppModule::class,
    CampfireModule::class,
    StorageModule::class,
    NetworkModule::class,
    RepositoryModule::class])
interface AppComponent : AndroidInjector<CampfireApplication> {

    @Component.Builder
    abstract class Builder : AndroidInjector.Builder<CampfireApplication>()
}