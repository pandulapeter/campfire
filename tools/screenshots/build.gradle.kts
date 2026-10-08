/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    application
    id("campfire-style")
}

dependencies {
    implementation(project(":app:di"))
    implementation(project(":data:model"))
    implementation(project(":data:repository:api"))
    implementation(project(":domain:api"))
    implementation(project(":metronome:api"))
    implementation(project(":presentation"))
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.components.resources)
    implementation(compose.desktop.currentOs)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlin.coroutines.swing)
    implementation(libs.kotlin.serialization.json)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

// The tool is a friend of :presentation, so a shot can reach the state the screens keep internal (a list's scroll
// position, the filter's codes) rather than the app's API being widened for a tool no build ships.
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>("compileKotlin") {
    val presentation = configurations.compileClasspath.map { classpath -> classpath.files.filter { it.absolutePath.contains("/presentation/build/") } }
    compilerOptions.freeCompilerArgs.addAll(presentation.map { files -> files.map { "-Xfriend-paths=${it.absolutePath}" } })
}

application {
    mainClass = "com.pandulapeter.campfire.screenshots.MainKt"
}

/**
 * The fonts the other platforms set the interface in, none of which a Mac has: Roboto and Android's monospace for the
 * Android and ChromeOS shots, and for the Windows ones Open Sans, the closest open relative of Segoe UI (the two share
 * their designer), which the license keeps on Windows. All three may be redistributed, but nothing needs them but this
 * tool, so they are fetched into the build folder rather than committed.
 */
val fonts: Provider<Directory> = layout.buildDirectory.dir("fonts")
val downloadFonts by tasks.registering {
    val fontDirectory = fonts
    outputs.dir(fontDirectory)
    doLast {
        val directory = fontDirectory.get().asFile.apply { mkdirs() }
        fun download(url: String) = URI(url).toURL().openStream().use { it.readBytes() }
        File(directory, "Roboto.ttf").writeBytes(download("https://github.com/google/fonts/raw/main/ofl/roboto/Roboto%5Bwdth,wght%5D.ttf"))
        File(directory, "DroidSansMono.ttf").writeBytes(
            download("https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/data/fonts/DroidSansMono.ttf"),
        )
        File(directory, "OpenSans.ttf").writeBytes(download("https://github.com/google/fonts/raw/main/ofl/opensans/OpenSans%5Bwdth,wght%5D.ttf"))
    }
}

tasks.named<JavaExec>("run") {
    dependsOn(downloadFonts)
    // Every run renders afresh from the library, which Gradle cannot see change.
    outputs.upToDateWhen { false }
    systemProperty("java.awt.headless", "true")
    systemProperty("campfire.screenshots.library", file("library").absolutePath)
    systemProperty("campfire.screenshots.renders", file("renders").absolutePath)
    systemProperty("campfire.screenshots.fonts", fonts.get().asFile.absolutePath)
    systemProperty("campfire.screenshots.appIcon", rootProject.file("app/desktop/src/main/composeResources/drawable/app_icon.png").absolutePath)
    systemProperty("campfire.screenshots.versionName", project.property("campfire.versionName").toString())
}

