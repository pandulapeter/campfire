/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.corpus

import java.io.File

/**
 * The messy songs under the test resources' `corpus` folder, by file name: ChordPro (`.cho`), plain-text chord sheets as
 * pasted from websites (`.txt`), and ABC tunes (`.abc`). The round-trip tests hold every one of them, and the variations
 * they generate from them, to what the code promises.
 */
internal object Corpus {
    private val folder = File(requireNotNull(Corpus::class.java.getResource("/corpus")) { "corpus" }.toURI())

    fun files(extension: String): Map<String, String> = folder.listFiles().orEmpty()
        .filter { it.extension == extension }
        .sortedBy { it.name }
        .associate { it.name to it.readText() }

    val chordPro get() = files("cho")
}
