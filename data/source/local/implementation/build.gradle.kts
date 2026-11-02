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

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":data:source:local:api"))
            implementation(project(":data:formats"))
            implementation(project(":chordpro"))
            implementation(libs.kotlin.coroutines)
            implementation(libs.kotlin.datetime)
            implementation(libs.kotlin.serialization.json)
        }
        wasmJsMain.dependencies {
            // The Origin Private File System is reached through the browser APIs, see OpfsFileStorage.kt.
            implementation(libs.kotlin.browser)
        }
    }
}

tasks.named<Test>("desktopTest") {
    val corpus = project.property("campfire.importCorpus").toString()
    systemProperty("campfire.importCorpus", corpus)
    // The corpus lives outside the project, so Gradle is told to rerun when a document or its .cho changes.
    if (corpus.isNotBlank()) inputs.files(fileTree(corpus) { exclude(".runs/**") }).withPropertyName("importCorpus")
}
