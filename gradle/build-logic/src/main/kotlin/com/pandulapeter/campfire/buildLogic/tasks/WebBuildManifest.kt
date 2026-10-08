/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.buildLogic.tasks

import java.security.MessageDigest

/**
 * What [FinishWebDistribution] writes about a build: its [id], the [pageManifest] index.html carries in place of its
 * placeholder, and build.json, which also names the digest of the finished page and is therefore made by [buildJson]
 * once the page is.
 */
internal class WebBuildManifest(
    val id: String,
    val pageManifest: String,
    private val files: String,
) {
    fun buildJson(pageSha256: String) = "{\"id\":${id.toJsonString()},\"page\":${pageSha256.toJsonString()},\"files\":{$files}}"
}

/**
 * The manifest of the versioned [files] of a distribution, given as their paths relative to it and their content, and
 * the source of its page. Every file is named with its full SHA-256 and its size, in the order of their paths; the
 * binaries the progress bar counts are the `.wasm` ones; the build id is the first sixteen hex digits of the SHA-256 of
 * that map and of the page's source.
 */
internal fun webBuildManifest(files: List<Pair<String, ByteArray>>, template: String): WebBuildManifest {
    val sorted = files.sortedBy { (path, _) -> path }
    val fileMap = sorted.joinToString(",") { (path, content) ->
        "${path.toJsonString()}:{\"sha256\":${content.sha256().toJsonString()},\"size\":${content.size}}"
    }
    val binaries = sorted.filter { (path, _) -> path.endsWith(".wasm") }
    val id = "$fileMap\n$template".toByteArray().sha256().take(16)
    val pageManifest = "{\"id\":${id.toJsonString()},\"binaryCount\":${binaries.size}," +
        "\"binaryBytes\":${binaries.sumOf { (_, content) -> content.size.toLong() }},\"files\":{$fileMap}}"
    return WebBuildManifest(id = id, pageManifest = pageManifest, files = fileMap)
}

/** The SHA-256 of these bytes in lowercase hex, the form the page computes it in with SubtleCrypto. */
internal fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

/**
 * A JSON string literal: the paths come from the Compose resources, which are plain today, but nothing guarantees a
 * name without a quote or a backslash in it.
 */
private fun String.toJsonString() = buildString {
    append('"')
    this@toJsonString.forEach { character ->
        when {
            character == '"' || character == '\\' -> append('\\').append(character)
            character < ' ' -> append("\\u%04x".format(character.code))
            else -> append(character)
        }
    }
    append('"')
}
