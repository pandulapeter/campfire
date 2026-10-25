/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
plugins {
    id("campfire-library")
    alias(libs.plugins.kotlin.serialization)
    id("campfire-koin")
}

/**
 * The build number stands for the code that packs a shared copy: a copy an older build made is built again once, and
 * one a newer build made is left alone (`shared/CLAUDE.md`). Taken from the one property every
 * platform's version code comes from, so that it is never typed by hand.
 */
val generateSyncConfiguration = tasks.register("generateSyncConfiguration") {
    val buildNumber = project.property("campfire.buildNumber").toString().toInt()
    val outputDirectory = layout.buildDirectory.dir("generated/sync/kotlin")
    inputs.property("buildNumber", buildNumber)
    outputs.dir(outputDirectory)
    doLast {
        outputDirectory.get().asFile.resolve("com/pandulapeter/campfire/data/sync/implementation").let { directory ->
            directory.mkdirs()
            directory.resolve("SyncConfiguration.kt").writeText(
                """
                package com.pandulapeter.campfire.data.sync.implementation

                internal const val CAMPFIRE_BUILD_NUMBER = $buildNumber

                """.trimIndent()
            )
        }
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateSyncConfiguration)
            dependencies {
                implementation(project(":data:repository:api"))
                implementation(project(":data:source:local:api"))
                implementation(project(":data:source:remote:api"))
                implementation(libs.kotlin.coroutines)
                implementation(libs.kotlin.serialization.json)
            }
        }
    }
}
