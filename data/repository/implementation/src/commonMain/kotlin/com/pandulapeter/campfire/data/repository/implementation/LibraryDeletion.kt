/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.repository.implementation.base.recovering

/** The files a [deleteEach] could not delete, and why. */
internal class RemainingFiles(
    private val fileNames: Set<String>,
    private val failures: List<Exception>,
) {
    operator fun contains(fileName: String) = fileName in fileNames

    fun throwFirstFailure() = failures.firstOrNull()?.let { throw it } ?: Unit
}

/**
 * Deletes every one of [fileNames], going on past a file that fails: a folder that is emptied should be as empty as it
 * can be made, rather than stop at the first file it could not delete and keep everything after it.
 */
internal suspend fun deleteEach(
    logger: Logger,
    fileNames: Collection<String>,
    delete: suspend (String) -> Unit,
): RemainingFiles {
    val remaining = mutableSetOf<String>()
    val failures = mutableListOf<Exception>()
    fileNames.forEach { fileName ->
        logger.recovering(
            describe = { "Could not delete \"$fileName\": ${it.message}" },
            fallback = {
                remaining += fileName
                failures += it
            },
        ) { delete(fileName) }
    }
    return RemainingFiles(fileNames = remaining, failures = failures)
}
