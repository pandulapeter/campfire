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
    wasmJs {
        browser {
            // Only what the browser itself does runs there (CI's web storage step): the storage contract, and the time a
            // staged archive of many entries takes to unpack over it. The rest of the module's tests run on the desktop.
            testTask {
                useKarma { useChromeHeadless() }
                filter.includeTestsMatching("*OpfsFileStorageContractTest*")
                filter.includeTestsMatching("*OpfsStagedUnpackTimeTest*")
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":data:source:local:api"))
            implementation(project(":data:formats"))
            implementation(project(":libs:chordpro"))
            implementation(project(":libs:chordsheet"))
            implementation(libs.kotlin.coroutines)
            implementation(libs.kotlin.datetime)
            implementation(libs.kotlin.serialization.json)
        }
        // The import's planner and its writes over this module's real sources, wired as the app wires them, for
        // ArchiveImportTest and the other tests importing through the whole graph.
        named("desktopTest").dependencies {
            implementation(project(":data:repository:api"))
            implementation(project(":data:repository:implementation"))
            implementation(project(":data:source:remote:implementation"))
            implementation(project(":data:sync:implementation"))
            implementation(project(":domain:implementation"))
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
    val sharedCopyParitySeeds = project.property("campfire.sharedCopyParitySeeds").toString()
    systemProperty("campfire.sharedCopyParitySeeds", sharedCopyParitySeeds)
    inputs.property("sharedCopyParitySeeds", sharedCopyParitySeeds)
    // The ZIP64 archives real tools wrote, kept as hex in :data:formats' tests, which no other module's tests can see.
    val realWriterArchives = rootProject.file("data/formats/src/commonTest/kotlin/com/pandulapeter/campfire/data/formats/zip/RealWriterArchives.kt")
    systemProperty("realWriterArchives", realWriterArchives.absolutePath)
    inputs.file(realWriterArchives).withPropertyName("realWriterArchives")
    // The corpus lives outside the project, so Gradle is told to rerun when a document or its .cho changes.
    if (corpus.isNotBlank()) inputs.files(fileTree(corpus) { exclude(".runs/**") }).withPropertyName("importCorpus")
}

/**
 * The large-file memory proof (`LargeFileMemoryTest`) in a JVM capped well below three copies of a 50 MiB attachment,
 * which is what a phone's heap spares; `desktopTest` runs it, and leaves it out of its own uncapped JVM.
 */
val largeFileMemoryTest = tasks.register<Test>("largeFileMemoryTest") {
    val desktopTest = tasks.named<Test>("desktopTest").get()
    testClassesDirs = desktopTest.testClassesDirs
    classpath = desktopTest.classpath
    maxHeapSize = "128m"
    filter.includeTestsMatching("*LargeFileMemoryTest")
}

tasks.named<Test>("desktopTest") {
    filter.excludeTestsMatching("*LargeFileMemoryTest")
    dependsOn(largeFileMemoryTest)
}

/**
 * The large-archive proof of the streaming import (`LargeArchiveImportTest`): archives far larger than the heap, and as
 * many entries as an import takes, imported in a JVM capped at 128 MiB, the largest of `campfire.largeArchiveTest`'s
 * size. A step of CI's tests of its own and left out of `desktopTest`, which the pre-commit hook runs: it writes and
 * imports gigabytes.
 */
tasks.register<Test>("largeArchiveImportTest") {
    val desktopTest = tasks.named<Test>("desktopTest").get()
    testClassesDirs = desktopTest.testClassesDirs
    classpath = desktopTest.classpath
    maxHeapSize = "128m"
    val size = project.property("campfire.largeArchiveTest").toString()
    systemProperty("campfire.largeArchiveTest", size)
    inputs.property("largeArchiveTest", size)
    filter.includeTestsMatching("*LargeArchiveImportTest")
    testLogging { showStandardStreams = true }
}

tasks.named<Test>("desktopTest") {
    filter.excludeTestsMatching("*LargeArchiveImportTest")
}
