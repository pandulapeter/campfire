/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.buildLogic.extensions

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Configures the shared set of targets every Campfire library module compiles for: Android, desktop (JVM), iOS and web (wasmJs).
 * The Android namespace is derived from the Gradle path, so modules only need to override it when they want something else.
 */
@OptIn(ExperimentalWasmDsl::class)
internal fun Project.configureKotlinMultiplatform(
    extension: KotlinMultiplatformExtension
) {
    // A klib carries the name of the artifact it is built into, which is the name of the Gradle project by default -
    // and several modules here are called "api" or "implementation". Derived from the whole path instead, so that no
    // two of them claim the same identity and the klib loader has nothing to disambiguate.
    extensions.configure<BasePluginExtension> {
        archivesName.set(path.removePrefix(":").replace(":", "-"))
    }
    extension.apply {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(libs.version("jvmTarget").toInt()))
        }
        extension.configure<KotlinMultiplatformAndroidLibraryTarget> {
            namespace = "com.pandulapeter.campfire" + path.replace(":", ".").replace("-", "_")
            minSdk = libs.version("android-minSdk").toInt()
            compileSdk = libs.version("android-compileSdk").toInt()
            packaging {
                resources {
                    excludes += "/META-INF/{AL2.0,LGPL2.1}"
                }
            }
        }
        jvm("desktop")
        iosArm64()
        iosSimulatorArm64()
        wasmJs {
            browser()
        }
    }
}
