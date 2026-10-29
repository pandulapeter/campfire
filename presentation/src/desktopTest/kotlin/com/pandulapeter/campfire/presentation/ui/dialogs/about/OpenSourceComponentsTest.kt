/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.about

import com.pandulapeter.campfire.presentation.ui.platform.AppBuild
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenSourceComponentsTest {

    @Test
    fun `every text an entry shows is among the files of every build that lists it`() {
        openSourceComponents.forEach { component ->
            listOfNotNull(component.licenseFile, component.noticeFile).forEach { path ->
                component.builds.forEach { build ->
                    val found = listOf("commonMain", sourceSetOf(build)).map { File("src/$it/composeResources", path) }.firstOrNull { it.isFile }
                    assertTrue(found != null && found.readText().isNotBlank(), "$path for ${build.name}")
                }
            }
        }
    }

    @Test
    fun `a build lists what it ships and nothing else`() {
        fun namesOf(build: AppBuild) = openSourceComponents.filter { build in it.builds }.map { it.name }
        assertTrue("Public Suffix List" in namesOf(AppBuild.ANDROID) && "Skia" !in namesOf(AppBuild.ANDROID))
        assertTrue("JetBrains Runtime" in namesOf(AppBuild.LINUX) && "ANGLE" !in namesOf(AppBuild.LINUX))
        assertTrue("ANGLE" in namesOf(AppBuild.WINDOWS))
        assertTrue("libfuse" in namesOf(AppBuild.LINUX) && "libfuse" !in namesOf(AppBuild.WINDOWS) && "libfuse" !in namesOf(AppBuild.MACOS))
        assertTrue("Inter" in namesOf(AppBuild.ANDROID) && "Inter" in namesOf(AppBuild.WEB) && "SLF4J" !in namesOf(AppBuild.WEB))
        assertTrue("js-joda" in namesOf(AppBuild.WEB) && "js-joda" !in namesOf(AppBuild.ANDROID))
        assertTrue("HarfBuzz" in namesOf(AppBuild.IOS) && "HarfBuzz" in namesOf(AppBuild.WEB) && "HarfBuzz" in namesOf(AppBuild.MACOS) && "HarfBuzz" !in namesOf(AppBuild.ANDROID))
        assertTrue("piex" in namesOf(AppBuild.MACOS) && "piex" !in namesOf(AppBuild.WEB) && "piex" !in namesOf(AppBuild.WINDOWS))
        assertTrue("FreeType" in namesOf(AppBuild.WEB) && "FreeType" in namesOf(AppBuild.LINUX) && "FreeType" !in namesOf(AppBuild.IOS))
        AppBuild.entries.forEach { assertEquals("Campfire", namesOf(it).first()) }
    }

    @Test
    fun `a long license is cut at blank lines into pieces that read as the text did`() {
        val text = (1..100).joinToString("\n") { if (it % 10 == 5) "" else "line $it" }
        val chunks = licenseTextChunks(text, linesPerChunk = 25)
        assertEquals(text, chunks.joinToString("\n"))
        assertTrue(chunks.drop(1).all { it.lines().first().isBlank() })
        assertTrue(chunks.all { it.lines().size <= 75 })
        assertEquals(listOf("one"), licenseTextChunks("one"))
    }

    private fun sourceSetOf(build: AppBuild) = when (build) {
        AppBuild.ANDROID -> "androidMain"
        AppBuild.IOS -> "iosMain"
        AppBuild.MACOS, AppBuild.WINDOWS, AppBuild.LINUX -> "desktopMain"
        AppBuild.WEB -> "wasmJsMain"
    }

    @Test
    fun `Campfire comes first and everything else by name, each once`() {
        assertEquals("Campfire", openSourceComponents.first().name)
        val rest = openSourceComponents.drop(1).map { it.name }
        assertEquals(rest.sortedBy { it.lowercase() }, rest)
        assertEquals(openSourceComponents.size, openSourceComponents.map { it.name }.distinct().size)
    }
}
