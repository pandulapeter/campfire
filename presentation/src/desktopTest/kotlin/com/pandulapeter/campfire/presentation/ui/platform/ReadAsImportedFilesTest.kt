/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadAsImportedFilesTest {

    private fun folder(build: File.() -> Unit): File = Files.createTempDirectory("campfire-drop").toFile().apply {
        try {
            build()
        } finally {
            deleteOnExit()
        }
    }

    private fun File.file(path: String, text: String = "x") = File(this, path).apply {
        parentFile.mkdirs()
        writeText(text)
    }

    private fun File.importedNames() = listOf(absolutePath).readAsImportedFiles().map { it.name }

    @Test
    fun `a dropped folder is read through the folders inside it, each in name order`() {
        val root = folder {
            file("songs/b.cho")
            file("songs/a.cho")
            file("setlists/night.setlist.json")
            file("top.txt")
        }

        assertEquals(listOf("night.setlist.json", "a.cho", "b.cho", "top.txt"), root.importedNames())
    }

    @Test
    fun `a folder reports the files the import does not know as skipped, unread, and leaves out hidden entries`() {
        val root = folder {
            file("a.cho")
            file("._a.cho")
            file(".git/config.cho")
            file("photo.jpg", text = "not a zip")
        }

        val files = listOf(root.absolutePath).readAsImportedFiles()

        assertEquals(listOf("a.cho", "photo.jpg"), files.map { it.name })
        assertEquals(listOf(true, false), files.map { it.bytes.isNotEmpty() })
    }

    @Test
    fun `a folder is read no deeper than the depth limit`() {
        val root = folder {
            val deepest = (1 until ImportLimits.MAX_FOLDER_DEPTH).joinToString("/") { "d$it" }
            file("$deepest/inside.cho")
            file("$deepest/d/outside.cho")
        }

        assertEquals(listOf("inside.cho"), root.importedNames())
    }

    @Test
    fun `a file named on its own is handed over whatever it is called, to be reported as skipped`() {
        val root = folder { file("photo.jpg") }

        assertEquals(listOf("photo.jpg"), listOf(File(root, "photo.jpg").absolutePath).readAsImportedFiles().map { it.name })
    }
}
