package com.pandulapeter.campfire

import android.app.Application
import org.koin.android.ext.android.startKoin

class CampfireApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin(
            this, listOf(
                integrationModule,
                networkingModule,
                repositoryModule,
                persistenceModule
            )
        )
    }
}