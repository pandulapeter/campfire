package com.pandulapeter.campfire.inversionOfControl.app

import com.pandulapeter.campfire.CampfireApplication
import com.pandulapeter.campfire.inversionOfControl.CampfireModule
import com.pandulapeter.campfire.inversionOfControl.module.IntegrationModule
import com.pandulapeter.campfire.inversionOfControl.module.NetworkModule
import com.pandulapeter.campfire.inversionOfControl.module.RepositoryModule
import com.pandulapeter.campfire.inversionOfControl.module.StorageModule
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
    IntegrationModule::class,
    NetworkModule::class,
    RepositoryModule::class])
interface AppComponent : AndroidInjector<CampfireApplication> {

    @Component.Builder
    abstract class Builder : AndroidInjector.Builder<CampfireApplication>()
}