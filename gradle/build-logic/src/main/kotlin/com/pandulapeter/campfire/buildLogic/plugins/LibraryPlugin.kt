package com.pandulapeter.campfire.buildLogic.plugins

import com.pandulapeter.campfire.buildLogic.extensions.configureKotlinMultiplatform
import com.pandulapeter.campfire.buildLogic.extensions.libs
import com.pandulapeter.campfire.buildLogic.extensions.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class LibraryPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            apply(libs.pluginId("kotlin-multiplatform"))
            apply(libs.pluginId("android-multiplatformLibrary"))
        }
        extensions.configure<KotlinMultiplatformExtension>(::configureKotlinMultiplatform)
    }
}
