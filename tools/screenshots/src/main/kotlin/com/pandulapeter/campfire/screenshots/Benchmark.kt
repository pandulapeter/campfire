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
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.model.preferences.AppLanguage
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import kotlin.system.exitProcess

/**
 * Times what a user waits for in a large library, through the whole app drawn offscreen as the Mac app's window: the
 * start until the songs list shows its first song, a long song's details until its first line is drawn, a PDF of sixty
 * songs from opening the export screen until the file is handed to the picker, and a zip of the whole library imported
 * into an empty one, until the review is up and until every song is listed. Each is taken once in a fresh process
 * (cold, what a launch feels like) and then [runs] more times with the code warmed up, every one in an app started anew
 * on a library of its own.
 *
 * `./gradlew :tools:screenshots:benchmark`, with `--args="--runs <n> --songs <n>"`. The numbers are this machine's,
 * and only comparable with runs on it: the table is printed and nothing is written.
 */
fun main(args: Array<String>) {
    val options = args.toList().windowed(size = 2, step = 2).associate { (key, value) -> key to value }
    val runs = options["--runs"]?.toInt() ?: DEFAULT_RUNS
    val songCount = options["--songs"]?.toInt() ?: DEFAULT_SONGS
    val library = BenchmarkLibrary(songCount = songCount, seed = SEED)
    val zip = ImportedFile(name = "benchmark.zip", bytes = library.zip())
    val results = linkedMapOf<String, MutableList<Long>>()
    var residentSet: Long? = null
    fun record(name: String, millis: Long) = results.getOrPut(name) { mutableListOf() }.add(millis)
    try {
        runBlocking(Dispatchers.Main) {
            repeat(runs + 1) { run ->
                print("Run ${run + 1} of ${runs + 1}… ")
                withBenchmarkApp(writeData = { library.writeInto(it) }) { app, started ->
                    val firstTitle = checkNotNull(app.awaitSongList(count = library.songs.size))
                    app.awaitFrame { app.scope.centerOf(firstTitle) != null }
                    record("Start until $songCount songs are listed", millis(since = started))
                    // Only the first start's: a JVM keeps the heap later starts grew, which a fresh app would not have.
                    if (run == 0) residentSet = residentSetBytes()
                    val longSong = app.scope.song(BenchmarkLibrary.LONG_SONG)
                    val opened = System.nanoTime()
                    app.viewModel.navigator.openSong(longSong)
                    app.awaitFrame { app.scope.centersOf(BenchmarkLibrary.LONG_SONG_FIRST_LINE).isNotEmpty() }
                    record("Long song opened until drawn", millis(since = opened))
                    app.viewModel.navigator.popBackStack()
                    app.scope.settle()
                    val songs = app.viewModel.libraryState.allSongs.value.take(PDF_SONGS)
                    val exported = System.nanoTime()
                    app.viewModel.dialogHost.showDialog(DialogType.Export(songs = songs, format = PrintSettings.Format.PDF))
                    app.awaitFrame { app.scope.centerOf(app.scope.string("print_save")) != null }
                    app.scope.tap(app.scope.string("print_save"))
                    app.awaitFrame { app.filePicker.savedAt != null }
                    record("PDF of $PDF_SONGS songs, export opened until saved", (checkNotNull(app.filePicker.savedAt) - exported) / 1_000_000)
                }
                withBenchmarkApp(writeData = {}) { app, _ ->
                    app.awaitSongList(count = 0)
                    val imported = System.nanoTime()
                    app.viewModel.importController.importFiles(listOf(zip))
                    app.awaitFrame(timeoutMillis = IMPORT_TIMEOUT_MILLIS) { app.viewModel.importReportState.report is ImportReport.Review }
                    record("Import of $songCount songs, until the review", millis(since = imported))
                    app.viewModel.importController.confirmImport()
                    app.awaitFrame(timeoutMillis = IMPORT_TIMEOUT_MILLIS) {
                        !app.viewModel.importController.isImporting.value && app.viewModel.libraryState.allSongs.value.size == library.songs.size
                    }
                    record("Import of $songCount songs, until listed", millis(since = imported))
                }
                println("done")
            }
        }
    } catch (throwable: Throwable) {
        // The Swing event thread and the app's singletons would keep the process alive past a failure.
        throwable.printStackTrace()
        exitProcess(1)
    }
    printTable(results = results, residentSet = residentSet, peakHeap = peakHeapBytes())
    exitProcess(0)
}

/** One start of the app the benchmark drives: what [ShotScope] finds on screen with, and a frame drawn at a time. */
private class BenchmarkApp(
    val viewModel: CampfireViewModel,
    val scope: ShotScope,
    val filePicker: BenchmarkFilePicker,
    private val frame: suspend () -> Unit,
) {

    /** Draws frames until [predicate] holds after one, which is that frame being the first to show what it asks for. */
    suspend fun awaitFrame(timeoutMillis: Long = TIMEOUT_MILLIS, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (true) {
            frame()
            if (predicate()) return
            check(System.nanoTime() < deadline) { "The app did not get there within ${timeoutMillis / 1000} seconds." }
        }
    }

    /**
     * Waits for the library to be read with [count] songs and the songs list worked out from it, which happens off the
     * main thread after, and answers the title the list starts with.
     */
    suspend fun awaitSongList(count: Int): String? {
        awaitFrame {
            viewModel.libraryState.isLoading.value == false && viewModel.libraryState.allSongs.value.size == count &&
                (count == 0 || viewModel.songListState.songGroups.value.groups.isNotEmpty())
        }
        return viewModel.songListState.songGroups.value.groups.firstOrNull()?.songs?.firstOrNull()?.title
    }
}

/**
 * Starts the app as [withCampfireApp] does, on a data directory [writeData] fills, composed at once, and runs [block]
 * with the moment the files were in place: what a launch from the dock waits through starts there.
 */
private suspend fun withBenchmarkApp(writeData: (File) -> Unit, block: suspend (BenchmarkApp, started: Long) -> Unit) {
    var started = 0L
    val device = Device.LAPTOP
    withCampfireApp(
        platform = device.platform,
        chrome = device.chrome,
        density = device.density,
        writeData = { dataDirectory ->
            writeData(dataDirectory)
            writePreferences(dataDirectory)
            started = System.nanoTime()
        },
    ) { host ->
        var viewModel: CampfireViewModel? = null
        val filePicker = BenchmarkFilePicker()
        val screen = DeviceScreen(
            device = device,
            app = {
                host.Content(filePicker = filePicker, isPrepared = true, keyboardHeight = 0.dp, onViewModel = { viewModel = it }, onAppReady = {})
            },
            chrome = {},
        )
        try {
            var time = 0L
            // A frame of a display's sixtieth of a second, and the pause the app's own coroutines on the main thread
            // run in, as they would between two frames of a window.
            suspend fun frame() {
                time += FRAME_MILLIS * 1_000_000
                screen.advance(time)
                delay(FRAME_MILLIS)
            }
            while (viewModel == null) frame()
            val scope = ShotScope(
                viewModel = checkNotNull(viewModel),
                device = device,
                language = AppLanguage.ENGLISH,
                settle = { milliseconds -> repeat((milliseconds / FRAME_MILLIS).toInt().coerceAtLeast(1)) { frame() } },
                scene = screen.window,
                windowTop = screen.windowTop.toFloat(),
                captureFrame = {},
                keyboard = OnScreenKeyboard(),
            )
            block(BenchmarkApp(viewModel = checkNotNull(viewModel), scope = scope, filePicker = filePicker, frame = ::frame), started)
        } finally {
            screen.close()
        }
    }
}

/** The preferences of a device that has been used before: no onboarding, no demo library, no question dialogs. */
private fun writePreferences(dataDirectory: File) {
    val preferences = buildJsonObject {
        put("language", AppLanguage.ENGLISH.id)
        put("isCoverArtEnabled", false)
        putJsonArray("seenWhatsNewVersions") { add(JsonPrimitive(System.getProperty("campfire.screenshots.versionName"))) }
        put("isSongLayoutChosen", true)
        put("isChordNamesChosen", true)
    }
    File(dataDirectory, "preferences").mkdirs()
    File(dataDirectory, "preferences/preferences.json").writeText(preferences.toString())
}

/** A file picker that saves nothing, and remembers when it was handed a file. */
private class BenchmarkFilePicker : FilePicker {
    var savedAt: Long? = null

    override val canShare = false

    override suspend fun pickFiles() = emptyList<ImportedFile>()

    override suspend fun saveFile(file: ExportedFile): Boolean {
        savedAt = System.nanoTime()
        return true
    }

    override suspend fun shareFile(file: ExportedFile) = false
}

private fun printTable(results: Map<String, List<Long>>, residentSet: Long?, peakHeap: Long) {
    val width = results.keys.maxOf { it.length }
    println()
    println("%-${width}s  %8s  %8s  %15s".format("Measure", "Cold", "Median", "Warm range"))
    results.forEach { (name, millis) ->
        val warm = millis.drop(1).sorted()
        val median = warm.getOrNull(warm.size / 2)
        println(
            "%-${width}s  %8s  %8s  %15s".format(
                name,
                millis.first().seconds(),
                median?.seconds() ?: "-",
                if (warm.isEmpty()) "-" else "${warm.first().seconds()}–${warm.last().seconds()}",
            ),
        )
    }
    println()
    println("Resident set with the library listed (cold): ${residentSet?.let { "${it / MEGABYTE} MB" } ?: "unknown"}")
    println("Peak heap over the whole run: ${peakHeap / MEGABYTE} MB")
    println("Timings are this machine's, under whatever else it was doing.")
}

private fun Long.seconds() = "%.2f s".format(this / 1000.0)

private fun millis(since: Long) = (System.nanoTime() - since) / 1_000_000

/** This process's resident set as `ps` reports it, which the JVM does not; null where there is no `ps`. */
private fun residentSetBytes() = runCatching {
    ProcessBuilder("ps", "-o", "rss=", "-p", ProcessHandle.current().pid().toString()).start().inputStream.bufferedReader().readText().trim().toLong() * 1024
}.getOrNull()

private fun peakHeapBytes() = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }.sumOf { it.peakUsage.used }

private const val SEED = 2017
private const val DEFAULT_RUNS = 3
private const val DEFAULT_SONGS = 3000
private const val PDF_SONGS = 60
private const val TIMEOUT_MILLIS = 60_000L
private const val IMPORT_TIMEOUT_MILLIS = 180_000L
private const val MEGABYTE = 1024 * 1024
