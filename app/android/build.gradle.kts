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
    implementation(project(":libs:metronome:api"))
    implementation(project(":presentation"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    // The home screen widgets, which RemoteViews draw: Glance writes them in Compose's terms, the colors in Material 3's.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    // Over the WorkManager 2.7 Glance brings: its Room 2.2 keeps WorkDatabase_Impl but not the constructor Room creates it
    // with, which R8's full mode then removes, and every launch fails in WorkManager's startup initializer.
    implementation(libs.androidx.work.runtime)
    // Only compiled against, for the widgets' colors: the Material 3 the shared UI already ships.
    implementation(libs.compose.material3)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.koin.android)
    baselineProfile(project(":app:baselineprofile"))
}

android {
    namespace = "com.pandulapeter.campfire"
    val targetSdkVersion = libs.versions.android.compileSdk.get().toInt()
    compileSdk {
        version = release(targetSdkVersion) {
            minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
        }
    }
    defaultConfig {
        applicationId = "com.pandulapeter.campfire"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = targetSdkVersion
        versionCode = project.property("campfire.buildNumber").toString().toInt()
        versionName = project.property("campfire.versionName").toString()
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
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
            storeFile = releaseKeystore(project.property("campfire.android.keystoreFile").toString())
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
            syncRedirectScheme("campfire-debug")
            signingConfig = signingConfigs.getByName(internalSigningConfig)
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            syncRedirectScheme("campfire")
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
        "dialogs/songDisplay/SongLayoutSheetKt",
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
        // ART compiles ahead of time only the methods a profile marks hot (H); startup (S) and post-startup (P) only
        // order the DEX. The recording marks hot just what grew hot during the walk, so most of the app's own code came
        // out SP and ran interpreted on every device until the JIT caught up, which a screen composed once per visit
        // rarely lets it. Every method the walk ran is compiled instead.
        profile.asFile.writeText(
            rules.lineSequence().joinToString("\n") { rule ->
                val flags = rule.takeWhile { it in "HSP" }
                if (flags.isEmpty() || 'H' in flags) rule else "H$rule"
            },
        )
    }
}

/**
 * The scheme the Dropbox sign-in comes back on, declared by the manifest's sign-in filter and handed to the graph
 * (`SyncRedirectScheme`). The debug build's is its own, so that with the store app installed beside it the browser's
 * return reaches the app that asked instead of a chooser. Each is a redirect URI registered in the Dropbox App Console.
 */
fun com.android.build.api.dsl.VariantDimension.syncRedirectScheme(scheme: String) {
    manifestPlaceholders["syncRedirectScheme"] = scheme
    buildConfigField("String", "SYNC_REDIRECT_SCHEME", "\"$scheme\"")
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

/**
 * The release keystore [path] names, relative to this module or absolute. A git worktree holds only committed files, so a
 * relative keystore that only the main checkout has (it is never committed) is taken from the main checkout's copy of
 * this module, which lets a worktree reuse the main checkout's local.properties as it is.
 */
fun releaseKeystore(path: String): File {
    val keystore = file(path)
    val gitFile = rootProject.file(".git")
    if (keystore.exists() || File(path).isAbsolute || !gitFile.isFile) return keystore
    // A linked worktree's .git file points at <main checkout>/.git/worktrees/<name>.
    val worktreeGitDir = rootDir.resolve(gitFile.readText().substringAfter("gitdir:").trim())
    val mainCheckout = worktreeGitDir.parentFile?.parentFile?.parentFile ?: return keystore
    return mainCheckout.resolve(keystore.relativeTo(rootDir)).takeIf { it.exists() } ?: keystore
}
