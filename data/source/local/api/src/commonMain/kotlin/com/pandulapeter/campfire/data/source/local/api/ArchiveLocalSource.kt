/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.domain.ImportedFile

interface ArchiveLocalSource {

    /**
     * Every file in a zip archive, with the paths of the entries stripped and archives inside it unpacked too (up to
     * a few levels deep, so that a zip of zips of zips cannot be used to make the app work forever). Throws when the
     * bytes are not a readable archive.
     *
     * Hidden files are not among them: an AppleDouble "._name.cho" or a ".DS_Store" is the archiving tool's own
     * bookkeeping rather than anything the user put in, so the caller never learns of them and never has to account
     * for them. Everything else is returned as it is, unread.
     */
    suspend fun unpack(archive: ByteArray): List<ImportedFile>

    /** Packs the given entries (name to content, the names may contain directories) into a zip archive. */
    suspend fun pack(files: Map<String, ByteArray>): ByteArray
}
