/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.compose.compiler)
    id("campfire-style")
}

dependencies {
    implementation(project(":app:di"))
    implementation(project(":data:source:remote:implementation"))
    implementation(project(":domain:api"))
    implementation(project(":metronome:api"))
    implementation(project(":presentation"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.koin.android)
    baselineProfile(project(":app:baselineprofile"))
}

android {
    namespace = "com.pandulapeter.campfire"
    val targetSdkVersion = libs.versions.android.compileSdk.get().toInt()
    compileSdk = targetSdkVersion
    defaultConfig {
        applicationId = "com.pandulapeter.campfire"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = targetSdkVersion
        versionCode = project.property("campfire.buildNumber").toString().toInt()
        versionName = project.property("campfire.versionName").toString()
    }
    buildFeatures.compose = true
    val internalSigningConfig = "internal"
    val releaseSigningConfig = "release"
    signingConfigs {
        // Deliberately literal: this is the standard Android debug keystore, committed next to this file, and there
        // is nothing about it worth hiding.
        create(internalSigningConfig) {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeFile = file("internal.keystore")
            storePassword = "android"
        }
        // Defaulted in gradle.properties to that same debug keystore, so a fresh clone can build a release variant
        // and get something installable. A real key belongs in local.properties, which is never committed and
        // overrides these - see the Build section of CLAUDE.md.
        create(releaseSigningConfig) {
            keyAlias = project.property("campfire.android.keyAlias").toString()
            keyPassword = project.property("campfire.android.keyPassword").toString()
            storeFile = file(project.property("campfire.android.keystoreFile").toString())
            storePassword = project.property("campfire.android.keystorePassword").toString()
        }
    }
    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            versionNameSuffix = "-debug"
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName(internalSigningConfig)
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(releaseSigningConfig)
        }
    }
}

baselineProfile {
    // Recorded by hand on an emulator and committed; a release build, CI's included, only reads the files.
    automaticGenerationDuringBuild = false
    saveInSrc = true
    mergeIntoMain = true
    dexLayoutOptimization = true
}

// A step of BaselineProfileGenerator's walk is skipped when its text never appears, which the run cannot report on its
// own: a failed test would stop Gradle before the profile is copied here. So the written profile is read once it is in
// place, and every walked screen must have a rule in it; the recording is kept either way, to compare. A new step of
// the walk adds the class its screen's composable compiles to.
tasks.matching { it.name == "generateBaselineProfile" }.configureEach {
    val profile = layout.projectDirectory.file("src/main/generated/baselineProfiles/baseline-prof.txt")
    val walkedScreens = listOf(
        "screens/songs/SongsScreenKt",
        "screens/songDetails/SongDetailsScreenKt",
        "dialogs/sheet/CampfireBottomSheetKt",
        "screens/songEditor/LoadedSongEditorKt",
        "screens/setlists/SetlistsScreenKt",
        "screens/metronome/MetronomeScreenKt",
        "screens/tuner/TunerScreenKt",
        "screens/chords/ChordsScreenKt",
        "screens/scales/ScalesScreenKt",
        "screens/settings/SettingsScreenKt",
    ).map { "Lcom/pandulapeter/campfire/presentation/ui/$it;" }
    doLast {
        val rules = profile.asFile.readText()
        val missingScreens = walkedScreens.filterNot { it in rules }
        if (missingScreens.isNotEmpty()) {
            throw GradleException(
                "The Baseline Profile has no rule for these walked screens, so their steps were skipped:\n" +
                    missingScreens.joinToString("\n") { "  $it" },
            )
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}
