/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.staging

import com.pandulapeter.campfire.data.formats.zip.EntrySink
import com.pandulapeter.campfire.data.model.importing.StagedFile

/**
 * A zip entry written into the staging folder through [sink], handed to [onStaged] once the reader closed it whole and
 * checked. An unpacked file's name is the SHA-256 the sink took of what it wrote, which must be the one the reader took
 * of the entry: one that is not is deleted rather than handed out under a name that does not say what it holds.
 */
internal class StagingEntrySink(
    private val staging: ImportStaging,
    private val sink: StagingSink,
    private val onStaged: (StagedFile) -> Unit,
) : EntrySink {

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = sink.write(bytes, offset, length)

    override suspend fun close(sha256Hex: String) {
        val staged = sink.finish()
        if (staged.sha256Hex != null && staged.sha256Hex != sha256Hex) {
            staging.deleteFile(sink.request, staged)
            throw IllegalStateException("The staged file's SHA-256 is not the entry's.")
        }
        onStaged(staged)
    }

    override suspend fun abandon() = sink.abandon()
}
