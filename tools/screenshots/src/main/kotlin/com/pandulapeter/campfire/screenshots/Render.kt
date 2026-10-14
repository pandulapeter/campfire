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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.metronome.api.Metronome
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.ImpersonatedPlatform
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
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What every render of a run shares: where the library and the system chrome's fonts are, and the version the app reports. */
internal class RenderEnvironment(
    val library: File,
    val fonts: Fonts,
    val appIcon: ImageBitmap?,
    val versionName: String,
)

/**
 * Takes [shot] on [device] into [output], and the clip its drive records, if it does, into [frames] as numbered JPEGs:
 * the library copied into a home of its own, the app started on it as the
 * device's platform, driven to the shot and drawn with the platform's chrome around it.
 *
 * Runs on the Swing thread, the desktop's main dispatcher, which the view model's work is dispatched to as well.
 */
internal suspend fun render(
    device: Device,
    shot: Shot,
    environment: RenderEnvironment,
    output: File,
    frames: File? = null,
) = withCampfireApp(
    platform = device.platform,
    chrome = device.chrome,
    density = device.density,
    writeData = { dataDirectory ->
        prepareLibrary(
            source = environment.library,
            target = dataDirectory,
            preferences = shot.preferences(device),
            shot = shot,
            versionName = environment.versionName,
        )
    },
) { host ->
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
        host.Content(
            filePicker = ScreenshotFilePicker(canShare = device.platform.canShare),
            isPrepared = isPrepared,
            keyboardHeight = 0.dp,
            onViewModel = { viewModel = it },
            onAppReady = { isAppReady = true },
            overlay = { device.chrome.Overlay(appearance) },
        )
    }
    var time = 0L
    suspend fun settle(milliseconds: Long) {
        repeat((milliseconds / FRAME_MILLIS).toInt().coerceAtLeast(1)) {
            time += FRAME_MILLIS * 1_000_000
            scene.render(time)
            delay(FRAME_MILLIS)
        }
    }
    var frameCount = 0
    suspend fun captureFrame() {
        time += RECORDING_FRAME_NANOS
        val image = scene.render(time)
        if (frames != null) {
            frames.mkdirs()
            File(frames, "%05d.jpg".format(frameCount++)).writeBytes(checkNotNull(image.encodeToData(EncodedImageFormat.JPEG, JPEG_QUALITY)).bytes)
        }
        // The scene's clock is what animations follow; the real pause is for the app's own coroutines (a beat
        // collected, a state arriving from another thread) to run before the next frame is drawn.
        delay(FRAME_MILLIS)
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
        ShotScope(viewModel = currentViewModel, device = device, language = shot.language, settle = ::settle, scene = scene, captureFrame = ::captureFrame)
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
            (host.koin.get<Metronome>() as SilentMetronome).beat(beat)
            settle(FRAME_MILLIS * 3)
        }
        output.parentFile.mkdirs()
        output.writeBytes(checkNotNull(scene.render(time).encodeToData(EncodedImageFormat.PNG)).bytes)
    } finally {
        scene.close()
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

private const val SETTLE_MILLIS = 1_500L
private const val STABLE_INTERVAL_MILLIS = 750L
private const val STABLE_CHECKS = 20
private const val LAUNCH_TIMEOUT_MILLIS = 30_000L
private const val RECORDING_FRAME_NANOS = 1_000_000_000L / 60
private const val JPEG_QUALITY = 95
