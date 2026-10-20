/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import com.pandulapeter.campfire.di.startCampfireDependencyGraph
import com.pandulapeter.campfire.presentation.ui.CampfireApp
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.platform.ImpersonatedPlatform
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.PlatformImpersonation
import com.pandulapeter.campfire.presentation.ui.platform.desktopDataDirectory
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.Koin
import org.koin.core.context.stopKoin
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Starts the app over a home directory of its own, as [platform] with [chrome] around its window, runs [block] with it,
 * and stops it again: what a screenshot and a UI test share.
 *
 * Everything the app reads at its start is set up before Koin is: the home directory, which the desktop file storage
 * derives the library from as it is created ([writeData] fills its data directory first), and the platform it is drawn
 * as, which the interface reads on every access. Koin is stopped again afterwards and the home deleted, so that the next
 * start has a library of its own. A scene composing [CampfireAppHost.Content] must be closed before [block] returns.
 */
@OptIn(DelicateCoilApi::class)
internal inline fun <T> withCampfireApp(
    platform: ImpersonatedPlatform,
    chrome: SystemChrome,
    density: Float,
    writeData: (dataDirectory: File) -> Unit,
    block: (CampfireAppHost) -> T,
): T {
    val home = createTempDirectory("campfire-app").toFile()
    try {
        System.setProperty("user.home", home.absolutePath)
        // Where the desktop file storage will look, which differs by system. On Linux and Windows an environment
        // variable can move it out of the home (XDG_DATA_HOME, APPDATA), and the app started on it would then read and
        // write the library of whoever runs the tool.
        val dataDirectory = desktopDataDirectory()
        check(dataDirectory.startsWith(home)) { "The app's data directory, $dataDirectory, is outside its own home; unset XDG_DATA_HOME or APPDATA." }
        writeData(dataDirectory)
        PlatformImpersonation.platform = platform
        // Coil's image loader is one per process and is built once, with the cover use case of the Koin it was first
        // composed under: kept for the next start, it would read the covers from the first start's home, deleted by now,
        // and show only the ones its memory cache happened to hold. Each start builds its own instead.
        SingletonImageLoader.reset()
        val koin = startCampfireDependencyGraph().koin
        koin.loadModules(listOf(fakes), allowOverride = true)
        val host = CampfireAppHost(koin = koin, dataDirectory = dataDirectory, chrome = chrome, density = density)
        try {
            return block(host)
        } finally {
            host.owner.viewModelStore.clear()
            stopKoin()
        }
    } finally {
        PlatformImpersonation.platform = null
        home.deleteRecursively()
    }
}

/**
 * One start of the app, made by [withCampfireApp]: its Koin, the data directory its library is in, and the window
 * [Content] draws it into.
 */
internal class CampfireAppHost(
    val koin: Koin,
    val dataDirectory: File,
    private val chrome: SystemChrome,
    private val density: Float,
) {
    val owner = SceneOwner()

    /**
     * The app as a window shows it, inside [chrome]'s bars, with the window owners the view model is created from. The
     * view model is created (and handed to [onViewModel]) as soon as this is composed, but the app is composed only
     * once [isPrepared], so a state a screen reads as it is first composed can be set before. [keyboardHeight] is the
     * on-screen keyboard's, reported to the app as the platform would while it is up; only [overlay] may draw one.
     */
    @OptIn(InternalComposeUiApi::class)
    @Composable
    fun Content(
        filePicker: FilePicker,
        isPrepared: Boolean,
        keyboardHeight: Dp,
        onViewModel: (CampfireViewModel) -> Unit,
        onAppReady: () -> Unit,
        overlay: @Composable () -> Unit,
    ) = CompositionLocalProvider(
        LocalViewModelStoreOwner provides owner,
        LocalLifecycleOwner provides owner,
        LocalSavedStateRegistryOwner provides owner,
        LocalFilePicker provides filePicker,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(top = chrome.windowTop, bottom = chrome.windowBottom)) {
                CompositionLocalProvider(LocalPlatformWindowInsets provides DeviceInsets(chrome, density, keyboardHeight)) {
                    val viewModel = koinViewModel<CampfireViewModel>()
                    SideEffect { onViewModel(viewModel) }
                    if (isPrepared) {
                        CampfireApp(
                            viewModel = viewModel,
                            urlOpener = {},
                            onAppReady = onAppReady,
                        )
                    }
                }
            }
            overlay()
        }
    }
}

/**
 * The edges of the app's window that [chrome] draws over, and the keyboard [keyboardHeight] tall where one is up, as the
 * platform would report them to the app.
 */
@OptIn(InternalComposeUiApi::class)
private class DeviceInsets(chrome: SystemChrome, private val density: Float, keyboardHeight: Dp) : PlatformWindowInsets {
    private fun Dp.toPx() = (value * density).toInt()

    override val statusBars = PlatformInsets(top = chrome.statusBar.toPx())
    override val navigationBars = PlatformInsets(bottom = chrome.navigationBar.toPx())
    override val captionBar = PlatformInsets(top = chrome.captionBar.toPx())
    override val ime = PlatformInsets(bottom = keyboardHeight.toPx())
    // Compose on iOS reports a safe area at the side both as the system bars and, in landscape, as the display cutout.
    override val displayCutout = PlatformInsets(right = chrome.trailingBar.toPx())
    override val systemBars = PlatformInsets(
        top = maxOf(chrome.statusBar, chrome.captionBar).toPx(),
        right = chrome.trailingBar.toPx(),
        bottom = chrome.navigationBar.toPx(),
    )
}

/**
 * What a window hands the app it shows: a resumed lifecycle, a store for its view model, and the saved state registry
 * Koin builds the view model's `SavedStateHandle` from, which it finds through the creation extras.
 */
internal class SceneOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, HasDefaultViewModelProviderFactory {
    private val lifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {}
    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply {
            set(SAVED_STATE_REGISTRY_OWNER_KEY, this@SceneOwner)
            set(VIEW_MODEL_STORE_OWNER_KEY, this@SceneOwner)
        }

    init {
        savedStateRegistryController.performAttach()
        enableSavedStateHandles()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }
}
