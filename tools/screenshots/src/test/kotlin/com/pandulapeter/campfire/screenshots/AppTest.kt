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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.presentation.localization.AppLocale
import com.pandulapeter.campfire.presentation.localization.LocalizedStrings
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.platform.ImpersonatedPlatform
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import java.io.File

/**
 * A window the app is tested in, drawn at a density of one as an Android device, so that what is asserted about its
 * size is the dp the app decides by. No system bars are drawn over it.
 */
internal class TestWindow(val width: Dp, val height: Dp) {
    companion object {
        /** The smallest phone the app supports. */
        val PHONE = TestWindow(width = 360.dp, height = 640.dp)
    }
}

/**
 * Runs [test] against the whole app - its real view model, use cases, repositories and files - started in [window]
 * over a library that holds the demo songs and the [songs] of the test resources' `songs` folder, with the launch
 * screen gone. The file picker hands over [pickedFiles] whenever the app asks for files.
 *
 * Only the network is kept out: sync is `Fakes.kt`'s, covers are switched off, and nothing else reaches outside unless
 * a test opens a lookup. The language is English and the current version's What's new counts as seen.
 */
@OptIn(ExperimentalTestApi::class)
internal fun runAppTest(
    window: TestWindow = TestWindow.PHONE,
    songs: List<String> = emptyList(),
    pickedFiles: List<ImportedFile> = emptyList(),
    test: suspend AppTestScope.() -> Unit,
) = withCampfireApp(
    platform = ImpersonatedPlatform.ANDROID,
    chrome = SystemChrome.None,
    density = 1f,
    fontFamily = null,
    monospaceFontFamily = null,
    writeData = { dataDirectory -> writeLibrary(dataDirectory, songs) },
) { host ->
    runSkikoComposeUiTest(size = Size(window.width.value, window.height.value), density = Density(1f)) {
        var isAppReady by mutableStateOf(false)
        setContent {
            host.Content(
                filePicker = TestFilePicker(pickedFiles),
                isPrepared = true,
                onViewModel = {},
                onAppReady = { isAppReady = true },
                overlay = {},
            )
        }
        waitUntil(timeoutMillis = LAUNCH_TIMEOUT_MILLIS) { isAppReady }
        AppTestScope(test = this, library = File(host.dataDirectory, "library")).test()
    }
}

/** What a test drives the app with: the UI test's finders and actions, and the library folder the app writes to. */
@OptIn(ExperimentalTestApi::class)
internal class AppTestScope(test: ComposeUiTest, val library: File) : ComposeUiTest by test {

    val songsFolder get() = File(library, "songs")

    /** The names of the library's song files, as they are on disk. */
    fun songFileNames() = songsFolder.list().orEmpty().filter { it.endsWith(".cho") }.toSet()

    /** The text of [resource] as the app shows it in English. */
    fun string(resource: StringResource, vararg arguments: Any) = if (arguments.isEmpty()) {
        LocalizedStrings.get(resource, AppLocale.DEFAULT)
    } else {
        LocalizedStrings.getFormatted(resource, *arguments, locale = AppLocale.DEFAULT)
    }

    /** The text of [resource] for [count] as the app shows it in English, the count filling its first argument. */
    fun pluralString(resource: PluralStringResource, count: Int) = LocalizedStrings.getPlural(resource, count, count, locale = AppLocale.DEFAULT)

    /** Waits for the app to write [condition] true of the library, which it does off the main thread. */
    fun waitForLibrary(condition: () -> Boolean) = waitUntil(timeoutMillis = WRITE_TIMEOUT_MILLIS, condition = condition)
}

/** The text of the test resource at [path]. */
internal fun resourceText(path: String) = checkNotNull(AppTestScope::class.java.getResource("/$path")) { "There is no test resource $path." }.readText()

/** The demo library's song at [name], as the app ships it. */
internal fun demoSongText(name: String) = runBlocking { Res.readBytes("files/demo/$name").decodeToString() }

internal const val HOUSE_OF_THE_RISING_SUN = "traditional_american-house_of_the_rising_sun.cho"
internal const val HOME_ON_THE_RANGE = "traditional_american-home_on_the_range_my_western_home.cho"

/**
 * Fills the data directory of a fresh install with what a test starts from. A preferences document is written, so the
 * start is not a first run (no onboarding, no demo planted on its own), and the demo songs are written as files instead.
 */
private fun writeLibrary(dataDirectory: File, songs: List<String>) {
    val songsFolder = File(dataDirectory, "library/songs").apply { mkdirs() }
    listOf(HOUSE_OF_THE_RISING_SUN, HOME_ON_THE_RANGE).forEach { File(songsFolder, it).writeText(demoSongText(it)) }
    songs.forEach { File(songsFolder, it).writeText(resourceText("songs/$it")) }
    val preferences = buildJsonObject {
        put("language", "en")
        // Covers are downloaded from the address a song names, the demo songs' included.
        put("isCoverArtEnabled", false)
        putJsonArray("seenWhatsNewVersions") { add(JsonPrimitive(System.getProperty("campfire.screenshots.versionName"))) }
    }
    File(dataDirectory, "preferences").mkdirs()
    File(dataDirectory, "preferences/preferences.json").writeText(preferences.toString())
}

/** A file picker that picks [files] every time it is asked, and never saves or shares. */
private class TestFilePicker(private val files: List<ImportedFile>) : FilePicker {
    override val canShare = false

    override suspend fun pickFiles() = files

    override suspend fun saveFile(file: ExportedFile) = false

    override suspend fun shareFile(file: ExportedFile) = false
}

private const val LAUNCH_TIMEOUT_MILLIS = 30_000L
private const val WRITE_TIMEOUT_MILLIS = 10_000L
