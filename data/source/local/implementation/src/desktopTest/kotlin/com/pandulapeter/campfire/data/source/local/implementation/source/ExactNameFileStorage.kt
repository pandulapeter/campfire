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

import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory

/**
 * [delegate] as Android, Linux, and the web find a library file: only under the very spelling it is stored by, where
 * the Mac and Windows file systems also find it under another case or Unicode form.
 */
internal class ExactNameFileStorage(private val delegate: FileStorage) : FileStorage by delegate {

    override suspend fun info(directory: StorageDirectory, name: String) = if (isHeld(directory, name)) delegate.info(directory, name) else null

    override suspend fun exists(directory: StorageDirectory, name: String) = isHeld(directory, name) && delegate.exists(directory, name)

    override suspend fun readText(directory: StorageDirectory, name: String) =
        if (isHeld(directory, name)) delegate.readText(directory, name) else null

    override suspend fun readBytes(directory: StorageDirectory, name: String) =
        if (isHeld(directory, name)) delegate.readBytes(directory, name) else null

    override suspend fun openSource(directory: StorageDirectory, name: String) =
        if (isHeld(directory, name)) delegate.openSource(directory, name) else null

    private suspend fun isHeld(directory: StorageDirectory, name: String) = directory !in LIBRARY || name in delegate.listNames(directory)

    private companion object {
        val LIBRARY = setOf(StorageDirectory.SONGS, StorageDirectory.SETLISTS, StorageDirectory.FILES)
    }
}
