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
    id("campfire-koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":domain:api"))
            implementation(project(":data:repository:api"))
            implementation(project(":data:formats"))
            implementation(project(":libs:chordsheet"))
            implementation(libs.kotlin.coroutines)
        }
        // PageCorpusTest reads the page corpus's labelled pages.
        named("desktopTest").dependencies {
            implementation(libs.kotlin.serialization.json)
        }
    }
}

tasks.named<Test>("desktopTest") {
    val pageCorpus = project.property("campfire.pageCorpus").toString()
    systemProperty("campfire.pageCorpus", pageCorpus)
    inputs.property("pageCorpus", pageCorpus)
    if (pageCorpus.isNotBlank()) inputs.files(fileTree(pageCorpus) { include("pages/**", "labels/**") }).withPropertyName("pageCorpusPages")
    // The two models :presentation ships, which PageCorpusTest runs over the corpus's labelled pages as the app chooses them.
    val pageModelWeights = rootProject.file("presentation/src/commonMain/composeResources/files/page-model.bin")
    systemProperty("pageModelWeights", pageModelWeights.absolutePath)
    inputs.files(pageModelWeights).withPropertyName("pageModelWeights")
    val pageModelMarkedWeights = rootProject.file("presentation/src/commonMain/composeResources/files/page-model-marked.bin")
    systemProperty("pageModelMarkedWeights", pageModelMarkedWeights.absolutePath)
    inputs.files(pageModelMarkedWeights).withPropertyName("pageModelMarkedWeights")
}
