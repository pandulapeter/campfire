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

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.InternalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.model.preferences.AppLanguage
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
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
import org.koin.core.module.Module
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A window the app is tested in, drawn at a density of one as an Android device, so that what is asserted about its
 * size is the dp the app decides by. No system bars are drawn over it.
 */
internal class TestWindow(val width: Dp, val height: Dp) {
    companion object {
        /** The smallest phone the app supports. */
        val PHONE = TestWindow(width = 360.dp, height = 640.dp)

        /** The smallest phone held on its side. */
        val PHONE_ON_ITS_SIDE = TestWindow(width = 640.dp, height = 360.dp)
    }
}

/**
 * How the app is drawn for a test: in [language], in the dark theme or the light one, with the system's text size at
 * [textScale], and laid out in [layoutDirection] where one is given instead of the language's own.
 */
internal data class TestAppearance(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val isDark: Boolean = false,
    val textScale: Float = 1f,
    val layoutDirection: LayoutDirection? = null,
)

/**
 * Runs [test] against the whole app - its real view model, use cases, repositories and files - started in [window]
 * over a library that holds the demo songs, the [songs] of the test resources' `songs` folder, and the [setlists] of its
 * `setlists` folder, with the launch
 * screen gone and the song details screen's text at [fontScale], its songs laid out as [songLayout]. The file picker hands over [pickedFiles] whenever the
 * app asks for files.
 *
 * Only the network is kept out: sync is `Fakes.kt`'s, the cover search finds nothing (`OfflineCoverArtRepository`),
 * covers are switched off unless [isCoverArtEnabled], and nothing else reaches outside unless a test opens a song
 * details lookup. The current version's What's new counts as seen.
 *
 * [preferenceFiles] are written into the data directory's `preferences` folder by name, as a device that has run the
 * app before would hold them (the shared links' mirror). With [isSyncConnected] false, sync shows no account instead of
 * `Fakes.kt`'s.
 *
 * With [isFirstRun] the data directory starts empty instead, as a fresh install's does: no preferences, so the
 * onboarding opens and the demo library is planted, and the language is the system's, which the test pins to
 * [appearance]'s language.
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, InternalTestApi::class)
internal fun runAppTest(
    window: TestWindow = TestWindow.PHONE,
    songs: List<String> = emptyList(),
    setlists: List<String> = emptyList(),
    fontScale: Float = UserPreferences.DEFAULT_FONT_SCALE,
    songLayout: SongLayout = SongLayout.PAGES,
    pickedFiles: List<ImportedFile> = emptyList(),
    appearance: TestAppearance = TestAppearance(),
    isCoverArtEnabled: Boolean = false,
    isFirstRun: Boolean = false,
    isSyncConnected: Boolean = true,
    preferenceFiles: Map<String, String> = emptyMap(),
    test: suspend AppTestScope.() -> Unit,
) {
    if (isFirstRun) {
        require(
            songs.isEmpty() && setlists.isEmpty() && preferenceFiles.isEmpty() && !isCoverArtEnabled &&
                fontScale == UserPreferences.DEFAULT_FONT_SCALE && songLayout == SongLayout.PAGES,
        ) {
            "A first run starts from an empty data directory, which has no songs, setlists, or preferences to set."
        }
    }
    // Without a preferences document the app speaks the system's language, which the test JVM leaves to the machine.
    val systemLocale = Locale.getDefault()
    if (isFirstRun) Locale.setDefault(Locale.forLanguageTag(appearance.language.id))
    try {
        runApp(
            window = window,
            pickedFiles = pickedFiles,
            appearance = appearance,
            fakes = if (isSyncConnected) listOf(testFakes) else listOf(testFakes, disconnectedSyncFakes),
            writeData = { dataDirectory ->
                if (!isFirstRun) writeLibrary(dataDirectory, songs, setlists, fontScale, songLayout, appearance, isCoverArtEnabled)
                preferenceFiles.forEach { (name, text) -> File(dataDirectory, "preferences/" + name).writeText(text) }
            },
            test = test,
        )
    } finally {
        Locale.setDefault(systemLocale)
    }
}

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, InternalTestApi::class)
private fun runApp(
    window: TestWindow,
    pickedFiles: List<ImportedFile>,
    appearance: TestAppearance,
    fakes: List<Module>,
    writeData: (File) -> Unit,
    test: suspend AppTestScope.() -> Unit,
) = withCampfireApp(
    platform = ImpersonatedPlatform.ANDROID,
    chrome = SystemChrome.None,
    density = 1f,
    writeData = writeData,
) { host ->
    host.koin.loadModules(fakes, allowOverride = true)
    val keyboardHeight = mutableStateOf(0.dp)
    // The scene is given the keyboard as well as the app's composition: a dialog, every bottom sheet among them, is a
    // layer of the scene with a root of its own, and Modifier.imePadding reads the insets of that root, which the
    // scene hands it, rather than the ones Content provides.
    SkikoComposeUiTest(
        width = window.width.value.toInt(),
        height = window.height.value.toInt(),
        density = Density(1f, appearance.textScale),
        semanticsOwnerListener = null,
        windowInsets = KeyboardInsets(keyboardHeight),
        useStandardTestDispatcherForComposition = false,
    ).runTest {
        var isAppReady by mutableStateOf(false)
        val backInput = DirectNavigationEventInput()
        setContent {
            val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
            DisposableEffect(dispatcher) {
                dispatcher?.addInput(backInput)
                onDispose { dispatcher?.removeInput(backInput) }
            }
            host.Content(
                filePicker = TestFilePicker(pickedFiles),
                isPrepared = true,
                keyboardHeight = keyboardHeight.value,
                layoutDirection = appearance.layoutDirection,
                onViewModel = {},
                onAppReady = { isAppReady = true },
            )
        }
        waitUntil(timeoutMillis = LAUNCH_TIMEOUT_MILLIS) { isAppReady }
        AppTestScope(
            test = this,
            library = File(host.dataDirectory, "library"),
            locale = AppLocale.findByCode(appearance.language.id),
            keyboardHeight = keyboardHeight,
            backInput = backInput,
        ).test()
    }
}

/** A window with no system bars, whose keyboard is [keyboardHeight] tall, read as it is laid out (a dp is a pixel). */
@OptIn(InternalComposeUiApi::class)
private class KeyboardInsets(private val keyboardHeight: State<Dp>) : PlatformWindowInsets {
    override val ime get() = PlatformInsets(bottom = keyboardHeight.value.value.roundToInt())
}

/**
 * What a test drives the app with: the UI test's finders and actions, the library folder the app writes to, and the
 * on-screen keyboard, which is up while [keyboardHeight] is more than zero. The keyboard is the test's to raise: the
 * desktop has none, so focusing a text field opens nothing. Back is the test's to send too ([pressBack], [swipeBack]):
 * the scene has no window to turn Escape into one, which the desktop app's window does.
 */
@OptIn(ExperimentalTestApi::class)
internal class AppTestScope(
    private val test: SkikoComposeUiTest,
    val library: File,
    private val locale: AppLocale,
    keyboardHeight: MutableState<Dp>,
    private val backInput: DirectNavigationEventInput,
) : ComposeUiTest by test {

    var keyboardHeight by keyboardHeight

    /** A back as the desktop's Escape, Android's back button, and the browser's Back send it, at once and whole. */
    fun pressBack() {
        backInput.backCompleted()
        waitForIdle()
    }

    /** A predictive back gesture from the start edge, dragged halfway across and let go of. */
    fun swipeBack() {
        backInput.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT))
        waitForIdle()
        backInput.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.5f))
        waitForIdle()
        backInput.backCompleted()
        waitForIdle()
    }

    /** The whole window as it is drawn, dialogs and popups included, which a node's capture leaves out. */
    fun captureWindow() = test.captureToImage()

    val songsFolder get() = File(library, "songs")

    /** The names of the library's song files, as they are on disk. */
    fun songFileNames() = songsFolder.list().orEmpty().filter { it.endsWith(".cho") }.toSet()

    /** The text of [resource] as the app shows it in the test's language. */
    fun string(resource: StringResource, vararg arguments: Any) = if (arguments.isEmpty()) {
        LocalizedStrings.get(resource, locale)
    } else {
        LocalizedStrings.getFormatted(resource, *arguments, locale = locale)
    }

    /** The text of [resource] for [count] as the app shows it in the test's language, the count filling its first argument. */
    fun pluralString(resource: PluralStringResource, count: Int) = LocalizedStrings.getPlural(resource, count, count, locale = locale)

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
private fun writeLibrary(
    dataDirectory: File,
    songs: List<String>,
    setlists: List<String>,
    fontScale: Float,
    songLayout: SongLayout,
    appearance: TestAppearance,
    isCoverArtEnabled: Boolean,
) {
    val songsFolder = File(dataDirectory, "library/songs").apply { mkdirs() }
    listOf(HOUSE_OF_THE_RISING_SUN, HOME_ON_THE_RANGE).forEach { File(songsFolder, it).writeText(demoSongText(it)) }
    songs.forEach { File(songsFolder, it).writeText(resourceText("songs/$it")) }
    val setlistsFolder = File(dataDirectory, "library/setlists").apply { mkdirs() }
    setlists.forEach { File(setlistsFolder, it).writeText(resourceText("setlists/$it")) }
    val preferences = buildJsonObject {
        put("language", appearance.language.id)
        put("uiMode", if (appearance.isDark) UserPreferences.UiMode.DARK.id else UserPreferences.UiMode.LIGHT.id)
        put("isCoverArtEnabled", isCoverArtEnabled)
        put("fontScale", fontScale)
        putJsonArray("seenWhatsNewVersions") { add(JsonPrimitive(System.getProperty("campfire.screenshots.versionName"))) }
        put("songLayout", songLayout.id)
        put("isSongLayoutChosen", true)
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
