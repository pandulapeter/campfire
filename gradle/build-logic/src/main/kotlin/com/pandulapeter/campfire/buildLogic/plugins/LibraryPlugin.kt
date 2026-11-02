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

import com.pandulapeter.campfire.buildLogic.extensions.configureKotlinMultiplatform
import com.pandulapeter.campfire.buildLogic.extensions.libs
import com.pandulapeter.campfire.buildLogic.extensions.pluginId
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class LibraryPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            apply(libs.pluginId("kotlin-multiplatform"))
            apply(libs.pluginId("android-multiplatformLibrary"))
            apply(StylePlugin::class.java)
        }
        extensions.configure<KotlinMultiplatformExtension>(::configureKotlinMultiplatform)
        if (path.startsWith(LIBS)) {
            requireOnlyLibraryDependencies()
        }
    }

    /**
     * A `:libs:*` module is code that knows nothing of the app, which is what lets it be read, tested, and reused on its
     * own; the folder says so only while nothing reaches past it, so a project dependency outside `:libs:` fails the
     * configuration that declares it rather than waiting for a review to notice.
     */
    private fun Project.requireOnlyLibraryDependencies() {
        val libraryPath = path
        configurations.configureEach {
            val configurationName = name
            dependencies.configureEach {
                if (this is ProjectDependency && !this.path.startsWith(LIBS)) {
                    throw GradleException(
                        "$libraryPath depends on ${this.path} in $configurationName, but a :libs:* module may depend only on other :libs:* modules.",
                    )
                }
            }
        }
    }

    private companion object {
        const val LIBS = ":libs:"
    }
}
