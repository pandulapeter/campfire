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
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":data:repository:implementation"))
    implementation(project(":data:source:local:implementation"))
    implementation(project(":data:source:remote:implementation"))
    implementation(project(":domain:implementation"))
    implementation(project(":presentation"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appCompat)
    implementation(libs.androidx.browser)
    implementation(libs.google.material)
    implementation(libs.koin.android)
}

android {
    namespace = "com.pandulapeter.campfire"
    val targetSdkVersion = libs.versions.android.compileSdk.get().toInt()
    compileSdk = targetSdkVersion
    defaultConfig {
        applicationId = "com.pandulapeter.campfire"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = targetSdkVersion
        versionCode = project.property("campfire.android.versionCode").toString().toInt()
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

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}