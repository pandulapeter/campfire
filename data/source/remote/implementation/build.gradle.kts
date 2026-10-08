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
    alias(libs.plugins.koin.compiler)
}

/**
 * What the remote sources are built with. The Dropbox app key is a public client identifier rather than a secret - the
 * OAuth flow Campfire uses (PKCE, no client secret) exists precisely for clients that cannot keep one - but it
 * identifies one particular developer's registered app, so it still does not belong in a repository. It therefore
 * defaults to empty in gradle.properties and is overridden from local.properties, which is never committed. An empty
 * key registers no provider at all, and the settings screen says so.
 *
 * The version goes into the User-Agent every request carries, which MusicBrainz asks of every client, and is taken
 * from the one property every platform's version comes from, so that it is never typed by hand.
 */
val generateRemoteConfiguration = tasks.register("generateRemoteConfiguration") {
    val appKey = project.property("campfire.dropbox.appKey").toString()
    val versionName = project.property("campfire.versionName").toString()
    val outputDirectory = layout.buildDirectory.dir("generated/remote/kotlin")
    inputs.property("appKey", appKey)
    inputs.property("versionName", versionName)
    outputs.dir(outputDirectory)
    doLast {
        outputDirectory.get().asFile.resolve("com/pandulapeter/campfire/data/source/remote/implementation").let { directory ->
            directory.mkdirs()
            directory.resolve("RemoteConfiguration.kt").writeText(
                """
                package com.pandulapeter.campfire.data.source.remote.implementation

                internal const val DROPBOX_APP_KEY = "$appKey"

                internal const val CAMPFIRE_VERSION = "$versionName"

                """.trimIndent()
            )
        }
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateRemoteConfiguration)
            dependencies {
                api(project(":data:source:remote:api"))
                implementation(project(":data:source:local:api"))
                implementation(libs.koin.annotations)
                implementation(libs.koin.core)
                implementation(libs.kotlin.coroutines)
                implementation(libs.kotlin.serialization.json)
                implementation(libs.ktor.client.core)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.coroutines.test)
            implementation(libs.ktor.client.mock)
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
