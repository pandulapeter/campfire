/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import com.pandulapeter.campfire.data.model.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.annotation.Single
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * The iOS [FileStorage], on top of `NSFileManager`.
 *
 * The library lives in the documents directory so that it can be exposed to the Files app, while the preferences -
 * which are app state rather than user documents - go to the application support directory, out of the user's way.
 */
@Single
internal class IosFileStorage(logger: Logger) : FileStorage by FoundationFileStorage(logger, ::appRootPath)

private fun appRootPath(directory: StorageDirectory): String {
    // The covers are the app's own copies of something that can be downloaded again, so they are no more the
    // Files app's business than the preferences are.
    val isApplicationSupport = directory == StorageDirectory.PREFERENCES || directory == StorageDirectory.COVERS
    return requireNotNull(
        NSFileManager.defaultManager.URLForDirectory(
            directory = if (isApplicationSupport) NSApplicationSupportDirectory else NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            // Unlike the documents directory, application support doesn't exist until something creates it.
            create = isApplicationSupport,
            error = null,
        )?.path
    ) { "Could not resolve the data directory." }
}
