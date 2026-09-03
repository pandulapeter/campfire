package com.pandulapeter.campfire

import androidx.compose.ui.window.ComposeUIViewController
import com.pandulapeter.campfire.data.repository.dataRepositoryModule
import com.pandulapeter.campfire.data.source.local.implementation.dataLocalSourceModule
import com.pandulapeter.campfire.data.source.remote.implementation.dataRemoteSourceModule
import com.pandulapeter.campfire.domain.implementation.domainModule
import com.pandulapeter.campfire.presentation.ios.CampfireIosApp
import com.pandulapeter.campfire.shared.presentationModule
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

private val dataModules
    get() = dataLocalSourceModule + dataRemoteSourceModule + dataRepositoryModule

private val koinApplication by lazy {
    startKoin { modules(dataModules + domainModule + presentationModule) }
}

/**
 * Entry point called from Swift. Returns the view controller hosting the shared Compose UI.
 */
@Suppress("unused", "FunctionName")
fun CampfireViewController(): UIViewController {
    koinApplication
    return ComposeUIViewController {
        CampfireIosApp(
            stateHolder = CampfireViewModelStateHolder.fromViewModel(KoinPlatform.getKoin().get()),
            urlOpener = ::openUrl
        )
    }
}

private fun openUrl(url: String) {
    NSURL.URLWithString(url)?.let { nsUrl ->
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
