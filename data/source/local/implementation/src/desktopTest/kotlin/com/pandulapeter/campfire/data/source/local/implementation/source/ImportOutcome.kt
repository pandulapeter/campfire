/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.hashing.Sha256
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.importing.IncomingContent

/**
 * What one import of an archive came to on one path: the review's [plan] and what Import wrote ([result], and every
 * file of the [library] by its SHA-256), or the [failure] that ended the preparation instead.
 */
internal class ImportOutcome(
    val plan: ImportPlan?,
    val result: ImportResult?,
    val library: Map<String, String>,
    val failure: String?,
) {

    /**
     * The SHA-256 of the content each song's pictures and attachments bring, in the plan's order, read from the bytes
     * in memory or from the name the staging gave the file it hashed. [ImportPlan.SongImage] leaves its content out of
     * its equality, so this is what holds two imports to the same bytes before anything is written.
     */
    val contents: List<List<String?>> = plan?.songs.orEmpty().map { song -> song.images.map { it.content?.sha256Hex() } }

    override fun equals(other: Any?) = other is ImportOutcome && plan == other.plan && result == other.result &&
        library == other.library && failure == other.failure && contents == other.contents

    override fun hashCode() = library.hashCode()

    override fun toString() = "ImportOutcome(failure=$failure, plan=$plan, result=$result, library=$library, contents=$contents)"
}

internal fun IncomingContent.sha256Hex() = when (this) {
    is IncomingContent.Bytes -> Sha256.hashToHex(bytes)
    is IncomingContent.Staged -> checkNotNull(file.sha256Hex) { "A staged picture or attachment without its hash: $file" }
}
