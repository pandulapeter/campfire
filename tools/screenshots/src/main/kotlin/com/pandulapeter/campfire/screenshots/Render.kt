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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.unit.Density
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
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.di.startCampfireDependencyGraph
import com.pandulapeter.campfire.metronome.api.Metronome
import com.pandulapeter.campfire.presentation.ui.CampfireApp
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.ImpersonatedPlatform
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.PlatformImpersonation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.skia.EncodedImageFormat
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.stopKoin
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.io.path.createTempDirectory

/** What every render of a run shares: where the library and the fonts are, and the version the app reports. */
internal class RenderEnvironment(
    val library: File,
    val fonts: Fonts,
    val appIcon: ImageBitmap?,
    val versionName: String,
)

/**
 * Takes [shot] on [device] into [output]: the library copied into a home of its own, the app started on it as the
 * device's platform, driven to the shot and drawn with the platform's chrome around it.
 *
 * Everything the app reads at its start is set up before Koin is: the home directory, which the desktop file storage
 * derives the library from as it is created, and the platform it is drawn as, which the interface reads on every
 * access. Koin is stopped again afterwards, so that the next shot starts from a library of its own. Runs on the Swing
 * thread, the desktop's main dispatcher, which the view model's work is dispatched to as well.
 */
@OptIn(InternalComposeUiApi::class)
internal suspend fun render(
    device: Device,
    shot: Shot,
    environment: RenderEnvironment,
    output: File,
) {
    val home = createTempDirectory("campfire-screenshots").toFile()
    try {
        prepareLibrary(
            source = environment.library,
            target = File(home, "Library/Application Support/Campfire"),
            preferences = shot.preferences(device),
            shot = shot,
            versionName = environment.versionName,
        )
        System.setProperty("user.home", home.absolutePath)
        PlatformImpersonation.platform = device.platform
        PlatformImpersonation.fontFamily = environment.fonts.interfaceFamily(device.platform)
        PlatformImpersonation.monospaceFontFamily = environment.fonts.monospaceFamily(device.platform)
        val koin = startCampfireDependencyGraph().koin
        koin.loadModules(listOf(fakes), allowOverride = true)
        val owner = SceneOwner()
        val appearance = ChromeAppearance(
            isDark = shot.uiMode == UserPreferences.UiMode.DARK,
            fontFamily = environment.fonts.chromeFamily(device.platform),
            appIcon = environment.appIcon,
        )
        var viewModel: CampfireViewModel? = null
        var isPrepared by mutableStateOf(false)
        var isAppReady = false
        val scene = ImageComposeScene(
            width = device.widthPx,
            height = device.heightPx,
            density = Density(device.density),
            coroutineContext = Dispatchers.Main,
        ) {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides owner,
                LocalLifecycleOwner provides owner,
                LocalSavedStateRegistryOwner provides owner,
                LocalFilePicker provides ScreenshotFilePicker(canShare = device.platform.canShare),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().padding(top = device.chrome.windowTop, bottom = device.chrome.windowBottom)) {
                        CompositionLocalProvider(LocalPlatformWindowInsets provides DeviceInsets(device.chrome, device.density)) {
                            val currentViewModel = koinViewModel<CampfireViewModel>()
                            SideEffect { viewModel = currentViewModel }
                            if (isPrepared) {
                                CampfireApp(
                                    viewModel = currentViewModel,
                                    urlOpener = {},
                                    onAppReady = { isAppReady = true },
                                )
                            }
                        }
                    }
                    device.chrome.Overlay(appearance)
                }
            }
        }
        var time = 0L
        suspend fun settle(milliseconds: Long) {
            repeat((milliseconds / FRAME_MILLIS).toInt().coerceAtLeast(1)) {
                time += FRAME_MILLIS * 1_000_000
                scene.render(time)
                delay(FRAME_MILLIS)
            }
        }
        try {
            // The view model is created before the app is composed, and reads the library on its own, so the shot is
            // prepared against the whole library rather than against whatever the first frames had of it.
            withTimeoutOrNull(LAUNCH_TIMEOUT_MILLIS) {
                while (viewModel?.isLoading?.value != false) settle(FRAME_MILLIS)
            } ?: error("The library was not read within ${LAUNCH_TIMEOUT_MILLIS / 1000} seconds.")
            val currentViewModel = checkNotNull(viewModel)
            PrepareScope(viewModel = currentViewModel, device = device, language = shot.language, settle = ::settle).apply { shot.prepare(this) }
            isPrepared = true
            withTimeoutOrNull(LAUNCH_TIMEOUT_MILLIS) {
                while (!isAppReady) settle(FRAME_MILLIS)
            } ?: error("The app did not finish starting within ${LAUNCH_TIMEOUT_MILLIS / 1000} seconds.")
            settle(SETTLE_MILLIS)
            ShotScope(viewModel = currentViewModel, device = device, language = shot.language, settle = ::settle, scene = scene)
                .apply { shot.drive(this) }
            settle(SETTLE_MILLIS)
            // Covers arrive on their own time, a few at once, so the shot waits until the screen stops changing rather
            // than for a fixed while that a slower start would outlast.
            fun frameHash() = scene.render(time).encodeToData(EncodedImageFormat.PNG)?.bytes?.contentHashCode()
            var previous = frameHash()
            var checks = 0
            while (checks++ < STABLE_CHECKS) {
                settle(STABLE_INTERVAL_MILLIS)
                val current = frameHash()
                if (current == previous) break
                previous = current
            }
            shot.beat?.let { beat ->
                (koin.get<Metronome>() as SilentMetronome).beat(beat)
                settle(FRAME_MILLIS * 3)
            }
            output.parentFile.mkdirs()
            output.writeBytes(checkNotNull(scene.render(time).encodeToData(EncodedImageFormat.PNG)).bytes)
        } finally {
            scene.close()
            owner.viewModelStore.clear()
            stopKoin()
        }
    } finally {
        PlatformImpersonation.platform = null
        PlatformImpersonation.fontFamily = null
        PlatformImpersonation.monospaceFontFamily = null
        home.deleteRecursively()
    }
}

/**
 * Copies the library, its covers and its preferences into [target], the data directory of a desktop install, with the
 * preferences changed to what [shot] asks for. The current version is marked as introduced, so that its What's new
 * dialog does not open over the shot.
 */
private fun prepareLibrary(
    source: File,
    target: File,
    preferences: Map<String, JsonElement>,
    shot: Shot,
    versionName: String,
) {
    listOf("library", "covers").forEach { name -> File(source, name).takeIf { it.isDirectory }?.copyRecursively(File(target, name)) }
    // The library is set on the day the system bars show, so every setlist's day is moved by however many days have
    // passed since, and a countdown reads the same on every run ("Tomorrow" stays tomorrow).
    val daysSinceLibraryDate = ChronoUnit.DAYS.between(LIBRARY_DATE, LocalDate.now())
    File(target, "library/setlists").listFiles { file -> file.name.endsWith(".setlist.json") }?.forEach { file ->
        val setlist = Json.parseToJsonElement(file.readText()).jsonObject
        val date = setlist["date"]?.jsonPrimitive?.content?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@forEach
        file.writeText(JsonObject(setlist + ("date" to JsonPrimitive(date.plusDays(daysSinceLibraryDate).toString()))).toString())
    }
    val preferencesFile = File(source, "preferences/preferences.json")
    val stored = if (preferencesFile.isFile) Json.parseToJsonElement(preferencesFile.readText()).jsonObject else JsonObject(emptyMap())
    val seenVersions = (stored["seenWhatsNewVersions"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
    val patched = JsonObject(
        stored.mergedWith(JsonObject(preferences)) + mapOf(
            "uiMode" to JsonPrimitive(shot.uiMode.id),
            "language" to JsonPrimitive(shot.language.id),
            "seenWhatsNewVersions" to JsonArray((seenVersions + versionName).distinct().map(::JsonPrimitive)),
        ),
    )
    File(target, "preferences").mkdirs()
    File(target, "preferences/preferences.json").writeText(patched.toString())
}

/**
 * This document with [changes] written into it. A member that is a document of its own in both (the metronome's
 * settings, and the accents of each time signature in them) is changed member by member, so that a shot keeps whatever
 * of it the library has and the shot does not mention.
 */
private fun JsonObject.mergedWith(changes: JsonObject): JsonObject = JsonObject(
    this + changes.mapValues { (key, value) ->
        val current = this[key]
        if (value is JsonObject && current is JsonObject) current.mergedWith(value) else value
    },
)

/** The day the shots are taken on, as far as the library and the system bars are concerned: Wednesday, October 7, 2026. */
private val LIBRARY_DATE: LocalDate = LocalDate.of(2026, 10, 7)

private val ImpersonatedPlatform.canShare
    get() = when (this) {
        ImpersonatedPlatform.ANDROID, ImpersonatedPlatform.IOS -> true
        ImpersonatedPlatform.MACOS, ImpersonatedPlatform.WINDOWS -> false
    }

/** The edges of the app's window that [chrome] draws over, as the platform would report them to the app. */
@OptIn(InternalComposeUiApi::class)
private class DeviceInsets(chrome: SystemChrome, private val density: Float) : PlatformWindowInsets {
    private fun Dp.toPx() = (value * density).toInt()

    override val statusBars = PlatformInsets(top = chrome.statusBar.toPx())
    override val navigationBars = PlatformInsets(bottom = chrome.navigationBar.toPx())
    override val captionBar = PlatformInsets(top = chrome.captionBar.toPx())
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
private class SceneOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, HasDefaultViewModelProviderFactory {
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

private const val SETTLE_MILLIS = 1_500L
private const val STABLE_INTERVAL_MILLIS = 750L
private const val STABLE_CHECKS = 20
private const val LAUNCH_TIMEOUT_MILLIS = 30_000L
