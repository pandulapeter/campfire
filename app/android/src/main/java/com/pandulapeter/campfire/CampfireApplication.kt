package com.pandulapeter.campfire

import android.app.Application
import com.pandulapeter.campfire.data.repository.dataRepositoryModule
import com.pandulapeter.campfire.data.source.local.implementationAndroid.dataLocalSourceAndroidModule
import com.pandulapeter.campfire.data.source.remote.implementationJvm.dataRemoteSourceJvmModule
import com.pandulapeter.campfire.domain.implementation.domainModule
import com.pandulapeter.campfire.presentation.androidDebugMenu.DebugMenu
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CampfireApplication : Application() {

    private val dataModules
        get() = dataLocalSourceAndroidModule + dataRemoteSourceJvmModule + dataRepositoryModule

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CampfireApplication)
            modules(dataModules + domainModule)
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