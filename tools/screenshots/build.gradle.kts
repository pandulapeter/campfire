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
import java.security.MessageDigest
import java.time.Duration

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
    implementation(project(":tuner:api"))
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.compose.components.resources)
    implementation(compose.desktop.currentOs)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlin.coroutines.swing)
    implementation(libs.kotlin.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(libs.androidx.navigation3.ui)
    testImplementation(libs.compose.ui.test)
    testRuntimeOnly(libs.junit.platformLauncher)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

// The tool is a friend of :presentation, so a shot can reach the state the screens keep internal (a list's scroll
// position, the filter's codes) rather than the app's API being widened for a tool no build ships.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    val presentation = configurations.compileClasspath.map { classpath -> classpath.files.filter { it.absolutePath.contains("/presentation/build/") } }
    compilerOptions.freeCompilerArgs.addAll(presentation.map { files -> files.map { "-Xfriend-paths=${it.absolutePath}" } })
}

application {
    mainClass = "com.pandulapeter.campfire.screenshots.MainKt"
}

/**
 * The fonts the other platforms draw their system chrome in (the status bar, the title bar), none of which a Mac has:
 * Roboto for the Android and ChromeOS shots, and for the Windows ones Open Sans, the closest open relative of Segoe UI
 * (the two share their designer), which the license keeps on Windows. The app itself is set in the fonts it bundles.
 * Both may be redistributed, but nothing needs them but this tool, so they are fetched into the build folder rather
 * than committed.
 *
 * Each is pinned to a commit of its repository and checked against its SHA-256, so that a font changed upstream never
 * lays the text of two retakes out differently, and an error page is never taken for a font. These are the files the
 * published shots were rendered with. Updating one is a new commit in its address and a new checksum here, which is
 * also what makes the task download it again.
 */
val fonts: Provider<Directory> = layout.buildDirectory.dir("fonts")
val downloadFonts = tasks.register("downloadFonts") {
    val fontSources = mapOf(
        "Roboto.ttf" to Pair(
            "https://raw.githubusercontent.com/google/fonts/5e8a3ba899557829a76cfdac30fa512bda91d7ca/ofl/roboto/Roboto%5Bwdth,wght%5D.ttf",
            "d7598e12c5dbef095ff8272cfc55da0250bd07fbdecbac8a530b9b277872a134",
        ),
        "OpenSans.ttf" to Pair(
            "https://raw.githubusercontent.com/google/fonts/5e8a3ba899557829a76cfdac30fa512bda91d7ca/ofl/opensans/OpenSans%5Bwdth,wght%5D.ttf",
            "36643644f318a812aab2d2ed3bb98f8cf0872527f835fe9398d95fe6b9adb878",
        ),
    )
    val fontDirectory = fonts
    inputs.property("fonts", fontSources.toString())
    outputs.dir(fontDirectory)
    doLast {
        val directory = fontDirectory.get().asFile.apply { mkdirs() }
        fontSources.forEach { (name, source) ->
            val (url, expected) = source
            val bytes = URI(url).toURL().openStream().use { it.readBytes() }
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            if (actual != expected) throw GradleException("$name from $url has SHA-256 $actual, expected $expected")
            File(directory, name).writeBytes(bytes)
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // The whole suite takes a few minutes. A screen that never stops animating keeps the test clock from ever idling, and
    // without a bound it holds CI's runner for hours rather than failing with the reports uploaded.
    timeout = Duration.ofMinutes(20)
    val isUpdatingGoldens = project.property("campfire.updateGoldens").toString().toBoolean()
    // Rewriting the goldens is asked for with a property, which a run that changed nothing else would not repeat.
    outputs.upToDateWhen { !isUpdatingGoldens }
    systemProperty("java.awt.headless", "true")
    systemProperty("campfire.screenshots.versionName", project.property("campfire.versionName").toString())
    systemProperty("campfire.screenshots.goldens", file("src/test/resources/goldens").absolutePath)
    systemProperty("campfire.screenshots.goldenOutput", layout.buildDirectory.dir("goldens").get().asFile.absolutePath)
    systemProperty("campfire.screenshots.updateGoldens", isUpdatingGoldens.toString())
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
