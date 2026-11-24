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

import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.AppLanguage
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.ImpersonatedPlatform
import com.pandulapeter.campfire.presentation.ui.state.setlists.SetlistWithSongs
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** How the transitions suite runs: `--runs`, `--hz`, `--width`, `--height`, `--density`, and `--csv`. */
internal class TransitionOptions(
    val runs: Int,
    val hz: Int,
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val csv: File?,
)

/** One frame after a trigger: what the frame cost, and by how much the pause after it overran what it asked for. */
internal class FrameRecord(val timing: FrameTiming, val betweenNanos: Long)

/**
 * Times every frame of the app's transitions, pass after pass, in one start of the app over the tool's committed
 * library, drawn as the Windows app's window ([PhasedScene]): opening and closing song details, switching tabs, and
 * opening and closing the song filters sheet. Each scenario starts from the settled songs list, is triggered through
 * the view model, as a tap would be, and is followed for [FRAMES_AFTER_TRIGGER] frames. Pass 1 is the first time in the
 * process; the passes after it reopen the same songs, except `push:fresh`, which opens one not opened before each time.
 * Answers every scenario's frames, pass by pass, and whether the frames were split into their phases.
 */
internal suspend fun runTransitionSuite(options: TransitionOptions): TransitionResults {
    val library = File(System.getProperty("campfire.screenshots.library"))
    val results = linkedMapOf<String, MutableList<List<FrameRecord>>>()
    var isSplit = false
    withCampfireApp(
        platform = ImpersonatedPlatform.WINDOWS,
        chrome = SystemChrome.None,
        density = options.density,
        writeData = { prepareTransitionLibrary(source = library, target = it) },
    ) { host ->
        var viewModel: CampfireViewModel? = null
        val scene = PhasedScene(widthPx = options.widthPx, heightPx = options.heightPx, density = options.density) {
            host.Content(
                filePicker = ScreenshotFilePicker(canShare = false),
                isPrepared = true,
                keyboardHeight = 0.dp,
                onViewModel = { viewModel = it },
                onAppReady = {},
            )
        }
        try {
            isSplit = scene.isSplit
            val clock = FrameClock(scene = scene, hz = options.hz)
            while (viewModel == null) clock.frame()
            val app = checkNotNull(viewModel)
            val deadline = System.nanoTime() + START_TIMEOUT_NANOS
            while (app.libraryState.isLoading.value != false || app.songListState.songGroups.value.groups.isEmpty()) {
                clock.frame()
                check(System.nanoTime() < deadline) { "The library was not listed within a minute." }
            }
            clock.frames(START_SETTLE_FRAMES)
            val songs = app.libraryState.songsByFileName.value
            fun song(fileName: String) = checkNotNull(songs[fileName]) { "The library has no song called $fileName." }
            val setlist = setlistWithSongs(app, fileName = SETLIST)
            val setlistSong = setlist.songs[setlist.songs.size / 2]
            val fresh = freshSongs(library = library, count = options.runs, excluded = SONGS.values + setlist.songs.map { it.fileName })
                .map(::song)
            repeat(options.runs) { pass ->
                print("Pass ${pass + 1} of ${options.runs}… ")
                suspend fun measure(name: String, trigger: () -> Unit) {
                    trigger()
                    results.getOrPut(name) { mutableListOf() }.add(clock.frames(FRAMES_AFTER_TRIGGER))
                }
                suspend fun pushAndPop(name: String, push: () -> Unit) {
                    measure("push:$name", push)
                    clock.frames(SETTLE_BEFORE_POP_FRAMES)
                    measure("pop:$name") { app.navigator.popBackStack() }
                    clock.frames(SETTLE_FRAMES)
                }
                SONGS.forEach { (name, fileName) -> pushAndPop(name) { app.navigator.openSong(song(fileName)) } }
                measure("tab:songs>setlists") { app.selectTopLevelDestination(CampfireDestination.Setlists) }
                clock.frames(SETTLE_FRAMES)
                measure("tab:setlists>songs") { app.selectTopLevelDestination(CampfireDestination.Songs) }
                clock.frames(SETTLE_FRAMES)
                measure("sheet:open") { app.dialogHost.showDialog(DialogType.SongFilters) }
                clock.frames(SETTLE_FRAMES)
                measure("sheet:close") { app.dialogHost.dismissDialog() }
                clock.frames(SETTLE_FRAMES)
                pushAndPop("setlist") { app.navigator.openSongInSetlist(setlist, setlistSong) }
                pushAndPop("fresh") { app.navigator.openSong(fresh[pass]) }
                println("done")
            }
        } finally {
            scene.close()
        }
    }
    return TransitionResults(scenarios = results, isSplit = isSplit)
}

/** Every scenario's frames after its trigger, pass by pass, in the order they ran. */
internal class TransitionResults(val scenarios: Map<String, List<List<FrameRecord>>>, val isSplit: Boolean)

/**
 * Steps [scene]'s clock by a frame of a [hz] display and then lets the app's own coroutines on the main thread run for
 * as long, as they would between two frames of a window. How much later than asked that pause ends is what they held
 * the thread for; it is wall clock rather than the thread's CPU time, which Windows counts in ticks of ~15.6 ms.
 */
private class FrameClock(private val scene: PhasedScene, hz: Int) {
    private val stepNanos = 1_000_000_000L / hz
    private val pauseMillis = 1000L / hz
    private var time = 0L

    suspend fun frame(): FrameRecord {
        time += stepNanos
        val timing = scene.frame(time)
        val paused = System.nanoTime()
        delay(pauseMillis)
        return FrameRecord(timing = timing, betweenNanos = (System.nanoTime() - paused - pauseMillis * 1_000_000).coerceAtLeast(0))
    }

    suspend fun frames(count: Int) = List(count) { frame() }
}

/**
 * The tool's library copied into [target] as `Render.kt` copies it, with the preferences of a device that has been used
 * before: English, this version's What's new seen, and the reading questions answered, so no dialog covers a screen.
 */
private fun prepareTransitionLibrary(source: File, target: File) {
    listOf("library", "covers").forEach { name -> File(source, name).takeIf { it.isDirectory }?.copyRecursively(File(target, name)) }
    val versionName = System.getProperty("campfire.screenshots.versionName")
    val stored = Json.parseToJsonElement(File(source, "preferences/preferences.json").readText()).jsonObject
    val seenVersions = (stored["seenWhatsNewVersions"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
    val patched = JsonObject(
        stored + mapOf(
            "language" to JsonPrimitive(AppLanguage.ENGLISH.id),
            "seenWhatsNewVersions" to JsonArray((seenVersions + versionName).distinct().map(::JsonPrimitive)),
            "isSongLayoutChosen" to JsonPrimitive(true),
            "isChordNamesChosen" to JsonPrimitive(true),
        ),
    )
    File(target, "preferences").mkdirs()
    File(target, "preferences/preferences.json").writeText(patched.toString())
}

/** The setlist [fileName] with its songs, as its screen hands it to song details. */
private fun setlistWithSongs(viewModel: CampfireViewModel, fileName: String): SetlistWithSongs {
    val setlist = checkNotNull(viewModel.libraryState.setlists.value.firstOrNull { it.fileName == fileName }) { "The library has no setlist called $fileName." }
    val songs = viewModel.libraryState.songsByFileName.value
    return SetlistWithSongs(
        setlist = setlist,
        entries = setlist.entries.mapIndexed { index, entry ->
            songs[entry.songFileName]?.let { SetlistWithSongs.Entry.Present(index = index, song = it) }
                ?: SetlistWithSongs.Entry.Missing(index = index, songFileName = entry.songFileName)
        },
    )
}

/**
 * The library's [count] longest songs, by the size of their files, that no other scenario opens, one for each pass of
 * `push:fresh`: a first open of a long song with the code already warm, which a cache of what was built for a song
 * opened before cannot speed up.
 */
private fun freshSongs(library: File, count: Int, excluded: Collection<String>): List<String> {
    val candidates = File(library, "library/songs").listFiles { file -> file.name.endsWith(".cho") && file.name !in excluded }.orEmpty()
        .sortedWith(compareByDescending<File> { it.length() }.thenBy { it.name })
    check(candidates.size >= count) { "The library has only ${candidates.size} songs for push:fresh, fewer than $count passes." }
    return candidates.take(count).map { it.name }
}

/** The songs opened and closed every pass, by the name their scenarios carry. */
private val SONGS = linkedMapOf(
    "plain" to "ed_sheeran-thinking_out_loud.cho",
    "long" to "tina_turner-proud_mary.cho",
    "tabs" to "traditional_american-house_of_the_rising_sun.cho",
    "abc" to "john_newton-amazing_grace.cho",
)
private const val SETLIST = "friday_night_by_the_lake.setlist.json"
internal const val FRAMES_AFTER_TRIGGER = 50
private const val SETTLE_FRAMES = 40

/** Long enough for a song's chord diagrams and notation to arrive before it is closed. */
private const val SETTLE_BEFORE_POP_FRAMES = 60
private const val START_SETTLE_FRAMES = 120
private const val START_TIMEOUT_NANOS = 60_000_000_000L
