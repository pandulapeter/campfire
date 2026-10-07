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

import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import java.io.File
import kotlin.system.exitProcess

/**
 * Renders the store screenshots - every [Shot] on every [Device] - and the README's [banners], into a folder per
 * Screenshot Bro row.
 *
 * `./gradlew :tools:screenshots:run` writes them to `tools/screenshots/renders` (gitignored), replacing what an earlier
 * run left there; `--args="--out <folder>"` writes them elsewhere, and `--only <names>` (comma separated, each a device's enum name, a
 * shot's id, or `listing`, `banners` or `website`) renders part of them. The paths the run needs are passed in by the build as
 * system properties.
 */
fun main(args: Array<String>) {
    val options = args.toList().windowed(size = 2, step = 2, partialWindows = false).associate { (key, value) -> key to value }
    val output = options["--out"]?.let(::File) ?: File(requireProperty("campfire.screenshots.renders")).apply { deleteRecursively() }
    val only = options["--only"]?.split(',')?.map(String::trim)?.toSet()
    val library = File(requireProperty("campfire.screenshots.library"))
    require(File(library, "library/songs").isDirectory) {
        "There is no library to take the screenshots of in $library: copy a Campfire data folder's library, covers and preferences there."
    }
    val environment = RenderEnvironment(
        library = library,
        fonts = Fonts(File(requireProperty("campfire.screenshots.fonts"))),
        appIcon = System.getProperty("campfire.screenshots.appIcon")?.let(::File)?.takeIf(File::isFile)
            ?.let { Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap() },
        versionName = requireProperty("campfire.screenshots.versionName"),
    )
    // A banner's files are numbered by the place of their device in it, which is the order they are imported in.
    val listing = Device.entries.filter { it.isInListing }.flatMap { device -> shots.map { shot -> Job("listing", device.folder, shot.id, device, shot) } }
    val bannerJobs = banners.flatMap { banner ->
        banner.slots.mapIndexed { index, (device, shot) ->
            Job("banners", banner.folder, "%02d-%s-%s".format(index + 1, shot.id, device.name.lowercase()), device, shot)
        }
    }
    val websiteJobs = websiteShots.map { (name, device, shot) -> Job("website", "Website", name, device, shot) }
    val renders = (listing + bannerJobs + websiteJobs)
        .filter { only == null || it.set in only || it.device.name in only || it.shot.id in only }
    var failures = 0
    runBlocking(Dispatchers.Main) {
        renders.forEachIndexed { index, job ->
            val file = File(output, "${job.folder}/${job.name}.png")
            print("[${index + 1}/${renders.size}] ${job.folder} / ${job.name}… ")
            try {
                render(device = job.device, shot = job.shot, environment = environment, output = file)
                println("done")
            } catch (exception: Exception) {
                failures++
                println("failed: ${exception.message}")
                exception.printStackTrace()
            }
        }
    }
    println("${renders.size - failures} of ${renders.size} screenshots written to $output")
    // The app's singletons leave coroutines and the Swing event thread running, which would keep the process alive.
    exitProcess(if (failures == 0) 0 else 1)
}

/** One image of a run: which [set] it belongs to, where it is written, and the shot it takes on which device. */
private class Job(
    val set: String,
    val folder: String,
    val name: String,
    val device: Device,
    val shot: Shot,
)

private fun requireProperty(name: String) = requireNotNull(System.getProperty(name)) { "Run the tool through Gradle, which sets $name." }
