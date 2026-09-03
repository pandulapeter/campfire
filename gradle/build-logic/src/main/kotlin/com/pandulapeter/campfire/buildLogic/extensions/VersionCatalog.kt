package com.pandulapeter.campfire.buildLogic.extensions

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs
    get(): VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String) = findVersion(alias).get().toString()

internal fun VersionCatalog.pluginId(alias: String) = findPlugin(alias).get().get().pluginId
