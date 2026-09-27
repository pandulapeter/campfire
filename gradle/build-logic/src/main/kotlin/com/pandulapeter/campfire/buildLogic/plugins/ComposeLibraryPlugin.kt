/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.buildLogic.plugins

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.pandulapeter.campfire.buildLogic.extensions.configureKotlinMultiplatform
import com.pandulapeter.campfire.buildLogic.extensions.libs
import com.pandulapeter.campfire.buildLogic.extensions.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class ComposeLibraryPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            apply(libs.pluginId("kotlin-multiplatform"))
            apply(libs.pluginId("android-multiplatformLibrary"))
            apply(libs.pluginId("compose"))
            apply(libs.pluginId("compose-compiler"))
        }
        extensions.configure<KotlinMultiplatformExtension> {
            configureKotlinMultiplatform(this)
            configure<KotlinMultiplatformAndroidLibraryTarget> {
                androidResources {
                    enable = true
                }
            }
        }
        // Read by the compiler of the module that uses the classes it names, which is why it is set here rather than
        // in :chordpro, whose model it vouches for.
        extensions.configure<ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("gradle/compose-stability.conf"))
        }
    }
}
