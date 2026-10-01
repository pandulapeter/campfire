/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    wasmJs {
        outputModuleName = "campfire"
        browser {
            commonWebpackConfig {
                outputFileName = "campfire.js"
            }
        }
        binaries.executable()
    }
    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":app:di"))
            implementation(project(":presentation"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}

// The development server keeps its source map, which is what makes the debugger show Kotlin. A
// distribution does not: the map is three times the size of the bundle it describes, it is deployed
// with it, and no visitor ever asks for it.
tasks.named<KotlinWebpack>("wasmJsBrowserProductionWebpack") {
    sourceMaps = false
}

/**
 * The token index.html carries in place of the build manifest. It is written as a comment followed by
 * `null`, so the page is valid JavaScript either way: substituted in a distribution, and left as
 * `null` on the development server, where the progress bar falls back to an estimate.
 */
val buildManifestPlaceholder = "/*{{BUILD}}*/null"

/** What the distribution is served as, rather than what it is made of. */
val transportSuffixes = setOf(".br", ".gz")

/** Text-shaped enough to be worth compressing; the icon already is. */
val compressibleSuffixes = setOf(".cho", ".cvr", ".html", ".js", ".json", ".txt", ".wasm", ".xml")

val distributionDirectory = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
val resourceDirectory = layout.projectDirectory.dir("src/wasmJsMain/resources")
val shouldPrecompress = project.property("campfire.web.precompress").toString().toBoolean()

/** Files of the distribution that are not part of the build the page starts: what the browser asks for past it. */
val unversionedFiles = setOf("index.html", "build.json", "service-worker.js")

/**
 * Tells index.html what makes up the build it belongs to, and writes the same into build.json for the page of an
 * earlier build to compare itself with, and then, with `campfire.web.precompress` on, writes a precompressed copy of
 * everything worth compressing next to it, for hosts that serve those.
 *
 * The map names every file the app can ask for, with its size, which the progress bar measures the download against,
 * and the full SHA-256 of its content, which the page checks every file it keeps in the browser against before it
 * starts a build from them, so an error page or a file of another release caught mid-deployment is never kept. The
 * first sixteen hex digits of it are the version every file is asked for with: a hash of its own content rather than
 * the version name, or one hash of the whole distribution, since a file built again from other sources must not be
 * taken for the one a browser still holds, while one that a release left as it was - skiko's binary, the fonts, the
 * drawables - can go on being served from it.
 *
 * The build id is a hash of that map and of the page's source, so two builds with one version name are still two
 * builds and the same sources are the same build. build.json carries the digest of the finished page too, which the
 * page cannot carry about itself.
 *
 * The page is written from its source rather than edited in place, so running this over the same distribution twice
 * produces the same distribution.
 */
val finishWebDistribution = tasks.register("finishWebDistribution") {
    description = "Fills in the build manifest of the web distribution and precompresses it if asked to."
    val root = distributionDirectory
    val templates = resourceDirectory
    val precompress = shouldPrecompress
    val placeholder = buildManifestPlaceholder
    val transport = transportSuffixes
    val compressible = compressibleSuffixes
    val unversioned = unversionedFiles
    doLast {
        val directory = root.get().asFile
        val page = "index.html"
        val template = templates.file(page).asFile.readText()
        check(template.contains(placeholder)) { "$page has no $placeholder for the build manifest" }

        val deployed = directory.walkTopDown()
            .filter { it.isFile && transport.none(it.name::endsWith) }
            .toList()
        val versioned = deployed.map { it.relativeTo(directory).invariantSeparatorsPath to it }
            .filter { (path, _) -> path !in unversioned }
            .sortedBy { (path, _) -> path }
        val files = versioned.joinToString(",") { (path, file) ->
            "${path.toJsonString()}:{\"sha256\":${file.readBytes().sha256().toJsonString()},\"size\":${file.length()}}"
        }
        val binaries = versioned.map { it.second }.filter { it.name.endsWith(".wasm") }
        val id = "$files\n$template".toByteArray().sha256().take(16)
        val manifest = "{\"id\":${id.toJsonString()},\"binaryCount\":${binaries.size}," +
            "\"binaryBytes\":${binaries.sumOf { it.length() }},\"files\":{$files}}"
        val finishedPage = template.replace(placeholder, manifest)
        File(directory, page).writeText(finishedPage)
        File(directory, "build.json").writeText(
            "{\"id\":${id.toJsonString()},\"page\":${finishedPage.toByteArray().sha256().toJsonString()},\"files\":{$files}}"
        )
        logger.lifecycle(
            "Web distribution: build $id, ${deployed.size} files, ${versioned.size} of them versioned, " +
                "${versioned.sumOf { it.second.length() } / 1024} KiB",
        )

        if (precompress) {
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
}

tasks.named("wasmJsBrowserDistribution") {
    finalizedBy(finishWebDistribution)
}

/** The SHA-256 of these bytes in lowercase hex, the form the page computes it in with SubtleCrypto. */
fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

/**
 * A JSON string literal: the paths come from the Compose resources, which are plain today, but nothing guarantees a
 * name without a quote or a backslash in it.
 */
fun String.toJsonString() = buildString {
    append('"')
    this@toJsonString.forEach { character ->
        when {
            character == '"' || character == '\\' -> append('\\').append(character)
            character < ' ' -> append("\\u%04x".format(character.code))
            else -> append(character)
        }
    }
    append('"')
}

/** Deflate at the highest level: this runs once per deployment and the result is served for months. */
fun File.gzipTo(target: File) = target.outputStream().use { output ->
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
fun File.brotliTo(target: File) = try {
    ProcessBuilder("brotli", "--quality=11", "--force", "--output=${target.absolutePath}", absolutePath)
        .redirectErrorStream(true)
        .start()
        .waitFor() == 0
} catch (_: IOException) {
    false
}
