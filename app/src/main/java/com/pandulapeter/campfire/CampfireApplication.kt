package com.pandulapeter.campfire

import android.app.Application
import com.pandulapeter.campfire.data.repository.dataRepositoryModule
import com.pandulapeter.campfire.data.source.localImpl.dataLocalSourceModule
import com.pandulapeter.campfire.data.source.remote.dataRemoteSourceModule
import com.pandulapeter.campfire.domain.domainModule
import com.pandulapeter.campfire.presentation.collections.presentationCollectionsModule
import com.pandulapeter.campfire.presentation.debugMenu.DebugMenu
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CampfireApplication : Application() {

    private val dataModules
        get() = dataLocalSourceModule + dataRemoteSourceModule + dataRepositoryModule
    private val presentationModules
        get() = presentationCollectionsModule

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CampfireApplication)
            modules(dataModules + domainModule + presentationModules)
        }
        DebugMenu.initialize(
            application = this,
            applicationTitle = getString(R.string.campfire),
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            themeResourceId = R.style.Campfire
        )
    }
}