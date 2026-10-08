/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.buildLogic.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

/**
 * Fills in the build manifest of the web distribution and precompresses it if asked to. What it writes and why is said
 * where `app/web/build.gradle.kts` registers it. It edits the directory `wasmJsBrowserDistribution` wrote, so its
 * state is not tracked: it runs whenever that task does, and running it twice over one distribution changes nothing.
 */
abstract class FinishWebDistribution : DefaultTask() {

    @get:Internal
    abstract val distributionDirectory: DirectoryProperty

    @get:InputFile
    abstract val pageTemplate: RegularFileProperty

    @get:Input
    abstract val precompress: Property<Boolean>

    @get:Input
    abstract val placeholder: Property<String>

    @get:Input
    abstract val transportSuffixes: SetProperty<String>

    @get:Input
    abstract val compressibleSuffixes: SetProperty<String>

    @get:Input
    abstract val unversionedFiles: SetProperty<String>

    init {
        doNotTrackState("Rewrites the distribution in place")
    }

    @TaskAction
    fun finish() {
        val directory = distributionDirectory.get().asFile
        val page = "index.html"
        val token = placeholder.get()
        val template = pageTemplate.get().asFile.readText()
        check(template.contains(token)) { "$page has no $token for the build manifest" }

        val transport = transportSuffixes.get()
        val unversioned = unversionedFiles.get()
        val deployed = directory.walkTopDown()
            .filter { it.isFile && transport.none(it.name::endsWith) }
            .toList()
        val versioned = deployed.map { it.relativeTo(directory).invariantSeparatorsPath to it }
            .filter { (path, _) -> path !in unversioned }
            .sortedBy { (path, _) -> path }
        val manifest = webBuildManifest(versioned.map { (path, file) -> path to file.readBytes() }, template)
        val finishedPage = template.replace(token, manifest.pageManifest)
        File(directory, page).writeText(finishedPage)
        File(directory, "build.json").writeText(manifest.buildJson(finishedPage.toByteArray().sha256()))
        logger.lifecycle(
            "Web distribution: build ${manifest.id}, ${deployed.size} files, ${versioned.size} of them versioned, " +
                "${versioned.sumOf { it.second.length() } / 1024} KiB",
        )

        if (precompress.get()) {
            val compressible = compressibleSuffixes.get()
            var isBrotliMissing = false
            directory.walkTopDown()
                .filter { file -> file.isFile && compressible.any(file.name::endsWith) }
                .forEach { file ->
                    file.gzipTo(File(file.parentFile, "${file.name}.gz"))
                    isBrotliMissing = isBrotliMissing || !file.brotliTo(File(file.parentFile, "${file.name}.br"))
                }
            if (isBrotliMissing) {
                logger.lifecycle("Web distribution: no brotli on the path, so only the gzipped copies were written.")
            }
        }
    }

    /** Deflate at the highest level: this runs once per deployment and the result is served for months. */
    private fun File.gzipTo(target: File) = target.outputStream().use { output ->
        object : GZIPOutputStream(output) {
            init {
                def.setLevel(Deflater.BEST_COMPRESSION)
            }
        }.use { compressed -> inputStream().use { it.copyTo(compressed as OutputStream) } }
    }

    /**
     * The same, through the brotli command line tool if it happens to be installed. Nothing about the
     * distribution depends on it, so a machine without it simply writes one fewer copy.
     */
    private fun File.brotliTo(target: File) = try {
        ProcessBuilder("brotli", "--quality=11", "--force", "--output=${target.absolutePath}", absolutePath)
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0
    } catch (_: IOException) {
        false
    }
}
