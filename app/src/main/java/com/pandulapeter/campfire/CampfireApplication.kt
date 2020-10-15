package com.pandulapeter.campfire

import android.app.Application
import com.pandulapeter.beagle.Beagle
import com.pandulapeter.beagle.common.configuration.Behavior
import com.pandulapeter.beagle.logOkHttp.BeagleOkHttpLogger
import com.pandulapeter.beagle.modules.AnimationDurationSwitchModule
import com.pandulapeter.beagle.modules.AppInfoButtonModule
import com.pandulapeter.beagle.modules.DeveloperOptionsButtonModule
import com.pandulapeter.beagle.modules.DeviceInfoModule
import com.pandulapeter.beagle.modules.DividerModule
import com.pandulapeter.beagle.modules.HeaderModule
import com.pandulapeter.beagle.modules.KeylineOverlaySwitchModule
import com.pandulapeter.beagle.modules.LifecycleLogListModule
import com.pandulapeter.beagle.modules.LogListModule
import com.pandulapeter.beagle.modules.NetworkLogListModule
import com.pandulapeter.beagle.modules.PaddingModule
import com.pandulapeter.beagle.modules.ScreenCaptureToolboxModule
import com.pandulapeter.beagle.modules.TextModule
import com.pandulapeter.campfire.data.networking.NetworkManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

@Suppress("unused")
class CampfireApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CampfireApplication)
            modules(
                listOf(
                    integrationModule,
                    networkingModule,
                    repositoryModule,
                    persistenceModule,
                    detailModule,
                    featureModule
                )
            )
        }
        setupDebugMenu()
    }

    private fun setupDebugMenu() {
        if (BuildConfig.BUILD_TYPE != "release") {
            Beagle.initialize(
                application = this,
                behavior = Behavior(
                    networkLoggers = listOf(BeagleOkHttpLogger)
                )
            )
            Beagle.set(
                HeaderModule(
                    title = getString(R.string.campfire),
                    subtitle = BuildConfig.APPLICATION_ID,
                    text = "${BuildConfig.BUILD_TYPE} v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                ),
                AppInfoButtonModule(type = TextModule.Type.NORMAL),
                DeveloperOptionsButtonModule(type = TextModule.Type.NORMAL),
                PaddingModule(),
                TextModule("General", TextModule.Type.SECTION_HEADER),
                KeylineOverlaySwitchModule(),
                AnimationDurationSwitchModule(),
                ScreenCaptureToolboxModule(),
                DividerModule(),
                TextModule("Logs", TextModule.Type.SECTION_HEADER),
                NetworkLogListModule(baseUrl = NetworkManager.BASE_URL),
                LogListModule(),
                LifecycleLogListModule(),
                DividerModule(),
                TextModule("Other", TextModule.Type.SECTION_HEADER),
                DeviceInfoModule()
            )
        }
    }
}