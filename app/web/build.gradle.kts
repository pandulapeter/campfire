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
            implementation(project(":data:repository:implementation"))
            implementation(project(":data:source:local:implementation"))
            implementation(project(":data:source:remote:implementation"))
            implementation(project(":domain:implementation"))
            implementation(project(":presentation"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.core)
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
val compressibleSuffixes = setOf(".cvr", ".html", ".js", ".json", ".txt", ".wasm", ".xml")

val distributionDirectory = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
val resourceDirectory = layout.projectDirectory.dir("src/wasmJsMain/resources")
val shouldPrecompress = project.property("campfire.web.precompress").toString().toBoolean()

/**
 * Tells index.html what its progress bar is about to download - it can only measure the binaries
 * against a total it knows before the first byte arrives - and then writes a precompressed copy of
 * everything worth compressing next to it, for hosts that serve those.
 *
 * The page is written from its source rather than edited in place, so running this over the same
 * distribution twice produces the same distribution.
 */
val finishWebDistribution by tasks.registering {
    description = "Fills in the build manifest of the web distribution and precompresses it."
    val root = distributionDirectory
    val templates = resourceDirectory
    val precompress = shouldPrecompress
    val placeholder = buildManifestPlaceholder
    val transport = transportSuffixes
    val compressible = compressibleSuffixes
    doLast {
        val directory = root.get().asFile
        val page = "index.html"

        val binaries = directory.walkTopDown().filter { it.isFile && it.name.endsWith(".wasm") }.toList()
        val manifest = "{\"binaryCount\":${binaries.size},\"binaryBytes\":${binaries.sumOf { it.length() }}}"
        val template = templates.file(page).asFile.readText()
        check(template.contains(placeholder)) { "$page has no $placeholder for the build manifest" }
        File(directory, page).writeText(template.replace(placeholder, manifest))

        val deployed = directory.walkTopDown()
            .filter { it.isFile && transport.none(it.name::endsWith) }
            .toList()
        logger.lifecycle("Web distribution: ${deployed.size} files, ${deployed.sumOf { it.length() } / 1024} KiB")

        if (precompress) {
            var isBrotliMissing = false
            deployed.filter { file -> compressible.any(file.name::endsWith) }
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
