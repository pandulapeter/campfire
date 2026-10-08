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
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Configures the shared set of targets every Campfire library module compiles for: Android, desktop (JVM), iOS and web (wasmJs).
 * The Android namespace is derived from the Gradle path, so modules only need to override it when they want something else.
 */
@OptIn(ExperimentalWasmDsl::class, ExperimentalKotlinGradlePluginApi::class)
internal fun Project.configureKotlinMultiplatform(
    extension: KotlinMultiplatformExtension,
) {
    // A klib carries the name of the artifact it is built into, which is the name of the Gradle project by default -
    // and several modules here are called "api" or "implementation". Derived from the whole path instead, so that no
    // two of them claim the same identity and the klib loader has nothing to disambiguate.
    extensions.configure<BasePluginExtension> {
        archivesName.set(path.removePrefix(":").replace(":", "-"))
    }
    // A coroutine test's outer entry point often hides the failing assertion's line in Gradle's short output.
    tasks.withType<Test>().configureEach {
        testLogging.exceptionFormat = TestExceptionFormat.FULL
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
            // The Android target compiles commonTest only when asked to, and warns about every module that has one
            // otherwise. The tests are run on the desktop target; this only gives them a home on the Android one too.
            withHostTest {}
        }
        jvm("desktop")
        iosArm64()
        iosSimulatorArm64()
        wasmJs {
            browser()
        }
        // The default template has no group for the two JVM targets, so code both of them need (java.io.File) would
        // otherwise be kept twice. Extended rather than replaced: a dependsOn written by hand switches the default
        // template off for the whole module. The Android target is matched by its type, the AGP KMP library target,
        // since it is not one of the targets the template's own withAndroidTarget() knows.
        applyDefaultHierarchyTemplate {
            common {
                group("jvmShared") {
                    withJvm()
                    withCompilations { it.target is KotlinMultiplatformAndroidLibraryTarget }
                }
            }
        }
        sourceSets.commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
