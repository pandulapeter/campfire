package com.pandulapeter.campfire

import android.app.Application
import com.pandulapeter.campfire.injection.integrationModule
import com.pandulapeter.campfire.injection.networkingModule
import com.pandulapeter.campfire.injection.repositoryModule
import org.koin.android.ext.android.startKoin

class CampfireApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin(this, listOf(integrationModule, networkingModule, repositoryModule))
    }
}