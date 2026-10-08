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

import com.pandulapeter.campfire.buildLogic.tasks.FinishWebDistribution
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    id("campfire-style")
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
val finishWebDistribution = tasks.register<FinishWebDistribution>("finishWebDistribution") {
    description = "Fills in the build manifest of the web distribution and precompresses it if asked to."
    distributionDirectory = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
    pageTemplate = layout.projectDirectory.file("src/wasmJsMain/resources/index.html")
    precompress = project.property("campfire.web.precompress").toString().toBoolean()
    placeholder = buildManifestPlaceholder
    // What the distribution is served as, rather than what it is made of.
    transportSuffixes = setOf(".br", ".gz")
    // Text-shaped enough to be worth compressing; the icon already is.
    compressibleSuffixes = setOf(".cho", ".cvr", ".html", ".js", ".json", ".txt", ".wasm", ".xml")
    // Files of the distribution that are not part of the build the page starts: what the browser asks for past it.
    unversionedFiles = setOf("index.html", "build.json", "service-worker.js")
}

tasks.named("wasmJsBrowserDistribution") {
    finalizedBy(finishWebDistribution)
}
