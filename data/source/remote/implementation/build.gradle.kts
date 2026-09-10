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
}

/**
 * The Dropbox app key. It is a public client identifier rather than a secret - the OAuth flow Campfire uses (PKCE,
 * no client secret) exists precisely for clients that cannot keep one - but it identifies one particular developer's
 * registered app, so it still does not belong in a repository.
 *
 * It therefore defaults to empty in gradle.properties and is overridden from local.properties, which is never
 * committed. An empty key registers no provider at all, and the settings screen says so.
 */
val generateSyncConfiguration = tasks.register("generateSyncConfiguration") {
    val appKey = project.property("campfire.dropbox.appKey").toString()
    val outputDirectory = layout.buildDirectory.dir("generated/sync/kotlin")
    inputs.property("appKey", appKey)
    outputs.dir(outputDirectory)
    doLast {
        outputDirectory.get().asFile.resolve("com/pandulapeter/campfire/data/source/remote/implementation").let { directory ->
            directory.mkdirs()
            directory.resolve("SyncConfiguration.kt").writeText(
                """
                package com.pandulapeter.campfire.data.source.remote.implementation

                internal const val DROPBOX_APP_KEY = "$appKey"

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
                api(project(":data:source:remote:api"))
                implementation(project(":data:source:local:api"))
                implementation(libs.koin.core)
                implementation(libs.kotlin.coroutines)
                implementation(libs.kotlin.serialization.json)
                implementation(libs.ktor.client.core)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        desktopMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}
