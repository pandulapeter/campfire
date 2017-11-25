package com.pandulapeter.campfire.ioc.app

import com.pandulapeter.campfire.CampfireApplication
import com.pandulapeter.campfire.ioc.activity.ActivityModule
import dagger.Component
import dagger.android.AndroidInjector
import dagger.android.support.AndroidSupportInjectionModule
import javax.inject.Singleton

@Singleton
@Component(modules = arrayOf(AndroidSupportInjectionModule::class, AppModule::class, ActivityModule::class))
interface AppComponent : AndroidInjector<CampfireApplication> {

    @Component.Builder
    abstract class Builder : AndroidInjector.Builder<CampfireApplication>()
}