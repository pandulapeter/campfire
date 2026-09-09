package com.pandulapeter.campfire

import android.app.Application
import com.pandulapeter.campfire.data.repository.dataRepositoryModule
import com.pandulapeter.campfire.data.source.local.implementation.dataLocalSourceModule
import com.pandulapeter.campfire.domain.implementation.domainModule
import com.pandulapeter.campfire.presentation.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CampfireAndroidApplication : Application() {

    private val dataModules
        get() = dataLocalSourceModule + dataRepositoryModule

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CampfireAndroidApplication)
            modules(dataModules + domainModule + presentationModule)
        }
    }
}