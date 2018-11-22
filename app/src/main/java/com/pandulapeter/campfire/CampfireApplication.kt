package com.pandulapeter.campfire

import android.app.Application
import org.koin.android.ext.android.startKoin

@Suppress("unused")
class CampfireApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin(
            this, listOf(
                integrationModule,
                networkingModule,
                repositoryModule,
                persistenceModule,
                detailModule,
                featureModule
            )
        )
    }
}