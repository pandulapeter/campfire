/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.library.normalizedToNfc

/**
 * How a platform's file system compares the names of a directory, for [FakeLibraryFileLocalSource]: two names are one
 * file where their [key]s are equal, and the file keeps the spelling it was created with, except that a write through
 * another spelling of it renames it to that spelling where [takesWrittenSpelling] (an atomic write is a new file moved
 * over the old one, and which name survives that move is the file system's business), and that a file system which
 * [storesDecomposed] keeps every name decomposed, the way an HFS+ volume does and a Mac hands out names made by one.
 */
internal data class FileSystemNaming(
    val ignoresCase: Boolean,
    val ignoresForm: Boolean,
    val storesDecomposed: Boolean = false,
    val takesWrittenSpelling: Boolean = false,
) {

    fun key(name: String) = name
        .let { if (ignoresForm) it.normalizedToNfc() else it }
        .let { if (ignoresCase) it.map { char -> char.uppercaseChar().lowercaseChar() }.joinToString("") else it }

    /** The spelling a file created under [name] is listed under. */
    fun stored(name: String) = if (storesDecomposed) name.decomposed() else name

    /** Only the letters the simulations name files with: there is no decomposition in the common standard library. */
    private fun String.decomposed() = buildString {
        this@decomposed.normalizedToNfc().forEach { char ->
            when (char) {
                'é' -> append("é")
                'É' -> append("É")
                'á' -> append("á")
                'Á' -> append("Á")
                else -> append(char)
            }
        }
    }

    companion object {
        /** Android, Linux, and the web's OPFS: every spelling is a file of its own. */
        val EXACT = FileSystemNaming(ignoresCase = false, ignoresForm = false)

        /** NTFS: case is ignored, Unicode forms are told apart. */
        val WINDOWS = FileSystemNaming(ignoresCase = true, ignoresForm = false)

        /** APFS on an iPhone: case-sensitive, but a name is found under either Unicode form. */
        val IPHONE = FileSystemNaming(ignoresCase = false, ignoresForm = true)

        /** APFS on a Mac: neither case nor Unicode form counts. */
        val MAC = FileSystemNaming(ignoresCase = true, ignoresForm = true)
    }
}
