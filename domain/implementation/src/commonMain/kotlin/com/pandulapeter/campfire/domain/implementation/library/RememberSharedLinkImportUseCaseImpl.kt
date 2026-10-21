/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.library

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.recovering
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.library.RememberSharedLinkImportUseCase
import org.koin.core.annotation.Factory

/** How many links [withSharedLinkImport] keeps a record of, the most recently imported ones. */
internal const val MAX_SHARED_LINK_IMPORTS = 100

/**
 * How many names [withSharedLinkImport] keeps for one link, the most recently written ones: a setlist link can hold any
 * number of songs, and `preferences.json` is written again on every change of a preference.
 */
internal const val MAX_SHARED_LINK_IMPORT_PATHS = 1_000

@Factory
internal class RememberSharedLinkImportUseCaseImpl(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val logger: Logger,
) : RememberSharedLinkImportUseCase {

    override suspend operator fun invoke(linkFileId: String, songFileNames: Collection<String>, setlistFileNames: Collection<String>) {
        val paths = songFileNames.map { "${LibraryFileKind.SONG.id}/$it" } + setlistFileNames.map { "${LibraryFileKind.SETLIST.id}/$it" }
        if (linkFileId.isBlank() || paths.isEmpty()) return
        logger.recovering(
            describe = { "Could not remember a shared link's import: ${it.message}" },
            fallback = {},
        ) {
            userPreferencesRepository.updateUserPreferences { it.copy(sharedLinkImports = it.sharedLinkImports.withSharedLinkImport(linkFileId, paths)) }
        }
    }
}

/**
 * The record with [paths] added to [linkFileId]'s entry, which moves to the end as the most recent. A union and never a
 * replacement, since an import does not write what the library already holds unchanged, and a path written again under
 * another spelling of its name keeps only the newer one. Past the limits the oldest links and paths are dropped, which
 * costs only a Replace preselected for them.
 */
internal fun Map<String, List<String>>.withSharedLinkImport(linkFileId: String, paths: Collection<String>): Map<String, List<String>> {
    val merged = (this[linkFileId].orEmpty() + paths)
        .asReversed()
        .distinctBy { path -> path.substringBefore('/') + '/' + LibraryFiles.identityKey(path.substringAfter('/')) }
        .asReversed()
        .takeLast(MAX_SHARED_LINK_IMPORT_PATHS)
    return ((this - linkFileId) + (linkFileId to merged)).toList().takeLast(MAX_SHARED_LINK_IMPORTS).toMap()
}
