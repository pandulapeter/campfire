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
import com.pandulapeter.campfire.data.model.preferences.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import java.io.File
import kotlin.system.exitProcess

/**
 * Renders the store screenshots - every [Shot] on every [Device] - and the [banners] the composed images show, into a
 * folder per device or banner.
 *
 * `./gradlew :tools:screenshots:run` writes them to `tools/screenshots/renders` (gitignored), replacing what an earlier
 * run left there; `--args="--out <folder>"` writes them elsewhere, and `--only <names>` (comma separated, each a device's enum name, a
 * shot's id, or `listing`, `store-banners`, `website`, `readme-banners` or `promo`) renders part of them. `--shard <index>/<count>` renders every
 * count-th image from the index-th (to split a run across machines), and `--parallel <processes>` splits what is left
 * across that many processes of the tool ([renderInParallel]), by default half the processors and at most four, as each
 * takes about 1.6 GB. `--frame store` renders nothing: it frames the renders (from `--renders <folder>`, by default the
 * renders folder) into the store images in `~/Desktop/Campfire store images <version>` (or `--out <folder>`), the
 * listing ([frameListing]) and the stores' images laid out by hand ([frameCompositions]); `--frame readme` composes the
 * README's banners into `documentation/screenshots` and the website's link preview into `~/Desktop/Campfire website
 * images`. The paths the run needs are passed in by the build as system properties.
 */
fun main(args: Array<String>) {
    val options = args.toList().windowed(size = 2, step = 2, partialWindows = false).associate { (key, value) -> key to value }
    options["--frame"]?.let { set ->
        require(set == "store" || set == "readme") { "--frame takes store or readme, not $set." }
        val isForStores = set == "store"
        val renders = options["--renders"]?.let(::File) ?: File(requireProperty("campfire.screenshots.renders"))
        val output = options["--out"]?.let(::File) ?: File(
            System.getProperty("user.home"),
            if (isForStores) "Desktop/Campfire store images ${requireProperty("campfire.screenshots.versionName")}" else "Desktop/Campfire website images",
        )
        // The store images replace the folder, so that a shot taken out of the listing leaves no image behind.
        if (isForStores) output.deleteRecursively()
        val texts = File(requireProperty("campfire.screenshots.screenshotTexts"))
        val readme = File(requireProperty("campfire.screenshots.readme"))
        val missing = runBlocking(Dispatchers.Main) {
            val listing = if (isForStores) frameListing(renders = renders, texts = texts, output = output) else 0
            listing + frameCompositions(renders = renders, texts = texts, output = output, readme = readme, isForStores = isForStores)
        }
        println("Framed into $output")
        exitProcess(if (missing == 0) 0 else 1)
    }
    val output = options["--out"]?.let(::File) ?: File(requireProperty("campfire.screenshots.renders")).apply { deleteRecursively() }
    val only = options["--only"]?.split(',')?.map(String::trim)?.toSet()
    val shard = options["--shard"]?.let(Shard::parse) ?: Shard(index = 0, count = 1)
    val processes = options["--parallel"]?.toInt() ?: (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, MAX_DEFAULT_PROCESSES)
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
    // The listing, and the banners the stores show in every language, are taken in every language of the app, each in a
    // folder of its own, which the framing reads.
    val languages = AppLanguage.entries.filter { it != AppLanguage.SYSTEM_DEFAULT }
    val listing = Device.entries.filter { it.isInListing }.flatMap { device ->
        languages.flatMap { language ->
            shots.map { shot -> Job("listing", "${device.folder}/${language.id}", shot.id, device, shot.inTheme(shot.uiMode, language)) }
        }
    }
    val bannerJobs = banners.flatMap { banner ->
        (if (banner.isForStores) languages else listOf(null)).flatMap { language ->
            banner.slots.mapIndexed { index, (device, shot) ->
                val folder = if (language == null) banner.folder else "${banner.folder}/${language.id}"
                Job(
                    set = if (banner.isForStores) "store-banners" else "readme-banners",
                    folder = folder,
                    name = banner.fileName(index),
                    device = device,
                    shot = if (language == null) shot else shot.inTheme(shot.uiMode, language),
                )
            }
        }
    }
    val websiteJobs = websiteShots.map { (name, device, shot) -> Job("website", "Website", name, device, shot) }
    val promoJobs = promoShots.map { Job("promo", "Promo", it.name, it.device, it.shot, isClip = it.isClip) }
    // The promotional video's footage is a minute of frames, so a run renders it only when its set is asked for by name,
    // never along with its device.
    val byNameOnly = setOf("promo")
    val renders = (listing + bannerJobs + websiteJobs + promoJobs)
        .filter { if (only == null) it.set !in byNameOnly else it.set in only || (it.set !in byNameOnly && (it.device.name in only || it.shot.id in only)) }
        .filterIndexed { index, _ -> index % shard.count == shard.index }
    if (processes > 1 && renders.size > 1) {
        exitProcess(renderInParallel(options = options, output = output, shard = shard, processes = processes.coerceAtMost(renders.size)))
    }
    var failures = 0
    try {
        runBlocking(Dispatchers.Main) {
            renders.forEachIndexed { index, job ->
                val file = File(output, "${job.folder}/${job.name}.png")
                print("[${index + 1}/${renders.size}] ${job.folder} / ${job.name}… ")
                try {
                    render(
                        device = job.device,
                        shot = job.shot,
                        environment = environment,
                        output = file,
                        frames = if (job.isClip) File(output, "${job.folder}/${job.name}") else null,
                    )
                    println("done")
                } catch (exception: Exception) {
                    failures++
                    println("failed: ${exception.message}")
                    exception.printStackTrace()
                }
            }
        }
    } catch (error: Throwable) {
        // An Error (a class the build rewrote under the run, memory running out) ends the run here: the Swing event thread
        // would keep the process alive past it, and a parallel run's parent waits for every part to exit.
        error.printStackTrace()
        exitProcess(1)
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
    val isClip: Boolean = false,
)

private const val MAX_DEFAULT_PROCESSES = 4

private fun requireProperty(name: String) = requireNotNull(System.getProperty(name)) { "Run the tool through Gradle, which sets $name." }
