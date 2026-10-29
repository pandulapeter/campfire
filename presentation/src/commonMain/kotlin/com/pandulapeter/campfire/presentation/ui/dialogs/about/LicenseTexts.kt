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

import com.pandulapeter.campfire.presentation.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every NOTICE and license text [components] name, by file, each cut into pieces ([licenseTextChunks]). */
internal suspend fun readLicenseTexts(components: List<OpenSourceComponent>) = withContext(Dispatchers.Default) {
    components.flatMap { listOfNotNull(it.noticeFile, it.licenseFile) }.distinct().mapNotNull { file ->
        runCatching { Res.readBytes(file).decodeToString() }.getOrNull()?.let { file to licenseTextChunks(it) }
    }.toMap()
}

/** This entry's NOTICE, where it has one, then its license, out of [texts] as [readLicenseTexts] read them. */
internal fun OpenSourceComponent.licenseTexts(texts: Map<String, List<String>>) =
    listOfNotNull(noticeFile, licenseFile).flatMap { texts[it].orEmpty() }

/**
 * [text] cut into pieces of about [linesPerChunk] lines, each ending before a blank line where the text has one in
 * reach, and otherwise at three times that, so that no piece of the list is a whole license to lay out. A piece after
 * the first starts with the blank line it was cut at, so the pieces read as the text did.
 */
internal fun licenseTextChunks(text: String, linesPerChunk: Int = 40): List<String> {
    val chunks = mutableListOf<String>()
    val current = mutableListOf<String>()
    text.trim().lines().forEach { line ->
        if (current.size >= linesPerChunk && line.isBlank() || current.size >= linesPerChunk * 3) {
            chunks += current.joinToString("\n")
            current.clear()
        }
        current += line
    }
    if (current.isNotEmpty()) chunks += current.joinToString("\n")
    return chunks
}
