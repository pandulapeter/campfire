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
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
    id("campfire-style")
}

android {
    namespace = "com.pandulapeter.campfire.baselineprofile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = compileSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app:android"
}

// The profile is recorded on whatever device is connected (the local emulator, see app/baselineprofile/CLAUDE.md),
// never on CI.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.uiautomator)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}
