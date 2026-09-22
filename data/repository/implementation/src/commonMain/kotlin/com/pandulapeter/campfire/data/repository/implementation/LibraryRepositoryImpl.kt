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

import com.pandulapeter.campfire.data.repository.api.LibraryRepository
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import org.koin.core.annotation.Single

@Single
internal class LibraryRepositoryImpl(
    private val libraryFileLocalSource: LibraryFileLocalSource,
) : LibraryRepository {

    override suspend fun loadLibrarySize() = libraryFileLocalSource.loadLibraryFiles().sumOf { it.size }
}
